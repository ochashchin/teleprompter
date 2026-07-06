package com.oprojectview.core.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSURL
import platform.Photos.PHAssetChangeRequest
import platform.Photos.PHPhotoLibrary
import platform.Photos.PHAccessLevelAddOnly
import platform.Photos.PHAuthorizationStatus
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusDenied
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHAuthorizationStatusRestricted
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import kotlin.coroutines.resume

// ─── Permission Helper ────────────────────────────────────────────────────────
// In Kotlin/Native 2.x, ObjC class (+) methods on AVCaptureDevice live on
// AVCaptureDevice.Companion (which implements AVCaptureDeviceMeta).

class IosPermissionHelper(
    private val onCameraResult: (Boolean, Boolean) -> Unit,
    private val onAudioResult: (Boolean) -> Unit
) : PermissionHelper {

    override fun hasCameraPermission(): Boolean {
        return AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) ==
                AVAuthorizationStatusAuthorized
    }

    override fun hasAudioPermission(): Boolean {
        return AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio) ==
                AVAuthorizationStatusAuthorized
    }

    override fun requestCameraPermission() {
        val currentStatus = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
        when (currentStatus) {
            AVAuthorizationStatusAuthorized -> {
                // Already granted — report immediately without prompting
                onCameraResult(true, false)
            }
            AVAuthorizationStatusDenied -> {
                // Previously denied — permanently denied (iOS has no "show rationale" concept)
                onCameraResult(false, true)
            }
            else -> {
                // Not determined — show the system prompt
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted: Boolean ->
                    onCameraResult(granted, !granted)
                }
            }
        }
    }

    override fun requestAudioPermission() {
        val currentStatus = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio)
        when (currentStatus) {
            AVAuthorizationStatusAuthorized -> {
                onAudioResult(true)
            }
            AVAuthorizationStatusDenied -> {
                onAudioResult(false)
            }
            else -> {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeAudio) { granted: Boolean ->
                    onAudioResult(granted)
                }
            }
        }
    }

    override fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString)
        if (url != null) {
            UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
        }
    }
}

@Composable
actual fun rememberPermissionHelper(
    onCameraResult: (granted: Boolean, permanentlyDenied: Boolean) -> Unit,
    onAudioResult: (granted: Boolean) -> Unit
): PermissionHelper {
    return remember(onCameraResult, onAudioResult) {
        IosPermissionHelper(onCameraResult, onAudioResult)
    }
}

// ─── Camera Calibrator ────────────────────────────────────────────────────────

class IosCameraCalibrator(
    private val onLaunch: () -> Unit
) : CameraCalibrator {
    override fun launch() {
        onLaunch()
    }
}

@Composable
actual fun rememberCameraCalibrator(
    onCancel: () -> Unit,
    onLaunch: () -> Unit,
    onCalibrationCompleted: (CalibrationData) -> Unit
): CameraCalibrator {
    return remember {
        IosCameraCalibrator {
            onLaunch()
        }
    }
}

// ─── Export to Photos Library ─────────────────────────────────────────────────

private suspend fun requestPhotosPermission(): Boolean {
    val status = PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelAddOnly)
    if (status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited) {
        return true
    }
    if (status == PHAuthorizationStatusDenied || status == PHAuthorizationStatusRestricted) {
        println("exportVideoToGallery: Photos access denied/restricted (status=$status)")
        return false
    }

    // status is PHAuthorizationStatusNotDetermined, request authorization
    return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { newStatus ->
            val granted = newStatus == PHAuthorizationStatusAuthorized || newStatus == PHAuthorizationStatusLimited
            println("exportVideoToGallery: Photos authorization request result (status=$newStatus, granted=$granted)")
            continuation.resume(granted)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun exportVideoToGallery(filePath: String, context: Any, fileName: String) {
    val fileManager = platform.Foundation.NSFileManager.defaultManager
    val sourceURL = NSURL.fileURLWithPath(filePath)
    
    val sourcePath = sourceURL.path
    if (sourcePath == null || !fileManager.fileExistsAtPath(sourcePath)) {
        println("exportVideoToGallery error: source file does not exist at path: $filePath")
        return
    }

    if (!requestPhotosPermission()) {
        println("exportVideoToGallery error: Photo library permission denied")
        return
    }

    val paths = platform.Foundation.NSSearchPathForDirectoriesInDomains(
        platform.Foundation.NSCachesDirectory,
        platform.Foundation.NSUserDomainMask,
        true
    )
    val cacheDirectory = paths.firstOrNull() as? String
    val destinationURL = if (cacheDirectory != null) {
        NSURL.fileURLWithPath("$cacheDirectory/$fileName")
    } else {
        sourceURL.URLByDeletingLastPathComponent?.URLByAppendingPathComponent(fileName)
    }

    val finalURL = if (destinationURL != null) {
        val destinationPath = destinationURL.path
        if (destinationPath != null && fileManager.fileExistsAtPath(destinationPath)) {
            fileManager.removeItemAtURL(destinationURL, null)
        }
        val success = fileManager.copyItemAtURL(sourceURL, destinationURL, null)
        if (success && destinationPath != null && fileManager.fileExistsAtPath(destinationPath)) {
            destinationURL
        } else {
            println("exportVideoToGallery warning: failed to copy item, falling back to sourceURL")
            sourceURL
        }
    } else {
        sourceURL
    }

    val finalPath = finalURL.path
    if (finalPath == null || !fileManager.fileExistsAtPath(finalPath)) {
        println("exportVideoToGallery error: final URL path is invalid or file does not exist: $finalPath")
        return
    }

    kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
        continuation.invokeOnCancellation { }
        PHPhotoLibrary.sharedPhotoLibrary().performChanges({
            PHAssetChangeRequest.creationRequestForAssetFromVideoAtFileURL(finalURL)
        }) { success: Boolean, error: platform.Foundation.NSError? ->
            if (!success) {
                error?.let { println("exportVideoToGallery error: ${it.localizedDescription}") }
            } else {
                println("exportVideoToGallery: Successfully saved video to Photo Library.")
            }
            if (finalURL != sourceURL) {
                val finalPathStr = finalURL.path
                if (finalPathStr != null && fileManager.fileExistsAtPath(finalPathStr)) {
                    fileManager.removeItemAtURL(finalURL, null)
                }
            }
            continuation.resume(Unit)
        }
    }
}

// ─── Calibration Overlay ──────────────────────────────────────────────────────
// Mirrors the Android CalibrationOverlay actual: shows the shared NativeCalibrationView
// (defined in commonMain) layered on top of the camera preview (InAppCameraView).

@Composable
actual fun CalibrationOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    onCalibrationCompleted: (CalibrationData) -> Unit,
    cameraControlState: CameraControlState,
    isHorizontal: Boolean,
    modifier: Modifier
) {
    if (visible) {
        NativeCalibrationView(
            onDismiss = onDismiss,
            onCalibrationCompleted = onCalibrationCompleted,
            zoomOptions = cameraControlState.zoomOptions,
            currentZoomIndex = cameraControlState.currentZoomIndex,
            onZoomIndexChanged = { index ->
                cameraControlState.currentZoomIndex = index
                cameraControlState.zoomRatio = cameraControlState.zoomOptions.getOrNull(index) ?: 1f
            },
            isTorchOn = cameraControlState.flashEnabled,
            onTorchChanged = { enabled ->
                cameraControlState.flashEnabled = enabled
            },
            isFrontCamera = cameraControlState.isFrontCamera,
            onFlipCamera = {
                cameraControlState.isFrontCamera = !cameraControlState.isFrontCamera
                if (cameraControlState.isFrontCamera) {
                    cameraControlState.flashEnabled = false
                }
            },
            isTorchSupported = cameraControlState.isTorchSupported,
            isHorizontal = isHorizontal,
            modifier = modifier
        )
    }
}

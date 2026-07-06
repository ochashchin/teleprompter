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
            UIApplication.sharedApplication.openURL(url)
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

@OptIn(ExperimentalForeignApi::class)
actual suspend fun exportVideoToGallery(filePath: String, context: Any, fileName: String) {
    val fileManager = platform.Foundation.NSFileManager.defaultManager
    val sourceURL = NSURL.fileURLWithPath(filePath)
    
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
        if (success) destinationURL else sourceURL
    } else {
        sourceURL
    }

    kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
        continuation.invokeOnCancellation { }
        PHPhotoLibrary.sharedPhotoLibrary().performChanges({
            PHAssetChangeRequest.creationRequestForAssetFromVideoAtFileURL(finalURL)
        }) { success: Boolean, error: platform.Foundation.NSError? ->
            if (!success) {
                error?.let { println("exportVideoToGallery error: ${it.localizedDescription}") }
            }
            if (finalURL != sourceURL) {
                fileManager.removeItemAtURL(finalURL, null)
            }
            continuation.resume(Unit)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun getUniqueExportFileName(context: Any, fileName: String): String {
    val fileManager = platform.Foundation.NSFileManager.defaultManager
    val paths = platform.Foundation.NSSearchPathForDirectoriesInDomains(
        platform.Foundation.NSCachesDirectory,
        platform.Foundation.NSUserDomainMask,
        true
    )
    val cacheDirectory = paths.firstOrNull() as? String
    
    var name = fileName
    val dotIndex = fileName.lastIndexOf('.')
    val nameWithoutExtension = if (dotIndex != -1) fileName.substring(0, dotIndex) else fileName
    val extension = if (dotIndex != -1) fileName.substring(dotIndex) else ""
    
    var counter = 1
    
    val recentFilenames = mutableSetOf<String>()
    val status = platform.Photos.PHPhotoLibrary.authorizationStatus()
    val hasPermission = status == platform.Photos.PHAuthorizationStatusAuthorized ||
            status == platform.Photos.PHAuthorizationStatusLimited
            
    if (hasPermission) {
        try {
            val fetchOptions = platform.Photos.PHFetchOptions().apply {
                sortDescriptors = listOf(platform.Foundation.NSSortDescriptor.sortDescriptorWithKey("creationDate", false))
                predicate = platform.Foundation.NSPredicate.predicateWithFormat("mediaType == %d", platform.Photos.PHAssetMediaTypeVideo)
            }
            val fetchResult = platform.Photos.PHAsset.fetchAssetsWithOptions(fetchOptions)
            val count = fetchResult.count.toInt()
            val checkCount = minOf(count, 100)
            for (i in 0 until checkCount) {
                val asset = fetchResult.objectAtIndex(i.toULong()) as? platform.Photos.PHAsset
                if (asset != null) {
                    val resources = platform.Photos.PHAssetResource.assetResourcesForAsset(asset)
                    val resource = resources.firstOrNull() as? platform.Photos.PHAssetResource
                    val originalFilename = resource?.originalFilename
                    if (originalFilename != null) {
                        recentFilenames.add(originalFilename)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    while (true) {
        val localPath = if (cacheDirectory != null) "$cacheDirectory/$name" else null
        val existsLocally = localPath != null && fileManager.fileExistsAtPath(localPath)
        val existsInPhotos = recentFilenames.contains(name)
        
        if (!existsLocally && !existsInPhotos) {
            break
        }
        name = "$nameWithoutExtension ($counter)$extension"
        counter++
    }
    return name
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
            },
            modifier = modifier
        )
    }
}

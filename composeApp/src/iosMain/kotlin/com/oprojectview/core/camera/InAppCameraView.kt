@file:OptIn(ExperimentalForeignApi::class)
package com.oprojectview.core.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.dp


import androidx.compose.ui.platform.LocalDensity

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.AVFoundation.*
import platform.CoreGraphics.CGRectZero
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.NSObject
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue

// ─── AVCaptureFileOutput recording delegate ──────────────────────────────────

class MovieFileOutputDelegate(
    private val onVideoSaved: (String) -> Unit
) : NSObject(), AVCaptureFileOutputRecordingDelegateProtocol {

    override fun captureOutput(
        output: AVCaptureFileOutput,
        didFinishRecordingToOutputFileAtURL: NSURL,
        fromConnections: List<*>,
        error: NSError?
    ) {
        val path = didFinishRecordingToOutputFileAtURL.path
        if (error != null) {
            logInfo("[Camera] Recording finished with error: ${error.localizedDescription} (code: ${error.code})")
        } else {
            logInfo("[Camera] Recording finished successfully: $path")
        }
        if (path != null) {
            onVideoSaved(path)
        }
    }
}

// ─── Camera preview UIView ───────────────────────────────────────────────────
// Named class (not an anonymous object) so that the ObjC runtime correctly
// dispatches layoutSubviews() overrides — anonymous Kotlin objects may not
// always register ObjC method overrides with the ObjC runtime.

class CameraPreviewView : UIView(frame = CGRectZero.readValue()) {

    // Set by InAppCameraView after the preview layer is created
    var previewLayer: AVCaptureVideoPreviewLayer? = null
    var isFrontCamera: Boolean = false

    override fun layoutSubviews() {
        super.layoutSubviews()
        val layer = previewLayer ?: return
        val b = this.bounds
        layer.frame = b
        
        // Sync corner radius and masksToBounds to the preview layer to clip camera content
        layer.cornerRadius = this.layer.cornerRadius
        layer.masksToBounds = true
        layer.videoGravity = AVLayerVideoGravityResizeAspect
        
        // Configure video connection orientation and mirroring to center/mirror the video feed
        layer.connection?.let { connection ->
            if (connection.isVideoOrientationSupported()) {
                connection.videoOrientation = AVCaptureVideoOrientationPortrait
            }
            if (connection.isVideoMirroringSupported()) {
                connection.automaticallyAdjustsVideoMirroring = false
                connection.videoMirrored = isFrontCamera
            }
        }
        
        clearParentBackgrounds()
        
        val width = b.useContents { size.width }
        val height = b.useContents { size.height }
        logInfo("[Camera] layoutSubviews called: bounds=${width}x${height}, cornerRadius=${layer.cornerRadius}")
    }

    override fun didMoveToWindow() {
        super.didMoveToWindow()
        clearParentBackgrounds()
        dispatch_async(dispatch_get_main_queue()) {
            clearParentBackgrounds()
        }
    }

    private fun clearParentBackgrounds() {
        this.opaque = false
        this.backgroundColor = UIColor.clearColor
        this.layer.backgroundColor = UIColor.clearColor.CGColor
        this.layer.masksToBounds = true
        
        var parent = this.superview
        while (parent != null && parent !is platform.UIKit.UIWindow) {
            parent.opaque = false
            parent.backgroundColor = UIColor.clearColor
            parent.layer.backgroundColor = UIColor.clearColor.CGColor
            parent = parent.superview
        }
    }
}



@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun InAppCameraView(
    modifier: Modifier,
    taskId: Int,
    calibrationData: CalibrationData,
    isRecording: Boolean,
    onVideoSaved: (String) -> Unit,
    onZoomStateAvailable: (Float, Float) -> Unit,
    onPreviewStateChanged: (Boolean) -> Unit,
    onTorchStateAvailable: (Boolean) -> Unit,
    alpha: Float,
    cornerRadiusDp: androidx.compose.ui.unit.Dp
) {
    val currentOnTorchStateAvailable by rememberUpdatedState(onTorchStateAvailable)
    LaunchedEffect(calibrationData.isFrontCamera) {
        if (calibrationData.isFrontCamera) {
            currentOnTorchStateAvailable(false)
        }
    }
    val session = remember { AVCaptureSession() }
    val previewLayer = remember {
        AVCaptureVideoPreviewLayer(session = session).also {
            // Set gravity early — before the first layout pass — so the very first
            // rendered frame already fills the container correctly.
            it.videoGravity = AVLayerVideoGravityResizeAspect
        }
    }
    val movieOutput = remember { AVCaptureMovieFileOutput() }
    val recordDelegate = remember { MovieFileOutputDelegate(onVideoSaved) }

    // Track the currently active camera device for dynamic zoom/torch updates
    var activeDevice by remember { mutableStateOf<AVCaptureDevice?>(null) }
    
    val isSimulator = remember {
        platform.Foundation.NSProcessInfo.processInfo.environment.containsKey("SIMULATOR_DEVICE_NAME")
    }

    // Preview readiness state — drives the black loading overlay
    var isPreviewActive by remember { mutableStateOf(false) }

    // Retry counter resets when taskId or camera lens changes, matching Android's rebindCounter logic
    var rebindCounter by remember(taskId, calibrationData.isFrontCamera) { mutableStateOf(0) }

    // UIView container that hosts the AVCaptureVideoPreviewLayer as a sublayer.
    // CameraPreviewView is a named class so the ObjC runtime correctly dispatches
    // layoutSubviews() — anonymous Kotlin objects may silently skip ObjC method
    // registration, causing layoutSubviews to never fire.
    val containerView = remember {
        CameraPreviewView().also { view ->
            view.previewLayer = previewLayer
            view.isFrontCamera = calibrationData.isFrontCamera
            view.clipsToBounds = true
            view.layer.addSublayer(previewLayer)
        }
    }

    // ── Session configuration and restart ─────────────────────────────────
    // Only triggered when: taskId changes, camera lens (front/back) flips, or rebindCounter ticks.
    // Zoom and torch changes are handled separately below to avoid black frames.
    LaunchedEffect(taskId, calibrationData.isFrontCamera, rebindCounter) {
        isPreviewActive = false

        // Stop existing session on a background thread before reconfiguring
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0uL)) {
            if (session.isRunning()) session.stopRunning()
        }
        // Small delay to allow the session to fully stop before reconfiguring
        delay(150)

        session.beginConfiguration()

        // Remove all existing inputs and outputs
        session.inputs.toList().forEach { session.removeInput(it as AVCaptureInput) }
        session.outputs.toList().forEach { session.removeOutput(it as AVCaptureOutput) }

        // Set session preset (1920x1080 matches Android's Quality.FHD target)
        if (session.canSetSessionPreset(AVCaptureSessionPreset1920x1080)) {
            session.sessionPreset = AVCaptureSessionPreset1920x1080
        } else if (session.canSetSessionPreset(AVCaptureSessionPreset1280x720)) {
            session.sessionPreset = AVCaptureSessionPreset1280x720
        }

        // ── Device selection via DiscoverySession (iOS 15+) ───────────────
        val position = if (calibrationData.isFrontCamera) {
            AVCaptureDevicePositionFront
        } else {
            AVCaptureDevicePositionBack
        }

        val deviceTypes = if (calibrationData.isFrontCamera) {
            listOf(AVCaptureDeviceTypeBuiltInWideAngleCamera)
        } else {
            listOf(
                AVCaptureDeviceTypeBuiltInTripleCamera,
                AVCaptureDeviceTypeBuiltInDualWideCamera,
                AVCaptureDeviceTypeBuiltInDualCamera,
                AVCaptureDeviceTypeBuiltInWideAngleCamera
            )
        }

        var selectedDevice: AVCaptureDevice? = null
        for (type in deviceTypes) {
            val discovery = AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
                deviceTypes = listOf(type),
                mediaType = AVMediaTypeVideo,
                position = position
            )
            selectedDevice = discovery.devices.filterIsInstance<AVCaptureDevice>().firstOrNull()
            if (selectedDevice != null) break
        }

        val device = selectedDevice
        if (device != null) {
            onTorchStateAvailable(!calibrationData.isFrontCamera && device.hasTorch)
            try {
                // ── Add video input ────────────────────────────────────────
                val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null)
                if (input != null && session.canAddInput(input)) {
                    session.addInput(input)
                }

                // ── Add audio input (only if authorized) ─────────────────
                val audioStatus = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio)
                if (audioStatus == AVAuthorizationStatusAuthorized) {
                    val audioDevice = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeAudio)
                    if (audioDevice != null) {
                        val audioInput = AVCaptureDeviceInput.deviceInputWithDevice(audioDevice, null)
                        if (audioInput != null && session.canAddInput(audioInput)) {
                            session.addInput(audioInput)
                        }
                    }
                }

                // ── Add movie file output ─────────────────────────────────
                if (session.canAddOutput(movieOutput)) {
                    session.addOutput(movieOutput)
                }

                // ── Lock device and apply initial configuration ─────────
                if (device.lockForConfiguration(null)) {
                    // Apply initial zoom
                    val minZoom = device.minAvailableVideoZoomFactor.toFloat()
                    val actualMaxZoom = device.activeFormat.videoMaxZoomFactor.toFloat()
                    val reportedMaxZoom = if (isSimulator) 10f else actualMaxZoom
                    
                    val clampedZoom = calibrationData.zoomRatio.coerceIn(minZoom, actualMaxZoom)
                    device.videoZoomFactor = clampedZoom.toDouble()

                    // Apply exposure bias
                    val bias = calibrationData.exposureBias.coerceIn(
                        device.minExposureTargetBias,
                        device.maxExposureTargetBias
                    )
                    device.setExposureTargetBias(bias, null)

                    // Apply torch state (back camera only — front has no torch)
                    if (device.isTorchModeSupported(AVCaptureTorchModeOn)) {
                        device.torchMode = if (calibrationData.flashEnabled) {
                            AVCaptureTorchModeOn
                        } else {
                            AVCaptureTorchModeOff
                        }
                    }

                    device.unlockForConfiguration()

                    // Report zoom range to parent — populates the zoom selector UI
                    onZoomStateAvailable(minZoom, reportedMaxZoom.coerceAtMost(10f))
                }

                activeDevice = device

            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // Simulator or headless environment fallback:
            // Report mock zoom state so the Zoom buttons render on screen.
            onTorchStateAvailable(false)
            onZoomStateAvailable(1f, 10f)
            isPreviewActive = true
            activeDevice = null
        }

        session.commitConfiguration()

        // Configure video connection orientation and mirroring immediately after committing configuration
        previewLayer.connection?.let { connection ->
            if (connection.isVideoOrientationSupported()) {
                connection.videoOrientation = AVCaptureVideoOrientationPortrait
            }
            if (connection.isVideoMirroringSupported()) {
                connection.automaticallyAdjustsVideoMirroring = false
                connection.videoMirrored = calibrationData.isFrontCamera
            }
        }

        // Start session on background thread (required by Apple)
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0uL)) {
            session.startRunning()

            // Once running, update preview state on main thread
            dispatch_async(dispatch_get_main_queue()) {
                isPreviewActive = session.isRunning()
                
                // Configure video connection orientation and mirroring once the session starts running
                previewLayer.connection?.let { connection ->
                    if (connection.isVideoOrientationSupported()) {
                        connection.videoOrientation = AVCaptureVideoOrientationPortrait
                    }
                    if (connection.isVideoMirroringSupported()) {
                        connection.automaticallyAdjustsVideoMirroring = false
                        connection.videoMirrored = calibrationData.isFrontCamera
                    }
                }
            }
        }
    }

    // ── Preview readiness observation with retry ───────────────────────────
    // Mirrors Android's 1-second timeout + 3 rebind retries, then forced true.
    val scope = rememberCoroutineScope()
    LaunchedEffect(rebindCounter, activeDevice) {
        if (isSimulator && activeDevice == null) {
            isPreviewActive = true
            return@LaunchedEffect
        }

        val job = scope.launch {
            delay(1500)
            if (!isPreviewActive) {
                if (rebindCounter < 3) {
                    rebindCounter++
                } else {
                    // All retries exhausted — force true as last resort
                    isPreviewActive = true
                }
            }
        }
        // Poll session.isRunning to detect when the camera becomes live
        var checked = 0
        while (!isPreviewActive && checked < 15) {
            delay(100)
            if (session.isRunning()) {
                isPreviewActive = true
                job.cancel()
            }
            checked++
        }
    }

    val currentOnPreviewStateChanged by rememberUpdatedState(onPreviewStateChanged)
    LaunchedEffect(isPreviewActive) {
        currentOnPreviewStateChanged(isPreviewActive)
    }

    // ── Dynamic zoom and torch updates (no session restart) ───────────────
    // Exactly matches Android's LaunchedEffect(zoomRatio, flashEnabled, cameraControl)
    LaunchedEffect(calibrationData.zoomRatio, calibrationData.flashEnabled, activeDevice) {
        val device = activeDevice ?: return@LaunchedEffect
        try {
            if (device.lockForConfiguration(null)) {
                // Update zoom without restarting the session
                val minZoom = device.minAvailableVideoZoomFactor.toFloat()
                val actualMaxZoom = device.activeFormat.videoMaxZoomFactor.toFloat()
                val clampedZoom = calibrationData.zoomRatio.coerceIn(minZoom, actualMaxZoom)
                device.videoZoomFactor = clampedZoom.toDouble()

                // Update torch without restarting the session
                if (device.isTorchModeSupported(AVCaptureTorchModeOn)) {
                    device.torchMode = if (calibrationData.flashEnabled) {
                        AVCaptureTorchModeOn
                    } else {
                        AVCaptureTorchModeOff
                    }
                }

                device.unlockForConfiguration()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ── Stop session on dispose ────────────────────────────────────────────
    DisposableEffect(Unit) {
        onDispose {
            if (movieOutput.isRecording()) {
                movieOutput.stopRecording()
            }
            dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0uL)) {
                session.stopRunning()
            }
        }
    }

    // ── Recording state management ─────────────────────────────────────────
    var wasRecording by remember { mutableStateOf(false) }
    LaunchedEffect(isRecording) {
        if (isRecording) {
            wasRecording = true
            if (!movieOutput.isRecording()) {
                // Wait for the session to be fully running and the video connection to be established
                // before attempting to start recording. This prevents the "No active/enabled connections" crash.
                var retries = 0
                while (!session.isRunning() || movieOutput.connectionWithMediaType(AVMediaTypeVideo) == null) {
                    if (retries > 50) break // Timeout after 5 seconds to prevent infinite suspension
                    delay(100)
                    retries++
                }

                val paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
                val cacheDirectory = paths.firstOrNull() as? String
                if (cacheDirectory != null) {
                    val timestamp = NSDate().timeIntervalSince1970.toLong()
                    val fileURL = NSURL.fileURLWithPath("$cacheDirectory/recorded_video_$timestamp.mp4")
                    
                    // Retrieve and configure the video connection on movieOutput
                    val videoConnection = movieOutput.connectionWithMediaType(AVMediaTypeVideo)
                    if (videoConnection != null) {
                        videoConnection.enabled = true
                        if (videoConnection.isVideoMirroringSupported()) {
                            videoConnection.videoMirrored = calibrationData.isFrontCamera
                        }
                        if (videoConnection.isVideoOrientationSupported()) {
                            videoConnection.videoOrientation = AVCaptureVideoOrientationPortrait
                        }
                        logInfo("[Camera] Starting recording to: $fileURL. Video connection configured.")
                        movieOutput.startRecordingToOutputFileURL(fileURL, recordDelegate)
                    } else {
                        logInfo("[Camera] Warning: No video connection found for movieOutput! Skipping recording to avoid crash.")
                    }
                }
            }
        } else {
            if (movieOutput.isRecording()) {
                movieOutput.stopRecording()
            }
            // If recording was requested but never actually started (device limitation),
            // signal completion so the player screen doesn't wait forever.
            if (wasRecording && !movieOutput.isRecording()) {
                wasRecording = false
            }
        }
    }

    // ── Camera preview UI ─────────────────────────────────────────────────
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier) {
        UIKitView(
            factory = { containerView },
            modifier = Modifier.fillMaxSize(),
            properties = UIKitInteropProperties(
                placedAsOverlay = cornerRadiusDp > 0.dp || alpha < 1.0f
            ),
            update = { view ->
                val radius = cornerRadiusDp.value.toDouble()
                view.isFrontCamera = calibrationData.isFrontCamera
                
                // Set the corner radius and clipping properties BEFORE calling layoutIfNeeded()
                view.layer.cornerRadius = radius
                view.layer.masksToBounds = true
                view.clipsToBounds = true
                
                // Set frame dynamically from Compose layout constraints to bypass UIKit layout latency/bugs
                previewLayer.frame = kotlinx.cinterop.cValue {
                    origin.x = 0.0
                    origin.y = 0.0
                    size.width = maxWidth.value.toDouble()
                    size.height = maxHeight.value.toDouble()
                }
                
                previewLayer.cornerRadius = radius
                previewLayer.masksToBounds = true
                previewLayer.videoGravity = AVLayerVideoGravityResizeAspect
                
                // Configure video connection orientation and mirroring to center/mirror the video feed
                previewLayer.connection?.let { connection ->
                    if (connection.isVideoOrientationSupported()) {
                        connection.videoOrientation = AVCaptureVideoOrientationPortrait
                    }
                    if (connection.isVideoMirroringSupported()) {
                        connection.automaticallyAdjustsVideoMirroring = false
                        connection.videoMirrored = calibrationData.isFrontCamera
                    }
                }
                
                // Apply transparency at the CALayer level (clamped to 0.01 minimum to avoid AVFoundation pause)
                previewLayer.opacity = if (alpha > 0f) alpha else 0.01f
                view.hidden = (alpha == 0f)
                view.userInteractionEnabled = (alpha > 0f)
                
                // Clear backgrounds of view and all its parent wrappers to ensure transparency works
                view.opaque = false
                view.backgroundColor = UIColor.clearColor
                view.layer.backgroundColor = UIColor.clearColor.CGColor
                var parent = view.superview
                while (parent != null && parent !is platform.UIKit.UIWindow) {
                    parent.opaque = false
                    parent.backgroundColor = UIColor.clearColor
                    parent.layer.backgroundColor = UIColor.clearColor.CGColor
                    parent = parent.superview
                }
                
                logInfo("[Camera] update called: alpha=$alpha, cornerRadius=${view.layer.cornerRadius}, previewOpacity=${previewLayer.opacity}")
            }
        )

        if (!isPreviewActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .alpha(alpha)
            )
        }
    }
}

// Helper to log both to standard output and Apple's NSLog (syslog)
private fun logInfo(msg: String) {
    println(msg)
    platform.Foundation.NSLog(msg.replace("%", "%%"))
}

actual fun isIos(): Boolean = true

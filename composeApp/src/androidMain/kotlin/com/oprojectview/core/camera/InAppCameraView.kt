package com.oprojectview.core.camera

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.View
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.Executor
import android.util.Size
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape


import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

@SuppressLint("RestrictedApi")
@Composable
actual fun InAppCameraView(
    modifier: Modifier,
    taskId: Int,
    calibrationData: CalibrationData,
    isRecording: Boolean,
    onVideoSaved: (String) -> Unit,
    onZoomStateAvailable: (Float, Float) -> Unit,
    onPreviewStateChanged: (Boolean) -> Unit,
    alpha: Float,
    cornerRadiusDp: androidx.compose.ui.unit.Dp
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    
    val previewView = remember { 
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var currentVideoFile by remember { mutableStateOf<File?>(null) }
    
    var activeVideoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Box(modifier = modifier)
        return
    }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var isPreviewActive by remember { mutableStateOf(false) }
    
    // Track rebind retries on camera initialization failure/stuck state.
    // Reset to 0 whenever the task or camera lens changes.
    var rebindCounter by remember(taskId, calibrationData.isFrontCamera) { mutableStateOf(0) }
    
    // Initialize CameraX provider and bind use cases
    // We bind only when the task ID, lens facing (front/back), permission, lifecycle, or rebindCounter changes.
    LaunchedEffect(taskId, calibrationData.isFrontCamera, hasCameraPermission, lifecycleOwner, rebindCounter) {
        if (!hasCameraPermission) return@LaunchedEffect
        isPreviewActive = false
        
        val provider = suspendCoroutine<ProcessCameraProvider> { continuation ->
            ProcessCameraProvider.getInstance(context).addListener({
                continuation.resume(ProcessCameraProvider.getInstance(context).get())
            }, mainExecutor)
        }
        cameraProvider = provider

        provider.unbindAll()

        // 1. Determine Aspect Ratio from calibration
        val calculatedRatio = calibrationData.width.toFloat() / calibrationData.height.toFloat()
        val targetRatio = if (Math.abs(calculatedRatio - 1.77f) < Math.abs(calculatedRatio - 1.33f)) {
            AspectRatio.RATIO_16_9
        } else {
            AspectRatio.RATIO_4_3
        }

        // 2. Select Lens
        val cameraSelector = if (calibrationData.isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        // 3. Configure Preview with a standard minimum resolution and aspect ratio to avoid emulator crashes in minimized mode
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy(
                    targetRatio,
                    AspectRatioStrategy.FALLBACK_RULE_AUTO
                )
            )
            .setResolutionStrategy(
                ResolutionStrategy(
                    if (targetRatio == AspectRatio.RATIO_16_9) Size(1280, 720) else Size(640, 480),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()

        val preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()


        try {
            // 4. Configure Video Capture using Recorder (bound on startup to avoid recording gaps)
            val quality = if (targetRatio == AspectRatio.RATIO_16_9) Quality.FHD else Quality.SD
            val qualitySelector = QualitySelector.from(
                quality,
                FallbackStrategy.lowerQualityOrHigherThan(quality)
            )
            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()
            val videoCapture = VideoCapture.withOutput(recorder)
            activeVideoCapture = videoCapture

            provider.unbindAll() // Explicitly clear any lingering bindings
            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                videoCapture
            )
            preview.setSurfaceProvider(previewView.surfaceProvider)
            
            cameraControl = camera.cameraControl
            
            // Observe zoom state to notify parent
            camera.cameraInfo.zoomState.observe(lifecycleOwner) { zoomState ->
                if (zoomState != null) {
                    onZoomStateAvailable(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                }
            }
            
            // 5. Apply Zoom safely
            try {
                val zoomState = camera.cameraInfo.zoomState.value
                val minZoom = zoomState?.minZoomRatio ?: 1f
                val maxZoom = zoomState?.maxZoomRatio ?: 1f
                camera.cameraControl.setZoomRatio(calibrationData.zoomRatio.coerceIn(minZoom, maxZoom))
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // 6. Apply Exposure Bias safely
            try {
                val exposureState = camera.cameraInfo.exposureState
                if (exposureState.isExposureCompensationSupported) {
                    val step = exposureState.exposureCompensationStep.toFloat()
                    if (step > 0f) {
                        val index = Math.round(calibrationData.exposureBias / step)
                        val clampedIndex = index.coerceIn(
                            exposureState.exposureCompensationRange.lower,
                            exposureState.exposureCompensationRange.upper
                        )
                        camera.cameraControl.setExposureCompensationIndex(clampedIndex)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 7. Apply Torch / Flash safely
            try {
                camera.cameraControl.enableTorch(calibrationData.flashEnabled)
            } catch (e: Exception) {
                e.printStackTrace()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback strategy if VideoCapture fails on emulators
            try {
                provider.unbindAll()
                val previewOnlyCamera = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview
                )
                preview.setSurfaceProvider(previewView.surfaceProvider)
                cameraControl = previewOnlyCamera.cameraControl
                
                previewOnlyCamera.cameraInfo.zoomState.observe(lifecycleOwner) { zoomState ->
                     if (zoomState != null) {
                         onZoomStateAvailable(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                     }
                }
                
                try {
                    val zoomState = previewOnlyCamera.cameraInfo.zoomState.value
                    val minZoom = zoomState?.minZoomRatio ?: 1f
                    val maxZoom = zoomState?.maxZoomRatio ?: 1f
                    previewOnlyCamera.cameraControl.setZoomRatio(calibrationData.zoomRatio.coerceIn(minZoom, maxZoom))
                } catch (zEx: Exception) {
                    zEx.printStackTrace()
                }
            } catch (fallbackEx: Exception) {
                fallbackEx.printStackTrace()
            }
        }
    }

    // Apply Zoom & Torch dynamically without re-binding/blinking camera
    LaunchedEffect(calibrationData.zoomRatio, calibrationData.flashEnabled, cameraControl) {
        val ctrl = cameraControl ?: return@LaunchedEffect
        try {
            ctrl.enableTorch(calibrationData.flashEnabled)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        try {
            ctrl.setZoomRatio(calibrationData.zoomRatio)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Observe preview view stream state with a timeout fallback/recovery for emulator reliability
    LaunchedEffect(previewView, lifecycleOwner, cameraProvider, rebindCounter) {
        if (cameraProvider == null) return@LaunchedEffect
        
        val job = launch {
            delay(1000)
            if (!isPreviewActive) {
                if (rebindCounter < 3) {
                    rebindCounter++
                } else {
                    // Fallback to true as a last resort if all retries are exhausted
                    isPreviewActive = true
                }
            }
        }
        
        previewView.previewStreamState.observe(lifecycleOwner) { streamState ->
            if (streamState == PreviewView.StreamState.STREAMING) {
                isPreviewActive = true
                job.cancel()
            }
        }
    }

    val currentOnPreviewStateChanged by rememberUpdatedState(onPreviewStateChanged)
    LaunchedEffect(isPreviewActive) {
        currentOnPreviewStateChanged(isPreviewActive)
    }

    DisposableEffect(cameraProvider) {
        onDispose {
            activeRecording?.stop()
            activeRecording = null
            cameraProvider?.unbindAll()
        }
    }

    var wasRecording by remember { mutableStateOf(false) }
    
    // Handle recording state updates
    LaunchedEffect(isRecording, activeVideoCapture) {
        if (isRecording) {
            wasRecording = true
            val videoCapture = activeVideoCapture ?: return@LaunchedEffect
            val file = File(context.cacheDir, "recorded_video_${System.currentTimeMillis()}.mp4")
            currentVideoFile = file

            val outputOptions = FileOutputOptions.Builder(file).build()
            
            try {
                var recordingBuilder = videoCapture.output
                    .prepareRecording(context, outputOptions)
                
                if (hasAudioPermission) {
                    @SuppressLint("MissingPermission")
                    recordingBuilder = recordingBuilder.withAudioEnabled()
                }

                val recording = recordingBuilder
                    .start(mainExecutor) { recordEvent ->
                        if (recordEvent is VideoRecordEvent.Finalize) {
                            wasRecording = false
                            onVideoSaved(file.absolutePath)
                        }
                    }
                activeRecording = recording
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            activeRecording?.stop()
            activeRecording = null
            
            // If recording was requested but unsupported by device, simulate completion
            // so the player screen doesn't hang waiting for the video.
            if (wasRecording && activeVideoCapture == null) {
                wasRecording = false
                onVideoSaved("")
            }
        }
    }

    Box(
        modifier = modifier
            .alpha(alpha)
            .clip(RoundedCornerShape(cornerRadiusDp))
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
        
        if (!isPreviewActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun InAppCameraViewPreview() {
    val dp16 = androidx.compose.ui.unit.Dp(16f)
    InAppCameraView(
        modifier = Modifier.fillMaxSize(),
        taskId = 1,
        calibrationData = CalibrationData(
            zoomRatio = 1.0f,
            exposureBias = 0.0f,
            isFrontCamera = true,
            width = 1920,
            height = 1080,
            orientation = 0,
            flashEnabled = false
        ),
        isRecording = false,
        onVideoSaved = {},
        onZoomStateAvailable = { _, _ -> },
        onPreviewStateChanged = {},
        alpha = 1.0f,
        cornerRadiusDp = dp16
    )
}

// Simple suspendCoroutine helper wrapper since standard Kotlin Coroutines are imported
private suspend inline fun <T> suspendCoroutine(crossinline block: (kotlin.coroutines.Continuation<T>) -> Unit): T {
    return kotlin.coroutines.suspendCoroutine { block(it) }
}

actual fun isIos(): Boolean = false


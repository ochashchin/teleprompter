package com.oprojectview.core.camera

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

interface CameraCalibrator {
    fun launch()
}

class CameraControlState {
    var zoomRatio by mutableStateOf(1f)
    var flashEnabled by mutableStateOf(false)
    var isFrontCamera by mutableStateOf(true)
    var zoomOptions by mutableStateOf(listOf(1f))
    var currentZoomIndex by mutableStateOf(0)
    var isTorchSupported by mutableStateOf(false)
}

interface PermissionHelper {
    fun hasCameraPermission(): Boolean
    fun hasAudioPermission(): Boolean
    fun requestCameraPermission()
    fun requestAudioPermission()
    fun openSettings()
}

@Composable
expect fun rememberPermissionHelper(
    onCameraResult: (granted: Boolean, permanentlyDenied: Boolean) -> Unit,
    onAudioResult: (granted: Boolean) -> Unit
): PermissionHelper

@Composable
expect fun rememberCameraCalibrator(
    onCancel: () -> Unit = {},
    onLaunch: () -> Unit = {},
    onCalibrationCompleted: (CalibrationData) -> Unit
): CameraCalibrator

expect suspend fun exportVideoToGallery(filePath: String, context: Any, fileName: String)

@Composable
expect fun CalibrationOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    onCalibrationCompleted: (CalibrationData) -> Unit,
    cameraControlState: CameraControlState,
    isHorizontal: Boolean = false,
    modifier: Modifier = Modifier
)


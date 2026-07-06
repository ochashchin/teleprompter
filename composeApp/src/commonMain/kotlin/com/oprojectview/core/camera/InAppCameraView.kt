package com.oprojectview.core.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
expect fun InAppCameraView(
    modifier: Modifier,
    taskId: Int,
    calibrationData: CalibrationData,
    isRecording: Boolean,
    onVideoSaved: (String) -> Unit,
    onZoomStateAvailable: (Float, Float) -> Unit = { _, _ -> },
    onPreviewStateChanged: (Boolean) -> Unit = {},
    onTorchStateAvailable: (Boolean) -> Unit = {},
    alpha: Float = 1f,
    cornerRadiusDp: Dp = 0.dp
)

expect fun isIos(): Boolean



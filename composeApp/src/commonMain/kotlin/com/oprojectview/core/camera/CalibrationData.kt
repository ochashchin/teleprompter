package com.oprojectview.core.camera

data class CalibrationData(
    val zoomRatio: Float = 1.0f,
    val exposureBias: Float = 0.0f,
    val isFrontCamera: Boolean = true,
    val width: Int = 1920,
    val height: Int = 1080,
    val orientation: Int = 0,
    val flashEnabled: Boolean = false
)

package com.example.kotlinmultiplatform.frame

import androidx.compose.ui.Modifier

/**
 * No-op stub.  The pixel-capture pipeline has been removed.
 *
 * Android PiP shows the Activity window (Compose PlayerScreen) directly.
 * No pixel copying, GraphicsLayer, or MediaCodec encoding is required.
 */
fun Modifier.pipCapture(
    captureState: FrameCaptureState,
    enabled: Boolean = true,
): Modifier = this

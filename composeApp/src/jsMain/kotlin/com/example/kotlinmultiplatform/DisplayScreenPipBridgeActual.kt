package com.example.kotlinmultiplatform

import androidx.compose.runtime.Composable
import com.example.kotlinmultiplatform.features.display.DisplayViewModel
import com.example.kotlinmultiplatform.features.player.PlayerViewModel
import com.example.kotlinmultiplatform.frame.FrameViewModel

/** JS/Web: PiP not supported. No-op. */
@Composable
actual fun DisplayScreenPipBridgeIfNeeded(
    taskId:    Int,
    displayVm: DisplayViewModel,
    playerVm:  PlayerViewModel,
    frameVm:   FrameViewModel,
) = Unit

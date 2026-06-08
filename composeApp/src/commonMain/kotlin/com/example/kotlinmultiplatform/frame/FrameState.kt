package com.example.kotlinmultiplatform.frame

import com.example.kotlinmultiplatform.AnimationMode
import com.example.kotlinmultiplatform.TransitionMode

/**
 * Immutable snapshot of every parameter that controls what the frame renderer
 * draws.  Produced by [FrameViewModel] from the live [DisplayTaskState] +
 * [PlayerState]; consumed by [FrameProducer] each tick.
 *
 * All fields have safe defaults so the pipeline can start without a task.
 */
data class FrameState(
    // ── text / display ────────────────────────────────────────────────────────
    val textSizeIndex:   Int            = 1,            // index into NORMAL_SIZES
    val isHorizontal:    Boolean        = false,
    val wpm:             Int            = 130,
    val animationMode:   AnimationMode  = AnimationMode.Frame,
    val transitionMode:  TransitionMode = TransitionMode.None,
    val distortionMode:  Float          = 1f,           // 1f / 1.4f / 2.9f
    val isMirror:        Boolean        = false,

    // ── PiP gate ─────────────────────────────────────────────────────────────
    // Mirrors DisplayTaskList item id=8 (Overlay).
    // PipController only calls enterPictureInPictureMode() when this is true.
    val overlayEnabled:  Boolean        = false,

    // ── pipeline control ──────────────────────────────────────────────────────
    val isPlaying:       Boolean        = false,
    val targetFps:       Int            = 60,
    val frameWidthPx:    Int            = 540,
    val frameHeightPx:   Int            = 960,
)

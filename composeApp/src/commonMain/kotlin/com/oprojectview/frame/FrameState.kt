package com.oprojectview.frame

import com.oprojectview.AnimationMode
import com.oprojectview.StyleSpan
import com.oprojectview.TransitionMode

/**
 * Immutable snapshot of every parameter that controls the frame renderer.
 *
 * ## Source-layout aspect-ratio contract
 *
 * [frameWidthPx] and [frameHeightPx] represent the **stable source-layout
 * canvas** — the dimensions at which Compose is rendered offscreen.
 *
 * On iOS these are set once from `UIScreen.mainScreen.nativeBounds` (device
 * native pixels) via [FrameVmIntent.SetFrameSize]. They are never updated in
 * response to PiP viewport changes — doing so would violate the spec:
 *
 * > "The renderer must NEVER re-layout the Compose subtree to match the PiP
 * > aspect ratio directly."
 *
 * PiP is a viewport into this canvas. `AVLayerVideoGravityResizeAspect` on
 * the `AVSampleBufferDisplayLayer` scales the frames to fit the PiP window
 * while preserving the source aspect ratio.
 *
 * ## [pipSizeClass] — layout profile switching
 *
 * Set by `IosPipController` when the PiP window is resized. Compose UI
 * observes this field to switch between compact and expanded content density.
 * It does NOT affect [frameWidthPx] / [frameHeightPx].
 */
data class FrameState(
    // ── text / display ────────────────────────────────────────────────────────
    val textSizeIndex:   Int            = 1,
    val isHorizontal:    Boolean        = false,
    val wpm:             Int            = 130,
    /** Color applied to the background if [fillColorVal] is 0L, sourced from MaterialTheme. */
    val defaultFillColorVal: Long = 0L,

    /** Default color applied to the text, sourced from MaterialTheme. */
    val textColorVal: Long = 0L,

    val primaryColorVal: Long = 0L,
    val surfaceVariantColorVal: Long = 0L,

    val animationMode:   AnimationMode  = AnimationMode.Frame,
    val transitionMode: TransitionMode = TransitionMode.None,
    val distortionMode:  Float          = 1f,
    val isMirror:        Boolean        = false,
    val isLoopEnabled:   Boolean        = false,

    // ── PiP gate ──────────────────────────────────────────────────────────────
    val overlayEnabled:  Boolean        = false,

    // ── pipeline control ──────────────────────────────────────────────────────
    val isPlaying:       Boolean        = false,
    val targetFps:       Int            = 60,

    // ── teleprompter content ──────────────────────────────────────────────────
    val scriptText:      String                          = "",
    val styleSpans:      List<StyleSpan> = emptyList(),
    val fillColorVal:    Long                            = 0L,
    val scrollFraction:  Float                           = 0f,
    val countdownDone:   Boolean                         = false,
    val countdownStartUs: Long                           = 0L,
    val pausedCountdownElapsedUs: Long                   = 0L,
    val playbackStartUs: Long                            = 0L,
    val pausedElapsedUs: Long                            = 0L,
    val totalDurationMs: Long                            = 0L,

    /**
     * Source-layout canvas width in native pixels.
     *
     * Default 540 is used until `MainViewController` calls
     * [FrameVmIntent.SetFrameSize] with the real screen size.
     * Never changes after initial setup — frozen to preserve aspect ratio.
     */
    val frameWidthPx:    Int            = 540,

    /**
     * Source-layout canvas height in native pixels.
     *
     * Default 960 is used until `MainViewController` calls
     * [FrameVmIntent.SetFrameSize] with the real screen size.
     * Never changes after initial setup — frozen to preserve aspect ratio.
     */
    val frameHeightPx:   Int            = 960,

    /**
     * Current PiP visual size class — drives Compose layout profile.
     *
     * Compact → simplified content, larger typography, low redraw rate.
     * Expanded → full layout, richer data, higher redraw rate.
     *
     * Null when PiP is not active.
     */
    val pipSizeClass:    PipSizeClass?  = null,

    /**
     * True while PiP is active and `IosPipController` has taken ownership of
     * [frameWidthPx] / [frameHeightPx]. While true, `PlayerScreen.onSizeChanged`
     * must NOT call [FrameVmIntent.SetFrameSize] so it doesn't stomp the
     * live PiP viewport dimensions.
     */
    val pipSizeOverrideActive: Boolean = false,
)

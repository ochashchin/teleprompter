package com.oprojectview.frame

import androidx.compose.ui.text.font.FontFamily
import com.oprojectview.AnimationMode
import com.oprojectview.DisplayTaskList
import com.oprojectview.DisplayTaskState
import com.oprojectview.TransitionMode
import com.oprojectview.animationModeOf
import com.oprojectview.distortionValueOf
import com.oprojectview.speedIndexToWpm
import com.oprojectview.transitionModeOf
import com.oprojectview.calculatePageDurationMs
import com.oprojectview.core.BaseViewModel
import com.oprojectview.core.UiEvent
import com.oprojectview.core.UiIntent
import com.oprojectview.core.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── PiP size class ────────────────────────────────────────────────────────────

/**
 * Shared representation of the PiP window's visual size class.
 *
 * Set by `IosPipController` when `didTransitionToRenderSize` fires, then
 * communicated to the shared layer via [FrameVmIntent.SetSizeClass].
 *
 * The Compose UI observes [FrameState.pipSizeClass] to switch between
 * compact and expanded layout profiles — same pattern as responsive
 * Compose breakpoints, as described in the architecture spec.
 */
enum class PipSizeClass { Compact, Expanded }

// ── State ─────────────────────────────────────────────────────────────────────

data class FrameVmState(
    val frameState: FrameState      = FrameState(),
    val metrics:    ProducerMetrics = ProducerMetrics(),
    val sinkStats:  SinkStats       = SinkStats(),
) : UiState

// ── Events ────────────────────────────────────────────────────────────────────

sealed interface FrameVmEvent : UiEvent {
    data object RequestPip : FrameVmEvent
    data object PipEnded   : FrameVmEvent
}

// ── Intents ───────────────────────────────────────────────────────────────────

sealed interface FrameVmIntent : UiIntent {
    data class SyncDisplayState(val state: DisplayTaskState)     : FrameVmIntent
    data class SetOverlay(val enabled: Boolean)                  : FrameVmIntent
    data class SetPlaying(val playing: Boolean)                  : FrameVmIntent

    /**
     * Sets the **stable source-layout canvas** dimensions in pixels.
     *
     * On iOS this is called once from `MainViewController` with the device's
     * native screen size, locking the render surface to the original screen
     * aspect ratio regardless of PiP viewport changes.
     *
     * Per spec: the renderer must NEVER re-layout to match the PiP aspect
     * ratio. PiP is a viewport into this stable canvas, not a resize trigger.
     */
    data class SetFrameSize(val widthPx: Int, val heightPx: Int, val pipOwned: Boolean = false) : FrameVmIntent

    /** PiP ended — release size override so PlayerScreen can update freely again. */
    data object ReleasePipSizeOverride : FrameVmIntent

    /** Adaptive render cadence — set by `IosPipController` on size changes. */
    data class SetTargetFps(val fps: Int)                        : FrameVmIntent

    /** Directly update the background fill color for PiP rendering. */
    data class SetFillColor(val fillColorVal: Long)              : FrameVmIntent
    data class SetDefaultColors(
        val textColorVal: Long,
        val defaultFillColorVal: Long,
        val primaryColorVal: Long,
        val surfaceVariantColorVal: Long
    ) : FrameVmIntent

    /**
     * Communicates the inferred PiP visual size class to the shared layer.
     *
     * iOS calls this from `IosPipController.onRenderSizeChanged()`.
     * The Compose `PlayerScreen` observes [FrameState.pipSizeClass] to
     * switch between compact (simplified) and expanded (full) layout profiles.
     *
     * This intent does NOT change [FrameState.frameWidthPx] / [frameHeightPx].
     * The source canvas dimensions are frozen at device-screen size.
     * Only content density and FPS change between profiles.
     */
    data class SetSizeClass(val sizeClass: PipSizeClass) : FrameVmIntent

    data class SyncPlayerState(
        val isPlaying:       Boolean,
        val countdownDone:   Boolean,
        val countdownStartUs: Long,
        val pausedCountdownElapsedUs: Long,
        val scrollFraction:  Float,
        val scriptText:      String,
        val styleSpans: List<com.oprojectview.StyleSpan>,
        val fillColorVal: Long,
        val animationMode: AnimationMode,
        val transitionMode: TransitionMode,
        val playbackStartUs: Long,
        val pausedElapsedUs: Long,
        val totalDurationMs: Long,
        val isMirror:        Boolean
    ) : FrameVmIntent

    data class SyncPlayerPlaybackState(
        val isPlaying:       Boolean,
        val countdownDone:   Boolean,
        val countdownStartUs: Long,
        val pausedCountdownElapsedUs: Long,
        val scrollFraction:  Float,
        val playbackStartUs: Long,
        val pausedElapsedUs: Long
    ) : FrameVmIntent

    data class SetScrollFraction(val fraction: Float) : FrameVmIntent
    data class SetFontFamilyResolver(val resolver: FontFamily.Resolver) : FrameVmIntent
    data object RequestPip : FrameVmIntent
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class FrameViewModel(
    private val renderer: FrameRenderer = OffscreenFrameRenderer(),
    private var sink:     FrameSink     = LogFrameSink(),
) : BaseViewModel<FrameVmState, FrameVmEvent>(
    initialState = FrameVmState(),
) {
    private val _frameState = MutableStateFlow(FrameState())
    val frameStateFlow: StateFlow<FrameState> = _frameState.asStateFlow()

    private var producer: FrameProducer = buildProducer()

    private fun buildProducer() = FrameProducer(
        renderer  = renderer,
        sink      = sink,
        stateFlow = _frameState,
    )

    init {
        viewModelScope.launch {
            producer.metrics.collect { m -> updateState { it.copy(metrics = m) } }
        }
        val initialSink = sink
        if (initialSink is LogFrameSink) {
            viewModelScope.launch {
                initialSink.stats.collect { s -> updateState { it.copy(sinkStats = s) } }
            }
        }
    }

    override fun onIntent(intent: UiIntent) {
        when (intent) {
            is FrameVmIntent.SyncDisplayState -> syncDisplayState(intent.state)
            is FrameVmIntent.SetOverlay       -> mutateFrame { it.copy(overlayEnabled = intent.enabled) }
            is FrameVmIntent.SetPlaying       -> onSetPlaying(intent.playing)
            is FrameVmIntent.SetFrameSize     -> mutateFrame {
                it.copy(
                    frameWidthPx = intent.widthPx,
                    frameHeightPx = intent.heightPx,
                    pipSizeOverrideActive = intent.pipOwned
                )
            }
            is FrameVmIntent.ReleasePipSizeOverride -> mutateFrame {
                it.copy(pipSizeOverrideActive = false)
            }
            is FrameVmIntent.SetFillColor     -> mutateFrame {
                it.copy(fillColorVal = intent.fillColorVal)
            }
            is FrameVmIntent.SetDefaultColors -> mutateFrame {
                it.copy(
                    textColorVal = intent.textColorVal,
                    defaultFillColorVal = intent.defaultFillColorVal,
                    primaryColorVal = intent.primaryColorVal,
                    surfaceVariantColorVal = intent.surfaceVariantColorVal
                )
            }
            is FrameVmIntent.SetTargetFps     -> mutateFrame { it.copy(targetFps = intent.fps) }
            is FrameVmIntent.SetSizeClass     -> mutateFrame { it.copy(pipSizeClass = intent.sizeClass) }
            is FrameVmIntent.SyncPlayerState  -> mutateFrame {
                it.copy(
                    isPlaying = intent.isPlaying,
                    countdownDone = intent.countdownDone,
                    countdownStartUs = intent.countdownStartUs,
                    pausedCountdownElapsedUs = intent.pausedCountdownElapsedUs,
                    scrollFraction = intent.scrollFraction,
                    scriptText = intent.scriptText,
                    styleSpans = intent.styleSpans,
                    fillColorVal = intent.fillColorVal,
                    animationMode = intent.animationMode,
                    transitionMode = intent.transitionMode,
                    playbackStartUs = intent.playbackStartUs,
                    pausedElapsedUs = intent.pausedElapsedUs,
                    totalDurationMs = intent.totalDurationMs,
                    isMirror = intent.isMirror
                )
            }
            is FrameVmIntent.SyncPlayerPlaybackState -> mutateFrame {
                it.copy(
                    isPlaying = intent.isPlaying,
                    countdownDone = intent.countdownDone,
                    countdownStartUs = intent.countdownStartUs,
                    pausedCountdownElapsedUs = intent.pausedCountdownElapsedUs,
                    scrollFraction = intent.scrollFraction,
                    playbackStartUs = intent.playbackStartUs,
                    pausedElapsedUs = intent.pausedElapsedUs
                )
            }
            is FrameVmIntent.SetScrollFraction -> mutateFrame {
                it.copy(scrollFraction = intent.fraction)
            }
            is FrameVmIntent.SetFontFamilyResolver -> {
                (renderer as? CpuTeleprompterFrameRenderer)?.fontFamilyResolver = intent.resolver
            }
            is FrameVmIntent.RequestPip -> emitEvent(FrameVmEvent.RequestPip)
            else -> Unit
        }
    }

    fun replaceSink(newSink: FrameSink) {
        val wasPlaying = _frameState.value.isPlaying
        producer.release()
        sink     = newSink
        producer = buildProducer()
        viewModelScope.launch {
            producer.metrics.collect { m -> updateState { it.copy(metrics = m) } }
        }
        if (wasPlaying) producer.start()
    }

    private fun syncDisplayState(ds: DisplayTaskState) {
        fun idx(id: Int): Int {
            val item = DisplayTaskList.first { it.id == id }
            return ds.selectedIndex(item) ?: item.defaultIndex
        }
        mutateFrame { fs ->
            fs.copy(
                textSizeIndex  = idx(1),
                isHorizontal   = idx(2) == 1,
                wpm            = speedIndexToWpm(idx(3)),
                animationMode  = animationModeOf(idx(4)),
                transitionMode = transitionModeOf(idx(5)),
                distortionMode = distortionValueOf(idx(6)),
                isMirror       = idx(7) == 1,
                overlayEnabled = idx(8) == 2,
                isLoopEnabled  = ds.isAnimationLoopEnabled(),
            )
        }
    }

    private fun onSetPlaying(playing: Boolean) {
        val nowUs = com.oprojectview.core.MonotonicClock.currentTimeUs()
        mutateFrame { fs ->
            var newFs = fs.copy(isPlaying = playing)
            if (playing && !fs.countdownDone && fs.countdownStartUs <= 0L) {
                newFs = newFs.copy(countdownStartUs = nowUs)
            }
            newFs
        }
        if (playing) {
            if (_frameState.value.overlayEnabled) {
                // Only start the render-loop when overlay is active.
                // On Android the producer is LogFrameSink (no-op) so starting it
                // is harmless, but on iOS it would push blank frames into the PiP
                // sink while the user is simply watching the PlayerScreen without
                // the overlay toggle enabled.
                producer.start()
                emitEvent(FrameVmEvent.RequestPip)
            }
            // When overlay is disabled, nothing to render — the system PiP window
            // on Android shows the live Activity UI with no frame pipeline involved.
        } else {
            producer.stop()
        }
    }

    private fun mutateFrame(transform: (FrameState) -> FrameState) {
        val updated = transform(_frameState.value)
        _frameState.value = updated
        updateState { it.copy(frameState = updated) }
    }

    override fun clear() {
        producer.release()
        sink.release()
        super.clear()
    }
}

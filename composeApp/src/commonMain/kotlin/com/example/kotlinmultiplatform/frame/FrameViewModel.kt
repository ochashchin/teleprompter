package com.example.kotlinmultiplatform.frame

import com.example.kotlinmultiplatform.DisplayTaskList
import com.example.kotlinmultiplatform.DisplayTaskState
import com.example.kotlinmultiplatform.animationModeOf
import com.example.kotlinmultiplatform.distortionValueOf
import com.example.kotlinmultiplatform.speedIndexToWpm
import com.example.kotlinmultiplatform.transitionModeOf
import com.example.kotlinmultiplatform.core.BaseViewModel
import com.example.kotlinmultiplatform.core.UiEvent
import com.example.kotlinmultiplatform.core.UiIntent
import com.example.kotlinmultiplatform.core.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── State ─────────────────────────────────────────────────────────────────────

data class FrameVmState(
    val frameState: FrameState      = FrameState(),
    val metrics:    ProducerMetrics = ProducerMetrics(),
    val sinkStats:  SinkStats       = SinkStats(),
) : UiState

// ── Events ────────────────────────────────────────────────────────────────────

sealed interface FrameVmEvent : UiEvent {
    /**
     * Emitted when Play is pressed AND overlay is enabled.
     * [PipController] (androidMain) observes this and calls
     * `Activity.enterPictureInPictureMode()`.
     */
    data object RequestPip : FrameVmEvent

    /** Emitted when PiP exits so the platform can clean up. */
    data object PipEnded   : FrameVmEvent
}

// ── Intents ───────────────────────────────────────────────────────────────────

sealed interface FrameVmIntent : UiIntent {
    /**
     * Sync the entire [DisplayTaskState] for a given task into the pipeline.
     * Called from [DisplayScreenPipBridge] before play is triggered.
     */
    data class SyncDisplayState(val state: DisplayTaskState)  : FrameVmIntent

    data class SetOverlay(val enabled: Boolean)               : FrameVmIntent
    data class SetPlaying(val playing: Boolean)               : FrameVmIntent
    data class SetFrameSize(val widthPx: Int, val heightPx: Int) : FrameVmIntent
    data class SetTargetFps(val fps: Int)                     : FrameVmIntent

}

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * Owns the [FrameProducer] lifecycle and translates [DisplayTaskState] indices
 * into a typed [FrameState].
 *
 * ## Dependency graph
 *
 * ```
 * DisplayTaskState (Settings-backed) ──► FrameViewModel ──► FrameProducer
 *                                                                  │
 *                                              ┌───────────────────┤
 *                                              ▼                   ▼
 *                                       FrameRenderer          FrameSink
 *                                   (OffscreenFrameRenderer) (LogFrameSink / AndroidFrameSink)
 * ```
 *
 * ## PiP gate
 *
 * [FrameVmIntent.SetPlaying] only emits [FrameVmEvent.RequestPip] when
 * `frameState.overlayEnabled == true`.  This enforces the spec rule:
 *   *overlay enabled + play pressed → PiP activates.*
 *
 * ## Platform injection
 *
 * On Android, [sink] is replaced with `AndroidFrameSink` at construction time.
 * On other platforms [LogFrameSink] is used, keeping commonMain clean.
 */
class FrameViewModel(
    private val sink:     FrameSink     = LogFrameSink(),
    private val renderer: FrameRenderer = OffscreenFrameRenderer(),
) : BaseViewModel<FrameVmState, FrameVmEvent>(
    initialState = FrameVmState(),
) {
    // Internal StateFlow that FrameProducer reads each tick.
    private val _frameState = MutableStateFlow(FrameState())
    val frameStateFlow: StateFlow<FrameState> = _frameState.asStateFlow()

    private val producer: FrameProducer = buildProducer()

    private fun buildProducer() = FrameProducer(
        renderer  = renderer,
        sink      = sink,
        stateFlow = _frameState,
    )

    init {
        // Mirror ProducerMetrics back into VM state for debug UI.
        viewModelScope.launch {
            producer.metrics.collect { m ->
                updateState { it.copy(metrics = m) }
            }
        }
        if (sink is LogFrameSink) {
            viewModelScope.launch {
                (sink as LogFrameSink).stats.collect { s -> updateState { it.copy(sinkStats = s) } }
            }
        }
    }

    override fun onIntent(intent: UiIntent) {
        when (intent) {
            is FrameVmIntent.SyncDisplayState -> syncDisplayState(intent.state)
            is FrameVmIntent.SetOverlay       -> mutateFrame { it.copy(overlayEnabled = intent.enabled) }
            is FrameVmIntent.SetPlaying       -> onSetPlaying(intent.playing)
            is FrameVmIntent.SetFrameSize     -> mutateFrame {
                it.copy(frameWidthPx = intent.widthPx, frameHeightPx = intent.heightPx)
            }
            is FrameVmIntent.SetTargetFps     -> mutateFrame { it.copy(targetFps = intent.fps) }
            else -> Unit
        }
    }

    // ── Sync DisplayTaskState → FrameState ────────────────────────────────────
    //
    // Reads all 8 DisplayTaskItem indices from the real DisplayTaskState
    // (Settings-backed) and maps them to typed FrameState fields.
    // This is the single translation point — no index magic elsewhere.

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
                overlayEnabled = idx(8) == 1,
            )
        }
    }

    // ── Play / PiP gate ───────────────────────────────────────────────────────

    private fun onSetPlaying(playing: Boolean) {
        mutateFrame { it.copy(isPlaying = playing) }

        if (playing) {
            // Emit RequestPip only when overlay is active — the spec gate.
            if (_frameState.value.overlayEnabled) {
                emitEvent(FrameVmEvent.RequestPip)
            }
            producer.start()
        } else {
            producer.stop()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

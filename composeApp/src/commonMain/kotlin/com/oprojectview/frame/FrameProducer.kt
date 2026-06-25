package com.oprojectview.frame

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

// ── ProducerMetrics ───────────────────────────────────────────────────────────

data class ProducerMetrics(
    val framesProduced:   Long = 0L,
    val lastRenderTimeUs: Long = 0L,
    val overrunCount:     Long = 0L,
)

// ── FrameProducer ─────────────────────────────────────────────────────────────

/**
 * Drives a vsync-like render loop at [FrameState.targetFps] (default 60).
 *
 * ## Timing model
 *
 * ```
 * frameDeadlineUs  = pipelineStartUs + (frameIndex + 1) * framePeriodUs
 * sleepUs          = frameDeadlineUs − nowUs
 * ```
 *
 * If a frame finishes early, the producer sleeps for the remainder, keeping
 * output cadence stable.  If a frame overruns its deadline (slow render), the
 * producer skips the sleep and emits immediately — the PTS remains monotonic
 * so the downstream encoder/sink always sees a valid timeline.
 *
 * ## Memory model
 *
 * [BitmapPool] is owned here and sized to `POOL_DEPTH` slots.  The pool is
 * recreated automatically when [FrameState.frameWidthPx] / [frameHeightPx]
 * changes (e.g. orientation flip).  The old pool is abandoned and GC'd.
 *
 * ## Back-pressure
 *
 * [FrameSink.onFrame] is called synchronously inside the loop.  If the sink
 * is slow the pool drains; [BitmapPool.acquire] allocates overflow bitmaps
 * rather than blocking, so the producer never deadlocks.
 *
 * ## Lifecycle
 *
 * ```
 * producer.start()   // begins the loop
 * producer.stop()    // cancels the loop job; pool and scope stay alive
 * producer.release() // stop + cancel scope + sink.release()
 * ```
 */
class FrameProducer(
    private val renderer:  FrameRenderer,
    private val sink:      FrameSink,
    private val stateFlow: StateFlow<FrameState>,
) {
    private val scope   = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loopJob: Job? = null

    private val _metrics = MutableStateFlow(ProducerMetrics())
    val metrics: StateFlow<ProducerMetrics> = _metrics.asStateFlow()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch { runLoop() }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    fun release() {
        stop()
        scope.cancel()
        sink.release()
    }

    // ── Frame loop ────────────────────────────────────────────────────────────

    private suspend fun runLoop() {
        var frameIndex   = 0L
        val startUs      = currentTimeUs()
        var currentState = stateFlow.value

        // BitmapPool: created once per resolution; recreated on size change.
        var pool = BitmapPool(
            widthPx  = currentState.frameWidthPx,
            heightPx = currentState.frameHeightPx,
            capacity = POOL_DEPTH,
        )

        while (true) {
            currentState = stateFlow.value

            if (!currentState.isPlaying) {
                delay(IDLE_POLL_MS)
                continue
            }

            val elapsedUs = if (currentState.countdownDone) {
                if (currentState.isPlaying) {
                    (currentTimeUs() - currentState.playbackStartUs).coerceAtLeast(0L)
                } else {
                    currentState.pausedElapsedUs
                }
            } else {
                val countdownElapsedUs = if (currentState.isPlaying) {
                    (currentTimeUs() - currentState.countdownStartUs).coerceAtLeast(0L)
                } else {
                    currentState.pausedCountdownElapsedUs
                }
                if (countdownElapsedUs < 6_000_000L) {
                    0L
                } else {
                    countdownElapsedUs - 6_000_000L
                }
            }
            val totalDurationUs = currentState.totalDurationMs * 1000L
            val computedFraction = if (totalDurationUs > 0L) {
                val rawFraction = (elapsedUs.toDouble() / totalDurationUs.toDouble()).toFloat()
                if (currentState.isLoopEnabled) {
                    rawFraction % 1f
                } else {
                    rawFraction.coerceIn(0f, 1f)
                }
            } else {
                0f
            }
            val frameStateToRender = currentState.copy(
                scrollFraction = computedFraction,
                elapsedUs = elapsedUs
            )

            val fps           = currentState.targetFps.coerceIn(1, 120)
            val periodUs      = 1_000_000L / fps
            val deadlineUs    = startUs + (frameIndex + 1) * periodUs

            // Recreate pool if frame dimensions changed.
            if (pool.widthPx != currentState.frameWidthPx ||
                pool.heightPx != currentState.frameHeightPx
            ) {
                pool = BitmapPool(
                    widthPx  = currentState.frameWidthPx,
                    heightPx = currentState.frameHeightPx,
                    capacity = POOL_DEPTH,
                )
            }

            // ── Render ────────────────────────────────────────────────────────
            val renderStart = currentTimeUs()
            val frame       = renderer.renderFrame(frameStateToRender, pool)
            val renderUs    = currentTimeUs() - renderStart

            // ── Deliver ───────────────────────────────────────────────────────
            sink.onFrame(frame) { pool.release(it.bitmap) }

            // ── Sleep to hit deadline ─────────────────────────────────────────
            val remainingUs = deadlineUs - currentTimeUs()
            when {
                remainingUs > MIN_SLEEP_US -> delay(remainingUs / 1_000L)
                else                        -> yield()   // overrun — yield only
            }

            frameIndex++
            val overruns = _metrics.value.overrunCount + if (remainingUs <= 0L) 1L else 0L
            _metrics.value = ProducerMetrics(
                framesProduced   = frameIndex,
                lastRenderTimeUs = renderUs,
                overrunCount     = overruns,
            )
        }
    }

    companion object {
        private const val POOL_DEPTH    = 4
        private const val IDLE_POLL_MS  = 50L
        private const val MIN_SLEEP_US  = 500L

        fun currentTimeUs() = com.oprojectview.core.MonotonicClock.currentTimeUs()
    }
}

package com.oprojectview.frame

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.Volatile

// ── FrameSink ─────────────────────────────────────────────────────────────────

/**
 * Consumes [RenderedFrame] objects produced by [FrameProducer].
 *
 * ### Ownership contract
 * - [onFrame] is called on `Dispatchers.Default`, never on Main.
 * - The sink MUST call `release(frame)` once it is finished with the bitmap so
 *   the [BitmapPool] slot is returned.  The lambda is threaded through rather
 *   than a direct pool reference, keeping ownership boundaries clean.
 * - [release] is idempotent and thread-safe.
 *
 * ### Back-pressure
 * If the sink is slower than the producer the pool will temporarily run dry.
 * [BitmapPool.acquire] will allocate overflow bitmaps rather than block —
 * natural back-pressure without deadlock risk.
 *
 * Platform implementations (e.g. `AndroidFrameSink`) replace [LogFrameSink]
 * by being injected at [FrameViewModel] construction time.
 */
interface FrameSink {
    /** Receive one frame.  Must call [release] when done with the bitmap. */
    fun onFrame(frame: RenderedFrame, release: (RenderedFrame) -> Unit)

    /** Pipeline is stopping.  Flush encoder / close codec / log summary. */
    fun release()
}

// ── SinkStats ─────────────────────────────────────────────────────────────────

data class SinkStats(
    val framesReceived: Long = 0L,
    val lastPtsUs:      Long = 0L,
)

// ── LogFrameSink ──────────────────────────────────────────────────────────────

/**
 * MVP sink — logs every [logEveryNFrames]-th frame to stdout and immediately
 * releases the bitmap back to the pool.  No encoding; no storage.
 *
 * Also exposes [lastFrame] for use in debug composables or unit tests.
 */
class LogFrameSink(
    private val logEveryNFrames: Int = 60,      // ~1 log line per second at 60 FPS
) : FrameSink {

    private val _stats = MutableStateFlow(SinkStats())
    val stats: StateFlow<SinkStats> = _stats.asStateFlow()

    @Volatile
    var lastFrame: RenderedFrame? = null
        private set

    override fun onFrame(frame: RenderedFrame, release: (RenderedFrame) -> Unit) {
        lastFrame = frame
        val count = frame.frameIndex + 1

        if (count % logEveryNFrames == 0L) {
            println(
                "[FrameSink] frame=${frame.frameIndex}  " +
                "pts=${frame.presentationTimeUs / 1_000}ms  " +
                "${frame.widthPx}×${frame.heightPx}"
            )
        }

        _stats.value = SinkStats(framesReceived = count, lastPtsUs = frame.presentationTimeUs)

        // Release immediately — no encoding work in the MVP.
        release(frame)
    }

    override fun release() {
        println("[FrameSink] pipeline stopped after ${_stats.value.framesReceived} frames")
    }
}

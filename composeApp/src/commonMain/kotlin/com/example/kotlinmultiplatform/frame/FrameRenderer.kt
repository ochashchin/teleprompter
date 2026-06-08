package com.example.kotlinmultiplatform.frame

import androidx.compose.ui.graphics.ImageBitmap

// ── RenderedFrame ─────────────────────────────────────────────────────────────

/**
 * One encoded frame ready for the sink.
 *
 * [presentationTimeUs] is microseconds since the pipeline started — carried
 * directly into MediaCodec.queueInputBuffer / Surface timestamps on Android.
 *
 * The [bitmap] is borrowed from [BitmapPool].  The sink MUST call its release
 * lambda once done so the pool slot becomes available for the next tick.
 */
data class RenderedFrame(
    val bitmap:             ImageBitmap,
    val presentationTimeUs: Long,
    val frameIndex:         Long,
    val widthPx:            Int,
    val heightPx:           Int,
)

// ── FrameRenderer ─────────────────────────────────────────────────────────────

/**
 * Converts one [FrameState] snapshot into one [RenderedFrame].
 *
 * ## PiP stream fix — why the old approach was wrong
 *
 * The original [OffscreenFrameRenderer] drew coloured geometric primitives
 * (bars, strips) on a background thread.  That is NOT the PlayerScreen UI —
 * it is a stub that was always intended to be replaced.  The PiP window
 * therefore showed a black rectangle with stripes instead of the real
 * teleprompter content.
 *
 * ## Correct approach — [ComposeCapturingRenderer]
 *
 * The real `PlayerScreenBody` composable must be captured using
 * `GraphicsLayer.record { drawContent() }` on the **main thread** inside the
 * Composition, then the resulting `ImageBitmap` is forwarded here.
 *
 * The bridge is [FrameCaptureState]: a shared object created once in
 * `AppRoot` / `PlayerScreenBody`, written to by the composable each frame via
 * `Modifier.onFrameCaptured`, and read by [ComposeCapturingRenderer] on the
 * producer coroutine.  The producer coroutine simply copies the latest
 * captured bitmap into the pipeline; it never draws anything itself.
 *
 * ```
 * PlayerScreenBody
 *   └─ Modifier.drawWithContent {
 *         drawContent()                     // normal screen draw
 *         captureState.submitFrame(…)       // copy pixels → pipeline
 *      }
 *      ↓ ImageBitmap
 * ComposeCapturingRenderer.renderFrame()   // wraps it in RenderedFrame
 *      ↓
 * AndroidFrameSink  →  MediaCodec  →  SurfaceViewRoute  →  PiP SurfaceView
 * ```
 *
 * ### Thread model
 * - `drawWithContent` fires on the **main thread** (Compose render pass).
 * - `renderFrame` is called on `Dispatchers.Default` (FrameProducer loop).
 * - `FrameCaptureState` mediates the handoff with a `kotlinx.atomicfu`-free
 *    `@Volatile` reference + copy semantics.
 */
interface FrameRenderer {
    /**
     * Render one frame from [state] into a bitmap from [pool].
     * Called on `Dispatchers.Default`.  Must NOT touch the main thread.
     *
     * The returned [RenderedFrame] is owned by the caller.
     */
    suspend fun renderFrame(state: FrameState, pool: BitmapPool): RenderedFrame
}

// ── BitmapPool ────────────────────────────────────────────────────────────────

/**
 * Pre-allocates a fixed number of [ImageBitmap] slots and recycles them
 * across frames to eliminate per-frame GC pressure inside the render loop.
 *
 * ### Threading
 * MVP: single-producer / single-consumer on `Dispatchers.Default` — a plain
 * `ArrayDeque` is sufficient.
 *
 * ### Overflow
 * If all slots are in-flight, [acquire] allocates a new bitmap rather than
 * blocking.  [release] discards bitmaps beyond [MAX_POOL_SIZE] to prevent
 * unbounded growth.
 */
class BitmapPool(
    val widthPx:  Int,
    val heightPx: Int,
    capacity:     Int = DEFAULT_CAPACITY,
) {
    private val idle = ArrayDeque<ImageBitmap>(capacity)

    init { repeat(capacity) { idle.addLast(ImageBitmap(widthPx, heightPx)) } }

    fun acquire(): ImageBitmap =
        if (idle.isNotEmpty()) idle.removeFirst()
        else ImageBitmap(widthPx, heightPx)

    fun release(bitmap: ImageBitmap) {
        if (idle.size < MAX_POOL_SIZE) idle.addLast(bitmap)
    }

    companion object {
        private const val DEFAULT_CAPACITY = 4
        private const val MAX_POOL_SIZE    = 8
    }
}

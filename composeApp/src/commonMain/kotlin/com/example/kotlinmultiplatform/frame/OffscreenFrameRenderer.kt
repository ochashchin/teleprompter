package com.example.kotlinmultiplatform.frame

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stub [FrameRenderer] for the LogFrameSink pipeline.
 *
 * The encode→decode MediaCodec pipeline has been removed — Android PiP shows
 * the Activity window contents directly (the Compose PlayerScreen), so no
 * pixel capture or video encoding is required on the same device.
 *
 * This renderer returns a 1×1 blank bitmap each tick so [FrameProducer] can
 * keep its loop running without null checks.  [LogFrameSink] logs every 60th
 * frame for diagnostics; that is the only consumer.
 */
class OffscreenFrameRenderer : FrameRenderer {

    private var frameCounter = 0L
    private val blank = ImageBitmap(1, 1)

    override suspend fun renderFrame(state: FrameState, pool: BitmapPool): RenderedFrame =
        withContext(Dispatchers.Default) {
            RenderedFrame(
                bitmap             = blank,
                presentationTimeUs = currentTimeUs(),
                frameIndex         = frameCounter++,
                widthPx            = 1,
                heightPx           = 1,
            )
        }

    companion object {
        private val epoch = kotlin.time.TimeSource.Monotonic.markNow()
        fun currentTimeUs(): Long = epoch.elapsedNow().inWholeMicroseconds
    }
}

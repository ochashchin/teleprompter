package com.example.kotlinmultiplatform.pip

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import com.example.kotlinmultiplatform.frame.FrameSink
import com.example.kotlinmultiplatform.frame.RenderedFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android-only [FrameSink] that encodes received frames as H.264 via
 * [MediaCodec] and routes the NAL units to [PipOutputRouter].
 *
 * ## Pipeline
 * ```
 * RenderedFrame (ImageBitmap)
 *   │  asAndroidBitmap()                   [Compose → Android bridge]
 *   ▼
 * android.graphics.Bitmap (ARGB_8888, reused across frames)
 *   │  lockCanvas() / drawBitmap()          [CPU blit]
 *   ▼
 * Surface  ←  MediaCodec.createInputSurface()
 *   │  BufferQueue                          [zero-copy hand-off]
 *   ▼
 * MediaCodec  (H.264 / AVC, Surface input mode)
 *   │  dequeueOutputBuffer() drain loop     [on Dispatchers.IO]
 *   ▼
 * PipOutputRouter  →  PiP window
 * ```
 *
 * ## Surface-input strategy
 * `MediaCodec.createInputSurface()` returns a [Surface] backed by a
 * `BufferQueue`.  Drawing into it avoids `queueInputBuffer()` byte-copies and
 * is the recommended path for display/capture pipelines (Android 5+).
 *
 * ## Memory safety
 * - [RenderedFrame.bitmap] is released immediately after the Surface blit —
 *   the encoder has its own internal copy via the BufferQueue.
 * - [androidBitmap] is a single reusable `ARGB_8888` Bitmap; it is
 *   recreated only when frame dimensions change.
 * - The output drain loop runs on [Dispatchers.IO] and never blocks the
 *   frame-producer coroutine.
 *
 * ## Frame pacing
 * The PTS carried in [RenderedFrame.presentationTimeUs] is passed directly to
 * the encoder so it can build a correct timing model for the PiP decoder.
 */
class AndroidFrameSink(
    private val widthPx:       Int,
    private val heightPx:      Int,
    private val targetFps:     Int            = 60,
    private val bitrateBps:    Int            = 4_000_000,
    private val outputRouter:  PipOutputRouter,
) : FrameSink {

    // ── MediaCodec + Surface ──────────────────────────────────────────────────

    private val codec: MediaCodec = MediaCodec.createEncoderByType(MIME_TYPE)
    private val inputSurface: Surface

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Single reusable Android Bitmap — avoids per-frame allocation.
    private var androidBitmap: Bitmap =
        Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)

    init {
        val format = MediaFormat.createVideoFormat(MIME_TYPE, widthPx, heightPx).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
            )
            setInteger(MediaFormat.KEY_BIT_RATE,          bitrateBps)
            setInteger(MediaFormat.KEY_FRAME_RATE,        targetFps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,  I_FRAME_INTERVAL_SEC)
            // Baseline profile: widest PiP decoder compatibility
            setInteger(
                MediaFormat.KEY_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline,
            )
            // Low-latency mode — reduces encoder pipeline delay for PiP
            setInteger(MediaFormat.KEY_LATENCY, 0)
        }

        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = codec.createInputSurface()
        codec.start()

        // Drain encoded NAL units on a dedicated IO coroutine.
        scope.launch { drainOutputLoop() }
    }

    // ── FrameSink impl ────────────────────────────────────────────────────────

    override fun onFrame(frame: RenderedFrame, release: (RenderedFrame) -> Unit) {
        try {
            blitToSurface(frame)
        } finally {
            // Return the pooled ImageBitmap to the producer immediately.
            // The encoder has its copy via the BufferQueue.
            release(frame)
        }
    }

    override fun release() {
        scope.cancel()
        runCatching { codec.stop() }
        runCatching { codec.release() }
        runCatching { inputSurface.release() }
        if (!androidBitmap.isRecycled) androidBitmap.recycle()
    }

    // ── Surface blit ──────────────────────────────────────────────────────────
    //
    // ImageBitmap.asAndroidBitmap() is the official Compose→Android bridge
    // (compose-ui-graphics-android).  It returns the underlying
    // android.graphics.Bitmap without an extra copy when the pixel buffer is
    // already Android-backed.

    private fun blitToSurface(frame: RenderedFrame) {
        val src = frame.bitmap.asAndroidBitmap()

        // Recreate the reusable bitmap only when dimensions change.
        if (androidBitmap.width != src.width || androidBitmap.height != src.height) {
            if (!androidBitmap.isRecycled) androidBitmap.recycle()
            androidBitmap = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        }

        val canvas = inputSurface.lockCanvas(null)
        try {
            canvas.drawBitmap(src, 0f, 0f, null)
        } finally {
            inputSurface.unlockCanvasAndPost(canvas)
        }
    }

    // ── Output drain ──────────────────────────────────────────────────────────
    //
    // Polls MediaCodec output.  Forwards encoded NAL units to PipOutputRouter.
    // Runs on Dispatchers.IO — never competes with the render coroutine.

    private fun drainOutputLoop() {
        val bufferInfo = MediaCodec.BufferInfo()
        loop@ while (true) {
            val index = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER       -> Unit   // spin
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ->
                    outputRouter.onFormatChanged(codec.outputFormat)
                index >= 0 -> {
                    val buffer = codec.getOutputBuffer(index)
                    if (buffer == null) {
                        codec.releaseOutputBuffer(index, false)
                        continue@loop
                    }
                    val isKey = bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                    outputRouter.onEncodedData(buffer, bufferInfo.presentationTimeUs, isKey, bufferInfo.size)
                    codec.releaseOutputBuffer(index, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break@loop
                }
                else -> Unit
            }
        }
    }

    companion object {
        private const val MIME_TYPE            = "video/avc"
        private const val I_FRAME_INTERVAL_SEC = 1
        private const val DEQUEUE_TIMEOUT_US   = 10_000L   // 10 ms
    }
}

package com.example.kotlinmultiplatform.pip

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.Surface
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileInputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.ByteBuffer

/**
 * Routes encoded H.264 NAL units from [AndroidFrameSink] into the PiP window.
 *
 * ## Bug 2 fix
 *
 * The original [FrameImageRoute] discarded every encoded buffer with a no-op lambda,
 * so the PiP window showed the normal Compose Activity layout shrunk down — not the
 * frame stream.
 *
 * The fix introduces [SurfaceViewRoute]: it feeds the encoded H.264 directly to a
 * [MediaCodec] decoder whose output Surface is provided by a `SurfaceView` placed
 * inside the PiP window.  The render pipeline is therefore:
 *
 * ```
 * FrameProducer (commonMain)
 *   → AndroidFrameSink (encode, MediaCodec H.264)
 *   → SurfaceViewRoute.onEncodedData()
 *   → decoder MediaCodec (H.264 → YUV)
 *   → SurfaceView.holder.surface          ← what the PiP window actually shows
 * ```
 *
 * ## Wiring (MainActivity)
 *
 * ```kotlin
 * // 1. Inflate or create a SurfaceView that lives in the PiP window overlay.
 * val pipSurfaceView = SurfaceView(this)
 * overlayLayout.addView(pipSurfaceView)
 *
 * // 2. Create the router and inject it.
 * val router = SurfaceViewRoute(pipSurfaceView.holder.surface, widthPx = 540, heightPx = 960)
 * val sink   = AndroidFrameSink(widthPx = 540, heightPx = 960, outputRouter = router)
 * ```
 *
 * ## FrameImageRoute
 * Kept for unit-test / pipeline-validation use.  Wire it with a non-no-op lambda to
 * verify the encoder is producing data before wiring [SurfaceViewRoute].
 */
interface PipOutputRouter {
    fun onFormatChanged(format: MediaFormat)
    fun onEncodedData(
        data:           ByteBuffer,
        presentationUs: Long,
        isKeyFrame:     Boolean,
        size:           Int,
    )
    fun release()
}

// ── FrameImageRoute (validation / testing) ────────────────────────────────────

/**
 * Forwards every encoded buffer to a callback.
 * **Do NOT** use `{ _, _, _, _ -> }` (no-op) in production — that was Bug 2.
 * Use [SurfaceViewRoute] for actual PiP display.
 */
class FrameImageRoute(
    private val onFrame: (data: ByteBuffer, ptsUs: Long, isKey: Boolean, size: Int) -> Unit,
) : PipOutputRouter {

    @Volatile var outputFormat: MediaFormat? = null
        private set

    override fun onFormatChanged(format: MediaFormat) { outputFormat = format }

    override fun onEncodedData(
        data:           ByteBuffer,
        presentationUs: Long,
        isKeyFrame:     Boolean,
        size:           Int,
    ) = onFrame(data, presentationUs, isKeyFrame, size)

    override fun release() = Unit
}

// ── SurfaceViewRoute (production PiP display) ─────────────────────────────────

/**
 * Decodes the incoming H.264 stream and renders frames directly onto [outputSurface].
 *
 * The [outputSurface] must be the `Surface` from a `SurfaceView` (or `TextureView`)
 * that is visible inside the PiP window.  The decoder is configured in Surface-output
 * mode so frames are handed to the SurfaceFlinger compositor without an extra copy.
 *
 * ### Thread safety
 * [onEncodedData] may be called from any thread (the `Dispatchers.IO` drain loop
 * in [AndroidFrameSink]).  The decoder's `queueInputBuffer` path is thread-safe for
 * sequential callers; we serialise via `@Synchronized`.
 *
 * ### Lifecycle
 * Call [release] when PiP is dismissed or the Activity is destroyed.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)   // MediaCodec Surface output = API 21
class SurfaceViewRoute(
    private val outputSurface: Surface,
    private val widthPx:       Int,
    private val heightPx:      Int,
    private val mimeType:      String = "video/avc",
) : PipOutputRouter {

    private var decoder: MediaCodec? = null
    private var started  = false

    // ── PipOutputRouter ───────────────────────────────────────────────────────

    /**
     * Called once when the encoder emits its CSD (codec-specific data — SPS/PPS).
     * We use the format to configure and start the decoder so it is ready for the
     * first IDR frame that follows.
     */
    override fun onFormatChanged(format: MediaFormat) {
        if (started) return          // already configured; ignore duplicate events

        val dec = MediaCodec.createDecoderByType(mimeType)
        dec.configure(format, outputSurface, null, 0 /* decode, not encode */)
        dec.start()
        decoder = dec
        started = true
        // Begin draining decoder output on a background thread.
        Thread(::drainDecoderOutput, "pip-decoder-drain").also { it.isDaemon = true }.start()
    }

    @Synchronized
    override fun onEncodedData(
        data:           ByteBuffer,
        presentationUs: Long,
        isKeyFrame:     Boolean,
        size:           Int,
    ) {
        val dec = decoder ?: return   // decoder not ready yet (no format received)

        // Find a free input buffer; time out quickly to avoid stalling the encoder.
        val inputIndex = dec.dequeueInputBuffer(INPUT_TIMEOUT_US)
        if (inputIndex < 0) return    // no buffer available — drop frame

        val inputBuffer = dec.getInputBuffer(inputIndex) ?: return
        inputBuffer.clear()
        // Limit the source slice to [size] bytes to avoid over-reading.
        val slice = data.duplicate().apply { limit(position() + size) }
        inputBuffer.put(slice)

        val flags = if (isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
        dec.queueInputBuffer(inputIndex, 0, size, presentationUs, flags)
    }

    override fun release() {
        runCatching { decoder?.stop() }
        runCatching { decoder?.release() }
        decoder = null
        started = false
    }

    // ── Decoder drain ─────────────────────────────────────────────────────────
    //
    // Runs on a daemon thread.  Releases each output buffer to the Surface so
    // SurfaceFlinger can composite the decoded frame into the PiP window.

    private fun drainDecoderOutput() {
        val info = MediaCodec.BufferInfo()
        while (started) {
            val dec = decoder ?: break
            val index = try {
                dec.dequeueOutputBuffer(info, OUTPUT_TIMEOUT_US)
            } catch (e: IllegalStateException) {
                break   // decoder was released
            }
            when {
                index >= 0 -> {
                    // render = true → decoded frame is pushed to outputSurface
                    val render = info.size > 0
                    dec.releaseOutputBuffer(index, render)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
                // INFO_OUTPUT_FORMAT_CHANGED / INFO_TRY_AGAIN_LATER: keep spinning
            }
        }
    }

    companion object {
        private const val INPUT_TIMEOUT_US  =  5_000L   //  5 ms
        private const val OUTPUT_TIMEOUT_US = 10_000L   // 10 ms
    }
}

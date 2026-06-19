@file:OptIn(ExperimentalForeignApi::class)

package com.oprojectview.pip

import com.oprojectview.frame.FrameSink
import com.oprojectview.frame.PipSizeClass
import com.oprojectview.frame.RenderedFrame
import kotlinx.cinterop.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.AVFoundation.AVSampleBufferDisplayLayer
import platform.AVFoundation.enqueueSampleBuffer
import platform.CoreFoundation.CFRelease
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreMedia.CMTimebaseGetTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimebaseSetTime
import platform.CoreMedia.CMTimeMake
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.UIKit.UIScreen
import kotlin.concurrent.Volatile

/**
 * Compile-time diagnostic override.
 *
 * true  → bypass Skia entirely; fill every CVPixelBuffer with a solid colour
 *          (fast, no main-thread stall).
 * false → use KMP Skia rendering (CpuTeleprompterFrameRenderer).
 */
private const val BYPASS_RENDERER_WITH_SOLID_COLOR = false

/**
 * iOS [FrameSink]: drives the full synthetic PiP pipeline.
 */
class IosFrameSink(
    widthPx:      Int,
    heightPx:     Int,
    val displayLayer: AVSampleBufferDisplayLayer,
) : FrameSink {

    // Mutable pool — recreated whenever PiP window dimensions change.
    private var pool = IosPixelBufferPool(widthPx, heightPx)
    private val bitmapRenderer = IosComposeFrameRenderer()
    private val sampleFactory  = IosSampleBufferFactory()
    private val mainScope      = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    var currentSizeClass: PipSizeClass = PipSizeClass.Compact

    @Volatile
    var onFirstFrameEnqueued: (() -> Unit)? = null

    private var lastFrameTimeUs = 0L
    private var frameCounter    = 0

    override fun onFrame(frame: RenderedFrame, release: (RenderedFrame) -> Unit) {
        // Adaptive FPS gate — drop frames that arrive faster than the target rate.
        val minPeriodUs = 1_000_000L / currentSizeClass.targetFps
        if (frame.presentationTimeUs - lastFrameTimeUs < minPeriodUs) {
            release(frame); return
        }
        lastFrameTimeUs = frame.presentationTimeUs

        // If the incoming frame dimensions changed (because FrameProducer updated its canvas), recreate the local pixel pool.
        if (frame.widthPx != pool.widthPx || frame.heightPx != pool.heightPx) {
            println("[IosFrameSink] Pool resize: ${pool.widthPx}x${pool.heightPx} → ${frame.widthPx}x${frame.heightPx}")
            pool.close()
            pool = IosPixelBufferPool(frame.widthPx, frame.heightPx)
        }

        val pixelBuf = pool.acquire()
        if (pixelBuf == null) {
            println("[IosFrameSink] pool.acquire() returned null, dropping frame")
            release(frame)
            return
        }

        val copied = if (BYPASS_RENDERER_WITH_SOLID_COLOR) {
            fillBufferFast(pixelBuf)
        } else {
            bitmapRenderer.copyBitmapToPixelBuffer(frame.bitmap, pixelBuf)
        }
        release(frame)   // return ImageBitmap to BitmapPool immediately after copy

        if (!copied) {
            println("[IosFrameSink] failed to fill pixel buffer, dropping frame")
            pool.release(pixelBuf)
            return
        }

        val sampleBuf = sampleFactory.wrap(pixelBuf, frame.presentationTimeUs)
        if (sampleBuf == null) {
            println("[IosFrameSink] wrap() returned null sampleBuf, dropping frame")
            pool.release(pixelBuf)
            return
        }

        val isPlaceholder = frame.isPlaceholder
        mainScope.launch {
            enqueueOnMain(sampleBuf, pixelBuf, frame.presentationTimeUs, isPlaceholder)
        }
    }

    /**
     * Must be called on the main thread.
     * Enqueues the sample buffer and then cleans up both the sample buffer and
     * the pixel buffer — in that order — so the layer has time to read the data.
     */
    private fun enqueueOnMain(
        sampleBuf:          CMSampleBufferRef?,
        pixelBuf:           CVPixelBufferRef?,
        presentationTimeUs: Long,
        isPlaceholder:      Boolean,
    ) {
        try {
            if (!isPlaceholder) {
                displayLayer.controlTimebase?.let { tb ->
                    val tbTime = CMTimebaseGetTime(tb)
                    val tbUs = (CMTimeGetSeconds(tbTime) * 1_000_000.0).toLong()
                    val diffUs = kotlin.math.abs(presentationTimeUs - tbUs)
 
                    // If the difference is greater than 100ms, resynchronize the timebase!
                    if (diffUs > 100_000L) {
                        println("[IosFrameSink] Resyncing controlTimebase from ${tbUs}us to ${presentationTimeUs}us (diff: ${diffUs}us)")
                        val pts = CMTimeMake(
                            value = presentationTimeUs,
                            timescale = 1_000_000
                        )
                        CMTimebaseSetTime(tb, pts)
                    }
                }
            }

            val n = ++frameCounter
            println("[IosFrameSink] enqueueSampleBuffer frame #$n  placeholder=$isPlaceholder")
            displayLayer.enqueueSampleBuffer(sampleBuf!!)

            if (!isPlaceholder) {
                onFirstFrameEnqueued?.invoke()
                onFirstFrameEnqueued = null
            }
        } finally {
            // Always clean up AFTER enqueue so the layer can read the data
            CFRelease(sampleBuf)
            pool.release(pixelBuf)
        }
    }

    /**
     * Fills the pixel buffer with a solid RED colour.
     */
    private fun fillBufferFast(pixelBuffer: CVPixelBufferRef): Boolean {
        val lockStatus = CVPixelBufferLockBaseAddress(pixelBuffer, 0uL)
        if (lockStatus != 0) {
            println("[IosFrameSink] CVPixelBufferLockBaseAddress failed: $lockStatus")
            return false
        }
        return try {
            val base = CVPixelBufferGetBaseAddress(pixelBuffer) ?: run {
                println("[IosFrameSink] CVPixelBufferGetBaseAddress returned null")
                return false
            }
            val bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer).toLong()
            val height      = CVPixelBufferGetHeight(pixelBuffer).toLong()
            
            val intPtr = base.reinterpret<UIntVar>()
            val pixelCount = (bytesPerRow * height) / 4L
            
            // kCVPixelFormatType_32BGRA: B, G, R, A.
            // Black: B=0, G=0, R=0, A=0xFF. 
            // In little-endian UInt, this is 0xFF000000u
            for (i in 0L until pixelCount) {
                intPtr[i] = 0xFF000000u
            }
            true
        } catch (e: Throwable) {
            println("[IosFrameSink] fillBufferFast failed: ${e.message}")
            false
        } finally {
            CVPixelBufferUnlockBaseAddress(pixelBuffer, 0uL)
        }
    }

    /**
     * Enqueues a placeholder frame synchronously to prime the display layer.
     * iOS requires readyForDisplay = true before startPictureInPicture() succeeds.
     *
     * MUST be called on the main thread.
     */
    fun enqueuePlaceholderFrame() {
        val pixelBuf = pool.acquire() ?: run {
            println("[IosFrameSink] enqueuePlaceholderFrame: pool.acquire() returned null")
            return
        }

        if (!fillBufferFast(pixelBuf)) {
            println("[IosFrameSink] enqueuePlaceholderFrame: fillBufferFast failed")
            pool.release(pixelBuf)
            return
        }

        val sampleBuf = sampleFactory.wrap(pixelBuf, 1L) ?: run {
            println("[IosFrameSink] enqueuePlaceholderFrame: wrap() failed")
            pool.release(pixelBuf)
            return
        }

        println("[IosFrameSink] enqueuePlaceholderFrame: enqueuing placeholder")
        enqueueOnMain(sampleBuf, pixelBuf, presentationTimeUs = 1L, isPlaceholder = true)
    }

    override fun release() {
        mainScope.cancel()
        pool.close()
    }
}

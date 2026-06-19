@file:OptIn(ExperimentalForeignApi::class)

package com.oprojectview.pip

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ColorAlphaType
import kotlinx.cinterop.*
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.CVPixelBufferRef

/**
 * Copies a Compose [ImageBitmap] (Color ARGB) into a CVPixelBuffer (BGRA).
 */
class IosComposeFrameRenderer {

    fun copyBitmapToPixelBuffer(
        bitmap:      ImageBitmap,
        pixelBuffer: CVPixelBufferRef?,
    ): Boolean {
        if (pixelBuffer == null) return false

        val lockStatus = CVPixelBufferLockBaseAddress(pixelBuffer, 0uL)
        if (lockStatus != 0) return false

        return try {
            val base        = CVPixelBufferGetBaseAddress(pixelBuffer) ?: return false
            val bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer).toInt()
            val bufW        = CVPixelBufferGetWidth(pixelBuffer).toInt()
            val bufH        = CVPixelBufferGetHeight(pixelBuffer).toInt()
            val w           = minOf(bitmap.width, bufW)
            val h           = minOf(bitmap.height, bufH)

            val skiaBitmap   = bitmap.asSkiaBitmap()
            val imageInfo    = ImageInfo(
                width     = w,
                height    = h,
                colorType = ColorType.BGRA_8888,
                alphaType = ColorAlphaType.PREMUL
            )
            val skiaRowBytes = w * 4
            val pixelBytes   = skiaBitmap.readPixels(
                dstInfo     = imageInfo,
                dstRowBytes = skiaRowBytes,
                srcX        = 0,
                srcY        = 0
            ) ?: return false

            val dest = interpretCPointer<ByteVar>(base.rawValue) ?: return false

            pixelBytes.usePinned { pinned ->
                val srcPtr = pinned.addressOf(0)
                for (y in 0 until h) {
                    val destOff = y * bytesPerRow
                    val srcOff  = y * skiaRowBytes
                    platform.posix.memcpy(
                        __dst = dest.plus(destOff.toLong()),
                        __src = srcPtr.plus(srcOff.toLong()),
                        __n   = skiaRowBytes.toULong()
                    )
                }
            }
            true
        } catch (e: Throwable) {
            println("[IosComposeFrameRenderer] copy failed: ${e.message}")
            e.printStackTrace()
            false
        } finally {
            CVPixelBufferUnlockBaseAddress(pixelBuffer, 0uL)
        }
    }
}

@file:OptIn(ExperimentalForeignApi::class)

package com.oprojectview.pip

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFRelease
import platform.CoreMedia.CMSampleBufferCreateReadyWithImageBuffer
import platform.CoreMedia.CMSampleTimingInfo
import platform.CoreMedia.CMVideoFormatDescriptionCreateForImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreMedia.CMSampleBufferRefVar
import platform.CoreMedia.CMVideoFormatDescriptionRefVar
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreMedia.CMSampleBufferGetSampleAttachmentsArray
import platform.CoreMedia.kCMSampleAttachmentKey_DisplayImmediately
import platform.CoreFoundation.CFArrayGetValueAtIndex
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.kCFBooleanTrue
import kotlinx.cinterop.reinterpret

/**
 * Wraps a CVPixelBuffer into a CMSampleBuffer for [AVSampleBufferDisplayLayer].
 */
class IosSampleBufferFactory {

    fun wrap(
        pixelBuffer:    CVPixelBufferRef?,
        presentationUs: Long,
    ): CMSampleBufferRef? = memScoped {
        if (pixelBuffer == null) return@memScoped null

        // ── 1. Format description ─────────────────────────────────────────────
        val outFmt = alloc<CMVideoFormatDescriptionRefVar>()

        val fmtStatus = CMVideoFormatDescriptionCreateForImageBuffer(
            null, pixelBuffer, outFmt.ptr
        )
        if (fmtStatus != 0) return@memScoped null
        val fmtDesc = outFmt.value ?: return@memScoped null

        // ── 2. Timing ─────────────────────────────────────────────────────────
        val timing = alloc<CMSampleTimingInfo>()
        timing.presentationTimeStamp.value     = presentationUs
        timing.presentationTimeStamp.timescale = 1_000_000   // µs timescale
        timing.presentationTimeStamp.flags     = 1u           // kCMTimeFlags_Valid

        // ── 3. Sample buffer ──────────────────────────────────────────────────
        val outBuf = alloc<CMSampleBufferRefVar>()

        val sbStatus = CMSampleBufferCreateReadyWithImageBuffer(
            null, pixelBuffer, fmtDesc, timing.ptr, outBuf.ptr
        )

        CFRelease(fmtDesc)
        if (sbStatus != 0) return@memScoped null
        val sampleBuf = outBuf.value

        // Attach DisplayImmediately metadata to sample buffer
        val attachments = CMSampleBufferGetSampleAttachmentsArray(sampleBuf, createIfNecessary = true)
        if (attachments != null) {
            val dict = CFArrayGetValueAtIndex(attachments, 0)
            if (dict != null) {
                CFDictionarySetValue(
                    dict.reinterpret(),
                    kCMSampleAttachmentKey_DisplayImmediately,
                    kCFBooleanTrue
                )
            }
        }

        sampleBuf
    }
}

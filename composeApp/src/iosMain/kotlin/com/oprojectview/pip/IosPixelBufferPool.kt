@file:OptIn(ExperimentalForeignApi::class)

package com.oprojectview.pip

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.cinterop.interpretObjCPointer
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreVideo.CVPixelBufferCreate
import platform.CoreVideo.CVPixelBufferPoolCreate
import platform.CoreVideo.CVPixelBufferPoolCreatePixelBuffer
import platform.CoreVideo.CVPixelBufferPoolRelease
import platform.CoreVideo.CVPixelBufferRelease
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferRefVar
import platform.CoreVideo.CVPixelBufferPoolRef
import platform.CoreVideo.CVPixelBufferPoolRefVar
import platform.CoreVideo.kCVPixelBufferHeightKey
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelBufferWidthKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.CoreVideo.kCVReturnSuccess
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSCopyingProtocol

class IosPixelBufferPool(
    val widthPx:  Int,
    val heightPx: Int,
) {
    private var pool: CVPixelBufferPoolRef? = createPool()

    private fun retainedAttrsPtr(): CFDictionaryRef? {
        val dict = NSMutableDictionary()

        val widthKey = interpretObjCPointer<NSString>(kCVPixelBufferWidthKey!!.rawValue) as NSCopyingProtocol
        val heightKey = interpretObjCPointer<NSString>(kCVPixelBufferHeightKey!!.rawValue) as NSCopyingProtocol
        val formatKey = interpretObjCPointer<NSString>(kCVPixelBufferPixelFormatTypeKey!!.rawValue) as NSCopyingProtocol
        val ioSurfaceKey = interpretObjCPointer<NSString>(platform.CoreVideo.kCVPixelBufferIOSurfacePropertiesKey!!.rawValue) as NSCopyingProtocol

        dict.setObject(NSNumber(int = widthPx),  widthKey)
        dict.setObject(NSNumber(int = heightPx), heightKey)
        dict.setObject(
            NSNumber(unsignedInt = kCVPixelFormatType_32BGRA),
            formatKey
        )
        dict.setObject(NSMutableDictionary(), ioSurfaceKey)

        return CFBridgingRetain(dict) as CFDictionaryRef?
    }

    private fun createPool(): CVPixelBufferPoolRef? = memScoped {
        val attrs = retainedAttrsPtr() ?: return@memScoped null
        val outPool = alloc<CVPixelBufferPoolRefVar>()

        val status = CVPixelBufferPoolCreate(
            null,
            null,
            attrs,
            outPool.ptr
        )

        CFRelease(attrs)
        if (status == kCVReturnSuccess) outPool.value else null
    }

    fun acquire(): CVPixelBufferRef? {
        val p = pool
        return if (p != null) acquireFromPool(p) ?: createDirectly() else createDirectly()
    }

    private fun acquireFromPool(p: CVPixelBufferPoolRef): CVPixelBufferRef? = memScoped {
        val outBuf = alloc<CVPixelBufferRefVar>()

        val status = CVPixelBufferPoolCreatePixelBuffer(
            null,
            p,
            outBuf.ptr
        )
        if (status == kCVReturnSuccess) outBuf.value else null
    }

    private fun createDirectly(): CVPixelBufferRef? = memScoped {
        val attrs = retainedAttrsPtr() ?: return@memScoped null
        val outBuf = alloc<CVPixelBufferRefVar>()

        val status = CVPixelBufferCreate(
            null,
            widthPx.toULong(),
            heightPx.toULong(),
            kCVPixelFormatType_32BGRA,
            attrs,
            outBuf.ptr
        )

        CFRelease(attrs)
        if (status == kCVReturnSuccess) outBuf.value else null
    }

    fun release(buffer: CVPixelBufferRef?) {
        if (buffer == null) return
        CVPixelBufferRelease(buffer)
    }

    fun close() {
        val p = pool ?: return
        CVPixelBufferPoolRelease(p)
        pool = null
    }
}

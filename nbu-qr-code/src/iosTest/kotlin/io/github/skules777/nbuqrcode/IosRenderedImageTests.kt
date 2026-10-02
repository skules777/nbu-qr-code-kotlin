@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.github.skules777.nbuqrcode

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateWithName
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGColorSpaceSRGB
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage

class IosRenderedImageTests : RenderedImageContract() {
    override fun render(renderer: NBUQRImageRenderer, payload: NBUQRPayload): TestImage =
        renderer.uiImage(payload).CGImage!!.toTestImage()

    override fun decodePng(png: ByteArray): TestImage {
        val data = png.usePinned { NSData.create(bytes = it.addressOf(0), length = png.size.convert()) }
        return UIImage(data = data).CGImage!!.toTestImage()
    }

    // CIDetector and Vision find no barcodes at all in the iOS Simulator on Apple Silicon (they do on
    // devices), so the round trip through a scanner runs on the JVM and Android; here the raster is
    // checked module by module instead (rasterReproducesTheMatrix).
    override val scanner: QRScanner? = null

    // RGBA bytes in sRGB, top row first: drawing a CGImage into a bitmap context keeps its top at
    // the start of the buffer.
    private fun CGImageRef.toTestImage(): TestImage {
        val width = CGImageGetWidth(this).toInt()
        val height = CGImageGetHeight(this).toInt()
        val bytes = ByteArray(width * height * 4)
        bytes.usePinned { pinned ->
            val colorSpace = CGColorSpaceCreateWithName(kCGColorSpaceSRGB)
            val context = CGBitmapContextCreate(
                pinned.addressOf(0), width.convert(), height.convert(), 8u, (width * 4).convert(), colorSpace,
                CGImageAlphaInfo.kCGImageAlphaNoneSkipLast.value,
            )
            CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), this)
            CGContextRelease(context)
            CGColorSpaceRelease(colorSpace)
        }
        val argb = IntArray(width * height) { i ->
            val r = bytes[i * 4].toInt() and 0xFF
            val g = bytes[i * 4 + 1].toInt() and 0xFF
            val b = bytes[i * 4 + 2].toInt() and 0xFF
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return TestImage(width, height, argb)
    }
}

@file:OptIn(ExperimentalForeignApi::class)

package io.github.skules777.nbuqrcode

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.readBytes
import platform.CoreFoundation.CFDataCreateMutable
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorCreateSRGB
import platform.CoreGraphics.CGColorRelease
import platform.CoreGraphics.CGColorSpaceCreateWithName
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextAddPath
import platform.CoreGraphics.CGContextFillEllipseInRect
import platform.CoreGraphics.CGContextFillPath
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextRef
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextSetFillColorWithColor
import platform.CoreGraphics.CGContextSetShouldAntialias
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGPathAddCurveToPoint
import platform.CoreGraphics.CGPathAddLineToPoint
import platform.CoreGraphics.CGPathCloseSubpath
import platform.CoreGraphics.CGPathCreateMutable
import platform.CoreGraphics.CGPathMoveToPoint
import platform.CoreGraphics.CGPathRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGColorSpaceSRGB
import platform.ImageIO.CGImageDestinationAddImage
import platform.ImageIO.CGImageDestinationCreateWithData
import platform.ImageIO.CGImageDestinationFinalize
import platform.UIKit.UIImage

/**
 * Зображення QR-коду для закодованого платежу як `UIImage` — для iOS-частини
 * KMP-застосунку. Показуйте без інтерполяції (`.interpolation(.none)` у SwiftUI),
 * щоб модулі лишались чіткими при масштабуванні.
 *
 * Помилки — ті самі, що в [NBUQRImageRenderer.pngBytes].
 */
@Throws(NBUQRException::class, NBUQRImageException::class)
public fun NBUQRImageRenderer.uiImage(payload: NBUQRPayload): UIImage {
    val image = layout(payload).toCGImage()
    try {
        return UIImage.imageWithCGImage(image)
    } finally {
        CGImageRelease(image)
    }
}

internal actual fun encodePng(layout: NBUQRImageLayout): ByteArray {
    val image = layout.toCGImage()
    val data = CFDataCreateMutable(null, 0)
    val type = CFStringCreateWithCString(null, "public.png", kCFStringEncodingUTF8)
    val destination = CGImageDestinationCreateWithData(data, type, 1u, null)
    try {
        if (destination == null) throw NBUQRImageException.RenderingFailed("ImageIO could not encode the image as PNG")
        CGImageDestinationAddImage(destination, image, null)
        if (!CGImageDestinationFinalize(destination)) {
            throw NBUQRImageException.RenderingFailed("ImageIO could not encode the image as PNG")
        }
        val bytes = CFDataGetBytePtr(data) ?: throw NBUQRImageException.RenderingFailed("ImageIO produced no PNG data")
        return bytes.readBytes(CFDataGetLength(data).convert())
    } finally {
        destination?.let { CFRelease(it) }
        CFRelease(type)
        CFRelease(data)
        CGImageRelease(image)
    }
}

/** CGImage з лічильником посилань +1 — викликач мусить звільнити його через `CGImageRelease`. */
private fun NBUQRImageLayout.toCGImage(): CGImageRef {
    val colorSpace = CGColorSpaceCreateWithName(kCGColorSpaceSRGB)
    val context = CGBitmapContextCreate(
        data = null,
        width = sidePixels.convert(),
        height = sidePixels.convert(),
        bitsPerComponent = 8u,
        bytesPerRow = 0u,
        space = colorSpace,
        bitmapInfo = CGImageAlphaInfo.kCGImageAlphaNoneSkipLast.value,
    )
    CGColorSpaceRelease(colorSpace)
    if (context == null) throw NBUQRImageException.RenderingFailed("CoreGraphics could not rasterise the image")

    try {
        // The shared layout uses a y-down axis; CoreGraphics bitmaps are y-up.
        CGContextTranslateCTM(context, 0.0, sidePixels.toDouble())
        CGContextScaleCTM(context, 1.0, -1.0)
        drawOn(CoreGraphicsQRCanvas(context))
        return CGBitmapContextCreateImage(context)
            ?: throw NBUQRImageException.RenderingFailed("CoreGraphics could not rasterise the image")
    } finally {
        CGContextRelease(context)
    }
}

private class CoreGraphicsQRCanvas(private val context: CGContextRef) : QRCanvas {
    override fun fillRect(x: Int, y: Int, width: Int, height: Int, argb: Int) {
        CGContextSetShouldAntialias(context, false)
        withFillColor(argb) {
            CGContextFillRect(context, CGRectMake(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble()))
        }
    }

    override fun fillCircle(centerX: Double, centerY: Double, radius: Double, argb: Int) {
        CGContextSetShouldAntialias(context, true)
        withFillColor(argb) {
            CGContextFillEllipseInRect(context, CGRectMake(centerX - radius, centerY - radius, radius * 2, radius * 2))
        }
    }

    override fun fillPath(path: List<PathCommand>, argb: Int) {
        CGContextSetShouldAntialias(context, true)
        val cgPath = CGPathCreateMutable()
        for (command in path) {
            when (command) {
                is PathCommand.MoveTo -> CGPathMoveToPoint(cgPath, null, command.x, command.y)
                is PathCommand.LineTo -> CGPathAddLineToPoint(cgPath, null, command.x, command.y)
                is PathCommand.CubicTo -> CGPathAddCurveToPoint(
                    cgPath, null, command.x1, command.y1, command.x2, command.y2, command.x, command.y,
                )
                PathCommand.Close -> CGPathCloseSubpath(cgPath)
            }
        }
        withFillColor(argb) {
            CGContextAddPath(context, cgPath)
            CGContextFillPath(context)
        }
        CGPathRelease(cgPath)
    }

    private inline fun withFillColor(argb: Int, fill: () -> Unit) {
        val color = CGColorCreateSRGB(
            red = ((argb shr 16) and 0xFF) / 255.0,
            green = ((argb shr 8) and 0xFF) / 255.0,
            blue = (argb and 0xFF) / 255.0,
            alpha = 1.0,
        )
        CGContextSetFillColorWithColor(context, color)
        fill()
        CGColorRelease(color)
    }
}

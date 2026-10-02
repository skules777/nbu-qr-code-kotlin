@file:JvmName("NBUQRImages")

package io.github.skules777.nbuqrcode

import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Зображення QR-коду для закодованого платежу як `BufferedImage` (`TYPE_INT_RGB`, sRGB) —
 * для бекенду чи десктопа. Java2D працює й без дисплея (headless).
 *
 * Помилки — ті самі, що в [NBUQRImageRenderer.pngBytes].
 */
@Throws(NBUQRException::class, NBUQRImageException::class)
public fun NBUQRImageRenderer.bufferedImage(payload: NBUQRPayload): BufferedImage = layout(payload).toBufferedImage()

internal actual fun encodePng(layout: NBUQRImageLayout): ByteArray {
    val output = ByteArrayOutputStream()
    val encoded = try {
        ImageIO.write(layout.toBufferedImage(), "png", output)
    } catch (e: java.io.IOException) {
        throw NBUQRImageException.RenderingFailed("ImageIO could not encode the image as PNG", e)
    }
    if (!encoded) throw NBUQRImageException.RenderingFailed("ImageIO has no PNG writer")
    return output.toByteArray()
}

private fun NBUQRImageLayout.toBufferedImage(): BufferedImage {
    val image = try {
        BufferedImage(sidePixels, sidePixels, BufferedImage.TYPE_INT_RGB)
    } catch (e: IllegalArgumentException) {
        throw NBUQRImageException.RenderingFailed("Java2D rejected a ${sidePixels}×$sidePixels image", e)
    }
    val graphics = image.createGraphics()
    try {
        drawOn(Java2DQRCanvas(graphics))
    } finally {
        graphics.dispose()
    }
    return image
}

private class Java2DQRCanvas(private val graphics: Graphics2D) : QRCanvas {
    init {
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    }

    override fun fillRect(x: Int, y: Int, width: Int, height: Int, argb: Int) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
        graphics.color = Color(argb, true)
        graphics.fillRect(x, y, width, height)
    }

    override fun fillCircle(centerX: Double, centerY: Double, radius: Double, argb: Int) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.color = Color(argb, true)
        graphics.fill(Ellipse2D.Double(centerX - radius, centerY - radius, radius * 2, radius * 2))
    }

    override fun fillPath(path: List<PathCommand>, argb: Int) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.color = Color(argb, true)
        val shape = Path2D.Double(Path2D.WIND_NON_ZERO)
        for (command in path) {
            when (command) {
                is PathCommand.MoveTo -> shape.moveTo(command.x, command.y)
                is PathCommand.LineTo -> shape.lineTo(command.x, command.y)
                is PathCommand.CubicTo -> shape.curveTo(command.x1, command.y1, command.x2, command.y2, command.x, command.y)
                PathCommand.Close -> shape.closePath()
            }
        }
        graphics.fill(shape)
    }
}

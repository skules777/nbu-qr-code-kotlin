@file:JvmName("NBUQRImages")

package io.github.skules777.nbuqrcode

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import java.io.ByteArrayOutputStream

/**
 * Зображення QR-коду для закодованого платежу як `Bitmap` (ARGB_8888, sRGB).
 * Для Compose: `bitmap(payload).asImageBitmap()`; показуйте з `FilterQuality.None`,
 * щоб модулі лишались чіткими при масштабуванні.
 *
 * Помилки — ті самі, що в [NBUQRImageRenderer.pngBytes].
 */
@Throws(NBUQRException::class, NBUQRImageException::class)
public fun NBUQRImageRenderer.bitmap(payload: NBUQRPayload): Bitmap = layout(payload).toBitmap()

internal actual fun encodePng(layout: NBUQRImageLayout): ByteArray {
    val bitmap = layout.toBitmap()
    val output = ByteArrayOutputStream()
    val encoded = bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    bitmap.recycle()
    if (!encoded) throw NBUQRImageException.RenderingFailed("Bitmap.compress could not encode the image as PNG")
    return output.toByteArray()
}

private fun NBUQRImageLayout.toBitmap(): Bitmap {
    val bitmap = try {
        Bitmap.createBitmap(sidePixels, sidePixels, Bitmap.Config.ARGB_8888)
    } catch (e: IllegalArgumentException) {
        throw NBUQRImageException.RenderingFailed("Bitmap.createBitmap rejected a ${sidePixels}×$sidePixels image", e)
    }
    drawOn(AndroidQRCanvas(Canvas(bitmap)))
    return bitmap
}

private class AndroidQRCanvas(private val canvas: Canvas) : QRCanvas {
    private val crisp = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
    private val smooth = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }

    override fun fillRect(x: Int, y: Int, width: Int, height: Int, argb: Int) {
        crisp.color = argb
        canvas.drawRect(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat(), crisp)
    }

    override fun fillCircle(centerX: Double, centerY: Double, radius: Double, argb: Int) {
        smooth.color = argb
        canvas.drawCircle(centerX.toFloat(), centerY.toFloat(), radius.toFloat(), smooth)
    }

    override fun fillPath(path: List<PathCommand>, argb: Int) {
        smooth.color = argb
        val androidPath = Path()
        for (command in path) {
            when (command) {
                is PathCommand.MoveTo -> androidPath.moveTo(command.x.toFloat(), command.y.toFloat())
                is PathCommand.LineTo -> androidPath.lineTo(command.x.toFloat(), command.y.toFloat())
                is PathCommand.CubicTo -> androidPath.cubicTo(
                    command.x1.toFloat(), command.y1.toFloat(),
                    command.x2.toFloat(), command.y2.toFloat(),
                    command.x.toFloat(), command.y.toFloat(),
                )
                PathCommand.Close -> androidPath.close()
            }
        }
        canvas.drawPath(androidPath, smooth)
    }
}

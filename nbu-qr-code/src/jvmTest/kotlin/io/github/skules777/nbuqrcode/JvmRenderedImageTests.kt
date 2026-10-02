package io.github.skules777.nbuqrcode

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmRenderedImageTests : RenderedImageContract() {
    override fun render(renderer: NBUQRImageRenderer, payload: NBUQRPayload): TestImage =
        renderer.bufferedImage(payload).toTestImage()

    override fun decodePng(png: ByteArray): TestImage = ImageIO.read(png.inputStream()).toTestImage()

    override val scanner: QRScanner = QRScanner(::zxingScan)
}

private fun BufferedImage.toTestImage() = TestImage(width, height, getRGB(0, 0, width, height, null, 0, width))

internal fun zxingScan(image: TestImage): String? {
    val source = RGBLuminanceSource(image.width, image.height, image.argb)
    return try {
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), mapOf(DecodeHintType.TRY_HARDER to true)).text
    } catch (_: NotFoundException) {
        null
    }
}

// ZXing's encoder is an independent implementation of ISO/IEC 18004: for the same data, level,
// version and mask both must produce the very same modules.
class QREncoderCrossCheckTests {
    // Every version from 1 to 40, each at its full byte capacity, for both levels and all masks.
    @Test
    fun matricesMatchZxingForEveryVersionAndMask() {
        val levels = listOf(NBUQRErrorCorrectionLevel.MEDIUM to ErrorCorrectionLevel.M, NBUQRErrorCorrectionLevel.QUARTILE to ErrorCorrectionLevel.Q)
        for ((level, zxingLevel) in levels) {
            val capacities = (1..2331).filter { length ->
                QREncoder.minimalVersion(length, level) != QREncoder.minimalVersion(length + 1, level)
            }
            assertEquals(40, capacities.size, "one full-capacity length per version from 1 at $level")
            for (length in capacities) {
                // Leading lower-case letter keeps ZXing in byte mode, as for real links.
                val text = "qkNECjAwMwoyCklDVAoK0s7C".repeat(100).take(length)
                for (mask in 0..7) {
                    val ours = QREncoder.encode(text.encodeToByteArray(), level, mask)!!
                    val hints = mapOf(
                        EncodeHintType.ERROR_CORRECTION to zxingLevel,
                        EncodeHintType.QR_MASK_PATTERN to mask,
                        EncodeHintType.MARGIN to 0,
                    )
                    val theirs = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
                    assertEquals(theirs.width, ours.size, "size for $length bytes at $level")
                    for (y in 0 until ours.size) {
                        for (x in 0 until ours.size) {
                            assertEquals(theirs[x, y], ours[x, y], "module $x,$y for $length bytes at $level, mask $mask")
                        }
                    }
                }
            }
        }
    }

    // The automatically chosen mask may differ from ZXing's (the penalty rules leave room for
    // interpretation), but the result must still scan.
    @Test
    fun automaticMaskScans() {
        val payload = NBUQRPayload(officialOnlineStorePayment())
        val matrix = QREncoder.encode(payload.qrUrl().encodeToByteArray(), NBUQRErrorCorrectionLevel.QUARTILE)!!
        val scale = 4
        val side = (matrix.size + 8) * scale
        val pixels = IntArray(side * side) { index ->
            val x = index % side / scale - 4
            val y = index / side / scale - 4
            if (x in 0 until matrix.size && y in 0 until matrix.size && matrix[x, y]) 0xFF000000.toInt() else -1
        }
        assertEquals(payload.qrUrl(), zxingScan(TestImage(side, side, pixels)))
    }
}

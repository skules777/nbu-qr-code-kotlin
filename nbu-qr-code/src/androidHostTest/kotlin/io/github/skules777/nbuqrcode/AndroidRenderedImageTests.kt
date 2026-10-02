package io.github.skules777.nbuqrcode

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Native graphics mode runs the real Skia raster instead of Robolectric's shadows, so the pixels
// checked here are the ones a device would draw.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class AndroidRenderedImageTests : RenderedImageContract() {
    override fun render(renderer: NBUQRImageRenderer, payload: NBUQRPayload): TestImage = renderer.bitmap(payload).toTestImage()

    override fun decodePng(png: ByteArray): TestImage = BitmapFactory.decodeByteArray(png, 0, png.size).toTestImage()

    override val scanner: QRScanner = QRScanner { image ->
        val source = RGBLuminanceSource(image.width, image.height, image.argb)
        try {
            QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), mapOf(DecodeHintType.TRY_HARDER to true)).text
        } catch (_: NotFoundException) {
            null
        }
    }

    private fun Bitmap.toTestImage(): TestImage {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return TestImage(width, height, pixels)
    }
}

package io.github.skules777.nbuqrcode

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Растр, прочитаний назад із зображення: ARGB-пікселі рядок за рядком згори донизу. */
class TestImage(val width: Int, val height: Int, val argb: IntArray) {
    fun rgb(x: Int, y: Int): List<Int> {
        val pixel = argb[y * width + x]
        return listOf((pixel shr 16) and 0xFF, (pixel shr 8) and 0xFF, pixel and 0xFF)
    }

    /** Яскравість 0–255. */
    fun gray(x: Int, y: Int): Int = rgb(x, y).sum() / 3
}

/** Сканер QR-коду: розпізнане повідомлення або `null`. */
fun interface QRScanner {
    fun scan(image: TestImage): String?
}

/**
 * Перевірки готового растра — однакові для всіх платформ. Кожна платформа
 * підставляє власну растеризацію ([render], [decodePng]) і, де можливо, сканер
 * ([scanner]): ZXing на JVM та Android.
 */
abstract class RenderedImageContract {
    abstract fun render(renderer: NBUQRImageRenderer, payload: NBUQRPayload): TestImage

    abstract fun decodePng(png: ByteArray): TestImage

    /** `null`, де справжнього сканера немає; тоді растр звіряється з матрицею в [rasterReproducesTheMatrix]. */
    abstract val scanner: QRScanner?

    private fun assertScansAs(expected: String, image: TestImage) {
        val scanner = scanner ?: return
        assertEquals(expected, scanner.scan(image))
    }

    private val green = listOf(0x00, 0x7B, 0x47)
    private val black = listOf(0, 0, 0)
    private val white = listOf(255, 255, 255)

    private fun payload() = NBUQRPayload(officialOnlineStorePayment())

    @Test
    fun renderedQRWithHryvniaMarkDecodesBackToTheSameUrl() {
        val payload = payload()
        val png = NBUQRImageRenderer().pngBytes(payload)
        assertTrue(png.isNotEmpty())
        assertScansAs(payload.qrUrl(), decodePng(png))
    }

    @Test
    fun pngMatchesThePlatformImage() {
        val renderer = NBUQRImageRenderer(accentColor = NBUQRColor.NBU_GREEN)
        val image = render(renderer, payload())
        val png = decodePng(renderer.pngBytes(payload()))
        assertEquals(image.width, png.width)
        assertEquals(image.height, png.height)
        for (y in 0 until image.height step 7) {
            for (x in 0 until image.width step 7) assertClose(image.rgb(x, y), png.rgb(x, y))
        }
    }

    @Test
    fun everyP2PAndBulkyPaymentScans() {
        for (payment in listOf(p2pPayment(), bulkyPayment(), largestQRPayment(officialOnlineStorePayment()))) {
            val payload = NBUQRPayload(payment)
            assertScansAs(payload.qrUrl(), render(NBUQRImageRenderer(), payload))
        }
    }

    @Test
    fun defaultRenderingStaysBlack() {
        val image = Rendered(NBUQRImageRenderer(), render(NBUQRImageRenderer(), payload()))
        for (finder in image.finders) assertClose(image.color(finder.center), black)
        assertClose(image.dominantSignColor(), black)
        assertClose(image.dominantDataModuleColor(), black)
    }

    // As on bank.gov.ua/ua/payments/use-qr: the accent paints the sign and the 3×3 centres of the
    // finder patterns; their outer ring and every other module stay black.
    @Test
    fun accentPaintsSignAndFinderCentersOnly() {
        val renderer = NBUQRImageRenderer(accentColor = NBUQRColor.NBU_GREEN)
        val raster = render(renderer, payload())
        val image = Rendered(renderer, raster)

        for (finder in image.finders) {
            for (dx in -1..1) {
                for (dy in -1..1) assertClose(image.color(finder.center.first + dx to finder.center.second + dy), green)
            }
            assertClose(image.color(finder.whiteRing), white)
            assertClose(image.color(finder.outerRing), black)
        }
        assertClose(image.dominantSignColor(), green)
        assertClose(image.dominantDataModuleColor(), black)
        assertFalse(image.bottomRightCornerContains(green), "the bottom-right corner has no finder pattern")
        assertScansAs(payload().qrUrl(), raster)
    }

    @Test
    fun customDarkAccentStillScans() {
        val renderer = NBUQRImageRenderer(accentColor = NBUQRColor.fromHex("#1A237E")!!)
        val raster = render(renderer, payload())
        assertClose(Rendered(renderer, raster).color(Rendered(renderer, raster).finders[0].center), listOf(0x1A, 0x23, 0x7E))
        assertScansAs(payload().qrUrl(), raster)
    }

    // Every module outside the background circle reads back exactly as the encoder produced it
    // (finder centres in the accent colour count as dark). Together with the encoder's cross-check
    // against ZXing this proves the raster is a valid code even where no scanner is available.
    @Test
    fun rasterReproducesTheMatrix() {
        for (payment in listOf(p2pPayment(), officialOnlineStorePayment(), bulkyPayment())) {
            val renderer = NBUQRImageRenderer(moduleSize = 4, quietZoneModules = 2, accentColor = NBUQRColor.NBU_GREEN)
            val payload = NBUQRPayload(payment)
            val layout = renderer.layout(payload)
            val image = render(renderer, payload)
            val matrix = layout.matrix
            val circleRadius = NBUQRImageLayout.backgroundCircleDiameterModules(matrix.version) / 2.0
            val center = matrix.size / 2.0
            for (y in 0 until matrix.size) {
                for (x in 0 until matrix.size) {
                    if (hypot(x + 0.5 - center, y + 0.5 - center) < circleRadius + 1) continue
                    val dark = image.gray((2 + x) * 4 + 2, (2 + y) * 4 + 2) < 128
                    assertEquals(matrix[x, y], dark, "module $x,$y of version ${matrix.version}")
                }
            }
        }
    }

    // Quiet zone is exactly as many modules as requested, zero included.
    @Test
    fun quietZoneIsExactlyAsRequested() {
        val moduleSize = 10
        val bare = render(NBUQRImageRenderer(moduleSize = moduleSize, quietZoneModules = 0), payload())
        assertEquals(0, bare.width % moduleSize)
        val matrixModules = bare.width / moduleSize
        assertTrue((matrixModules - 17) / 4 in NBUQRImageRenderer.ALLOWED_VERSIONS)

        for (quietZone in listOf(0, 1, 2, 4, 7)) {
            val image = render(NBUQRImageRenderer(moduleSize = moduleSize, quietZoneModules = quietZone), payload())
            assertEquals((matrixModules + 2 * quietZone) * moduleSize, image.width, "quietZone $quietZone")

            // The matrix's corner module is the finder pattern's outer ring, i.e. dark; everything
            // left of and above it is white margin.
            fun brightness(x: Int, y: Int) = image.gray(x * moduleSize + moduleSize / 2, y * moduleSize + moduleSize / 2)
            assertTrue(brightness(quietZone, quietZone) < 128, "quietZone $quietZone")
            if (quietZone > 0) assertTrue(brightness(quietZone - 1, quietZone - 1) > 128, "quietZone $quietZone")
        }
    }

    @Test
    fun onePixelModuleRenders() {
        val image = render(NBUQRImageRenderer(moduleSize = 1, quietZoneModules = 0), payload())
        assertTrue((image.width - 17) / 4 in NBUQRImageRenderer.ALLOWED_VERSIONS)
    }

    // Size and position as in the examples of appendix 4: the sign's box is centred on the
    // background circle, and its enclosing radius is a fixed share of that circle's radius.
    @Test
    fun renderedSignMatchesTheExamplesProportions() {
        val renderer = NBUQRImageRenderer()
        val image = render(renderer, payload())
        val geometry = Rendered(renderer, image)
        val signExtent = geometry.signRadius * NBUHryvniaSignTests.FARTHEST_POINT_FROM_BOX_CENTER
        val center = image.width / 2.0

        var farthestInk = 0.0
        var ink = 0.0
        var darkPixelsInGap = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val distance = hypot(x + 0.5 - center, y + 0.5 - center)
                if (distance >= geometry.backgroundRadius - 1) continue
                val coverage = 1 - image.gray(x, y) / 255.0
                if (coverage > 0.5) farthestInk = max(farthestInk, distance)
                if (distance <= signExtent + 1) {
                    ink += coverage
                } else if (coverage > 0.02) {
                    darkPixelsInGap++
                }
            }
        }

        assertTrue(abs(farthestInk - signExtent) <= 1.5, "farthest ink $farthestInk, expected $signExtent")
        assertEquals(0, darkPixelsInGap, "the ring between the sign and the background circle's edge must stay white")
        val share = ink / (PI * geometry.signRadius * geometry.signRadius)
        assertTrue(abs(share - INK_SHARE_OF_ENCLOSING_CIRCLE) <= 0.01, "ink share $share")
    }

    private fun assertClose(actual: List<Int>, expected: List<Int>) {
        assertEquals(3, actual.size, "got $actual, expected $expected")
        assertTrue(actual.zip(expected).all { (a, e) -> abs(a - e) <= 1 }, "got $actual, expected $expected")
    }

    /** Растр, адресований у модулях "голої" QR-матриці (початок у її верхньому лівому куті, y вниз). */
    private class Rendered(renderer: NBUQRImageRenderer, val image: TestImage) {
        class Finder(val center: Pair<Double, Double>, val whiteRing: Pair<Double, Double>, val outerRing: Pair<Double, Double>)

        val module = renderer.moduleSize.toDouble()
        val matrixOrigin = renderer.quietZoneModules * module
        val matrixModules = image.width / renderer.moduleSize - 2 * renderer.quietZoneModules
        val backgroundRadius = NBUQRImageLayout.backgroundCircleDiameterModules((matrixModules - 17) / 4) * module / 2
        val signRadius = backgroundRadius * NBUQRImageLayout.SIGN_TO_BACKGROUND_CIRCLE_RATIO

        val finders: List<Finder>
            get() {
                val far = (matrixModules - 7).toDouble()
                return listOf(0.0 to 0.0, far to 0.0, 0.0 to far).map { (x, y) ->
                    Finder(center = x + 3.5 to y + 3.5, whiteRing = x + 1.5 to y + 3.5, outerRing = x + 0.5 to y + 3.5)
                }
            }

        fun color(point: Pair<Double, Double>): List<Int> =
            image.rgb((matrixOrigin + point.first * module).toInt(), (matrixOrigin + point.second * module).toInt())

        fun dominantSignColor(): List<Int> = dominant { x, y -> distanceFromCenter(x, y) < signRadius }

        /** Модулі поза фоновим колом і поза трьома шуковими візерунками. */
        fun dominantDataModuleColor(): List<Int> = dominant { x, y ->
            val mx = (x - matrixOrigin) / module
            val my = (y - matrixOrigin) / module
            val far = (matrixModules - 8).toDouble()
            val inFinder = (mx < 8 || mx >= far) && (my < 8 || my >= far) && !(mx >= far && my >= far)
            !inFinder && distanceFromCenter(x, y) > backgroundRadius + module
        }

        fun bottomRightCornerContains(color: List<Int>): Boolean {
            val start = (matrixOrigin + (matrixModules - 8) * module).toInt()
            val end = (matrixOrigin + matrixModules * module).toInt()
            for (y in start until end) {
                for (x in start until end) {
                    if (image.rgb(x, y).zip(color).all { (a, b) -> abs(a - b) <= 1 }) return true
                }
            }
            return false
        }

        private fun distanceFromCenter(x: Int, y: Int): Double {
            val center = image.width / 2.0
            return hypot(x + 0.5 - center, y + 0.5 - center)
        }

        private inline fun dominant(include: (Int, Int) -> Boolean): List<Int> {
            val counts = HashMap<List<Int>, Int>()
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    if (!include(x, y)) continue
                    val color = image.rgb(x, y)
                    if (color != listOf(255, 255, 255)) counts[color] = (counts[color] ?: 0) + 1
                }
            }
            return counts.maxByOrNull { it.value }?.key ?: emptyList()
        }
    }

    private companion object {
        // Share of the enclosing circle covered by the traced sign, measured on the outline itself.
        // Catches the sign silently vanishing or changing weight.
        const val INK_SHARE_OF_ENCLOSING_CIRCLE = 0.4324
    }
}

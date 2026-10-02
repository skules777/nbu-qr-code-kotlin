package io.github.skules777.nbuqrcode

/** Команда векторного контуру; координати в пікселях, вісь y донизу. */
internal sealed interface PathCommand {
    data class MoveTo(val x: Double, val y: Double) : PathCommand
    data class LineTo(val x: Double, val y: Double) : PathCommand
    data class CubicTo(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val x: Double, val y: Double) : PathCommand
    data object Close : PathCommand
}

/**
 * Мінімальне полотно, на яке спільний код малює QR-код. Платформи реалізують його
 * через Android `Canvas`, Java2D та CoreGraphics; кольори — ARGB, вісь y донизу.
 */
internal interface QRCanvas {
    /** Прямокутник рівно по сітці пікселів, без згладжування: модулі мають лишатись чіткими. */
    fun fillRect(x: Int, y: Int, width: Int, height: Int, argb: Int)

    /** Коло зі згладжуванням. */
    fun fillCircle(centerX: Double, centerY: Double, radius: Double, argb: Int)

    /** Замкнений контур зі згладжуванням. */
    fun fillPath(path: List<PathCommand>, argb: Int)
}

/**
 * Готова до малювання розкладка: QR-матриця, тиха зона, акцентні центри шукових
 * візерунків і фонове коло зі знаком ₴. Платформонезалежна, тож усі рендери
 * малюють одне й те саме.
 */
internal class NBUQRImageLayout(
    val matrix: QRMatrix,
    val moduleSize: Int,
    val quietZoneModules: Int,
    val accentColor: NBUQRColor,
) {
    val version: Int get() = matrix.version
    val sidePixels: Int = (matrix.size + 2 * quietZoneModules) * moduleSize
    private val matrixOriginPixels: Int = quietZoneModules * moduleSize

    fun drawOn(canvas: QRCanvas) {
        canvas.fillRect(0, 0, sidePixels, sidePixels, WHITE)

        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (matrix[x, y]) {
                    canvas.fillRect(
                        matrixOriginPixels + x * moduleSize,
                        matrixOriginPixels + y * moduleSize,
                        moduleSize,
                        moduleSize,
                        BLACK,
                    )
                }
            }
        }
        drawFinderCenters(canvas)
        drawCurrencyMark(canvas)
    }

    /**
     * Зафарбовує `accentColor` темний центр 3×3 кожного шукового візерунка: у верхньому
     * лівому, верхньому правому й нижньому лівому кутах матриці.
     */
    private fun drawFinderCenters(canvas: QRCanvas) {
        val far = matrix.size - 7
        for ((column, row) in listOf(0 to 0, far to 0, 0 to far)) {
            canvas.fillRect(
                matrixOriginPixels + (column + 2) * moduleSize,
                matrixOriginPixels + (row + 2) * moduleSize,
                3 * moduleSize,
                3 * moduleSize,
                accentColor.argb,
            )
        }
    }

    private fun drawCurrencyMark(canvas: QRCanvas) {
        val center = sidePixels / 2.0
        val circleRadius = backgroundCircleDiameterModules(version) * moduleSize / 2.0
        canvas.fillCircle(center, center, circleRadius, WHITE)

        val signRadius = circleRadius * SIGN_TO_BACKGROUND_CIRCLE_RATIO
        canvas.fillPath(NBUHryvniaSign.path(signRadius, center, center), accentColor.argb)
    }

    companion object {
        const val WHITE: Int = 0xFFFFFFFF.toInt()
        const val BLACK: Int = 0xFF000000.toInt()

        /**
         * Радіус описаного кола знака відносно радіуса фонового кола — як у прикладах
         * постанови (додаток 4, приклади 1–4): у всіх чотирьох 0,6957 ± 0,0009, хоча
         * самі кола там різного розміру. Це менше, ніж давало б буквальне вписування
         * в коло на 4 модулі менше (п. 12), — розмір свідомо взято з прикладів.
         */
        const val SIGN_TO_BACKGROUND_CIRCLE_RATIO: Double = 0.6957

        /**
         * Діаметр фонового кола в модулях (додаток 1, п. 11). Таблиця охоплює
         * рівно версії 10–17 — інших значень у Правилах немає, і саме тому версія
         * перевіряється до рендеру: для версії поза цим діапазоном довелось би
         * вигадати діаметр, якого стандарт не визначає.
         */
        fun backgroundCircleDiameterModules(version: Int): Int = when (version) {
            10 -> 17
            11, 12 -> 19
            13 -> 21
            14, 15 -> 23
            // The version is guaranteed to be within 10–17, so this branch only covers 16 and 17.
            else -> 25
        }
    }
}

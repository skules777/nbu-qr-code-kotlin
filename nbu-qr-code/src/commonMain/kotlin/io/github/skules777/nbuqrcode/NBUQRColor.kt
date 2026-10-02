package io.github.skules777.nbuqrcode

import kotlin.jvm.JvmField
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Колір акценту графічного QR-коду в sRGB (див. [NBUQRImageRenderer.accentColor]).
 *
 * Постанова задає колір лише фонового кола — білий (додаток 1, п. 10); колір
 * знака ₴ вона лишає вільним.
 *
 * Компоненти поза 0…1 обмежуються цим діапазоном.
 */
public class NBUQRColor(red: Double, green: Double, blue: Double) {
    /** Червоний компонент, 0…1. */
    public val red: Double = red.coerceIn(0.0, 1.0)

    /** Зелений компонент, 0…1. */
    public val green: Double = green.coerceIn(0.0, 1.0)

    /** Синій компонент, 0…1. */
    public val blue: Double = blue.coerceIn(0.0, 1.0)

    /** Відносна яскравість за sRGB / WCAG: 0 для чорного, 1 для білого. */
    internal val relativeLuminance: Double
        get() {
            fun linear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
        }

    /** Непрозорий колір у форматі ARGB (`0xFFRRGGBB`), як `@ColorInt` в Android. */
    internal val argb: Int
        get() = (0xFF shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)

    private fun channel(value: Double) = (value * 255).roundToInt()

    override fun equals(other: Any?): Boolean =
        other is NBUQRColor && red == other.red && green == other.green && blue == other.blue

    override fun hashCode(): Int = 31 * (31 * red.hashCode() + green.hashCode()) + blue.hashCode()

    override fun toString(): String = "NBUQRColor(red=$red, green=$green, blue=$blue)"

    public companion object {
        /** Чорний — акцент за замовчуванням, тобто весь код одного кольору. */
        @JvmField
        public val BLACK: NBUQRColor = NBUQRColor(0.0, 0.0, 0.0)

        /**
         * Зелений НБУ, `#007B47`: фірмовий колір bank.gov.ua, яким там же, на
         * сторінці про QR-коди (bank.gov.ua/ua/payments/use-qr), намальовано знак ₴
         * і центри шукових візерунків.
         */
        @JvmField
        public val NBU_GREEN: NBUQRColor = NBUQRColor(0.0, 123.0 / 255, 71.0 / 255)

        /** `"#007B47"` або `"007B47"`; `null`, якщо рядок не є 6 hex-цифрами. */
        public fun fromHex(hex: String): NBUQRColor? {
            val digits = hex.removePrefix("#")
            if (digits.length != 6 || !digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
            val value = digits.toInt(16)
            return NBUQRColor(
                red = ((value shr 16) and 0xFF) / 255.0,
                green = ((value shr 8) and 0xFF) / 255.0,
                blue = (value and 0xFF) / 255.0,
            )
        }
    }
}

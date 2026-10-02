package io.github.skules777.nbuqrcode

/**
 * Кодова сторінка Windows-1251 (таблиця Microsoft). Власна, а не `Charset`:
 * у спільному коді KMP кодувань, крім UTF-8, немає.
 */
internal object Windows1251 {
    // Bytes 0x80–0xFF. 0x98 is the one unassigned byte: no character encodes to it,
    // which is what lets validation skip a separate check for it (appendix 1, item 4).
    private const val UPPER_HALF =
        "ЂЃ‚ѓ„…†‡€‰Љ‹ЊЌЋЏ" +
            "ђ‘’“”•–—￿™љ›њќћџ" +
            " ЎўЈ¤Ґ¦§Ё©Є«¬­®Ї" +
            "°±Ііґµ¶·ё№є»јЅѕї" +
            "АБВГДЕЖЗИЙКЛМНОП" +
            "РСТУФХЦЧШЩЪЫЬЭЮЯ" +
            "абвгдежзийклмноп" +
            "рстуфхцчшщъыьэюя"

    private const val UNASSIGNED = '￿'

    private val encodeTable: Map<Char, Byte> = buildMap {
        UPPER_HALF.forEachIndexed { index, char ->
            if (char != UNASSIGNED) put(char, (0x80 + index).toByte())
        }
    }

    /** Байти рядка у Windows-1251 або `null`, якщо хоч один символ не має відповідника. */
    fun encode(value: String): ByteArray? {
        val bytes = ByteArray(value.length)
        for ((index, char) in value.withIndex()) {
            bytes[index] = if (char.code < 0x80) char.code.toByte() else encodeTable[char] ?: return null
        }
        return bytes
    }

    fun canEncode(value: String): Boolean = encode(value) != null

    /** Рядок із байтів Windows-1251; непризначений байт 0x98 дає U+FFFD. */
    fun decode(bytes: ByteArray): String = buildString(bytes.size) {
        for (byte in bytes) {
            val code = byte.toInt() and 0xFF
            append(if (code < 0x80) code.toChar() else UPPER_HALF[code - 0x80].takeIf { it != UNASSIGNED } ?: '�')
        }
    }
}

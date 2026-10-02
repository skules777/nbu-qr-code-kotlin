package io.github.skules777.nbuqrcode

import kotlin.io.encoding.Base64

// Parsing a QR link deliberately lives in tests, not in the library: the library's job is to
// generate QR codes and payment links, not to process someone else's scans. The decoder is
// only a tool for checking the encoder, so it does not defend against malicious input.

internal class NBUQRDecodingException(message: String) : Exception(message)

/** Розібрана структура даних QR-коду версії формату 003 (17 полів таблиці 2 додатка 4). */
internal data class NBUQRParsedData(
    val serviceTag: String,
    val version: String,
    val encoding: String,
    val function: String,
    val uniqueId: String,
    val recipient: String,
    val iban: String,
    val amount: String,
    val recipientCode: String,
    val category: String,
    val reference: String,
    val purpose: String,
    val displayText: String,
    /** Сирий hex поля 14 — розкодуйте через `NBUQRLockMask.editableFields(fromHex)`. */
    val lockMask: String,
    val validUntil: String,
    val createdAt: String,
    val signature: String,
)

internal object NBUQRDecoder {
    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    /**
     * Розбирає посилання незалежно від коду старту застосунку — `qr.bank.gov.ua`, iOS-адреси
     * будь-якого з `NBUQRBank.ALL` або Android Intent URI.
     */
    fun parse(url: String): NBUQRParsedData {
        if (url.startsWith("intent://")) {
            return parse("https://" + url.removePrefix("intent://").substringBefore("#Intent;"))
        }
        val knownAppStartUrls = listOf(NBUQRFormat.QR_APP_START_URL) + NBUQRBank.ALL.mapNotNull { it.iosAppStartUrl }
        val matched = knownAppStartUrls.firstOrNull { url.startsWith(it) }
            ?: throw NBUQRDecodingException("Unknown app start URL: $url")
        return parsePayload(url.removePrefix(matched))
    }

    /** Розбирає чистий Base64URL-вміст (без коду старту застосунку), тобто `NBUQRPayload.value`. */
    fun parsePayload(encoded: String): NBUQRParsedData {
        val decoded = runCatching { base64Url.decode(encoded) }.getOrElse {
            throw NBUQRDecodingException("Not Base64URL")
        }
        val parts = splitByLineFeed(decoded)
        if (parts.size < 17) throw NBUQRDecodingException("Expected 17 fields, got ${parts.size}")

        val isUtf8 = parts[2].decodeToString() == NBUQRTextEncoding.UTF_8.code
        fun ascii(index: Int) = parts[index].decodeToString()
        fun text(index: Int) = if (isUtf8) parts[index].decodeToString() else Windows1251.decode(parts[index])

        return NBUQRParsedData(
            serviceTag = ascii(0),
            version = ascii(1),
            encoding = ascii(2),
            function = ascii(3),
            uniqueId = ascii(4),
            recipient = text(5),
            iban = ascii(6),
            amount = ascii(7),
            recipientCode = text(8),
            category = ascii(9),
            reference = ascii(10),
            purpose = text(11),
            displayText = text(12),
            lockMask = ascii(13),
            validUntil = ascii(14),
            createdAt = ascii(15),
            signature = ascii(16),
        )
    }

    private fun splitByLineFeed(data: ByteArray): List<ByteArray> {
        val parts = mutableListOf<ByteArray>()
        var start = 0
        for (index in data.indices) {
            if (data[index] == 0x0A.toByte()) {
                parts += data.copyOfRange(start, index)
                start = index + 1
            }
        }
        parts += data.copyOfRange(start, data.size)
        return parts
    }
}

/** Біти полів 1–5, 11, 14 і 15, які п. 4.14 вимагає блокувати в кожній масці. Полям 16–17 потрібно те саме, але біта для них немає. */
internal val NBUQRLockMask.alwaysLockedBits: Int get() = 0b1100_1000_0011_1110

/**
 * Поле 14 — "число від 0 до FFFF (hex)" змінної довжини до 4 B (таблиця 2),
 * тож "FF", "0" і "feff" у нижньому регістрі — усі валідні значення.
 */
internal fun NBUQRLockMask.parse(hex: String): Int? {
    // Only ASCII hex digits: toIntOrNull(16) would also accept a leading "+" or "-".
    if (hex.length !in 1..4 || !hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return hex.toInt(16)
}

/**
 * Розкодовує сирий `lock_mask` у поля, які він дозволяє змінювати. Невідомі
 * та RFU-біти ігноруються. Повертає `null`, якщо `hex` не є hex-числом з 1–4 цифр.
 */
internal fun NBUQRLockMask.editableFields(fromHex: String): Set<NBUQREditableField>? {
    val mask = parse(fromHex) ?: return null
    return NBUQREditableField.entries.filter { mask and it.bit == 0 }.toSet()
}

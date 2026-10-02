package io.github.skules777.nbuqrcode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class NBUQRPayloadRoundTripTests {
    @Test
    fun urlRoundTrip() {
        val url = qrUrl(validPayment())
        assertTrue(url.startsWith("https://qr.bank.gov.ua/"))
        assertTrue(url.encodeToByteArray().size <= NBUQRFormat.MAX_QR_DATA_LENGTH)

        val parsed = parsedQR(validPayment())
        assertEquals("BCD", parsed.serviceTag)
        assertEquals("003", parsed.version)
        assertEquals("ICT", parsed.function)
        assertEquals("ТОВ \"Тестова компанія\"", parsed.recipient)
        assertEquals("UA511234567890123456789012345", parsed.iban)
    }

    @Test
    fun payloadValueIsTheQrUrlWithoutHost() {
        val payload = NBUQRPayload(validPayment())
        assertEquals("https://qr.bank.gov.ua/" + payload.value, payload.qrUrl())
        assertEquals(parsedQR(validPayment()), NBUQRDecoder.parsePayload(payload.value))
    }

    // Golden vector produced independently (Python, codec cp1251 + base64).
    @Test
    fun payloadMatchesIndependentlyComputedBytes() {
        assertEquals(
            "QkNECjAwMwoyCklDVAoK0s7CICLS5fHy7uLgIOru7O_g7bP_IgpVQTUxMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NQpVQUgxMDAuNTAKMTIzNDU2NzgK" +
                "TVAyQi9HU0NCClJFRi0xMjMK0uXx8u7i6Okg7-vg8rPmCgoKCgo",
            NBUQRPayload(validPayment()).value,
        )
    }

    @Test
    fun equalPaymentsProduceEqualPayloads() {
        assertEquals(validPayment(), validPayment())
        assertEquals(NBUQRPayload(validPayment()), NBUQRPayload(validPayment()))
    }

    // Amounts compare by value on every platform, like Swift's Decimal — even though
    // BigDecimal.equals tells 100.5 and 100.50 apart.
    @Test
    fun paymentsWithNumericallyEqualAmountsAreEqual() {
        val a = validPayment().copy(amount = "100.5".toNBUQRDecimal())
        val b = validPayment().copy(amount = "100.50".toNBUQRDecimal())
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(validPayment().copy(amount = "0".toNBUQRDecimal()), validPayment().copy(amount = "0.00".toNBUQRDecimal()))
        assertNotEquals(a, validPayment().copy(amount = "100.51".toNBUQRDecimal()))
        assertNotEquals(a, validPayment().copy(amount = null))
    }

    @Test
    fun nullAmountProducesEmptyAmountField() {
        assertEquals("", parsedQR(validPayment().copy(amount = null)).amount)
    }

    // Appendix 4, item 4.8: kopecks are either absent or exactly two digits.
    @Test
    fun amountFormatting() {
        val cases = listOf(
            "100" to "UAH100",
            "100.00" to "UAH100",
            "100.5" to "UAH100.50",
            "100.50" to "UAH100.50",
            "100.10" to "UAH100.10",
            "100.99" to "UAH100.99",
            "100.05" to "UAH100.05",
            "0" to "UAH0",
            "0.5" to "UAH0.50",
            "0.01" to "UAH0.01",
            "999999999.99" to "UAH999999999.99",
            "999999999.90" to "UAH999999999.90",
            "100.994" to "UAH100.99",
            "100.996" to "UAH101",
            "100.995" to "UAH101",
            "1e2" to "UAH100",
        )
        for ((amount, expected) in cases) {
            assertEquals(expected, parsedQR(validPayment().copy(amount = amount.toNBUQRDecimal())).amount, "amount $amount")
        }
    }

    // Fields 15–16 are encoded in Kyiv time regardless of the machine's time zone.
    @Test
    fun dateTimeFormatting() {
        val date = Instant.parse("2025-09-21T12:30:45+03:00")
        val parsed = parsedQR(validPayment().copy(validUntil = date, createdAt = date))
        assertEquals("250921123045", parsed.validUntil)
        assertEquals("250921123045", parsed.createdAt)
    }

    // The same instant given in any other zone must yield the same digits: otherwise the bytes
    // would depend on where the code runs.
    @Test
    fun sameInstantEncodesIdenticallyRegardlessOfCallerTimeZone() {
        // 21.09.2025 09:30:45 UTC is 12:30:45 in Kyiv (summer time, +3).
        val instant = Instant.parse("2025-09-21T09:30:45Z")
        assertEquals("250921123045", parsedQR(validPayment().copy(validUntil = instant)).validUntil)
    }

    // Ukraine switches to winter time: the same wall-clock date in winter must use +2, not +3.
    @Test
    fun winterTimeUsesStandardOffset() {
        val instant = Instant.parse("2025-12-21T10:30:45Z")
        assertEquals("251221123045", parsedQR(validPayment().copy(validUntil = instant)).validUntil)
    }

    // Sub-second precision is dropped, not rounded: 12:30:45.999 is still second 45.
    @Test
    fun fractionalSecondsAreTruncated() {
        val instant = Instant.parse("2025-09-21T09:30:45.999Z")
        assertEquals("250921123045", parsedQR(validPayment().copy(validUntil = instant)).validUntil)
    }

    @Test
    fun decoderRejectsUnknownHost() {
        assertFailsWith<NBUQRDecodingException> { NBUQRDecoder.parse("https://invalid.com/test") }
    }
}

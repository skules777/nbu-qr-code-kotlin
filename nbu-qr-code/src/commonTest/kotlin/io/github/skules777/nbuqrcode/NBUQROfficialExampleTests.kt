package io.github.skules777.nbuqrcode

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.time.Instant

// Field values come from the official example of the NBU Rules (appendix 4, example 1: utility
// payment), decoded by hand from its hex bytes. They are checked by round-trip, not by comparing
// the payload byte by byte: there is no byte-exact match because of field 17
// (docs/NBU-COMPLIANCE.md, "Кодування даних", item 10). Only the Windows-1251 bytes of the
// "Recipient" field are compared directly — where the encoding matters. The dates in the
// document's example contradict its own hex bytes in text — apparently a PDF artefact — so they
// are not checked here.
class NBUQROfficialExampleTests {
    private fun officialExamplePayment() = NBUQRPayment(
        function = NBUQRPaymentFunction.UCT,
        encoding = NBUQRTextEncoding.WINDOWS_1251,
        recipient = "ТОВ «ГК«Нафтогаз України»",
        iban = "UA201234560000000260323012042",
        amount = "2998.39".toNBUQRDecimal(),
        recipientCode = "40121452",
        category = "SUPP/SUPP",
        reference = "AA15678-679",
        purpose = "?TickNo=”YA1267”&Addr=”вулиця Лугова, буд. 911,Микитинцi”",
        editableFields = setOf(NBUQREditableField.AMOUNT),
    )

    @Test
    fun officialExampleFieldsRoundTrip() {
        val o = officialExamplePayment()
        val parsed = parsedQR(o)

        assertEquals("UCT", parsed.function)
        assertEquals("ТОВ «ГК«Нафтогаз України»", parsed.recipient)
        assertEquals("UA201234560000000260323012042", parsed.iban)
        assertEquals("UAH2998.39", parsed.amount)
        assertEquals("40121452", parsed.recipientCode)
        assertEquals("SUPP/SUPP", parsed.category)
        assertEquals("AA15678-679", parsed.reference)
        assertEquals(o.purpose, parsed.purpose)
        assertEquals("FEFF", parsed.lockMask)
    }

    @Test
    fun officialExampleLockMaskDecodesToAmount() {
        val parsed = parsedQR(officialExamplePayment())
        assertEquals(setOf(NBUQREditableField.AMOUNT), NBUQRLockMask.editableFields(fromHex = parsed.lockMask))
    }

    @Test
    fun windows1251BytesMatchOfficialExample() {
        val expected = intArrayOf(
            0xD2, 0xCE, 0xC2, 0x20, 0xAB, 0xC3, 0xCA, 0xAB, 0xCD,
            0xE0, 0xF4, 0xF2, 0xEE, 0xE3, 0xE0, 0xE7, 0x20, 0xD3,
            0xEA, 0xF0, 0xE0, 0xBF, 0xED, 0xE8, 0xBB,
        ).map { it.toByte() }.toByteArray()
        assertContentEquals(expected, Windows1251.encode("ТОВ «ГК«Нафтогаз України»"))
    }
}

// Field values come from the official example of the NBU Rules (appendix 4, example 4: online
// store purchase, table 9), decoded by hand from its hex bytes. Unlike example 1, the text and
// the hex bytes of the dates agree here, so the dates are checked too.
class NBUQROfficialOnlineStoreExampleTests {
    // 21.03.2025 12:00 and 29.01.2025 12:00 Kyiv time — winter time, UTC+2.
    private val validUntil = Instant.parse("2025-03-21T10:00:00Z")
    private val createdAt = Instant.parse("2025-01-29T10:00:00Z")

    private fun examplePayment() = officialOnlineStorePayment().copy(validUntil = validUntil, createdAt = createdAt)

    @Test
    fun officialExampleFieldsRoundTrip() {
        val o = examplePayment()
        val parsed = parsedQR(o)

        assertEquals("ICT", parsed.function)
        assertEquals("ТОВ «ФК „ЕВО“»", parsed.recipient)
        assertEquals("UA673005280000026500504354077", parsed.iban)
        assertEquals("UAH150", parsed.amount)
        assertEquals("37193071", parsed.recipientCode)
        assertEquals("OTHR/GDDS", parsed.category)
        assertEquals("1225102576", parsed.reference)
        assertEquals(o.purpose, parsed.purpose)
        assertEquals(o.displayText, parsed.displayText)
        assertEquals("FFFF", parsed.lockMask)
        assertEquals("250321120000", parsed.validUntil)
        assertEquals("250129120000", parsed.createdAt)
    }

    @Test
    fun windows1251BytesMatchOfficialExample() {
        val expected = intArrayOf(
            0xD2, 0xCE, 0xC2, 0x20, 0xAB, 0xD4, 0xCA, 0x20,
            0x84, 0xC5, 0xC2, 0xCE, 0x93, 0xBB,
        ).map { it.toByte() }.toByteArray()
        assertContentEquals(expected, Windows1251.encode("ТОВ «ФК „ЕВО“»"))
    }

    // Golden vector produced independently (Python, codec cp1251 + base64) from the same fields:
    // a byte-exact check of the whole structure, beyond the field-by-field round-trip.
    @Test
    fun payloadMatchesIndependentlyComputedBytes() {
        assertEquals(
            "QkNECjAwMwoyCklDVAoK0s7CIKvUyiCExcLOk7sKVUE2NzMwMDUyODAwMDAwMjY1MDA1MDQzNTQwNzcKVUFIMTUwCjM3MTkzMDcxCk9USFIvR0REUwox" +
                "MjI1MTAyNTc2Cj9NZXJjaGFudEJ1c2luZXNzTmFtZT0iUk9aRVRLQS5VQSIsIM_u6vPv6uAg8u7i4PCz4iwg5-Ds7uLr5e3t_yC5ODIxNTU4OTY1Lgo_" +
                "PEluc3RyRm9yQ2R0ckFndD48SW5zdHJJbmY-TWVyY2hJRDowMTIzNC1UZXJtSWQ6NDMyMTA8L0luc3RySW5mPjwvSW5zdHJGb3JDZHRyQWd0PgpGRkZG" +
                "CjI1MDMyMTEyMDAwMAoyNTAxMjkxMjAwMDAK",
            NBUQRPayload(examplePayment()).value,
        )
    }
}

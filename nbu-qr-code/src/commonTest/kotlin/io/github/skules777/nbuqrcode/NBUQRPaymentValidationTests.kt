package io.github.skules777.nbuqrcode

import io.github.skules777.nbuqrcode.NBUQRValidationIssue.LengthUnit
import io.github.skules777.nbuqrcode.NBUQRValidationIssue.Problem
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun issue(field: NBUQRField, problem: Problem) = NBUQRValidationIssue(field, problem)

class NBUQRPaymentValidationTests {
    @Test
    fun validPaymentProducesNoIssues() {
        assertEquals(emptyList(), validPayment().validate())
    }

    @Test
    fun emptyRecipientFails() {
        assertContains(validPayment().copy(recipient = "").validate(), issue(NBUQRField.RECIPIENT, Problem.Missing))
    }

    @Test
    fun emptyIbanFails() {
        assertContains(validPayment().copy(iban = "").validate(), issue(NBUQRField.IBAN, Problem.Missing))
    }

    @Test
    fun wrongLengthIbanFails() {
        assertContains(validPayment().copy(iban = "INVALID_IBAN").validate(), issue(NBUQRField.IBAN, Problem.InvalidFormat))
    }

    @Test
    fun nonUkrainianIbanFails() {
        assertContains(
            validPayment().copy(iban = "DE511234567890123456789012345").validate(),
            issue(NBUQRField.IBAN, Problem.InvalidFormat),
        )
    }

    @Test
    fun nonAsciiDigitsInIbanFail() {
        // Arabic-Indic digits pass Char.isDigit() but are not an account number.
        assertContains(
            validPayment().copy(iban = "UA5112345678901234567890123٤٥").validate(),
            issue(NBUQRField.IBAN, Problem.InvalidFormat),
        )
    }

    @Test
    fun wrongIbanChecksumFails() {
        assertContains(
            validPayment().copy(iban = "UA521234567890123456789012345").validate(),
            issue(NBUQRField.IBAN, Problem.InvalidChecksum),
        )
    }

    @Test
    fun validChecksumPassesValidation() {
        // Real accounts from the official examples of the Rules (appendix 4, examples 1 and 4).
        for (iban in listOf("UA201234560000000260323012042", "UA673005280000026500504354077")) {
            assertEquals(emptyList(), validPayment().copy(iban = iban).validate(), iban)
        }
    }

    @Test
    fun checksumIsCheckedOnlyAfterShapeIsValid() {
        // One account, one problem: there is no point in a checksum for a string that is not yet an IBAN.
        assertFalse(
            issue(NBUQRField.IBAN, Problem.InvalidChecksum) in validPayment().copy(iban = "DE511234567890123456789012345").validate(),
        )
    }

    @Test
    fun spacesInIbanAreIgnored() {
        assertEquals(emptyList(), validPayment().copy(iban = "UA51 1234 5678 9012 3456 7890 12345").validate())
    }

    @Test
    fun emptyRecipientCodeFails() {
        assertContains(validPayment().copy(recipientCode = "").validate(), issue(NBUQRField.RECIPIENT_CODE, Problem.Missing))
    }

    @Test
    fun emptyPurposeFails() {
        assertContains(validPayment().copy(purpose = "").validate(), issue(NBUQRField.PURPOSE, Problem.Missing))
    }

    @Test
    fun negativeAmountFails() {
        assertContains(
            validPayment().copy(amount = (-100).toNBUQRDecimal()).validate(),
            issue(NBUQRField.AMOUNT, Problem.OutOfRange),
        )
    }

    @Test
    fun tooLargeAmountFails() {
        assertContains(
            validPayment().copy(amount = 1_000_000_000.toNBUQRDecimal()).validate(),
            issue(NBUQRField.AMOUNT, Problem.OutOfRange),
        )
    }

    @Test
    fun maximumAmountFromFloatingPointPasses() {
        // The exact value of the Double 999999999.99 — what BigDecimal(999999999.99) and Swift's
        // Decimal literal both hold.
        val o = validPayment().copy(amount = "999999999.9900000095367431640625".toNBUQRDecimal())
        assertEquals(emptyList(), o.validate())
        assertEquals("UAH999999999.99", parsedQR(o).amount)
    }

    @Test
    fun amountIsCheckedAfterRoundingToKopecks() {
        val roundsDown = validPayment().copy(amount = "999999999.994".toNBUQRDecimal())
        assertEquals(emptyList(), roundsDown.validate())
        assertEquals("UAH999999999.99", parsedQR(roundsDown).amount)

        val roundsUp = validPayment().copy(amount = "999999999.995".toNBUQRDecimal())
        assertContains(roundsUp.validate(), issue(NBUQRField.AMOUNT, Problem.OutOfRange))
    }

    @Test
    fun tinyNegativeAmountFailsEvenThoughItRoundsToZero() {
        assertContains(
            validPayment().copy(amount = "-0.001".toNBUQRDecimal()).validate(),
            issue(NBUQRField.AMOUNT, Problem.OutOfRange),
        )
    }

    @Test
    fun hugeAmountFailsInsteadOfOverflowing() {
        assertContains(
            validPayment().copy(amount = "1e30".toNBUQRDecimal()).validate(),
            issue(NBUQRField.AMOUNT, Problem.OutOfRange),
        )
    }

    @Test
    fun tooLongRecipientFails() {
        assertContains(
            validPayment().copy(recipient = "А".repeat(141)).validate(),
            issue(NBUQRField.RECIPIENT, Problem.TooLong(140, LengthUnit.CHARACTERS)),
        )
    }

    @Test
    fun tooLongRecipientCodeFails() {
        assertContains(
            validPayment().copy(recipientCode = "12345678901").validate(),
            issue(NBUQRField.RECIPIENT_CODE, Problem.TooLong(10, LengthUnit.BYTES)),
        )
    }

    @Test
    fun recipientCodeByteLimitCountsBytesNotCharactersForUtf8() {
        // 10 Cyrillic characters are 10 bytes in Windows-1251 but 20 in UTF-8 — field 9 is declared
        // in BYTES (table 2 of appendix 4), so it must fail for UTF-8 only.
        val tooLong = issue(NBUQRField.RECIPIENT_CODE, Problem.TooLong(10, LengthUnit.BYTES))
        val tenCyrillicChars = "А".repeat(10)

        val utf8 = validPayment().copy(encoding = NBUQRTextEncoding.UTF_8, recipientCode = tenCyrillicChars)
        assertContains(utf8.validate(), tooLong)

        val windows1251 = validPayment().copy(encoding = NBUQRTextEncoding.WINDOWS_1251, recipientCode = tenCyrillicChars)
        assertFalse(tooLong in windows1251.validate())
    }

    @Test
    fun malformedCategoryFails() {
        val malformed = listOf(
            "1234567890", // too long
            "MP2B", // no second part
            "MP2B/", // empty second part
            "MP2/GSCB", // first part shorter than 4
            "MP2B/GSCBB", // second part longer than 4
            "mp2b/gscb", // lower case
            "MP2B GSCB", // no separator
            "MP2B/GSCB/XXXX", // extra part
        )
        for (category in malformed) {
            assertTrue(
                issue(NBUQRField.CATEGORY, Problem.InvalidFormat) in validPayment().copy(category = category).validate(),
                "expected $category to be rejected",
            )
        }
    }

    @Test
    fun wellFormedCategoryPasses() {
        for (category in listOf("MP2B/GSCB", "SUPP/SUPP", "OTHR/GDDS", "1234/5678")) {
            assertEquals(emptyList(), validPayment().copy(category = category).validate(), category)
        }
    }

    // There is no official list of CCCC/PPPP values, so field 10 cannot be demanded from the
    // user — an empty value stays valid.
    @Test
    fun emptyCategoryPasses() {
        assertEquals(emptyList(), validPayment().copy(category = "").validate())
    }

    // Privat24 rejects a code whose field 10 is empty (both QR and link), so an empty category is
    // encoded as ISO 20022 "other / other" rather than left empty.
    @Test
    fun emptyCategoryIsEncodedAsOther() {
        assertEquals("OTHR/OTHR", parsedQR(validPayment().copy(category = "")).category)
        val withDefaults = NBUQRPayment(recipient = "К", iban = "UA511234567890123456789012345", recipientCode = "1", purpose = "Ч")
        assertEquals("OTHR/OTHR", NBUQRDecoder.parsePayload(NBUQRPayload(withDefaults).value).category)
    }

    @Test
    fun givenCategoryIsEncodedAsIs() {
        assertEquals("MP2B/GSCB", parsedQR(validPayment()).category)
    }

    @Test
    fun tooLongReferenceFails() {
        assertContains(
            validPayment().copy(reference = "A".repeat(36)).validate(),
            issue(NBUQRField.REFERENCE, Problem.TooLong(35, LengthUnit.CHARACTERS)),
        )
    }

    @Test
    fun tooLongPurposeFails() {
        assertContains(
            validPayment().copy(purpose = "А".repeat(421)).validate(),
            issue(NBUQRField.PURPOSE, Problem.TooLong(420, LengthUnit.CHARACTERS)),
        )
    }

    @Test
    fun tooLongDisplayTextFails() {
        assertContains(
            validPayment().copy(displayText = "А".repeat(141)).validate(),
            issue(NBUQRField.DISPLAY_TEXT, Problem.TooLong(140, LengthUnit.CHARACTERS)),
        )
    }

    @Test
    fun anyEditableFieldsPassValidation() {
        val sets = listOf(null, emptySet(), setOf(NBUQREditableField.AMOUNT), NBUQREditableField.entries.toSet())
        for (fields in sets) {
            assertEquals(emptyList(), validPayment().copy(editableFields = fields).validate(), "$fields")
        }
    }

    @Test
    fun delCharacterInRecipientFails() {
        assertContains(
            validPayment().copy(recipient = "Тест\u007FОтримувач").validate(),
            issue(NBUQRField.RECIPIENT, Problem.ForbiddenCharacters),
        )
    }

    @Test
    fun nbspCharacterInPurposeFails() {
        assertContains(
            validPayment().copy(purpose = "Оплата товару").validate(),
            issue(NBUQRField.PURPOSE, Problem.ForbiddenCharacters),
        )
    }

    // Appendix 1, item 4 allows characters from 32 only — everything below is forbidden.
    // The most dangerous is LF: it separates fields in the QR structure.
    @Test
    fun controlCharactersInTextFieldsFail() {
        val controls = listOf("LF" to '\u000A', "CR" to '\u000D', "TAB" to '\u0009', "NUL" to '\u0000')
        for ((name, char) in controls) {
            assertTrue(
                issue(NBUQRField.PURPOSE, Problem.ForbiddenCharacters) in validPayment().copy(purpose = "Оплата${char}товару").validate(),
                "expected $name to be rejected",
            )
        }
    }

    // A line break in the purpose does not distort the value but shifts the whole structure by one
    // field — so encoding must fail rather than produce a "valid" QR with shifted details.
    @Test
    fun lineFeedInPurposeCannotShiftStructure() {
        assertFailsWith<NBUQRException.InvalidPayment> {
            NBUQRPayload(validPayment().copy(purpose = "За каву\nUA111111111111111111111111111"))
        }
    }

    @Test
    fun ibanWithNonBreakingSpacesPasses() {
        // Statements group IBAN digits with NBSP (U+00A0) or narrow NBSP (U+202F).
        val o = validPayment().copy(iban = "UA51 1234 5678 9012 3456 7890 12345")
        assertEquals(emptyList(), o.validate())
        assertEquals("UA511234567890123456789012345", parsedQR(o).iban)
    }

    @Test
    fun ibanWithTabIsStillRejected() {
        assertContains(
            validPayment().copy(iban = "UA51\t1234567890123456789012345").validate(),
            issue(NBUQRField.IBAN, Problem.ForbiddenCharacters),
        )
    }

    @Test
    fun controlCharactersAreRejectedInEveryTextField() {
        val cases = listOf(
            NBUQRField.RECIPIENT to validPayment().copy(recipient = "ТОВ\u000AТест"),
            NBUQRField.RECIPIENT_CODE to validPayment().copy(recipientCode = "401\u000A214"),
            NBUQRField.REFERENCE to validPayment().copy(reference = "REF\u000A123"),
            NBUQRField.DISPLAY_TEXT to validPayment().copy(displayText = "Текст\u000Aдалі"),
            NBUQRField.IBAN to validPayment().copy(iban = "UA51\u000A1234567890123456789012345"),
        )
        for ((field, payment) in cases) {
            assertTrue(issue(field, Problem.ForbiddenCharacters) in payment.validate(), "$field")
        }
    }

    @Test
    fun payloadThrowsAllIssuesOnValidationFailure() {
        val error = assertFailsWith<NBUQRException.InvalidPayment> {
            NBUQRPayload(validPayment().copy(recipient = "", purpose = ""))
        }
        assertEquals(
            listOf(issue(NBUQRField.RECIPIENT, Problem.Missing), issue(NBUQRField.PURPOSE, Problem.Missing)),
            error.issues,
        )
    }

    @Test
    fun issueDescriptionNamesFieldAndProblem() {
        assertEquals(
            "recipientCode is too long (max 10 bytes)",
            issue(NBUQRField.RECIPIENT_CODE, Problem.TooLong(10, LengthUnit.BYTES)).toString(),
        )
    }

    @Test
    fun invalidPaymentMessageListsEveryIssue() {
        val error = assertFailsWith<NBUQRException.InvalidPayment> {
            NBUQRPayload(validPayment().copy(recipient = "", purpose = ""))
        }
        assertEquals("Invalid payment: recipient is required, purpose is required", error.message)
    }
}

// Table 1 of appendix 4 limits the Base64URL content itself (row 2, 475 B) separately from the
// total data volume (item 8 of section IV, 507 B). With the 23-byte qr.bank.gov.ua app start URL,
// table 1's limit comes first.
class NBUQRPayloadSizeTests {
    @Test
    fun payloadLimitIsTheStricterOfTheTwoStandardLimits() {
        assertEquals(475, NBUQRFormat.MAX_PAYLOAD_LENGTH)
        assertTrue(
            NBUQRFormat.QR_APP_START_URL.encodeToByteArray().size + NBUQRFormat.MAX_PAYLOAD_LENGTH <=
                NBUQRFormat.MAX_QR_DATA_LENGTH,
        )
    }

    @Test
    fun largestAcceptedPaymentFitsBothLimits() {
        val payload = NBUQRPayload(largestQRPayment(officialOnlineStorePayment()))
        assertTrue(payload.value.encodeToByteArray().size <= NBUQRFormat.MAX_PAYLOAD_LENGTH)
        assertTrue(payload.qrUrl().encodeToByteArray().size <= NBUQRFormat.MAX_QR_DATA_LENGTH)
    }

    @Test
    fun oversizedPayloadIsRejectedForQR() {
        val payload = NBUQRPayload(officialOnlineStorePayment().copy(purpose = "т".repeat(400)))
        val error = assertFailsWith<NBUQRException.QRDataTooLong> { payload.qrUrl() }
        assertTrue(error.actual > error.max)
        assertEquals(NBUQRFormat.MAX_PAYLOAD_LENGTH, error.max)
    }
}

// validate() must be the complete answer to "will the payment encode": an empty result obliges
// NBUQRPayload(payment) to succeed.
class NBUQRValidationCompletenessTests {
    @Test
    fun nonAsciiReferenceIsRejectedByValidation() {
        assertContains(
            validPayment().copy(reference = "ЗАМОВЛЕННЯ-1").validate(),
            issue(NBUQRField.REFERENCE, Problem.UnsupportedCharacters),
        )
    }

    @Test
    fun charactersOutsideWindows1251AreRejectedByValidation() {
        val o = validPayment().copy(encoding = NBUQRTextEncoding.WINDOWS_1251, recipient = "株式会社テスト")
        assertContains(o.validate(), issue(NBUQRField.RECIPIENT, Problem.UnsupportedCharacters))
    }

    // Appendix 1, item 4: UTF-8 allows only equivalents of Windows-1251 characters, so switching
    // the encoding must not widen the character set.
    @Test
    fun charactersOutsideWindows1251AreRejectedUnderUtf8Encoding() {
        val o = validPayment().copy(encoding = NBUQRTextEncoding.UTF_8, recipient = "株式会社テスト", purpose = "Оплата 😀")
        val issues = o.validate()
        assertContains(issues, issue(NBUQRField.RECIPIENT, Problem.UnsupportedCharacters))
        assertContains(issues, issue(NBUQRField.PURPOSE, Problem.UnsupportedCharacters))
    }

    @Test
    fun windows1251CharactersPassUnderUtf8Encoding() {
        val o = validPayment().copy(
            encoding = NBUQRTextEncoding.UTF_8,
            recipient = "ТОВ «Ґрунт» — Київ",
            purpose = "Замовлення №15, 20 € ‰",
        )
        assertEquals(emptyList(), o.validate())
        qrUrl(o)
    }

    // An emoji is one character, as in Swift: 140 of them are not "too long", only unsupported.
    @Test
    fun characterLimitsCountCodePointsNotUtf16Units() {
        val issues = validPayment().copy(recipient = "😀".repeat(140)).validate()
        assertEquals(listOf(issue(NBUQRField.RECIPIENT, Problem.UnsupportedCharacters)), issues)
    }

    // Contract test: empty validation ⇔ successful encoding. Encoding an unvalidated value would
    // fail an internal check rather than throw a typed error, so this verifies validate() filters
    // out every such input.
    @Test
    fun validationAgreesWithPayloadCreation() {
        val probes = listOf(
            validPayment().copy(reference = "ЗАМОВЛЕННЯ-1"),
            validPayment().copy(reference = "REF-123"),
            validPayment().copy(recipient = "株式会社テスト"),
            validPayment().copy(purpose = "Оплата 😀"),
            validPayment().copy(displayText = "Тест далі"),
            validPayment().copy(recipientCode = "Код😀"),
            validPayment().copy(purpose = "т".repeat(400)),
            validPayment().copy(encoding = NBUQRTextEncoding.UTF_8, recipient = "株式会社テスト"),
            validPayment().copy(amount = "-0.001".toNBUQRDecimal()),
        )
        for (o in probes) {
            val valid = o.validate().isEmpty()
            val created = runCatching { NBUQRPayload(o) }.also { result ->
                result.exceptionOrNull()?.let { assertTrue(it is NBUQRException.InvalidPayment, "$it") }
            }.isSuccess
            assertEquals(valid, created, "$o")
        }
    }
}

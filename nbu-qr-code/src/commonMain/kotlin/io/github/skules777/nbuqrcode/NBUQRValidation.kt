package io.github.skules777.nbuqrcode

/** Поле платежу, до якого належить [NBUQRValidationIssue]. */
public enum class NBUQRField(internal val logName: String) {
    /** Поле 6 — Отримувач. */
    RECIPIENT("recipient"),

    /** Поле 7 — Рахунок отримувача. */
    IBAN("iban"),

    /** Поле 8 — Сума. */
    AMOUNT("amount"),

    /** Поле 9 — Код отримувача. */
    RECIPIENT_CODE("recipientCode"),

    /** Поле 10 — Категорія / ціль. */
    CATEGORY("category"),

    /** Поле 11 — Reference. */
    REFERENCE("reference"),

    /** Поле 12 — Призначення платежу. */
    PURPOSE("purpose"),

    /** Поле 13 — Відображення (дисплей). */
    DISPLAY_TEXT("displayText"),
}

/**
 * Одна причина, з якої [NBUQRPayment] не можна закодувати. `toString()` —
 * англійською, для логів.
 *
 * @property field Поле, до якого належить проблема — щоб підсвітити його у формі.
 * @property problem Що не так зі значенням.
 */
public data class NBUQRValidationIssue(
    public val field: NBUQRField,
    public val problem: Problem,
) {
    /** Одиниця ліміту довжини: таблиця 2 задає частину полів у символах, частину — в байтах. */
    public enum class LengthUnit {
        /** Символи. */
        CHARACTERS,

        /** Байти в обраному кодуванні. */
        BYTES,
    }

    /** Що саме не так зі значенням поля. */
    public sealed interface Problem {
        /** Обов'язкове поле порожнє. */
        public data object Missing : Problem

        /** Значення довше за ліміт таблиці 2. */
        public data class TooLong(public val max: Int, public val unit: LengthUnit) : Problem

        /** Не та форма: IBAN не `UA` + 27 цифр, категорія не `CCCC/PPPP`. */
        public data object InvalidFormat : Problem

        /** Контрольна сума IBAN (ISO 7064 MOD 97-10) не сходиться. */
        public data object InvalidChecksum : Problem

        /** Сума поза межами 0…999999999.99. */
        public data object OutOfRange : Problem

        /** Символи поза набором поля: Windows-1251 для полів "*", ISO 646 для полів "А". */
        public data object UnsupportedCharacters : Problem

        /** Керуючі символи, DEL чи NBSP (додаток 1, п. 4). */
        public data object ForbiddenCharacters : Problem
    }

    override fun toString(): String {
        val field = field.logName
        return when (val problem = problem) {
            Problem.Missing -> "$field is required"
            is Problem.TooLong -> "$field is too long (max ${problem.max} ${if (problem.unit == LengthUnit.BYTES) "bytes" else "characters"})"
            Problem.InvalidFormat -> "$field has an invalid format"
            Problem.InvalidChecksum -> "$field has an invalid checksum"
            Problem.OutOfRange -> "$field is out of the allowed range"
            Problem.UnsupportedCharacters -> "$field contains characters outside the set allowed by the standard"
            Problem.ForbiddenCharacters -> "$field contains a character forbidden by the standard (control character, DEL or NBSP)"
        }
    }
}

internal object NBUQRValidation {
    fun validate(payment: NBUQRPayment): List<NBUQRValidationIssue> = with(payment) {
        val issues = mutableListOf<NBUQRValidationIssue>()
        fun report(field: NBUQRField, problem: NBUQRValidationIssue.Problem) {
            issues += NBUQRValidationIssue(field, problem)
        }

        if (recipient.isEmpty()) {
            report(NBUQRField.RECIPIENT, NBUQRValidationIssue.Problem.Missing)
        } else if (recipient.characterCount() > 140) {
            report(NBUQRField.RECIPIENT, tooLong(140, NBUQRValidationIssue.LengthUnit.CHARACTERS))
        }

        val iban = normalizedIban
        if (iban.isEmpty()) {
            report(NBUQRField.IBAN, NBUQRValidationIssue.Problem.Missing)
        } else if (!hasUkrainianIbanShape(iban)) {
            report(NBUQRField.IBAN, NBUQRValidationIssue.Problem.InvalidFormat)
        } else if (iso7064Mod97(iban) != 1) {
            report(NBUQRField.IBAN, NBUQRValidationIssue.Problem.InvalidChecksum)
        }

        // The range is checked on the value that gets encoded: a floating-point source like
        // BigDecimal(999999999.99) is 999999999.990000009… and would otherwise fail on its own maximum.
        // The sign, though, is checked before rounding: -0.001 rounds to zero but is still negative.
        if (amount != null) {
            val kopecks = amount.roundedKopecksOrNull()
            if (amount.isNegative() || kopecks == null || kopecks > NBUQRFormat.MAX_AMOUNT_KOPECKS) {
                report(NBUQRField.AMOUNT, NBUQRValidationIssue.Problem.OutOfRange)
            }
        }

        if (recipientCode.isEmpty()) {
            report(NBUQRField.RECIPIENT_CODE, NBUQRValidationIssue.Problem.Missing)
        } else {
            // Table 2 (appendix 4) sets field 9's length in *bytes*, unlike the character limits
            // above, and unlike them it is a "*" field (may hold Windows-1251/UTF-8 text), so byte
            // and character counts really can differ here (10 Cyrillic characters are 20 UTF-8 bytes).
            val byteCount = when (encoding) {
                NBUQRTextEncoding.UTF_8 -> recipientCode.encodeToByteArray().size
                NBUQRTextEncoding.WINDOWS_1251 -> Windows1251.encode(recipientCode)?.size
            }
            if (byteCount != null && byteCount > 10) {
                report(NBUQRField.RECIPIENT_CODE, tooLong(10, NBUQRValidationIssue.LengthUnit.BYTES))
            }
        }

        if (category.isNotEmpty() && !hasCategoryShape(category)) {
            report(NBUQRField.CATEGORY, NBUQRValidationIssue.Problem.InvalidFormat)
        }

        if (reference.characterCount() > 35) {
            report(NBUQRField.REFERENCE, tooLong(35, NBUQRValidationIssue.LengthUnit.CHARACTERS))
        }
        // Table 2's "A" fields allow ISO 646 only. IBAN and category are already checked more
        // strictly above and the library builds the mask itself, which leaves `reference`.
        if (!reference.all { it.code < 0x80 }) {
            report(NBUQRField.REFERENCE, NBUQRValidationIssue.Problem.UnsupportedCharacters)
        }

        if (purpose.isEmpty()) {
            report(NBUQRField.PURPOSE, NBUQRValidationIssue.Problem.Missing)
        } else if (purpose.characterCount() > 420) {
            report(NBUQRField.PURPOSE, tooLong(420, NBUQRValidationIssue.LengthUnit.CHARACTERS))
        }

        if (displayText.characterCount() > 140) {
            report(NBUQRField.DISPLAY_TEXT, tooLong(140, NBUQRValidationIssue.LengthUnit.CHARACTERS))
        }

        // Appendix 1, item 4 defines the character set through Windows-1251 for both encodings:
        // UTF-8 only allows "equivalents of these characters". So UTF-8 changes how text is written,
        // not which characters are allowed — emoji and CJK are forbidden there too. Checking
        // Windows-1251 encodability also guarantees encoding cannot fail in the chosen format: every
        // Windows-1251 character encodes in UTF-8 as well.
        val encodedTextFields = listOf(
            NBUQRField.RECIPIENT to recipient, NBUQRField.RECIPIENT_CODE to recipientCode,
            NBUQRField.PURPOSE to purpose, NBUQRField.DISPLAY_TEXT to displayText,
        )
        for ((field, value) in encodedTextFields) {
            if (!Windows1251.canEncode(value)) report(field, NBUQRValidationIssue.Problem.UnsupportedCharacters)
        }

        val forbiddenCharacterFields = listOf(
            NBUQRField.RECIPIENT to recipient, NBUQRField.IBAN to iban, NBUQRField.RECIPIENT_CODE to recipientCode,
            NBUQRField.CATEGORY to category, NBUQRField.REFERENCE to reference, NBUQRField.PURPOSE to purpose,
            NBUQRField.DISPLAY_TEXT to displayText,
        )
        for ((field, value) in forbiddenCharacterFields) {
            if (containsForbiddenCharacter(value)) report(field, NBUQRValidationIssue.Problem.ForbiddenCharacters)
        }

        return issues
    }

    private fun tooLong(max: Int, unit: NBUQRValidationIssue.LengthUnit) = NBUQRValidationIssue.Problem.TooLong(max, unit)

    // Code points, not UTF-16 units: an emoji is one character, as the standard counts it.
    // Swift's String.count counts grapheme clusters instead; the two agree on every string that
    // passes the Windows-1251 check, and differ only for text that is rejected anyway.
    private fun String.characterCount(): Int {
        var count = 0
        var index = 0
        while (index < length) {
            index += if (this[index].isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) 2 else 1
            count++
        }
        return count
    }

    // ASCII digits specifically: Char.isDigit() also accepts Arabic-Indic, fullwidth and other
    // Unicode digits, which would reach iso7064Mod97 as weightless characters.
    private fun hasUkrainianIbanShape(iban: String): Boolean =
        iban.length == 29 && iban.startsWith("UA") && iban.drop(2).all { it in '0'..'9' }

    /**
     * Контрольна сума IBAN за ISO 7064 MOD 97-10 (ISO 13616): для валідного
     * рахунку залишок дорівнює 1. Без цієї перевірки будь-які 27 цифр після "UA"
     * вважались би рахунком, а помилку в реквізитах платник побачив би вже у
     * банківському застосунку.
     */
    fun iso7064Mod97(iban: String): Int {
        var remainder = 0
        // ISO 13616: the first four characters move to the end, and letters weigh
        // A = 10 … Z = 35, i.e. contribute two decimal digits instead of one.
        for (char in iban.drop(4) + iban.take(4)) {
            remainder = when (char) {
                in '0'..'9' -> (remainder * 10 + (char - '0')) % 97
                in 'A'..'Z' -> (remainder * 100 + (char - 'A') + 10) % 97
                else -> return -1
            }
        }
        return remainder
    }

    // Only the shape is checked, not the codes themselves: the NBU publishes no list of allowed
    // CCCC/PPPP values, so rejecting an unfamiliar code would block payments banks accept.
    // An empty field 10 is valid and handled by the caller.
    private fun hasCategoryShape(category: String): Boolean {
        val parts = category.split("/")
        return parts.size == 2 && parts.all { part ->
            part.length == 4 && part.all { it in 'A'..'Z' || it in '0'..'9' }
        }
    }

    // Appendix 1, item 4: a data element may contain characters 32 to 255 except 127 (DEL),
    // 152 and 160 (NBSP) — in Windows-1251 or the UTF-8 equivalent; DEL and NBSP have the same
    // Unicode code points in either encoding.
    //
    // The lower bound (everything below 32) matters as much as the named exceptions: LF (0x0A)
    // separates fields in this format, so an unchecked line break inside text does not spoil the
    // value itself but shifts the rest of the structure by one field — the purpose lands in
    // "display", the mask in a date, and so on. The details still look valid but are silently
    // wrong, which is why such text is rejected here.
    //
    // The third forbidden byte, 152 (0x98) in Windows-1251, needs no separate check: no Unicode
    // character maps to it, so a string that encodes successfully cannot physically contain it.
    private fun containsForbiddenCharacter(value: String): Boolean =
        value.any { it.code < 0x20 || it.code == 0x7F || it.code == 0xA0 }
}

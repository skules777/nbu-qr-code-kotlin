package io.github.skules777.nbuqrcode

import kotlin.time.Instant

/** Функція платежу згідно з Правилами НБУ (додаток 4, п. 4.4). */
public enum class NBUQRPaymentFunction(
    /** Значення поля 4. */
    public val code: String,
) {
    /** Кредитовий переказ (Ukrainian Credit Transfer). */
    UCT("UCT"),

    /** Миттєвий кредитовий переказ (Instant Credit Transfer). */
    ICT("ICT"),

    /** Миттєвий або кредитовий переказ. */
    XCT("XCT"),
}

/** Кодування кириличних полів структури даних QR-коду (додаток 4, п. 4.3). */
public enum class NBUQRTextEncoding(
    /** Значення поля 3. */
    public val code: String,
) {
    /**
     * UTF-8: кирилиця займає 2 байти, тож у QR-код вміщується майже вдвічі
     * менше тексту.
     */
    UTF_8("1"),

    /**
     * Windows-1251 (за замовчуванням): 1 байт на символ — найкомпактніше, як і
     * вимагає додаток 1, п. 3.
     */
    WINDOWS_1251("2"),
}

/**
 * Дані для формування рахунку/платежу відповідно до таблиці 2 додатка 4
 * Правил формування, передачі та обробки структури даних і графічного
 * зображення QR-коду для здійснення кредитових та миттєвих кредитових
 * переказів (версія формату QR-коду 003).
 *
 * Сама модель нічого не гарантує — перевірку робить [validate], а
 * [NBUQRPayload] приймає лише платіж, що її пройшов. Значення в конструкторі
 * не перевіряються.
 *
 * Суми порівнюються за значенням: платежі з `100.5` і `100.50` рівні, хоча
 * `BigDecimal.equals` їх розрізняє.
 *
 * @property function Функція платежу (поле 4). За замовчуванням [NBUQRPaymentFunction.ICT].
 * @property encoding Кодування полів "*" (поле 3). За замовчуванням [NBUQRTextEncoding.WINDOWS_1251].
 * @property recipient Прізвище, ім'я, по батькові або найменування отримувача (поле 6, до 140 символів).
 * @property iban IBAN отримувача, напр. "UA123456789012345678901234567" (поле 7, рівно 29
 * символів; пробіли, якими IBAN групують у виписках, ігноруються).
 * @property amount Сума платежу; `null` — суму заповнює платник (поле 8).
 * Округлюється до копійок, половина — від нуля.
 * @property recipientCode Код отримувача коштів (поле 9, до 10 байтів в обраному кодуванні).
 * @property category Код категорії/цілі у форматі CCCC/PPPP за ISO 20022 (поле 10). Порожнє значення
 * в QR-код потрапляє як `OTHR/OTHR` ("інше / інше"): поле 10 обовʼязкове, і Приват24 код із
 * порожнім полем відхиляє.
 * @property reference Ідентифікатор рахунку на оплату отримувача (поле 11, до 35 символів ASCII).
 * @property purpose Призначення платежу (поле 12, до 420 символів).
 * @property displayText Додатковий текст для відображення (поле 13, до 140 символів).
 * @property editableFields Поля, які платник може змінити у своєму застосунку (поле 14, "Код
 * заборони зміни полів"). `null` лишає поле 14 порожнім — обмеження не встановлюється;
 * порожня множина блокує все, що стандарт дозволяє блокувати.
 * @property validUntil Дата/час дії рахунку на оплату (поле 15), кодується за київським часом.
 * @property createdAt Дата/час формування рахунку на оплату (поле 16), кодується за київським часом.
 */
public data class NBUQRPayment(
    public val function: NBUQRPaymentFunction = NBUQRPaymentFunction.ICT,
    public val encoding: NBUQRTextEncoding = NBUQRTextEncoding.WINDOWS_1251,
    public val recipient: String,
    public val iban: String,
    public val amount: NBUQRDecimal? = null,
    public val recipientCode: String,
    public val category: String = "",
    public val reference: String = "",
    public val purpose: String,
    public val displayText: String = "",
    public val editableFields: Set<NBUQREditableField>? = null,
    public val validUntil: Instant? = null,
    public val createdAt: Instant? = null,
) {
    /**
     * Усі причини, з яких платіж не можна закодувати; порожній список означає,
     * що `NBUQRPayload(payment)` завершиться успіхом. Обсяг даних для графічного
     * QR-коду тут не перевіряється — він залежить від підсумкового розміру,
     * тому перевіряється в [NBUQRPayload.qrUrl].
     */
    public fun validate(): List<NBUQRValidationIssue> = NBUQRValidation.validate(this)

    // Only space separators are dropped (space, NBSP, narrow NBSP…): statements and banking
    // apps group IBAN digits with them, so they end up in a copied account number. Line breaks
    // and tabs stay and are reported as forbidden characters.
    internal val normalizedIban: String
        get() = iban.filterNot { it.category == CharCategory.SPACE_SEPARATOR }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NBUQRPayment) return false
        val amountsEqual = if (amount == null || other.amount == null) {
            amount == null && other.amount == null
        } else {
            amount.isNumericallyEqualTo(other.amount)
        }
        return amountsEqual &&
            function == other.function &&
            encoding == other.encoding &&
            recipient == other.recipient &&
            iban == other.iban &&
            recipientCode == other.recipientCode &&
            category == other.category &&
            reference == other.reference &&
            purpose == other.purpose &&
            displayText == other.displayText &&
            editableFields == other.editableFields &&
            validUntil == other.validUntil &&
            createdAt == other.createdAt
    }

    override fun hashCode(): Int {
        var result = function.hashCode()
        result = 31 * result + encoding.hashCode()
        result = 31 * result + recipient.hashCode()
        result = 31 * result + iban.hashCode()
        result = 31 * result + (amount?.numericHashCode() ?: 0)
        result = 31 * result + recipientCode.hashCode()
        result = 31 * result + category.hashCode()
        result = 31 * result + reference.hashCode()
        result = 31 * result + purpose.hashCode()
        result = 31 * result + displayText.hashCode()
        result = 31 * result + (editableFields?.hashCode() ?: 0)
        result = 31 * result + (validUntil?.hashCode() ?: 0)
        result = 31 * result + (createdAt?.hashCode() ?: 0)
        return result
    }
}

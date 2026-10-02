package io.github.skules777.nbuqrcode

/**
 * Провалідований і закодований платіж: Base64URL-вміст структури даних QR-коду
 * версії формату 003 (таблиця 2 додатка 4), без "Коду старту застосунку".
 *
 * Створюється лише з платежу, що пройшов [NBUQRPayment.validate], тож усе,
 * що з нього будується, — посилання для QR-коду, посилання в застосунок банку,
 * графічне зображення — вже не може впасти на валідації.
 */
public class NBUQRPayload private constructor(
    /** Base64URL-вміст, спільний для QR-коду й усіх посилань — різниться лише код старту застосунку. */
    public val value: String,
) {
    /**
     * Кодує платіж.
     *
     * @throws NBUQRException.InvalidPayment якщо `payment.validate()` не порожній.
     */
    @Throws(NBUQRException::class)
    public constructor(payment: NBUQRPayment) : this(encode(payment))

    /**
     * Посилання, яке кодується в графічний QR-код. Код старту застосунку
     * завжди `https://qr.bank.gov.ua/` — лише такий QR читають сканери всіх банків
     * (docs/NBU-COMPLIANCE.md, "QR-код і deep link").
     *
     * @throws NBUQRException.QRDataTooLong якщо вміст не вміщується в ліміти
     * обсягу даних графічного QR-коду.
     */
    @Throws(NBUQRException::class)
    public fun qrUrl(): String {
        val length = value.encodeToByteArray().size
        if (length > NBUQRFormat.MAX_PAYLOAD_LENGTH) {
            throw NBUQRException.QRDataTooLong(actual = length, max = NBUQRFormat.MAX_PAYLOAD_LENGTH)
        }
        return NBUQRFormat.join(NBUQRFormat.QR_APP_START_URL, value)
    }

    /**
     * Посилання, що відкриває платіж напряму в iOS-застосунку банку (додаток 4,
     * п. 2 пп. 1) — для кнопки "Оплатити через ...", не для QR-коду. `null`, якщо в
     * банку немає [NBUQRBank.iosAppStartUrl].
     *
     * Ліміти обсягу даних тут не застосовуються: вони обмежують дані, які
     * кодуються в зображення, а перехід за посиланням зображення не потребує.
     * Тому посилання лишається робочим навіть там, де [qrUrl] уже кидає.
     */
    public fun iosDeepLink(bank: NBUQRBank): String? =
        bank.iosAppStartUrl?.let { NBUQRFormat.join(it, value) }

    /**
     * Посилання, що відкриває платіж напряму в Android-застосунку банку, у форматі
     * Android Intent URI: `intent://qr.bank.gov.ua/…#Intent;scheme=https;package=…;end`.
     * Таке посилання на вебсторінці Chrome відкриває в застосунку банку, а в
     * Android-коді його розбирає `Intent.parseUri(link, Intent.URI_INTENT_SCHEME)`;
     * у застосунку зручніше одразу `deepLinkIntent(bank)`. `null`, якщо в банку немає
     * [NBUQRBank.androidPackage].
     *
     * Ліміти обсягу даних, як і для [iosDeepLink], не застосовуються.
     */
    public fun androidDeepLink(bank: NBUQRBank): String? = bank.androidPackage?.let { packageName ->
        "intent://" + NBUQRFormat.join(NBUQRFormat.QR_APP_START_URL, value).removePrefix("https://") +
            "#Intent;scheme=https;package=$packageName;end"
    }

    override fun equals(other: Any?): Boolean = other is NBUQRPayload && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "NBUQRPayload(value=$value)"

    private companion object {
        fun encode(payment: NBUQRPayment): String {
            val issues = payment.validate()
            if (issues.isNotEmpty()) throw NBUQRException.InvalidPayment(issues)
            return NBUQRFormat.base64UrlEncode(NBUQRFormat.data(payment))
        }
    }
}

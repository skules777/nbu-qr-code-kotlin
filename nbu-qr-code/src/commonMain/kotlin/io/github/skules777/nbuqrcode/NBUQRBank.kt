package io.github.skules777.nbuqrcode

import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * Банк, у застосунку якого платіж можна відкрити напряму — для кнопки
 * "Оплатити через ...". Застосунки банків відкриваються на iOS і Android
 * по-різному, тож банк описує обидва способи; кожен може бути відсутній, якщо
 * на цій платформі прямого способу немає.
 *
 * Це клас, а не enum, щоб новий банк у бібліотеці не ламав клієнтський `when`,
 * а банк, якого тут немає, можна було описати самому через конструктор.
 *
 * @property name Назва для кнопки "Оплатити через ...".
 * @property iosAppStartUrl Персоніфікований "код старту застосунку" для iOS (додаток 4,
 * таблиця 1, рядок 1; п. 2 пп. 1) — адреса, до якої дописується закодований платіж:
 * https-адреса банку (`https://www.privat24.ua/rd/send_qr/nbu/`) або власна схема
 * застосунку (`abank24://bank.gov.ua/qr/`). Якщо адреса закінчується на `/` чи `=`
 * (параметр запиту, як `?payload=`), платіж дописується як є, інакше між ними додається `/`.
 * `null` — на iOS прямого способу відкрити застосунок немає.
 * @property androidPackage Ідентифікатор застосунку банку в Android (`com.ftband.mono`):
 * посилання `https://qr.bank.gov.ua/…` адресується саме цьому застосунку. `null` — на
 * Android прямого способу відкрити застосунок немає.
 */
public class NBUQRBank @JvmOverloads constructor(
    public val name: String,
    public val iosAppStartUrl: String? = null,
    public val androidPackage: String? = null,
) {
    init {
        require(iosAppStartUrl != null || androidPackage != null) {
            "Bank \"$name\" needs an iOS app start URL, an Android package, or both"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is NBUQRBank && name == other.name && iosAppStartUrl == other.iosAppStartUrl &&
            androidPackage == other.androidPackage

    override fun hashCode(): Int = (31 * name.hashCode() + iosAppStartUrl.hashCode()) * 31 + androidPackage.hashCode()

    override fun toString(): String = "NBUQRBank(name=$name, iosAppStartUrl=$iosAppStartUrl, androidPackage=$androidPackage)"

    // iOS app start URLs and Android packages come from the bank registry of a production IBAN
    // payment service (format 002); except where noted, none was checked with format 003.
    public companion object {
        /**
         * iOS: deep link перевірено оплатою на пристрої. Android: не перевірено.
         */
        @JvmField
        public val PRIVAT_BANK: NBUQRBank =
            NBUQRBank("Приват24", "https://www.privat24.ua/rd/send_qr/nbu/", "ua.privatbank.ap24")

        /**
         * iOS: deep link перевірено оплатою на пристрої. Android: не перевірено.
         */
        @JvmField
        public val MONOBANK: NBUQRBank = NBUQRBank("monobank", "https://mbnk.app/qr/", "com.ftband.mono")

        /** Не перевірено з форматом 003 — на розсуд клієнта. */
        @JvmField
        public val PRIVAT24_BUSINESS: NBUQRBank =
            NBUQRBank("Privat24 for business", "p24business://bank.gov.ua/qr/", "ua.privatbank.cb")

        /** Не перевірено з форматом 003 — на розсуд клієнта. */
        @JvmField
        public val ABANK: NBUQRBank = NBUQRBank("Абанк", "abank24://bank.gov.ua/qr/", "ua.com.abank")

        /** Лише iOS. Не перевірено з форматом 003 — на розсуд клієнта. */
        @JvmField
        public val PUMB: NBUQRBank = NBUQRBank("ПУМБ", iosAppStartUrl = "https://mobile-app.pumb.ua/bank.gov.ua/qr/")

        /** Не перевірено з форматом 003 — на розсуд клієнта. */
        @JvmField
        public val RAIFFEISEN: NBUQRBank =
            NBUQRBank("Райффайзен Банк", "https://my-raif.apps.raiffeisen.ua/qr?payload=", "ua.raiffeisen.myraif")

        /** Не перевірено з форматом 003 — на розсуд клієнта. */
        @JvmField
        public val UKRSIBBANK: NBUQRBank =
            NBUQRBank("UKRSIBBANK", "com.ukrsibbank.ukrsibonline.new://bank.gov.ua/qr/", "com.ukrsibbank.uso.android")

        /** Не перевірено з форматом 003 — на розсуд клієнта. */
        @JvmField
        public val UKRGASBANK: NBUQRBank = NBUQRBank("Укргазбанк", "ecobank://bank.gov.ua/qr/", "com.ugb.app")

        /** Не перевірено з форматом 003 — на розсуд клієнта. */
        @JvmField
        public val OTP_BANK: NBUQRBank = NBUQRBank("ОТП БАНК", "otpbankua://payment/qr/", "ua.otpbank.android")

        /** Не перевірено з форматом 003 — на розсуд клієнта. */
        @JvmField
        public val CREDIT_AGRICOLE: NBUQRBank =
            NBUQRBank("Креді Агріколь Банк", "https://caplusapp.credit-agricole.ua/qr/", "ua.creditagricole.mobile.app")

        /** Не перевірено з форматом 003 — на розсуд клієнта. */
        @JvmField
        public val RADABANK: NBUQRBank = NBUQRBank("РАДАБАНК", "https://radabank.com.ua/qr_code/", "com.radabank.rb24")

        /** Не перевірено з форматом 003 — на розсуд клієнта. */
        @JvmField
        public val VST_BANK: NBUQRBank = NBUQRBank("VST BANK", "vostok://bank.gov.ua/qr/", "com.vostok.bv")

        /** Лише Android. Не перевірено з форматом 003 — на розсуд клієнта. */
        @JvmField
        public val KREDOBANK: NBUQRBank = NBUQRBank("KredoBank", androidPackage = "ua.android.kredobank.prod")

        /** Лише Android. Не перевірено з форматом 003 — на розсуд клієнта. */
        @JvmField
        public val SENSE_BANK: NBUQRBank = NBUQRBank("Sense Bank", androidPackage = "ua.alfabank.mobile.android")

        /** Лише Android. Не перевірено з форматом 003 — на розсуд клієнта. */
        @JvmField
        public val IZIBANK: NBUQRBank = NBUQRBank("izibank", androidPackage = "ua.izibank.app")

        /** Лише Android. Не перевірено з форматом 003 — на розсуд клієнта. */
        @JvmField
        public val CREDIT_DNIPRO: NBUQRBank = NBUQRBank("Банк Кредит Дніпро", androidPackage = "com.creditdnepr.mb")

        /** Лише Android. Не перевірено з форматом 003 — на розсуд клієнта. */
        @JvmField
        public val GLOBUS_BANK: NBUQRBank = NBUQRBank("Глобус Банк", androidPackage = "com.t18.bone.personal.globus")

        /**
         * Усі вбудовані банки, зокрема ті, що відкриваються лише на одній платформі —
         * для кнопок на конкретній платформі беріть ті, для яких посилання не `null`.
         * Deep link перевірено оплатою лише для Приват24 і monobank на iOS; решту
         * надано як є, на розсуд клієнта (README, "Банки"). Список поповнюється в
         * мінорних версіях бібліотеки, тож кому потрібен фіксований набір кнопок,
         * збирає його сам, напр. `listOf(PRIVAT_BANK, MONOBANK)`.
         */
        @JvmField
        public val ALL: List<NBUQRBank> = listOf(
            PRIVAT_BANK, MONOBANK, PRIVAT24_BUSINESS, ABANK, PUMB, RAIFFEISEN,
            UKRSIBBANK, UKRGASBANK, OTP_BANK, CREDIT_AGRICOLE, RADABANK, VST_BANK,
            KREDOBANK, SENSE_BANK, IZIBANK, CREDIT_DNIPRO, GLOBUS_BANK,
        )
    }
}

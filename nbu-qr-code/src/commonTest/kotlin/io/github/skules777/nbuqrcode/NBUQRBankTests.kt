package io.github.skules777.nbuqrcode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Banks' personalised links (appendix 4, item 2.1) exist for links only: the same Base64URL
// payload behind a different prefix. Such a prefix never gets into the graphic QR code.
class NBUQRBankTests {
    private fun payload() = NBUQRPayload(officialOnlineStorePayment())

    // The registry of a production IBAN payment service: Android package and iOS app start URL
    // per bank. Privat24 and monobank keep the https app start URLs verified by payment on iOS.
    private val registry = listOf(
        Triple(NBUQRBank.PRIVAT_BANK, "https://www.privat24.ua/rd/send_qr/nbu/", "ua.privatbank.ap24"),
        Triple(NBUQRBank.MONOBANK, "https://mbnk.app/qr/", "com.ftband.mono"),
        Triple(NBUQRBank.PRIVAT24_BUSINESS, "p24business://bank.gov.ua/qr/", "ua.privatbank.cb"),
        Triple(NBUQRBank.ABANK, "abank24://bank.gov.ua/qr/", "ua.com.abank"),
        Triple(NBUQRBank.PUMB, "https://mobile-app.pumb.ua/bank.gov.ua/qr/", null),
        Triple(NBUQRBank.RAIFFEISEN, "https://my-raif.apps.raiffeisen.ua/qr?payload=", "ua.raiffeisen.myraif"),
        Triple(NBUQRBank.UKRSIBBANK, "com.ukrsibbank.ukrsibonline.new://bank.gov.ua/qr/", "com.ukrsibbank.uso.android"),
        Triple(NBUQRBank.UKRGASBANK, "ecobank://bank.gov.ua/qr/", "com.ugb.app"),
        Triple(NBUQRBank.OTP_BANK, "otpbankua://payment/qr/", "ua.otpbank.android"),
        Triple(NBUQRBank.CREDIT_AGRICOLE, "https://caplusapp.credit-agricole.ua/qr/", "ua.creditagricole.mobile.app"),
        Triple(NBUQRBank.RADABANK, "https://radabank.com.ua/qr_code/", "com.radabank.rb24"),
        Triple(NBUQRBank.VST_BANK, "vostok://bank.gov.ua/qr/", "com.vostok.bv"),
        Triple(NBUQRBank.KREDOBANK, null, "ua.android.kredobank.prod"),
        Triple(NBUQRBank.SENSE_BANK, null, "ua.alfabank.mobile.android"),
        Triple(NBUQRBank.IZIBANK, null, "ua.izibank.app"),
        Triple(NBUQRBank.CREDIT_DNIPRO, null, "com.creditdnepr.mb"),
        Triple(NBUQRBank.GLOBUS_BANK, null, "com.t18.bone.personal.globus"),
    )

    @Test
    fun builtInBanksMatchTheRegistry() {
        assertEquals(registry.map { it.first }, NBUQRBank.ALL)
        for ((bank, ios, android) in registry) {
            assertEquals(ios, bank.iosAppStartUrl, bank.name)
            assertEquals(android, bank.androidPackage, bank.name)
        }
        assertEquals("Приват24", NBUQRBank.PRIVAT_BANK.name)
        assertEquals("monobank", NBUQRBank.MONOBANK.name)
        assertTrue(NBUQRBank.ALL.none { it.name.isEmpty() })
    }

    // "Pay with …" buttons differ by name and links by prefix or package: a duplicate would mean
    // two identical buttons or two names for one app.
    @Test
    fun builtInBanksHaveDistinctNamesUrlsAndPackages() {
        assertEquals(NBUQRBank.ALL.size, NBUQRBank.ALL.map { it.name }.toSet().size)
        val iosUrls = NBUQRBank.ALL.mapNotNull { it.iosAppStartUrl }
        assertEquals(iosUrls.size, iosUrls.toSet().size)
        val packages = NBUQRBank.ALL.mapNotNull { it.androidPackage }
        assertEquals(packages.size, packages.toSet().size)
    }

    @Test
    fun bankNeedsAtLeastOnePlatform() {
        assertFailsWith<IllegalArgumentException> { NBUQRBank("Example") }
    }

    @Test
    fun privatBankIosDeepLinkHasCorrectPrefix() {
        assertTrue(payload().iosDeepLink(NBUQRBank.PRIVAT_BANK)!!.startsWith("https://www.privat24.ua/rd/send_qr/nbu/"))
    }

    @Test
    fun monobankIosDeepLinkHasCorrectPrefix() {
        assertTrue(payload().iosDeepLink(NBUQRBank.MONOBANK)!!.startsWith("https://mbnk.app/qr/"))
    }

    @Test
    fun androidDeepLinkIsAnIntentUriForTheBankPackage() {
        val payload = payload()
        assertEquals(
            "intent://qr.bank.gov.ua/${payload.value}#Intent;scheme=https;package=com.ftband.mono;end",
            payload.androidDeepLink(NBUQRBank.MONOBANK),
        )
    }

    @Test
    fun linksAreNullWhereTheBankHasNoAppOnThatPlatform() {
        assertNull(payload().androidDeepLink(NBUQRBank.PUMB))
        assertNull(payload().iosDeepLink(NBUQRBank.SENSE_BANK))
    }

    @Test
    fun customBankAppStartUrlWithoutTrailingSlash() {
        val payload = payload()
        assertEquals("https://bank.example/pay/" + payload.value, payload.iosDeepLink(NBUQRBank("Example", "https://bank.example/pay")))
    }

    @Test
    fun customBankAppStartUrlWithTrailingSlash() {
        val payload = payload()
        assertEquals("https://bank.example/pay/" + payload.value, payload.iosDeepLink(NBUQRBank("Example", "https://bank.example/pay/")))
    }

    // Built-in iOS app start URLs end in "/" or "=", so the payment is appended verbatim, with no separator.
    @Test
    fun everyBuiltInIosDeepLinkIsAppStartUrlFollowedByPayload() {
        val payload = payload()
        for (bank in NBUQRBank.ALL) {
            val appStartUrl = bank.iosAppStartUrl ?: continue
            assertEquals(appStartUrl + payload.value, payload.iosDeepLink(bank), bank.name)
        }
    }

    // Raiffeisen takes the payment in a query parameter: a slash after "=" would land in the value
    // itself and corrupt the payload.
    @Test
    fun raiffeisenPayloadIsTheQueryParameterValue() {
        val payload = payload()
        val query = payload.iosDeepLink(NBUQRBank.RAIFFEISEN)!!.substringAfter('?')
        val value = query.split('&').map { it.split('=', limit = 2) }.first { it[0] == "payload" }[1]
        assertEquals(payload.value, value)
    }

    @Test
    fun customAppStartUrlEndingWithEqualsSignGetsNoSlash() {
        val payload = payload()
        assertEquals(
            "https://bank.example/pay?qr=" + payload.value,
            payload.iosDeepLink(NBUQRBank("Example", "https://bank.example/pay?qr=")),
        )
    }

    @Test
    fun customSchemeAppStartUrl() {
        val payload = payload()
        assertEquals("examplebank://pay/qr/" + payload.value, payload.iosDeepLink(NBUQRBank("Example", "examplebank://pay/qr/")))
    }

    @Test
    fun decoderRecognizesEveryBankLink() {
        val payment = officialOnlineStorePayment()
        val payload = NBUQRPayload(payment)
        for (bank in NBUQRBank.ALL) {
            for (link in listOfNotNull(payload.iosDeepLink(bank), payload.androidDeepLink(bank))) {
                assertEquals(payment.recipient, NBUQRDecoder.parse(link).recipient, link)
            }
        }
    }

    @Test
    fun qrUrlAlwaysUsesOfficialAppStartUrl() {
        assertTrue(payload().qrUrl().startsWith("https://qr.bank.gov.ua/"))
        assertEquals("https://qr.bank.gov.ua/", NBUQRFormat.QR_APP_START_URL)
    }

    @Test
    fun qrUrlAndDeepLinksShareTheSamePayload() {
        val payload = payload()
        assertTrue(payload.qrUrl().endsWith(payload.value))
        for (bank in NBUQRBank.ALL) {
            payload.iosDeepLink(bank)?.let { assertTrue(it.endsWith(payload.value), bank.name) }
            payload.androidDeepLink(bank)?.let { assertTrue(it.contains("/${payload.value}#Intent;"), bank.name) }
        }
    }

    // The 507-byte limit bounds the data encoded into the image (appendix 4, item 8). Following a
    // link needs no image, so Privat24's longer app start URL (39 bytes vs 23) does not invalidate it.
    @Test
    fun deepLinksAreNotLimitedByQrDataSize() {
        val payload = NBUQRPayload(largestQRPayment(officialOnlineStorePayment()))
        assertTrue(payload.value.encodeToByteArray().size > NBUQRFormat.MAX_PAYLOAD_LENGTH - 8)

        // Privat24's app start URL is 16 bytes longer than qr.bank.gov.ua, so the link exceeds
        // 507 bytes — and that must not stop it from being built.
        val privatUrl = payload.iosDeepLink(NBUQRBank.PRIVAT_BANK)!!
        assertTrue(privatUrl.encodeToByteArray().size > NBUQRFormat.MAX_QR_DATA_LENGTH)
        assertTrue(payload.androidDeepLink(NBUQRBank.PRIVAT_BANK)!!.encodeToByteArray().size > NBUQRFormat.MAX_QR_DATA_LENGTH)
    }

    // The link is built even when there is already too much data for a graphic QR code.
    @Test
    fun deepLinksWorkWhereQrUrlThrows() {
        val payload = NBUQRPayload(officialOnlineStorePayment().copy(purpose = "т".repeat(400)))
        assertFailsWith<NBUQRException.QRDataTooLong> { payload.qrUrl() }
        assertTrue(payload.iosDeepLink(NBUQRBank.MONOBANK)!!.endsWith(payload.value))
        assertTrue(payload.androidDeepLink(NBUQRBank.MONOBANK)!!.contains(payload.value))
    }
}

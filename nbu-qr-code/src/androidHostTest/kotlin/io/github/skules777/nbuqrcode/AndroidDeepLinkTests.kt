package io.github.skules777.nbuqrcode

import android.content.Intent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidDeepLinkTests {
    private val payload = NBUQRPayload(officialOnlineStorePayment())

    // deepLinkIntent() must be exactly what Android makes of the intent:// link a web page would use.
    @Test
    fun intentMatchesTheParsedIntentUri() {
        for (bank in NBUQRBank.ALL) {
            val link = payload.androidDeepLink(bank) ?: continue
            val parsed = Intent.parseUri(link, Intent.URI_INTENT_SCHEME)
            val intent = payload.deepLinkIntent(bank)!!
            assertEquals(parsed.data, intent.data, bank.name)
            assertEquals(parsed.`package`, intent.`package`, bank.name)
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        }
    }

    @Test
    fun intentOpensTheStandardLinkInTheBankApp() {
        val intent = payload.deepLinkIntent(NBUQRBank.MONOBANK)!!
        assertEquals("https://qr.bank.gov.ua/" + payload.value, intent.dataString)
        assertEquals("com.ftband.mono", intent.`package`)
    }

    @Test
    fun noIntentForBanksWithoutAnAndroidApp() {
        assertNull(payload.deepLinkIntent(NBUQRBank.PUMB))
    }
}

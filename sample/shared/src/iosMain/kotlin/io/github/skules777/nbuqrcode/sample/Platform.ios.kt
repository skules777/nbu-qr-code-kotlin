package io.github.skules777.nbuqrcode.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import io.github.skules777.nbuqrcode.NBUQRBank
import io.github.skules777.nbuqrcode.NBUQRImageRenderer
import io.github.skules777.nbuqrcode.NBUQRPayload
import org.jetbrains.compose.resources.decodeToImageBitmap
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenURLOptionUniversalLinksOnly
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal actual fun NBUQRImageRenderer.imageBitmap(payload: NBUQRPayload): ImageBitmap =
    pngBytes(payload).decodeToImageBitmap()

@Composable
internal actual fun rememberBankAppLauncher(): BankAppLauncher = remember { IosBankAppLauncher() }

/**
 * Застосунки з власною схемою (`abank24://`…) видно через `canOpenURL` — схеми
 * оголошено в `LSApplicationQueriesSchemes` (iosApp/Info.plist). Чи встановлено
 * застосунок, що обробляє https-посилання банку (universal link), наперед дізнатися
 * не можна: така кнопка показується завжди, а [open] повідомляє, якщо застосунку немає.
 */
private class IosBankAppLauncher : BankAppLauncher {
    override fun availableBanks(payload: NBUQRPayload): List<NBUQRBank> =
        NBUQRBank.ALL.filter { bank ->
            val url = deepLinkUrl(bank, payload) ?: return@filter false
            url.isUniversalLink || UIApplication.sharedApplication.canOpenURL(url)
        }

    override suspend fun open(bank: NBUQRBank, payload: NBUQRPayload): Boolean {
        val url = deepLinkUrl(bank, payload) ?: return false
        // Universal links only: without this option a missing app would open the bank's website instead.
        val options: Map<Any?, *> = if (url.isUniversalLink) mapOf(UIApplicationOpenURLOptionUniversalLinksOnly to true) else emptyMap<Any?, Any>()
        return suspendCoroutine { continuation ->
            UIApplication.sharedApplication.openURL(url, options) { success -> continuation.resume(success) }
        }
    }

    private fun deepLinkUrl(bank: NBUQRBank, payload: NBUQRPayload): NSURL? =
        payload.iosDeepLink(bank)?.let { NSURL.URLWithString(it) }

    private val NSURL.isUniversalLink: Boolean get() = scheme == "https"
}

package io.github.skules777.nbuqrcode.sample

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import io.github.skules777.nbuqrcode.NBUQRBank
import io.github.skules777.nbuqrcode.NBUQRImageRenderer
import io.github.skules777.nbuqrcode.NBUQRPayload
import io.github.skules777.nbuqrcode.bitmap
import io.github.skules777.nbuqrcode.deepLinkIntent

internal actual fun NBUQRImageRenderer.imageBitmap(payload: NBUQRPayload): ImageBitmap =
    bitmap(payload).asImageBitmap()

@Composable
internal actual fun rememberBankAppLauncher(): BankAppLauncher {
    val context = LocalContext.current
    return remember(context) { AndroidBankAppLauncher(context) }
}

/**
 * Кнопки — лише для встановлених застосунків: `resolveActivity` бачить їх завдяки
 * `<queries>` у маніфесті androidApp.
 */
private class AndroidBankAppLauncher(private val context: Context) : BankAppLauncher {
    override fun availableBanks(payload: NBUQRPayload): List<NBUQRBank> =
        NBUQRBank.ALL.filter { bank -> payload.deepLinkIntent(bank)?.resolveActivity(context.packageManager) != null }

    override suspend fun open(bank: NBUQRBank, payload: NBUQRPayload): Boolean {
        val intent = payload.deepLinkIntent(bank) ?: return false
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            // The app was removed after the button was shown.
            false
        }
    }
}

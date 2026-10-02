package io.github.skules777.nbuqrcode.sample

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import io.github.skules777.nbuqrcode.NBUQRBank
import io.github.skules777.nbuqrcode.NBUQRImageRenderer
import io.github.skules777.nbuqrcode.NBUQRPayload

/**
 * Зображення QR-коду для Compose — через рідний для платформи шлях бібліотеки:
 * `bitmap()` на Android, PNG на iOS.
 */
internal expect fun NBUQRImageRenderer.imageBitmap(payload: NBUQRPayload): ImageBitmap

/** Відкриває платіж у застосунку банку — на кожній платформі своїм способом. */
interface BankAppLauncher {
    /** Банки, для яких показувати кнопку: ті, чий застосунок, наскільки платформа дозволяє дізнатися, встановлено. */
    fun availableBanks(payload: NBUQRPayload): List<NBUQRBank>

    /** `false` — застосунок не відкрився (немає застосунку чи способу його відкрити). */
    suspend fun open(bank: NBUQRBank, payload: NBUQRPayload): Boolean
}

@Composable
internal expect fun rememberBankAppLauncher(): BankAppLauncher

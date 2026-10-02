@file:JvmName("NBUQRDeepLinks")

package io.github.skules777.nbuqrcode

import android.content.Intent
import android.net.Uri

/**
 * Intent, що відкриває платіж напряму в застосунку банку, — для кнопки "Оплатити
 * через ...": посилання `https://qr.bank.gov.ua/…`, адресоване застосунку
 * [NBUQRBank.androidPackage]. Те саме, що [NBUQRPayload.androidDeepLink], розібране
 * через `Intent.parseUri`. `null`, якщо в банку немає Android-застосунку.
 *
 * Якщо застосунку не встановлено, `startActivity` кине `ActivityNotFoundException`.
 * Щоб показати лише кнопки встановлених банків, перевірте `resolveActivity` —
 * на Android 11+ для цього оголосіть пакети банків у `<queries>` маніфесту.
 */
public fun NBUQRPayload.deepLinkIntent(bank: NBUQRBank): Intent? = bank.androidPackage?.let { packageName ->
    Intent(Intent.ACTION_VIEW, Uri.parse(NBUQRFormat.join(NBUQRFormat.QR_APP_START_URL, value)))
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .setPackage(packageName)
}

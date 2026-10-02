package io.github.skules777.nbuqrcode.sample

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Точка входу спільного UI — її показують і Android `MainActivity`, і iOS `MainViewController`. */
@Composable
fun App() {
    SampleTheme {
        PaymentRoute()
    }
}

// The NBU green from bank.gov.ua, the same accent the QR code uses.
private val NbuGreen = Color(0xFF007B47)
private val NbuGreenLight = Color(0xFF6FDBA4)

@Composable
private fun SampleTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = NbuGreenLight)
    } else {
        lightColorScheme(primary = NbuGreen)
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

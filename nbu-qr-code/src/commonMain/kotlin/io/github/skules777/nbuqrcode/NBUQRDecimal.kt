@file:JvmName("NBUQRDecimals")

package io.github.skules777.nbuqrcode

import kotlin.jvm.JvmName

/**
 * Десяткове число для суми платежу — платформний тип, а не власна реалізація:
 * `java.math.BigDecimal` на JVM і Android, `NSDecimalNumber` на iOS. Тож на Android і бекенді
 * суму передають звичайним `BigDecimal`, без перетворень.
 *
 * У спільному (common) коді значення створюють через [toNBUQRDecimal].
 */
public expect open class NBUQRDecimal

private val decimalSyntax = Regex("""[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?""")

/**
 * Розбирає десятковий запис з крапкою як роздільником (`"100.50"`, `"-0.001"`, `"1e3"`)
 * незалежно від локалі пристрою.
 *
 * @throws NumberFormatException якщо рядок не є десятковим числом.
 */
public fun String.toNBUQRDecimal(): NBUQRDecimal {
    // NSDecimalNumber silently accepts a valid prefix ("12abc" → 12), so the grammar is
    // checked here to make every platform reject the same strings BigDecimal rejects.
    if (!decimalSyntax.matches(this)) throw NumberFormatException("Not a decimal number: \"$this\"")
    return parseDecimal(this)
}

/** Розбирає десятковий запис або повертає `null`, якщо рядок не є числом. */
public fun String.toNBUQRDecimalOrNull(): NBUQRDecimal? =
    if (decimalSyntax.matches(this)) parseDecimal(this) else null

/** Ціле число гривень. */
public fun Long.toNBUQRDecimal(): NBUQRDecimal = parseDecimal(toString())

/** Ціле число гривень. */
public fun Int.toNBUQRDecimal(): NBUQRDecimal = parseDecimal(toString())

internal expect fun parseDecimal(value: String): NBUQRDecimal

internal expect fun NBUQRDecimal.isNegative(): Boolean

/**
 * Сума, округлена до копійок способом "половина — від нуля" (як `.plain` у Swift),
 * у копійках; `null`, якщо значення не число або за модулем більше за 10^16 копійок, —
 * обидва випадки однаково поза дозволеним діапазоном.
 */
internal expect fun NBUQRDecimal.roundedKopecksOrNull(): Long?

internal expect fun NBUQRDecimal.isNumericallyEqualTo(other: NBUQRDecimal): Boolean

internal expect fun NBUQRDecimal.numericHashCode(): Int

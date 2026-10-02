package io.github.skules777.nbuqrcode

import platform.Foundation.NSDecimalNumber
import platform.Foundation.NSDecimalNumberHandler
import platform.Foundation.NSLocale
import platform.Foundation.NSOrderedAscending
import platform.Foundation.NSOrderedDescending
import platform.Foundation.NSOrderedSame
import platform.Foundation.NSRoundingMode

public actual typealias NBUQRDecimal = NSDecimalNumber

private val kopecksRounding = NSDecimalNumberHandler(
    roundingMode = NSRoundingMode.NSRoundPlain,
    scale = 2,
    raiseOnExactness = false,
    raiseOnOverflow = false,
    raiseOnUnderflow = false,
    raiseOnDivideByZero = false,
)

private val zero = NSDecimalNumber.zero
private val hryvniasLimit = NSDecimalNumber(string = "1E14", locale = posixLocale())

private fun posixLocale(): NSLocale = NSLocale(localeIdentifier = "en_US_POSIX")

internal actual fun parseDecimal(value: String): NBUQRDecimal =
    NSDecimalNumber(string = value, locale = posixLocale())

private fun NBUQRDecimal.isNaN(): Boolean = this == NSDecimalNumber.notANumber || isEqualToNumber(NSDecimalNumber.notANumber)

internal actual fun NBUQRDecimal.isNegative(): Boolean = !isNaN() && compare(zero) == NSOrderedAscending

internal actual fun NBUQRDecimal.roundedKopecksOrNull(): Long? {
    if (isNaN()) return null
    // Arithmetic without a behavior raises NSDecimalNumberOverflowException near the 10^127 limit,
    // which would crash the app, so the range is checked before any multiplication.
    val magnitude = if (compare(zero) == NSOrderedAscending) zero.decimalNumberBySubtracting(this, kopecksRounding) else this
    if (magnitude.compare(hryvniasLimit) == NSOrderedDescending) return null
    return decimalNumberByRoundingAccordingToBehavior(kopecksRounding)
        .decimalNumberByMultiplyingByPowerOf10(2, kopecksRounding)
        .longLongValue
}

internal actual fun NBUQRDecimal.isNumericallyEqualTo(other: NBUQRDecimal): Boolean = compare(other) == NSOrderedSame

internal actual fun NBUQRDecimal.numericHashCode(): Int = stringValue.hashCode()

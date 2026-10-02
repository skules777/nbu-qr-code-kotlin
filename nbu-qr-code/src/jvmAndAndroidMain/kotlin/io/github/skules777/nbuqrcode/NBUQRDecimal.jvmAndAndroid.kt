package io.github.skules777.nbuqrcode

import java.math.BigDecimal
import java.math.RoundingMode

public actual typealias NBUQRDecimal = BigDecimal

private val hryvniasLimit = BigDecimal("1E14")
private val halfKopeck = BigDecimal("0.005")

internal actual fun parseDecimal(value: String): NBUQRDecimal = BigDecimal(value)

internal actual fun NBUQRDecimal.isNegative(): Boolean = signum() < 0

internal actual fun NBUQRDecimal.roundedKopecksOrNull(): Long? {
    // setScale on "1e9999999" or "1e-9999999" builds a ten-million-digit number and takes seconds
    // (minutes for one more digit of exponent), so a short user-supplied string could stall a
    // backend. compareTo looks at exponents first and stays cheap; after these two checks the
    // value has at most 15 integer digits.
    val magnitude = abs()
    if (magnitude > hryvniasLimit) return null
    if (magnitude < halfKopeck) return 0
    return setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
}

internal actual fun NBUQRDecimal.isNumericallyEqualTo(other: NBUQRDecimal): Boolean = compareTo(other) == 0

// stripTrailingZeros makes 100.5 and 100.50 hash alike, matching compareTo-based equality.
internal actual fun NBUQRDecimal.numericHashCode(): Int =
    if (signum() == 0) 0 else stripTrailingZeros().hashCode()

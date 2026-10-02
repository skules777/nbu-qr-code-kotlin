package io.github.skules777.nbuqrcode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

// The platform decimal types must behave identically: same accepted syntax, same rounding.
class NBUQRDecimalTests {
    @Test
    fun acceptsTheSameSyntaxAsBigDecimal() {
        for (value in listOf("1", "+1", "-1", "1.", ".5", "1.50", "1e3", "1E-2", "-0.001", "007")) {
            value.toNBUQRDecimal()
        }
    }

    @Test
    fun rejectsWhatBigDecimalRejects() {
        for (value in listOf("", " 1", "1 ", "1,5", "12abc", "abc", ".", "1e", "--1", "NaN", "Infinity", "1_000")) {
            assertFailsWith<NumberFormatException>(value) { value.toNBUQRDecimal() }
            assertNull(value.toNBUQRDecimalOrNull(), value)
        }
    }

    @Test
    fun roundsHalfAwayFromZero() {
        val cases = listOf(
            "0.005" to 1L, "0.004" to 0L, "-0.005" to -1L, "-0.004" to 0L,
            "2.675" to 268L, "1e2" to 10_000L, "999999999.99" to 99_999_999_999L,
        )
        for ((value, kopecks) in cases) {
            assertEquals(kopecks, value.toNBUQRDecimal().roundedKopecksOrNull(), value)
        }
    }

    @Test
    fun hugeValuesHaveNoKopecks() {
        assertNull("1e30".toNBUQRDecimal().roundedKopecksOrNull())
        assertNull("-1e30".toNBUQRDecimal().roundedKopecksOrNull())
    }

    // Values far outside the platform types' comfortable range must neither crash (NSDecimalNumber
    // raises on overflow near 10^127) nor stall (BigDecimal.setScale expands huge exponents).
    @Test
    fun extremeAmountsAreRejectedWithoutCrashing() {
        for (value in listOf("1e999999999", "-1e999999999", "1e200", "1e127", "-1e127", "1e126", "99999999999999.995")) {
            val issues = validPayment().copy(amount = value.toNBUQRDecimal()).validate()
            assertEquals(listOf(NBUQRValidationIssue(NBUQRField.AMOUNT, NBUQRValidationIssue.Problem.OutOfRange)), issues, value)
        }
        // Tiny values either round to zero (BigDecimal) or are not representable (NSDecimalNumber
        // stops at 10^-128) — either way validation completes.
        for (value in listOf("1e-999999999", "1e-200", "1e-130", "-1e-130")) {
            validPayment().copy(amount = value.toNBUQRDecimal()).validate()
        }
    }

    @Test
    fun integerFactories() {
        assertEquals(15_000L, 150.toNBUQRDecimal().roundedKopecksOrNull())
        assertEquals(15_000L, 150L.toNBUQRDecimal().roundedKopecksOrNull())
    }
}

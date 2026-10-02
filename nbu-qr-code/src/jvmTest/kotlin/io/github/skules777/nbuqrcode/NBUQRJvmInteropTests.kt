package io.github.skules777.nbuqrcode

import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class NBUQRJvmInteropTests {
    // The hand-written table must agree with the JDK's windows-1251 for every BMP character.
    @Test
    fun windows1251TableMatchesTheJdkCharset() {
        val encoder = Charset.forName("windows-1251").newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        for (code in 0..0xFFFF) {
            val char = code.toChar()
            if (char.isSurrogate()) continue
            val expected = try {
                encoder.encode(CharBuffer.wrap(charArrayOf(char))).let { buffer -> ByteArray(buffer.remaining()).also { buffer.get(it) } }
            } catch (_: CharacterCodingException) {
                null
            }
            assertContentEquals(expected, Windows1251.encode(char.toString()), "U+%04X".format(code))
        }
    }

    @Test
    fun windows1251DecodingMatchesTheJdkCharset() {
        val charset = Charset.forName("windows-1251")
        for (byte in 0..0xFF) {
            if (byte == 0x98) continue
            val bytes = byteArrayOf(byte.toByte())
            assertEquals(charset.decode(ByteBuffer.wrap(bytes)).toString(), Windows1251.decode(bytes), "0x%02X".format(byte))
        }
    }

    // On the JVM NBUQRDecimal is BigDecimal itself, so a backend or Android caller passes it as is.
    @Test
    fun amountAcceptsBigDecimalDirectly() {
        val o = validPayment().copy(amount = BigDecimal("2998.39"))
        assertEquals("UAH2998.39", parsedQR(o).amount)
    }

    // A ten-character string must not cost seconds of CPU: BigDecimal.setScale on such exponents
    // builds a number with hundreds of millions of digits.
    @org.junit.Test(timeout = 2_000)
    fun hugeExponentsAreHandledWithoutExpandingTheNumber() {
        val huge = validPayment().copy(amount = BigDecimal("1e999999999"))
        assertEquals(listOf(NBUQRValidationIssue(NBUQRField.AMOUNT, NBUQRValidationIssue.Problem.OutOfRange)), huge.validate())

        val tiny = validPayment().copy(amount = BigDecimal("1e-999999999"))
        assertEquals(emptyList(), tiny.validate())
        assertEquals("UAH0", parsedQR(tiny).amount)

        assertEquals(huge, validPayment().copy(amount = BigDecimal("10e999999998")))
        huge.hashCode()
    }

    // The same trap Swift has with a Decimal float literal: BigDecimal(Double) holds the exact binary
    // value 999999999.990000009…, which must still pass on its own maximum.
    @Test
    fun maximumAmountFromDoubleConstructorPasses() {
        val o = validPayment().copy(amount = BigDecimal(999999999.99))
        assertEquals(emptyList(), o.validate())
        assertEquals("UAH999999999.99", parsedQR(o).amount)
    }
}

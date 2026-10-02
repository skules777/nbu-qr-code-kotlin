package io.github.skules777.nbuqrcode

import io.github.skules777.nbuqrcode.NBUQRErrorCorrectionLevel.MEDIUM
import io.github.skules777.nbuqrcode.NBUQRErrorCorrectionLevel.QUARTILE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QREncoderTests {
    // Byte-mode capacities from ISO/IEC 18004, table 7.
    @Test
    fun capacitiesAtTheVersionBoundaries() {
        assertEquals(1, QREncoder.minimalVersion(14, MEDIUM))
        assertEquals(2, QREncoder.minimalVersion(15, MEDIUM))
        assertEquals(1, QREncoder.minimalVersion(11, QUARTILE))
        assertEquals(2, QREncoder.minimalVersion(12, QUARTILE))
        assertEquals(40, QREncoder.minimalVersion(2331, MEDIUM))
        assertNull(QREncoder.minimalVersion(2332, MEDIUM))
        assertEquals(40, QREncoder.minimalVersion(1663, QUARTILE))
        assertNull(QREncoder.minimalVersion(1664, QUARTILE))
    }

    @Test
    fun matrixSideFollowsTheVersion() {
        for (length in listOf(1, 50, 200, 500, 1000)) {
            for (level in NBUQRErrorCorrectionLevel.entries) {
                val matrix = QREncoder.encode(ByteArray(length) { 'a'.code.toByte() }, level)!!
                assertEquals(matrix.version * 4 + 17, matrix.size)
                assertEquals(QREncoder.minimalVersion(length, level), matrix.version)
            }
        }
    }

    // A lower correction level never needs a larger version — the premise of the automatic fallback.
    @Test
    fun mediumNeverNeedsALargerVersionThanQuartile() {
        for (length in 1..1663) {
            assertTrue(QREncoder.minimalVersion(length, MEDIUM)!! <= QREncoder.minimalVersion(length, QUARTILE)!!)
        }
    }

    @Test
    fun finderPatternsSitInThreeCorners() {
        val matrix = QREncoder.encode("https://qr.bank.gov.ua/test".encodeToByteArray(), QUARTILE)!!
        val far = matrix.size - 7
        for ((x0, y0) in listOf(0 to 0, far to 0, 0 to far)) {
            for (d in 0..6) {
                assertTrue(matrix[x0 + d, y0] && matrix[x0, y0 + d], "outer ring at $x0,$y0")
            }
            assertTrue(!matrix[x0 + 1, y0 + 1] && matrix[x0 + 3, y0 + 3], "ring and centre at $x0,$y0")
        }
    }

    @Test
    fun tooMuchDataYieldsNoMatrix() {
        assertNull(QREncoder.encode(ByteArray(2332), MEDIUM))
    }
}

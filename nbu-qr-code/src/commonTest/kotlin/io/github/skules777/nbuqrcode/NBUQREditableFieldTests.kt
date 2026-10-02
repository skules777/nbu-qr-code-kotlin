package io.github.skules777.nbuqrcode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NBUQREditableFieldTests {
    @Test
    fun amountAloneProducesFEFF() {
        assertEquals("FEFF", NBUQRLockMask.hex(setOf(NBUQREditableField.AMOUNT)))
    }

    @Test
    fun emptySetLocksEverything() {
        assertEquals("FFFF", NBUQRLockMask.hex(emptySet()))
    }

    @Test
    fun allFieldsProducesExpectedMask() {
        // Each of the 7 togglable bits (6, 7, 8, 9, 10, 12, 13) is cleared; 0, 1–5, 11, 14, 15 stay set.
        assertEquals("C83F", NBUQRLockMask.hex(NBUQREditableField.entries.toSet()))
    }

    @Test
    fun combiningMultipleFields() {
        // 1 shl 8 | 1 shl 12 = 0x1100; inv() and 0xFFFF = 0xEEFF
        assertEquals("EEFF", NBUQRLockMask.hex(setOf(NBUQREditableField.AMOUNT, NBUQREditableField.PURPOSE)))
    }

    @Test
    fun nullLeavesField14Empty() {
        assertEquals("", parsedQR(validPayment().copy(editableFields = null)).lockMask)
    }

    @Test
    fun editableFieldsReachTheEncodedMask() {
        assertEquals("FEFF", parsedQR(validPayment().copy(editableFields = setOf(NBUQREditableField.AMOUNT))).lockMask)
    }

    @Test
    fun decodeRoundTrip() {
        val original = setOf(NBUQREditableField.AMOUNT, NBUQREditableField.RECIPIENT_CODE)
        assertEquals(original, NBUQRLockMask.editableFields(fromHex = NBUQRLockMask.hex(original)))
    }

    @Test
    fun decodeIgnoresUnknownBits() {
        // The trailing "E" also clears the unused bit 0 — decoding must ignore it and return only known fields.
        assertEquals(setOf(NBUQREditableField.AMOUNT), NBUQRLockMask.editableFields(fromHex = "FEFE"))
    }

    @Test
    fun decodeAcceptsShortAndLowercaseMasks() {
        // Field 14 is a variable-length hex number (table 2), so "FEF" means 0x0FEF: the high bits of
        // purpose and display are zero, so those fields are editable.
        assertEquals(
            setOf(NBUQREditableField.PURPOSE, NBUQREditableField.DISPLAY_TEXT),
            NBUQRLockMask.editableFields(fromHex = "FEF"),
        )
        assertEquals(setOf(NBUQREditableField.AMOUNT), NBUQRLockMask.editableFields(fromHex = "feff"))
    }

    @Test
    fun decodeRejectsWrongLength() {
        assertNull(NBUQRLockMask.editableFields(fromHex = ""))
        assertNull(NBUQRLockMask.editableFields(fromHex = "FEFFF"))
    }

    @Test
    fun decodeRejectsNonHex() {
        assertNull(NBUQRLockMask.editableFields(fromHex = "ZZZZ"))
        assertNull(NBUQRLockMask.editableFields(fromHex = "+FFF"))
        assertNull(NBUQRLockMask.editableFields(fromHex = "ＦＥＦＦ"))
    }

    // The library builds the mask itself, so item 4.14 ("fields 1–5, 11, 14, 15 are always locked")
    // must hold for any set of fields.
    @Test
    fun everyConstructedMaskKeepsAlwaysLockedFields() {
        val all = NBUQREditableField.entries
        for (bits in 0 until (1 shl all.size)) {
            val fields = all.filterIndexed { index, _ -> bits and (1 shl index) != 0 }.toSet()
            val value = NBUQRLockMask.hex(fields).toInt(16)
            assertEquals(NBUQRLockMask.alwaysLockedBits, value and NBUQRLockMask.alwaysLockedBits, "$fields")
        }
    }
}

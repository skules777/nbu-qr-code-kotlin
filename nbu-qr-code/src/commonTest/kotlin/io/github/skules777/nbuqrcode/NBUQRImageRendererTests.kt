package io.github.skules777.nbuqrcode

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun NBUQRImageRenderer.version(payment: NBUQRPayment): Int = layout(NBUQRPayload(payment)).version

// The Rules (appendix 1, item 5; appendix 4, item 9) require QR version 10–17. The constraint
// depends not only on the data volume but on the correction level too: at "Q" version 17 runs out
// before the permitted 507 bytes of data.
class NBUQRVersionRangeTests {
    @Test
    fun typicalP2PPaymentStaysWithinNBUVersionRange() {
        val version = NBUQRImageRenderer(errorCorrection = NBUQRErrorCorrection.Fixed(NBUQRErrorCorrectionLevel.QUARTILE))
            .version(p2pPayment())
        assertTrue(version in NBUQRImageRenderer.ALLOWED_VERSIONS, "got version $version")
    }

    @Test
    fun tooLittleDataIsRejectedInsteadOfRenderingVersionBelow10() {
        val error = assertFailsWith<NBUQRImageException.DataTooShort> { NBUQRImageRenderer().version(tinyPayment()) }
        assertTrue(error.version < error.minimumVersion)
        assertTrue(error.additionalDataBytes > 0)
        assertEquals(NBUQRErrorCorrectionLevel.QUARTILE, error.level)
    }

    // The number in the error must be actionable: adding that many bytes to the purpose really
    // produces a compliant QR code.
    @Test
    fun reportedShortfallIsEnoughToMakeItRender() {
        val renderer = NBUQRImageRenderer()
        val shortfall = assertFailsWith<NBUQRImageException.DataTooShort> { renderer.version(tinyPayment()) }.additionalDataBytes

        val payment = tinyPayment().copy(purpose = tinyPayment().purpose + "т".repeat(shortfall))
        assertTrue(renderer.version(payment) in NBUQRImageRenderer.ALLOWED_VERSIONS)
    }

    // ... and it is the smallest such number, give or take the Base64 rounding of 3 bytes into 4 characters.
    @Test
    fun reportedShortfallIsNotWildlyOverstated() {
        val renderer = NBUQRImageRenderer()
        val shortfall = assertFailsWith<NBUQRImageException.DataTooShort> { renderer.version(tinyPayment()) }.additionalDataBytes
        val payment = tinyPayment().copy(purpose = tinyPayment().purpose + "т".repeat(shortfall - 3))
        assertFailsWith<NBUQRImageException.DataTooShort> { renderer.version(payment) }
    }

    // Too much data for "Q" but enough for "M" — automatic selection must quietly fall back to the
    // lower level instead of producing version 18+.
    @Test
    fun automaticFallsBackToMediumWhenQuartileOverflows() {
        val layout = NBUQRImageRenderer().layout(NBUQRPayload(bulkyPayment()))
        assertTrue(layout.version in NBUQRImageRenderer.ALLOWED_VERSIONS, "got version ${layout.version}")
        assertTrue(
            QREncoder.minimalVersion(qrUrl(bulkyPayment()).encodeToByteArray().size, NBUQRErrorCorrectionLevel.QUARTILE)!! > 17,
            "the fixture must overflow Q",
        )
    }

    // Item 8 allows 507 bytes of data, but version 17 runs out earlier even at "M". With table 1's
    // limit (payload ≤ 475, i.e. URL ≤ 498) that zone became unreachable: the largest payment that
    // passes validation is guaranteed to render.
    @Test
    fun largestAcceptedPaymentAlwaysRenders() {
        for (encoding in NBUQRTextEncoding.entries) {
            val maximal = largestQRPayment(officialOnlineStorePayment().copy(encoding = encoding))
            assertTrue(NBUQRImageRenderer().version(maximal) in NBUQRImageRenderer.ALLOWED_VERSIONS, "$encoding")
        }
    }

    @Test
    fun fixedQuartileRefusesOversizedDataInsteadOfSilentlyExceeding() {
        val renderer = NBUQRImageRenderer(errorCorrection = NBUQRErrorCorrection.Fixed(NBUQRErrorCorrectionLevel.QUARTILE))
        val error = assertFailsWith<NBUQRImageException.DataTooLong> { renderer.version(bulkyPayment()) }
        assertTrue(error.version > error.maximumVersion)
        assertTrue(error.excessDataBytes > 0)
        assertEquals(NBUQRErrorCorrectionLevel.QUARTILE, error.level)
    }

    @Test
    fun reportedExcessIsEnoughToMakeItRender() {
        val renderer = NBUQRImageRenderer(errorCorrection = NBUQRErrorCorrection.Fixed(NBUQRErrorCorrectionLevel.QUARTILE))
        val excess = assertFailsWith<NBUQRImageException.DataTooLong> { renderer.version(bulkyPayment()) }.excessDataBytes
        // "т" is one byte in Windows-1251.
        val payment = bulkyPayment().copy(purpose = bulkyPayment().purpose.dropLast(excess))
        assertTrue(renderer.version(payment) in NBUQRImageRenderer.ALLOWED_VERSIONS)
    }

    @Test
    fun typicalPaymentStillRendersAtQuartile() {
        val layout = NBUQRImageRenderer().layout(NBUQRPayload(p2pPayment()))
        assertTrue(layout.version in NBUQRImageRenderer.ALLOWED_VERSIONS)
        assertEquals(
            QREncoder.minimalVersion(qrUrl(p2pPayment()).encodeToByteArray().size, NBUQRErrorCorrectionLevel.QUARTILE),
            layout.version,
        )
    }

    @Test
    fun dataTooLongFromThePayloadSurfacesAsTheCoreError() {
        val payload = NBUQRPayload(officialOnlineStorePayment().copy(purpose = "т".repeat(400)))
        assertFailsWith<NBUQRException.QRDataTooLong> { NBUQRImageRenderer().layout(payload) }
    }
}

// The capacity table in docs/NBU-COMPLIANCE.md was measured with CIQRCodeGenerator in the Swift
// version. Matching it proves that this encoder picks the same versions: how many "т" characters of
// purpose fit at "Q" (version ≤ 17) and overall (the "M" column is also the qrUrl() ceiling).
class NBUQRCapacityParityTests {
    private fun minimalPayment(encoding: NBUQRTextEncoding) = NBUQRPayment(
        encoding = encoding,
        recipient = "К",
        iban = "UA511234567890123456789012345",
        recipientCode = "1",
        purpose = "т",
    )

    /** Найдовше призначення "ттт…", що вміщується в QR-код на рівні `level`. */
    private fun maxPurpose(base: NBUQRPayment, level: NBUQRErrorCorrectionLevel): Int {
        var best = 0
        for (n in 1..420) {
            val payload = runCatching { NBUQRPayload(base.copy(purpose = "т".repeat(n))) }.getOrNull() ?: break
            if (payload.value.length > NBUQRFormat.MAX_PAYLOAD_LENGTH) break
            val version = QREncoder.minimalVersion((NBUQRFormat.QR_APP_START_URL + payload.value).encodeToByteArray().size, level) ?: break
            if (version < NBUQRImageRenderer.ALLOWED_VERSIONS.first) continue
            if (version > NBUQRImageRenderer.ALLOWED_VERSIONS.last) break
            best = n
        }
        return best
    }

    // Field 10 is empty here, so it is encoded as OTHR/OTHR — as in the table.
    @Test
    fun minimalPaymentWindows1251() {
        val base = minimalPayment(NBUQRTextEncoding.WINDOWS_1251)
        assertEquals(189, maxPurpose(base, NBUQRErrorCorrectionLevel.QUARTILE))
        assertEquals(290, maxPurpose(base, NBUQRErrorCorrectionLevel.MEDIUM))
    }

    @Test
    fun minimalPaymentUtf8() {
        val base = minimalPayment(NBUQRTextEncoding.UTF_8)
        assertEquals(94, maxPurpose(base, NBUQRErrorCorrectionLevel.QUARTILE))
        assertEquals(144, maxPurpose(base, NBUQRErrorCorrectionLevel.MEDIUM))
    }

    @Test
    fun officialExample4Windows1251() {
        val base = officialOnlineStorePayment()
        assertEquals(66, maxPurpose(base, NBUQRErrorCorrectionLevel.QUARTILE))
        assertEquals(167, maxPurpose(base, NBUQRErrorCorrectionLevel.MEDIUM))
    }

    @Test
    fun officialExample4Utf8() {
        val base = officialOnlineStorePayment().copy(encoding = NBUQRTextEncoding.UTF_8)
        assertEquals(26, maxPurpose(base, NBUQRErrorCorrectionLevel.QUARTILE))
        assertEquals(76, maxPurpose(base, NBUQRErrorCorrectionLevel.MEDIUM))
    }
}

// Impossible sizes must yield an error carrying the value itself, not RenderingFailed, which
// means a platform failure rather than bad input.
class NBUQRRendererConfigurationTests {
    private fun payload() = NBUQRPayload(officialOnlineStorePayment())

    @Test
    fun nonPositiveModuleSizeIsRejected() {
        for (moduleSize in listOf(0, -1)) {
            val error = assertFailsWith<NBUQRImageException.InvalidModuleSize> {
                NBUQRImageRenderer(moduleSize = moduleSize).layout(payload())
            }
            assertEquals(moduleSize, error.moduleSize)
        }
    }

    @Test
    fun negativeQuietZoneIsRejected() {
        val error = assertFailsWith<NBUQRImageException.InvalidQuietZone> {
            NBUQRImageRenderer(quietZoneModules = -1).layout(payload())
        }
        assertEquals(-1, error.quietZoneModules)
    }

    // Values are checked at render time, so a value set through copy() is rejected as well.
    @Test
    fun invalidValueSetThroughCopyIsRejected() {
        assertFailsWith<NBUQRImageException.InvalidModuleSize> { NBUQRImageRenderer().copy(moduleSize = 0).layout(payload()) }
    }

    // Swift traps on this overflow; here it must be a typed error, not a negative image side.
    @Test
    fun imageSideOverflowIsRejected() {
        assertFailsWith<NBUQRImageException.RenderingFailed> {
            NBUQRImageRenderer(moduleSize = Int.MAX_VALUE / 10).layout(payload())
        }
        assertFailsWith<NBUQRImageException.RenderingFailed> {
            NBUQRImageRenderer(quietZoneModules = Int.MAX_VALUE).layout(payload())
        }
    }

    @Test
    fun quietZoneIsExactlyAsRequested() {
        for (quietZone in listOf(0, 1, 2, 4, 7)) {
            val layout = NBUQRImageRenderer(moduleSize = 10, quietZoneModules = quietZone).layout(payload())
            assertEquals((layout.matrix.size + 2 * quietZone) * 10, layout.sidePixels, "quietZone $quietZone")
        }
    }

    @Test
    fun errorMessagesMatchTheSwiftVersion() {
        assertEquals("Module size must be at least 1 pixel, got 0.", NBUQRImageException.InvalidModuleSize(0).message)
        assertEquals("Quiet zone cannot be negative, got -1 modules.", NBUQRImageException.InvalidQuietZone(-1).message)
        assertEquals(
            "Accent colour is too light for a white background: contrast 0.66, at least 0.70 is required, since it paints " +
                "the finder patterns' centres too (ISO/IEC 18004 symbol contrast grade A). Use a darker colour.",
            NBUQRImageException.InsufficientAccentContrast(0.6551, 0.7).message,
        )
    }

    @Test
    fun backgroundCircleFollowsTheTableOfItem11() {
        val expected = mapOf(10 to 17, 11 to 19, 12 to 19, 13 to 21, 14 to 23, 15 to 23, 16 to 25, 17 to 25)
        for ((version, diameter) in expected) {
            assertEquals(diameter, NBUQRImageLayout.backgroundCircleDiameterModules(version), "version $version")
        }
    }
}

class NBUQRColorTests {
    @Test
    fun hexParsing() {
        assertEquals(NBUQRColor.NBU_GREEN, NBUQRColor.fromHex("#007B47"))
        assertEquals(NBUQRColor.NBU_GREEN, NBUQRColor.fromHex("007b47"))
        assertEquals(NBUQRColor.BLACK, NBUQRColor.fromHex("000000"))
        assertNull(NBUQRColor.fromHex("#007B4"))
        assertNull(NBUQRColor.fromHex("#007B4G"))
        assertNull(NBUQRColor.fromHex("+07B47"))
        assertNull(NBUQRColor.fromHex("#００７B47"))
    }

    // NaN survives coerceIn, so a NaN component must still fail the contrast check rather than crash later.
    @Test
    fun nanComponentIsRejectedAsInsufficientContrast() {
        assertFailsWith<NBUQRImageException.InsufficientAccentContrast> {
            NBUQRImageRenderer(accentColor = NBUQRColor(Double.NaN, 0.0, 0.0)).layout(NBUQRPayload(officialOnlineStorePayment()))
        }
    }

    @Test
    fun componentsAreClamped() {
        assertEquals(NBUQRColor(0.0, 1.0, 0.5), NBUQRColor(-1.0, 2.0, 0.5))
    }

    @Test
    fun argbMatchesTheHexValue() {
        assertEquals(0xFF007B47.toInt(), NBUQRColor.NBU_GREEN.argb)
        assertEquals(0xFF1A237E.toInt(), NBUQRColor.fromHex("#1A237E")!!.argb)
    }

    // The contrast table in docs/NBU-COMPLIANCE.md.
    @Test
    fun contrastOfDocumentedColours() {
        val cases = mapOf("#007B47" to 0.85, "#1A237E" to 0.97, "#E65100" to 0.77, "#9E9E9E" to 0.66, "#FF9800" to 0.56)
        for ((hex, contrast) in cases) {
            assertTrue(abs(1 - NBUQRColor.fromHex(hex)!!.relativeLuminance - contrast) < 0.005, hex)
        }
    }

    // The finder patterns' centres are how a scanner locates the code, so a pale accent would break
    // detection, not merely look faded.
    @Test
    fun tooLightAccentIsRejected() {
        val error = assertFailsWith<NBUQRImageException.InsufficientAccentContrast> {
            NBUQRImageRenderer(accentColor = NBUQRColor.fromHex("#9E9E9E")!!).layout(NBUQRPayload(officialOnlineStorePayment()))
        }
        assertTrue(error.contrast < error.minimum)
    }
}

class NBUHryvniaSignTests {
    private fun sampledPoints(path: List<PathCommand>): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        var current = 0.0 to 0.0
        for (command in path) {
            when (command) {
                is PathCommand.MoveTo -> current = (command.x to command.y).also { points += it }
                is PathCommand.LineTo -> current = (command.x to command.y).also { points += it }
                is PathCommand.CubicTo -> {
                    for (step in 1..64) {
                        val t = step / 64.0
                        val mt = 1 - t
                        val a = mt * mt * mt
                        val b = 3 * mt * mt * t
                        val c = 3 * mt * t * t
                        val d = t * t * t
                        points += (a * current.first + b * command.x1 + c * command.x2 + d * command.x) to
                            (a * current.second + b * command.y1 + c * command.y2 + d * command.y)
                    }
                    current = command.x to command.y
                }
                PathCommand.Close -> Unit
            }
        }
        return points
    }

    // The outline is centred on its bounding box rather than on the enclosing circle, so its
    // farthest point lies slightly beyond radius 1.
    @Test
    fun outlineIsCenteredOnItsBoundingBox() {
        val points = sampledPoints(NBUHryvniaSign.path(enclosingRadius = 1.0, centerX = 0.0, centerY = 0.0))
        val xs = points.map { it.first }
        val ys = points.map { it.second }
        assertTrue(abs((xs.min() + xs.max()) / 2) < 0.001)
        assertTrue(abs((ys.min() + ys.max()) / 2) < 0.001)
        assertTrue(abs(points.maxOf { hypot(it.first, it.second) } - FARTHEST_POINT_FROM_BOX_CENTER) < 0.001)
    }

    // The canvas is y-down, so the source data (y-up, as in the Swift version) must be flipped:
    // the ₴'s upper hook is the part with the most negative y.
    @Test
    fun outlineIsFlippedForAYDownCanvas() {
        val start = NBUHryvniaSign.path(enclosingRadius = 1.0, centerX = 0.0, centerY = 0.0).first() as PathCommand.MoveTo
        assertEquals(-0.5334, start.x)
        assertEquals(-0.8491, start.y)
    }

    companion object {
        const val FARTHEST_POINT_FROM_BOX_CENTER = 1.0027
    }
}

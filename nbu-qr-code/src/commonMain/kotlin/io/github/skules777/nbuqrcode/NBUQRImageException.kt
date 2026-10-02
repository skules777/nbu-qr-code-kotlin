package io.github.skules777.nbuqrcode

import kotlin.math.roundToLong

/**
 * Помилки рендеру. `message` — англійською, для логів і розробника;
 * користувачу показуйте власний текст за типом помилки.
 */
public sealed class NBUQRImageException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /**
     * Даних замало: версія нижча за мінімальну 10 (додаток 1, п. 5).
     * [additionalDataBytes] — скільки приблизно байтів даних бракує; заповнені
     * як слід поля платежу покривають це з запасом.
     */
    public class DataTooShort(
        public val version: Int,
        public val minimumVersion: Int,
        public val additionalDataBytes: Int,
        public val level: NBUQRErrorCorrectionLevel,
    ) : NBUQRImageException(
        "Payment data is too short: QR version $version at correction level ${level.code}, " +
            "but the NBU rules require at least version $minimumVersion. Add about $additionalDataBytes more bytes " +
            "of payment data — a full recipient name, a meaningful purpose or a reference is normally enough.",
    )

    /**
     * Даних забагато: версія вища за максимальну 17 (додаток 4, п. 9).
     * [excessDataBytes] — на скільки приблизно треба скоротити дані.
     */
    public class DataTooLong(
        public val version: Int,
        public val maximumVersion: Int,
        public val excessDataBytes: Int,
        public val level: NBUQRErrorCorrectionLevel,
    ) : NBUQRImageException(
        "Payment data is too long: QR version $version at correction level ${level.code}, " +
            "but the NBU rules allow at most version $maximumVersion. Remove about $excessDataBytes bytes of payment " +
            "data — the purpose field is usually the place to shorten.",
    )

    /** `accentColor` надто світлий на білому тлі: контраст `1 − яскравість` нижчий за [minimum]. */
    public class InsufficientAccentContrast(
        public val contrast: Double,
        public val minimum: Double,
    ) : NBUQRImageException(
        "Accent colour is too light for a white background: contrast " +
            twoDecimals(contrast) + ", at least " + twoDecimals(minimum) +
            " is required, since it paints the finder patterns' centres too " +
            "(ISO/IEC 18004 symbol contrast grade A). Use a darker colour.",
    )

    /** `moduleSize` менший за 1 піксель. */
    public class InvalidModuleSize(
        public val moduleSize: Int,
    ) : NBUQRImageException("Module size must be at least 1 pixel, got $moduleSize.")

    /** `quietZoneModules` від'ємний. */
    public class InvalidQuietZone(
        public val quietZoneModules: Int,
    ) : NBUQRImageException("Quiet zone cannot be negative, got $quietZoneModules modules.")

    /**
     * Збій платформної растеризації чи кодування PNG — не наслідок вхідних
     * даних. [reason] пояснює деталі для логів.
     */
    public class RenderingFailed(
        public val reason: String,
        cause: Throwable? = null,
    ) : NBUQRImageException("Failed to render the QR code image: $reason", cause)
}

// String.format is JVM-only; contrast lies in 0…1, so fixed-point rounding is exact enough.
private fun twoDecimals(value: Double): String {
    if (value.isNaN()) return "NaN"
    val hundredths = (value * 100).roundToLong()
    val sign = if (hundredths < 0) "-" else ""
    val magnitude = kotlin.math.abs(hundredths)
    return sign + (magnitude / 100) + "." + (magnitude % 100).toString().padStart(2, '0')
}

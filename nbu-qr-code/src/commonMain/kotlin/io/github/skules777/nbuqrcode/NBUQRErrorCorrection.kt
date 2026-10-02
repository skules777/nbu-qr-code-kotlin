package io.github.skules777.nbuqrcode

/**
 * Рівень корекції помилок графічного QR-коду.
 *
 * "L" тут навмисно відсутній: Правила НБУ забороняють його використання при
 * обов'язковому (для версії формату 003) накладанні графічного знака гривні
 * (додаток 1, п. 13). Причина фізична — фонове коло зі знаком гривні затирає
 * ≈7% площі коду, тобто рівно стільки, скільки здатен відновити рівень "L".
 */
public enum class NBUQRErrorCorrectionLevel(
    /** Позначення рівня: "M" або "Q". */
    public val code: String,
) {
    /** "M" — відновлює ≈15% пошкоджень коду. */
    MEDIUM("M"),

    /** "Q" — відновлює ≈25% пошкоджень коду. */
    QUARTILE("Q"),
}

/** Спосіб вибору рівня корекції. */
public sealed interface NBUQRErrorCorrection {
    /**
     * Найвищий рівень, за якого версія QR-коду лишається в дозволених 10–17:
     * спершу "Q", і лише якщо даних забагато — "M".
     */
    public data object Automatic : NBUQRErrorCorrection

    /**
     * Фіксований рівень. Якщо він дає версію поза 10–17, рендер кидає
     * [NBUQRImageException.DataTooShort] або [NBUQRImageException.DataTooLong]
     * замість того, щоб мовчки видати несумісний код.
     */
    public data class Fixed(public val level: NBUQRErrorCorrectionLevel) : NBUQRErrorCorrection
}

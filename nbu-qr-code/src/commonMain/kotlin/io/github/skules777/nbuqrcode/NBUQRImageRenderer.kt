package io.github.skules777.nbuqrcode


/**
 * Рендерить графічне зображення QR-коду НБУ версії формату 003 — сам QR-код
 * плюс обов'язковий графічний знак національної грошової одиниці (₴) у
 * білому колі по центру (додаток 1, пп. 10–14). Знак — векторний контур, тож
 * від шрифтів системи рендер не залежить.
 *
 * У зображення завжди кодується [NBUQRPayload.qrUrl], тобто код старту
 * застосунку `https://qr.bank.gov.ua/`. Передати сюди готове посилання неможливо
 * навмисно: QR-код з кодом старту застосунку банку читає лише сканер цього
 * банку (docs/NBU-COMPLIANCE.md, "QR-код і deep link").
 *
 * Результат — PNG ([pngBytes], на будь-якій платформі) або платформне зображення:
 * `bitmap()` на Android, `bufferedImage()` на JVM, `uiImage()` на iOS.
 *
 * Значення перевіряються під час рендеру, а не в конструкторі.
 *
 * @property errorCorrection Спосіб вибору рівня корекції. За замовчуванням [NBUQRErrorCorrection.Automatic].
 * @property moduleSize Розмір одного модуля QR-коду в пікселях ("X-розмір", додаток 1, п. 7).
 * Щонайменше 1, інакше рендер кидає [NBUQRImageException.InvalidModuleSize]. Верхньої межі свідомо
 * немає: розмір зображення — рішення клієнта, а не стандарту.
 * @property quietZoneModules Тиха зона з кожного боку QR-коду, у модулях. За замовчуванням 4 — як у
 * ДСТУ ISO/IEC 18004:2019, на який спираються Правила. Менше значення, зокрема 0, — свідоме
 * відхилення на розсуд клієнта; від'ємне — [NBUQRImageException.InvalidQuietZone].
 * @property accentColor Колір знака ₴ і центрів 3×3 трьох шукових візерунків у кутах коду —
 * як у прикладі на bank.gov.ua/ua/payments/use-qr. Решта модулів лишається чорною, тло, тиха
 * зона й фонове коло — білими.
 */
public data class NBUQRImageRenderer(
    public val errorCorrection: NBUQRErrorCorrection = NBUQRErrorCorrection.Automatic,
    public val moduleSize: Int = 10,
    public val quietZoneModules: Int = 4,
    public val accentColor: NBUQRColor = NBUQRColor.BLACK,
) {
    /**
     * PNG-байти зображення QR-коду для закодованого платежу. Доступно на всіх
     * платформах, зокрема в спільному коді KMP.
     *
     * @throws NBUQRException.QRDataTooLong якщо дані не вміщуються в ліміти графічного QR-коду.
     * @throws NBUQRImageException.DataTooShort якщо жоден дозволений рівень корекції не дає версію в межах 10–17.
     * @throws NBUQRImageException.DataTooLong те саме, коли даних забагато.
     * @throws NBUQRImageException.InsufficientAccentContrast для надто світлого [accentColor].
     * @throws NBUQRImageException.InvalidModuleSize для [moduleSize] менше 1.
     * @throws NBUQRImageException.InvalidQuietZone для від'ємного [quietZoneModules].
     * @throws NBUQRImageException.RenderingFailed при збої платформної растеризації.
     */
    @Throws(NBUQRException::class, NBUQRImageException::class)
    public fun pngBytes(payload: NBUQRPayload): ByteArray = encodePng(layout(payload))

    internal fun layout(payload: NBUQRPayload): NBUQRImageLayout {
        if (moduleSize < 1) throw NBUQRImageException.InvalidModuleSize(moduleSize)
        if (quietZoneModules < 0) throw NBUQRImageException.InvalidQuietZone(quietZoneModules)
        val contrast = 1 - accentColor.relativeLuminance
        // Negated so that a NaN component (contrast NaN) is rejected too instead of slipping through.
        if (!(contrast >= MINIMUM_ACCENT_CONTRAST)) {
            throw NBUQRImageException.InsufficientAccentContrast(contrast, MINIMUM_ACCENT_CONTRAST)
        }
        val matrix = selectMatrix(payload.qrUrl())
        // Swift traps on this overflow; here it would silently wrap to a negative side.
        val sidePixels = (matrix.size + 2L * quietZoneModules) * moduleSize
        if (sidePixels > Int.MAX_VALUE) {
            throw NBUQRImageException.RenderingFailed("A ${sidePixels}×$sidePixels pixel image exceeds the platform limit")
        }
        return NBUQRImageLayout(matrix, moduleSize, quietZoneModules, accentColor)
    }

    // region Error correction level selection

    private fun selectMatrix(url: String): QRMatrix = when (val correction = errorCorrection) {
        is NBUQRErrorCorrection.Fixed -> checked(matrix(url, correction.level), url, correction.level)
        NBUQRErrorCorrection.Automatic -> {
            val quartile = matrix(url, NBUQRErrorCorrectionLevel.QUARTILE)
            when {
                quartile.version in ALLOWED_VERSIONS -> quartile
                // A lower level always yields a smaller or equal version, so falling back to "M" only
                // rescues from exceeding the ceiling. If even "Q" did not reach the minimum version 10,
                // "M" will not either — there is simply too little data.
                quartile.version < ALLOWED_VERSIONS.first -> checked(quartile, url, NBUQRErrorCorrectionLevel.QUARTILE)
                else -> checked(matrix(url, NBUQRErrorCorrectionLevel.MEDIUM), url, NBUQRErrorCorrectionLevel.MEDIUM)
            }
        }
    }

    private fun checked(matrix: QRMatrix, url: String, level: NBUQRErrorCorrectionLevel): QRMatrix {
        if (matrix.version < ALLOWED_VERSIONS.first) {
            throw NBUQRImageException.DataTooShort(
                version = matrix.version,
                minimumVersion = ALLOWED_VERSIONS.first,
                additionalDataBytes = dataBytes(urlCharacters = shortfall(url, level)),
                level = level,
            )
        }
        if (matrix.version > ALLOWED_VERSIONS.last) {
            throw NBUQRImageException.DataTooLong(
                version = matrix.version,
                maximumVersion = ALLOWED_VERSIONS.last,
                excessDataBytes = dataBytes(urlCharacters = excess(url, level)),
                level = level,
            )
        }
        return matrix
    }

    private fun matrix(url: String, level: NBUQRErrorCorrectionLevel): QRMatrix =
        QREncoder.encode(url.encodeToByteArray(), level)
            ?: throw NBUQRImageException.RenderingFailed("${url.length} bytes do not fit any QR version at level ${level.code}")

    // endregion

    // region How far off the data is

    // The bounds are not hard-coded but found by trial encoding, as in the Swift version, where
    // CIQRCodeGenerator owns the version-to-capacity mapping. Probes run only on the error path.

    /** Скільки символів бракує посиланню, щоб дотягти до мінімальної версії. */
    private fun shortfall(url: String, level: NBUQRErrorCorrectionLevel): Int = search { padding ->
        // Pad the real link: a mixed-case string is encoded in byte mode, and an appended character
        // does not change the mode.
        val version = QREncoder.minimalVersion(url.encodeToByteArray().size + padding, level)
        version != null && version >= ALLOWED_VERSIONS.first
    }

    /** Скільки символів зайві відносно максимальної версії. */
    private fun excess(url: String, level: NBUQRErrorCorrectionLevel): Int = search { trim ->
        if (trim >= url.length) return@search false
        val version = QREncoder.minimalVersion(url.dropLast(trim).encodeToByteArray().size, level)
        version != null && version <= ALLOWED_VERSIONS.last
    }

    /** Найменше `n` в межах `1..PROBE_LIMIT`, для якого `fits` істинна. */
    private inline fun search(fits: (Int) -> Boolean): Int {
        var low = 1
        var high = PROBE_LIMIT
        var answer = PROBE_LIMIT
        while (low <= high) {
            val mid = (low + high) / 2
            if (fits(mid)) {
                answer = mid
                high = mid - 1
            } else {
                low = mid + 1
            }
        }
        return answer
    }

    /** Base64URL кодує 3 байти даних у 4 символи посилання. */
    private fun dataBytes(urlCharacters: Int): Int = (urlCharacters * 3 + 3) / 4

    // endregion

    internal companion object {
        /** Додаток 1, п. 5 (мінімум) і додаток 4, п. 9 (максимум для формату 003). */
        val ALLOWED_VERSIONS: IntRange = 10..17

        /**
         * Найменший контраст `accentColor` з білим тлом, `1 − яскравість`. Центри
         * шукових візерунків — частина розмітки, за якою сканер знаходить код, тож
         * світлий акцент зламав би пошук. 0,7 — межа класу "A" за методикою
         * ISO/IEC 15415, якою ДСТУ ISO/IEC 18004 оцінює якість символу.
         */
        const val MINIMUM_ACCENT_CONTRAST: Double = 0.7

        private const val PROBE_LIMIT = 512
    }
}

/** Растеризує розкладку платформними засобами й кодує в PNG. */
internal expect fun encodePng(layout: NBUQRImageLayout): ByteArray

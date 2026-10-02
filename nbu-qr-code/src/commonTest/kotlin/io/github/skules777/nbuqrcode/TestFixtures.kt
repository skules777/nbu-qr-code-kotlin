package io.github.skules777.nbuqrcode

internal fun validPayment(): NBUQRPayment = NBUQRPayment(
    function = NBUQRPaymentFunction.ICT,
    recipient = "ТОВ \"Тестова компанія\"",
    iban = "UA511234567890123456789012345",
    amount = "100.50".toNBUQRDecimal(),
    recipientCode = "12345678",
    category = "MP2B/GSCB",
    reference = "REF-123",
    purpose = "Тестовий платіж",
)

/**
 * Додаток 4, приклад 4 (оплата товару в інтернет-магазині, таблиця 9) — офіційний
 * приклад із Постанови №97, а не вигадані дані.
 */
internal fun officialOnlineStorePayment(): NBUQRPayment = NBUQRPayment(
    function = NBUQRPaymentFunction.ICT,
    encoding = NBUQRTextEncoding.WINDOWS_1251,
    recipient = "ТОВ «ФК „ЕВО“»",
    iban = "UA673005280000026500504354077",
    amount = 150.toNBUQRDecimal(),
    recipientCode = "37193071",
    category = "OTHR/GDDS",
    reference = "1225102576",
    purpose = "?MerchantBusinessName=\"ROZETKA.UA\", Покупка товарів, замовлення №821558965.",
    displayText = "?<InstrForCdtrAgt><InstrInf>MerchID:01234-TermId:43210</InstrInf></InstrForCdtrAgt>",
    editableFields = emptySet(),
)

internal fun qrUrl(payment: NBUQRPayment): String = NBUQRPayload(payment).qrUrl()

internal fun parsedQR(payment: NBUQRPayment): NBUQRParsedData = NBUQRDecoder.parse(qrUrl(payment))

/**
 * Найбільший варіант `base` з призначенням "ттт…", для якого ще будується
 * `qrUrl()`; зупиняється раніше, щойно посилання досягає `urlBytes`.
 */
internal fun largestQRPayment(base: NBUQRPayment, reachingUrlBytes: Int = Int.MAX_VALUE): NBUQRPayment {
    var best = base
    for (n in 1..420) {
        val probe = base.copy(purpose = "т".repeat(n))
        val url = runCatching { qrUrl(probe) }.getOrNull() ?: break
        best = probe
        if (url.encodeToByteArray().size >= reachingUrlBytes) break
    }
    return best
}

/** Основні поля прикладу 2 Постанови (P2P-переказ) — той самий платіж, що й у README. */
internal fun p2pPayment(): NBUQRPayment = NBUQRPayment(
    function = NBUQRPaymentFunction.ICT,
    recipient = "Петренко Роман Петрович",
    iban = "UA906543210000000260323012024",
    amount = 63.toNBUQRDecimal(),
    recipientCode = "40121425",
    category = "MP2P/MP2B",
    reference = "DR-5678-12",
    purpose = "За каву",
)

/** Замало даних навіть для версії 10. */
internal fun tinyPayment(): NBUQRPayment = NBUQRPayment(
    function = NBUQRPaymentFunction.ICT,
    recipient = "К",
    iban = "UA511234567890123456789012345",
    recipientCode = "1",
    category = "MP2P/MP2B",
    purpose = "Ч",
)

/** Понад стелю "Q", але в межах "M". */
internal fun bulkyPayment(): NBUQRPayment = largestQRPayment(officialOnlineStorePayment(), reachingUrlBytes = 450)

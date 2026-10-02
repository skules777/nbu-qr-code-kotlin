# NBUQRCode (Kotlin Multiplatform)

[![CI](https://github.com/skules777/nbu-qr-code-kotlin/actions/workflows/ci.yml/badge.svg)](https://github.com/skules777/nbu-qr-code-kotlin/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.skules777/nbu-qr-code.svg)](https://central.sonatype.com/namespace/io.github.skules777)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF.svg?logo=kotlin&logoColor=white)
![Platforms](https://img.shields.io/badge/platforms-Android%20%7C%20JVM%20%7C%20iOS-lightgrey.svg)

Платіжні QR-коди НБУ для оплати за IBAN (формат 003,
[Постанова №97](https://bank.gov.ua/admin_uploads/law/19082025_97.pdf)).
Платник сканує код у застосунку свого банку — і отримувач, IBAN, ЄДРПОУ, сума
та призначення платежу заповнюються самі. А кнопки «Оплатити через …»
відкривають той самий платіж одразу в застосунку конкретного банку.

## Встановлення

Вимоги: Kotlin 2.3+; Android 5.0+ (API 21), JVM 11+ або iOS (arm64, симулятор arm64).

```kotlin
dependencies {
    implementation("io.github.skules777:nbu-qr-code:1.0.0")
}
```

У KMP-проєкті — у `commonMain.dependencies { … }`.

Для Swift-проєктів є [nbu-qr-code-swift](https://github.com/skules777/nbu-qr-code-swift).

## Швидкий старт

<p align="center">
  <img src="docs/example-qr.png" width="200" alt="QR-код НБУ, згенерований бібліотекою">
</p>

Цей QR-код згенеровано ось цим кодом:

```kotlin
val payment = NBUQRPayment(
    recipient = "Петренко Роман Петрович",
    iban = "UA906543210000000260323012024",
    amount = BigDecimal("63"),
    recipientCode = "40121425",
    purpose = "За каву",
)

val payload = NBUQRPayload(payment)

val bitmap = NBUQRImageRenderer(accentColor = NBUQRColor.NBU_GREEN).bitmap(payload)
val monobank = payload.deepLinkIntent(NBUQRBank.MONOBANK)
```

Дані — з офіційного прикладу Постанови (P2P-переказ). `bitmap(payload)` — для
Android; на JVM — `bufferedImage(payload)`, на iOS — `uiImage(payload)`, на будь-якій
платформі — `pngBytes(payload)` (PNG).

Сума — `NBUQRDecimal`: `BigDecimal` на JVM і Android, `NSDecimalNumber` на iOS.
У спільному коді — `"63.50".toNBUQRDecimal()`.

## Як це працює

У QR-коді — посилання `https://qr.bank.gov.ua/…`. Що відбувається далі,
залежить від того, чим його відкрити:

- **Сканер у застосунку банку** — реквізити заповнюються автоматично. Саме так
  радить НБУ:

  > **ВАЖЛИВО!** Для швидкої оплати наступного разу скануйте QR-код із
  > зображенням знаку гривні відразу із платіжного застосунку.
  >
  > — [bank.gov.ua/ua/qr](https://bank.gov.ua/ua/qr)

- **Камера iPhone** відкриває застосунок банку, який обробляв такий QR-код
  останнім.
- **Перехід за посиланням** на iPhone відкриває один зі встановлених
  банківських застосунків — дати платнику вибір iOS не дозволяє.

Тому, щоб платник сам обрав банк, покажіть поруч із QR-кодом кнопки
«Оплатити через …» — див. [Банки](#банки).

QR-код з кодом старту застосунку конкретного банку (напр. monobank) бібліотека свідомо не
генерує: його опрацює лише застосунок цього банку, сканер будь-якого іншого
його не прочитає. А знак ₴ на коді обіцяє платнику, що підійде будь-який
банк, — тож такий QR лише вводить в оману.

## Банки

QR-код сканують застосунки банків з
[офіційного переліку НБУ](https://bank.gov.ua/ua/qr).

Кнопки «Оплатити через …» (deep link) відкривають платіж одразу в застосунку
конкретного банку:

- **Протестовано:** Приват24, monobank (iOS).
- **Не протестовано:** Приват24 і monobank на Android, Privat24 for business,
  Абанк, ПУМБ, Райффайзен Банк, UKRSIBBANK, Укргазбанк, ОТП БАНК, Креді Агріколь
  Банк, РАДАБАНК, VST BANK, KredoBank, Sense Bank, izibank, Банк Кредит Дніпро,
  Глобус Банк.

Якщо на платформі прямого способу відкрити застосунок банку немає, посилання —
`null`.

```kotlin
// Android
val buttons = NBUQRBank.ALL.mapNotNull { bank ->
    payload.deepLinkIntent(bank)
        ?.takeIf { it.resolveActivity(packageManager) != null } // лише встановлені
        ?.let { bank.name to it }
}

// iOS (KMP) і бекенд
payload.iosDeepLink(NBUQRBank.MONOBANK)     // https://mbnk.app/qr/…
payload.androidDeepLink(NBUQRBank.MONOBANK) // intent://qr.bank.gov.ua/…;package=com.ftband.mono;end
```

`androidDeepLink` — Android Intent URI, який Chrome відкриває прямо з вебсторінки.

### Встановлені застосунки

- **Android 11+:** щоб `resolveActivity` бачив застосунки банків, оголосіть їхні
  пакети в `<queries>` маніфесту — готовий список є в
  [маніфесті демо-застосунку](sample/androidApp/src/main/AndroidManifest.xml).
- **iOS:** частина банків відкривається власною схемою (`abank24://`,
  `vostok://`…), а не https-адресою. Щоб приховати кнопки невстановлених
  застосунків через `canOpenURL`, додайте схеми банків у
  `LSApplicationQueriesSchemes` свого Info.plist.

### Додати інший банк

Банк, якого немає в списку, додається одним рядком:

```kotlin
val bank = NBUQRBank(
    name = "Example Bank",
    iosAppStartUrl = "https://bank.example/qr/",
    androidPackage = "com.example.bank",
)
payload.iosDeepLink(bank) // https://bank.example/qr/<payload>
```

## Налаштування

### Колір

```kotlin
NBUQRImageRenderer()                                            // чорний
NBUQRImageRenderer(accentColor = NBUQRColor.NBU_GREEN)          // зелений НБУ, як на bank.gov.ua
NBUQRImageRenderer(accentColor = NBUQRColor.fromHex("#1A237E")!!) // колір вашого бренду
```

Колір отримують знак ₴ і центри трьох кутових квадратів. Надто світлий колір
рендер відхилить, щоб код лишився читабельним.

### Категорія (поле 10)

```kotlin
category = "MP2P/MP2B" // код категорії / цілі за ISO 20022
category = ""          // за замовчуванням: у QR-код потрапляє OTHR/OTHR («інше / інше»)
```

Порожнім поле 10 бібліотека не лишає: постанова позначає його обовʼязковим, а
Приват24 код із порожнім полем відхиляє. Деталі — у
[docs/NBU-COMPLIANCE.md](docs/NBU-COMPLIANCE.md#кодування-даних).

### Заборона змінювати поля

Які поля платник може змінити у своєму застосунку перед оплатою:

```kotlin
editableFields = null                                                    // за замовчуванням: рішення за банком
editableFields = emptySet()                                              // змінити нічого не можна
editableFields = setOf(NBUQREditableField.AMOUNT)                        // лише суму
editableFields = setOf(NBUQREditableField.AMOUNT, NBUQREditableField.PURPOSE) // суму й призначення
```

### Валідація форми

```kotlin
val issues = payment.validate() // List<NBUQRValidationIssue>
// [NBUQRValidationIssue(field = IBAN, problem = InvalidChecksum)]
```

Кожна проблема прив'язана до поля, тож її легко підсвітити у формі. Порожній
список гарантує, що `NBUQRPayload(payment)` не кине помилку.

### Помилки

- `NBUQRException.InvalidPayment(issues)` — той самий список, що й `validate()`.
- `NBUQRException.QRDataTooLong` — дані не вміщуються в QR-код (deep link при цьому
  працює).
- `NBUQRImageException.DataTooShort` / `DataTooLong` — даних замало чи забагато
  для QR-коду за вимогами НБУ; помилка каже, на скільки байтів.

Тексти помилок (`message`) — англійською, для логів і розробників. Для
користувача покажіть власний текст за типом помилки.

## Приклад: екран оплати в Compose

<details>
<summary>QR-код і кнопки «Оплатити через …»</summary>

```kotlin
@Composable
fun PaymentQR(orderId: String, amount: BigDecimal) {
    val context = LocalContext.current
    val payment = remember(orderId, amount) {
        NBUQRPayment(
            function = NBUQRPaymentFunction.XCT,
            recipient = "ФОП Коваленко Ірина Олександрівна",
            iban = "UA906543210000000260323012024",
            amount = amount,
            recipientCode = "40121425",
            reference = orderId,
            purpose = "Оплата замовлення №$orderId",
            // Reference — єдиний ключ, що звʼязує переказ із замовленням,
            // тож усе інше блокуємо від редагування платником.
            editableFields = emptySet(),
        )
    }

    // Рендер — CPU-робота, тож не в головному потоці.
    val state by produceState<Result<Pair<NBUQRPayload, ImageBitmap>>?>(null, payment) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val payload = NBUQRPayload(payment)
                payload to NBUQRImageRenderer().bitmap(payload).asImageBitmap()
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        state?.onSuccess { (payload, image) ->
            Image(
                bitmap = image,
                contentDescription = "Платіжний QR-код",
                filterQuality = FilterQuality.None, // модулі лишаються чіткими
                modifier = Modifier.size(260.dp),
            )
            for (bank in listOf(NBUQRBank.PRIVAT_BANK, NBUQRBank.MONOBANK)) {
                val intent = payload.deepLinkIntent(bank) ?: continue
                Button(onClick = { context.startActivity(intent) }) {
                    Text("Оплатити через ${bank.name}")
                }
            }
        }?.onFailure {
            Text("Не вдалося згенерувати QR: ${it.message}", color = MaterialTheme.colorScheme.error)
        } ?: CircularProgressIndicator()
    }
}
```

</details>

## Демо-застосунок

[`sample/`](sample) — застосунок на Compose Multiplatform для Android та iOS: форма
платежу з валідацією, QR-код і кнопки «Оплатити через …» для встановлених
банківських застосунків.

## Бекенд

На JVM бібліотека працює без Android і без дисплея (headless):

```kotlin
val png: ByteArray = NBUQRImageRenderer().pngBytes(NBUQRPayload(payment))
```

З Java — так само: `new NBUQRPayload(payment)`, `NBUQRImages.bufferedImage(renderer, payload)`.

## Відповідність стандарту НБУ

Як бібліотека виконує вимоги Постанови №97 і що показала перевірка в
банківських застосунках — у [docs/NBU-COMPLIANCE.md](docs/NBU-COMPLIANCE.md).

## Ліцензія

[MIT](LICENSE). QR-кодувальник — на основі
[QR Code generator](https://www.nayuki.io/page/qr-code-generator-library)
Project Nayuki (MIT), див. [NOTICE](NOTICE).

# Module nbu-qr-code

Платіжні QR-коди НБУ формату 003: опис платежу, валідація, посилання в застосунки
банків і зображення QR-коду зі знаком ₴.

Опишіть платіж у `NBUQRPayment`, перевірте його через `NBUQRPayment.validate()` і закодуйте
в `NBUQRPayload`. З одного закодованого вмісту беруться посилання для QR-коду
(`NBUQRPayload.qrUrl()`), посилання в застосунки банків і зображення
(`NBUQRImageRenderer`): PNG на будь-якій платформі, `Bitmap` на Android,
`BufferedImage` на JVM, `UIImage` на iOS.

# Package io.github.skules777.nbuqrcode

Платіж, валідація, кодування, посилання й рендер QR-коду.

package io.github.skules777.nbuqrcode

/**
 * Помилки формування платежу. `message` — англійською, для логів і
 * розробника; користувачу показуйте власний текст за типом помилки (для
 * [InvalidPayment] — за `field` і `problem` кожної проблеми).
 */
public sealed class NBUQRException(message: String) : Exception(message) {
    /** Платіж не пройшов [NBUQRPayment.validate]. */
    public class InvalidPayment(
        public val issues: List<NBUQRValidationIssue>,
    ) : NBUQRException("Invalid payment: " + issues.joinToString(", "))

    /**
     * Base64URL-вміст довший, ніж дозволяють ліміти обсягу даних графічного
     * QR-коду (таблиця 1 і п. 8 додатка 4). На посилання в застосунок банку не впливає.
     */
    public class QRDataTooLong(
        public val actual: Int,
        public val max: Int,
    ) : NBUQRException("Encoded payment data ($actual bytes) exceeds the QR code maximum of $max bytes")
}

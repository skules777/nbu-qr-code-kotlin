package io.github.skules777.nbuqrcode.sample

import io.github.skules777.nbuqrcode.NBUQRField
import io.github.skules777.nbuqrcode.NBUQRPayload
import io.github.skules777.nbuqrcode.NBUQRPayment
import io.github.skules777.nbuqrcode.NBUQRValidationIssue
import io.github.skules777.nbuqrcode.toNBUQRDecimalOrNull

/** Те, що користувач ввів у форму, — сирі рядки, ще без перевірки. */
data class PaymentForm(
    val recipient: String,
    val iban: String,
    val amount: String,
    val recipientCode: String,
    val purpose: String,
) {
    companion object {
        /** Офіційний приклад P2P-переказу з Постанови №97, додаток 4. */
        val EXAMPLE = PaymentForm(
            recipient = "Петренко Роман Петрович",
            iban = "UA906543210000000260323012024",
            amount = "63",
            recipientCode = "40121425",
            purpose = "За каву",
        )
    }
}

/** Чому поле форми не приймається. */
sealed interface FieldError {
    /** Проблема, яку знайшла бібліотека ([io.github.skules777.nbuqrcode.NBUQRPayment.validate]). */
    data class Invalid(val problem: NBUQRValidationIssue.Problem) : FieldError

    /** Рядок суми не є числом — до бібліотеки він навіть не доходить. */
    data object NotANumber : FieldError
}

/** Результат перевірки форми: помилки за полями або готовий до показу платіж. */
data class PaymentValidation(
    val errors: Map<NBUQRField, FieldError>,
    val payload: NBUQRPayload?,
) {
    companion object {
        fun of(form: PaymentForm): PaymentValidation {
            // Users type a comma as the decimal separator; the library expects a dot.
            val amountText = form.amount.trim().replace(',', '.')
            val amount = amountText.toNBUQRDecimalOrNull()
            val amountError = if (amountText.isNotEmpty() && amount == null) FieldError.NotANumber else null

            val payment = NBUQRPayment(
                recipient = form.recipient,
                iban = form.iban,
                amount = amount,
                recipientCode = form.recipientCode,
                purpose = form.purpose,
            )
            val issues = payment.validate()
            // The first problem per field is enough to highlight it in the form.
            val errors = issues
                .groupBy({ it.field }, { FieldError.Invalid(it.problem) })
                .mapValues { (_, problems) -> problems.first() as FieldError }
                .let { if (amountError != null) it + (NBUQRField.AMOUNT to amountError) else it }

            // An empty validate() list guarantees that NBUQRPayload succeeds.
            val payload = if (errors.isEmpty()) NBUQRPayload(payment) else null
            return PaymentValidation(errors, payload)
        }
    }
}

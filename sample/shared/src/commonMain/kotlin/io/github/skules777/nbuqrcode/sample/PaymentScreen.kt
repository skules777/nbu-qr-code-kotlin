package io.github.skules777.nbuqrcode.sample

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.skules777.nbuqrcode.NBUQRBank
import io.github.skules777.nbuqrcode.NBUQRException
import io.github.skules777.nbuqrcode.NBUQRField
import io.github.skules777.nbuqrcode.NBUQRImageException
import io.github.skules777.nbuqrcode.NBUQRValidationIssue
import io.github.skules777.nbuqrcode.NBUQRValidationIssue.Problem
import io.github.skules777.nbuqrcode.sample.resources.Res
import io.github.skules777.nbuqrcode.sample.resources.app_title
import io.github.skules777.nbuqrcode.sample.resources.bank_app_missing
import io.github.skules777.nbuqrcode.sample.resources.bank_button
import io.github.skules777.nbuqrcode.sample.resources.banks_none
import io.github.skules777.nbuqrcode.sample.resources.banks_title
import io.github.skules777.nbuqrcode.sample.resources.error_forbidden_characters
import io.github.skules777.nbuqrcode.sample.resources.error_invalid_checksum
import io.github.skules777.nbuqrcode.sample.resources.error_invalid_format
import io.github.skules777.nbuqrcode.sample.resources.error_missing
import io.github.skules777.nbuqrcode.sample.resources.error_not_a_number
import io.github.skules777.nbuqrcode.sample.resources.error_out_of_range
import io.github.skules777.nbuqrcode.sample.resources.error_too_long_bytes
import io.github.skules777.nbuqrcode.sample.resources.error_too_long_characters
import io.github.skules777.nbuqrcode.sample.resources.error_unsupported_characters
import io.github.skules777.nbuqrcode.sample.resources.field_amount
import io.github.skules777.nbuqrcode.sample.resources.field_iban
import io.github.skules777.nbuqrcode.sample.resources.field_purpose
import io.github.skules777.nbuqrcode.sample.resources.field_recipient
import io.github.skules777.nbuqrcode.sample.resources.field_recipient_code
import io.github.skules777.nbuqrcode.sample.resources.qr_content_description
import io.github.skules777.nbuqrcode.sample.resources.qr_error_generic
import io.github.skules777.nbuqrcode.sample.resources.qr_error_too_long
import io.github.skules777.nbuqrcode.sample.resources.qr_error_too_short
import io.github.skules777.nbuqrcode.sample.resources.qr_hint
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Підключає [PaymentScreen] до ViewModel і до платформного запуску банківських застосунків. */
@Composable
internal fun PaymentRoute(viewModel: PaymentViewModel = viewModel { PaymentViewModel() }) {
    val qr by viewModel.qr.collectAsStateWithLifecycle()
    val validation = viewModel.validation
    val launcher = rememberBankAppLauncher()
    // Which bank apps open does not depend on the payment's contents, so the list is rebuilt only
    // when the form turns valid — not on every keystroke (on Android each check is an IPC call).
    val hasPayload = validation.payload != null
    val banks = remember(hasPayload, launcher) {
        validation.payload?.let(launcher::availableBanks).orEmpty()
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    PaymentScreen(
        form = viewModel.form,
        errors = validation.errors,
        qr = qr,
        banks = banks.takeIf { hasPayload },
        snackbarHostState = snackbarHostState,
        onFormChange = viewModel::onFormChange,
        onBankClick = { bank ->
            val payload = validation.payload ?: return@PaymentScreen
            scope.launch {
                if (!launcher.open(bank, payload)) {
                    snackbarHostState.showSnackbar(getString(Res.string.bank_app_missing, bank.name))
                }
            }
        },
    )
}

/**
 * Екран без власного стану: усе приходить параметрами, події йдуть назовні.
 *
 * @param banks `null`, поки форма невалідна; порожній список — валідна, але жодного застосунку немає.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaymentScreen(
    form: PaymentForm,
    errors: Map<NBUQRField, FieldError>,
    qr: QrState,
    banks: List<NBUQRBank>?,
    snackbarHostState: SnackbarHostState,
    onFormChange: (PaymentForm) -> Unit,
    onBankClick: (NBUQRBank) -> Unit,
) {
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(Res.string.app_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PaymentField(Res.string.field_recipient, form.recipient, errors[NBUQRField.RECIPIENT]) {
                onFormChange(form.copy(recipient = it))
            }
            PaymentField(Res.string.field_iban, form.iban, errors[NBUQRField.IBAN], KeyboardType.Ascii) {
                onFormChange(form.copy(iban = it))
            }
            PaymentField(Res.string.field_amount, form.amount, errors[NBUQRField.AMOUNT], KeyboardType.Decimal) {
                onFormChange(form.copy(amount = it))
            }
            PaymentField(Res.string.field_recipient_code, form.recipientCode, errors[NBUQRField.RECIPIENT_CODE], KeyboardType.Number) {
                onFormChange(form.copy(recipientCode = it))
            }
            PaymentField(Res.string.field_purpose, form.purpose, errors[NBUQRField.PURPOSE], singleLine = false, imeAction = ImeAction.Done) {
                onFormChange(form.copy(purpose = it))
            }

            QrCard(qr)

            if (banks != null) {
                BankButtons(banks, onBankClick)
            }
        }
    }
}

@Composable
private fun PaymentField(
    label: StringResource,
    value: String,
    error: FieldError?,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = error?.let { { Text(errorMessage(it)) } },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Тексти бібліотеки — англійською, для логів; користувачу — власний текст за типом проблеми. */
@Composable
private fun errorMessage(error: FieldError): String = when (error) {
    FieldError.NotANumber -> stringResource(Res.string.error_not_a_number)
    is FieldError.Invalid -> when (val problem = error.problem) {
        Problem.Missing -> stringResource(Res.string.error_missing)
        is Problem.TooLong -> when (problem.unit) {
            NBUQRValidationIssue.LengthUnit.BYTES -> stringResource(Res.string.error_too_long_bytes, problem.max)
            NBUQRValidationIssue.LengthUnit.CHARACTERS -> stringResource(Res.string.error_too_long_characters, problem.max)
        }
        Problem.InvalidFormat -> stringResource(Res.string.error_invalid_format)
        Problem.InvalidChecksum -> stringResource(Res.string.error_invalid_checksum)
        Problem.OutOfRange -> stringResource(Res.string.error_out_of_range)
        Problem.UnsupportedCharacters -> stringResource(Res.string.error_unsupported_characters)
        Problem.ForbiddenCharacters -> stringResource(Res.string.error_forbidden_characters)
    }
}

@Composable
private fun QrCard(qr: QrState) {
    // A QR code needs a white background whatever the app theme is.
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = Color.Black),
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        Box(Modifier.size(QrSize).padding(8.dp), contentAlignment = Alignment.Center) {
            when (qr) {
                QrState.Rendering -> CircularProgressIndicator()
                QrState.Hidden -> Text(stringResource(Res.string.qr_hint), textAlign = TextAlign.Center)
                is QrState.Ready -> Image(
                    bitmap = qr.image,
                    contentDescription = stringResource(Res.string.qr_content_description),
                    // Modules must stay crisp when the bitmap is scaled to fit.
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.fillMaxSize(),
                )
                is QrState.Failed -> Text(
                    text = stringResource(qrErrorMessage(qr.error)),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun qrErrorMessage(error: Exception): StringResource = when (error) {
    is NBUQRImageException.DataTooShort -> Res.string.qr_error_too_short
    is NBUQRImageException.DataTooLong, is NBUQRException.QRDataTooLong -> Res.string.qr_error_too_long
    else -> Res.string.qr_error_generic
}

/** Deep link працює й там, де QR-код не побудувати, тож кнопки не залежать від [QrState]. */
@Composable
private fun BankButtons(banks: List<NBUQRBank>, onBankClick: (NBUQRBank) -> Unit) {
    Text(
        text = stringResource(Res.string.banks_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    if (banks.isEmpty()) {
        Text(stringResource(Res.string.banks_none), modifier = Modifier.fillMaxWidth())
    }
    for (bank in banks) {
        OutlinedButton(onClick = { onBankClick(bank) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.bank_button, bank.name))
        }
    }
}

private val QrSize = 280.dp

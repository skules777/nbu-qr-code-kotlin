package io.github.skules777.nbuqrcode.sample

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.skules777.nbuqrcode.NBUQRColor
import io.github.skules777.nbuqrcode.NBUQRException
import io.github.skules777.nbuqrcode.NBUQRImageException
import io.github.skules777.nbuqrcode.NBUQRImageRenderer
import io.github.skules777.nbuqrcode.NBUQRPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** Стан QR-коду на екрані. */
sealed interface QrState {
    /** Форма містить помилки — показувати нічого. */
    data object Hidden : QrState

    data object Rendering : QrState

    data class Ready(val image: ImageBitmap, val payload: NBUQRPayload) : QrState

    /** Платіж валідний, але QR-код для нього не побудувати; deep link при цьому працює. */
    data class Failed(val error: Exception, val payload: NBUQRPayload) : QrState
}

class PaymentViewModel : ViewModel() {
    private val renderer = NBUQRImageRenderer(accentColor = NBUQRColor.NBU_GREEN)

    // Text field input lives in snapshot state rather than a StateFlow: an asynchronous round trip
    // through a flow can drop or reorder keystrokes and move the cursor.
    var form: PaymentForm by mutableStateOf(PaymentForm.EXAMPLE)
        private set

    /** Перевірка дешева, тож іде синхронно з введенням — помилки видно одразу. */
    val validation: PaymentValidation by derivedStateOf { PaymentValidation.of(form) }

    /** Рендер — помітна CPU-робота, тож він іде поза головним потоком і лише коли введення вщухло. */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val qr: StateFlow<QrState> = snapshotFlow { validation.payload }
        .distinctUntilChanged()
        .debounce(RENDER_DEBOUNCE_MILLIS)
        .mapLatest { payload -> if (payload == null) QrState.Hidden else render(payload) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QrState.Rendering)

    fun onFormChange(form: PaymentForm) {
        this.form = form
    }

    private suspend fun render(payload: NBUQRPayload): QrState = withContext(Dispatchers.Default) {
        try {
            QrState.Ready(renderer.imageBitmap(payload), payload)
        } catch (e: NBUQRException) {
            QrState.Failed(e, payload)
        } catch (e: NBUQRImageException) {
            QrState.Failed(e, payload)
        }
    }

    private companion object {
        const val RENDER_DEBOUNCE_MILLIS = 300L
    }
}

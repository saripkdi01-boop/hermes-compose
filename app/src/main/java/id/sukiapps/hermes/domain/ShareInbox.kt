package id.sukiapps.hermes.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v19: kotak masuk teks dari share sheet aplikasi lain.
 *
 * MainActivity menerima intent ACTION_SEND (text/plain) lalu menaruh
 * teksnya di sini; HermesChatScreen mengonsumsinya sekali (chat baru +
 * teks terisi di kotak input, TIDAK otomatis terkirim — user meninjau
 * dulu, sama seperti perilaku "Ambil dari web"). StateFlow sederhana
 * cukup: satu produsen (MainActivity), satu konsumen (layar chat),
 * dan nilai tertahan sampai layar chat tampil.
 */
object ShareInbox {
    private val _text = MutableStateFlow<String?>(null)
    val text: StateFlow<String?> = _text.asStateFlow()

    fun post(value: String) {
        _text.value = value
    }

    fun clear() {
        _text.value = null
    }
}

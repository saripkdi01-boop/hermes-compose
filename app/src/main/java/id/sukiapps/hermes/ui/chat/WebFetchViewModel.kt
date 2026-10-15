package id.sukiapps.hermes.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sukiapps.hermes.data.audit.AuditLog
import id.sukiapps.hermes.data.connectors.WebFetchConnector
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Ambil dari web" — aksi MANUAL user (bukan browsing otomatis agen).
 * PermissionGate: LOW — dicatat di audit diam-diam tanpa popup, karena
 * user yang eksplisit menekan tombolnya.
 */
@HiltViewModel
class WebFetchViewModel @Inject constructor(
    private val web: WebFetchConnector,
    private val audit: AuditLog
) : ViewModel() {
    var urlText by mutableStateOf("")
        private set
    var loading by mutableStateOf(false)
        private set
    var result by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun setUrl(v: String) {
        urlText = v
    }

    fun reset() {
        urlText = ""
        result = null
        error = null
    }

    fun fetch() {
        val url = urlText.trim()
        if (url.isEmpty()) {
            error = "Isi URL dulu."
            return
        }
        viewModelScope.launch {
            loading = true
            result = null
            error = null
            val r = web.fetch(url)
            loading = false
            r.fold(
                onSuccess = { text ->
                    result = text
                    // Catat diam-diam (LOW): user yang inisiasi, tanpa popup.
                    viewModelScope.launch {
                        audit.log("Web", "Ambil: $url", granted = true, via = "auto")
                    }
                },
                onFailure = { error = (it.message ?: "Gagal.").take(200) }
            )
        }
    }
}

package id.sukiapps.hermes.ui.keysetup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import id.sukiapps.hermes.KeyVault
import id.sukiapps.hermes.data.local.AppPrefs
import id.sukiapps.hermes.domain.AIProvider
import id.sukiapps.hermes.domain.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class KeySetupUiState(
    val apiKey: String = "",
    val baseUrl: String = Prefs.DEFAULT_BASE,
    val models: List<String> = emptyList(),
    val model: String = "",
    val testing: Boolean = false,
    val testMessage: String? = null,
    val testOk: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val keystoreBroken: Boolean = false
)

@HiltViewModel
class KeySetupViewModel @Inject constructor(
    @ApplicationContext private val appCtx: Context,
    private val prefs: AppPrefs,
    private val ai: AIProvider
) : ViewModel() {

    private val _state = MutableStateFlow(
        KeySetupUiState(baseUrl = prefs.baseUrl, keystoreBroken = KeyVault.broken)
    )
    val state: StateFlow<KeySetupUiState> = _state.asStateFlow()

    fun setApiKey(v: String) = _state.update { it.copy(apiKey = v, testOk = false) }
    fun setBaseUrl(v: String) = _state.update { it.copy(baseUrl = v, testOk = false) }
    fun setModel(v: String) = _state.update { it.copy(model = v) }

    /** Test koneksi: simpan sementara di memori, panggil /models. */
    fun testConnection() {
        val key = _state.value.apiKey.trim()
        val base = _state.value.baseUrl.trim().trimEnd('/').ifBlank { Prefs.DEFAULT_BASE }
        if (key.isEmpty()) {
            _state.update { it.copy(error = "API key wajib diisi.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(testing = true, testMessage = null, error = null) }
            try {
                // Simpan key sementara agar AuthInterceptor bisa membacanya.
                withContext(Dispatchers.IO) {
                    KeyVault.putApiKey(appCtx, key)
                }
                prefs.baseUrl = base
                val ids = withContext(Dispatchers.IO) { ai.models() }
                _state.update {
                    it.copy(
                        testing = false,
                        testOk = true,
                        testMessage = "Terhubung. Ditemukan ${ids.size} model.",
                        models = ids,
                        model = it.model.ifBlank { ids.firstOrNull() ?: "" },
                        keystoreBroken = KeyVault.broken
                    )
                }
            } catch (e: Exception) {
                val broken = KeyVault.broken
                if (broken) {
                    // Fallback: simpan plaintext dengan peringatan (perilaku legacy v7.0).
                    withContext(Dispatchers.IO) {
                        KeyVault.putApiKeyPlain(appCtx, key)
                    }
                }
                _state.update {
                    it.copy(
                        testing = false,
                        testOk = false,
                        testMessage = (e.message ?: "Gagal terhubung.").take(200),
                        keystoreBroken = broken
                    )
                }
            }
        }
    }

    /** Simpan permanen lalu lanjut. */
    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (s.apiKey.trim().isEmpty()) {
            _state.update { it.copy(error = "API key wajib diisi.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            withContext(Dispatchers.IO) {
                try {
                    KeyVault.putApiKey(appCtx, s.apiKey.trim())
                } catch (e: Exception) {
                    KeyVault.putApiKeyPlain(appCtx, s.apiKey.trim())
                }
                prefs.baseUrl = s.baseUrl.trim().trimEnd('/').ifBlank { Prefs.DEFAULT_BASE }
                if (s.model.isNotBlank()) prefs.model = s.model
            }
            _state.update { it.copy(saving = false) }
            onDone()
        }
    }
}

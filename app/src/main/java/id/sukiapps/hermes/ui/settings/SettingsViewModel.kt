package id.sukiapps.hermes.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import id.sukiapps.hermes.KeyVault
import id.sukiapps.hermes.data.local.AppPrefs
import id.sukiapps.hermes.domain.AIProvider
import id.sukiapps.hermes.domain.PersonaPreset
import id.sukiapps.hermes.util.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val model: String = "",
    val models: List<String> = emptyList(),
    val modelsLoading: Boolean = false,
    val temperature: Float = 1.0f,
    val topP: Float = 1.0f,
    val maxTokens: Int = 0,
    val persona: String = "",
    val presets: List<PersonaPreset> = emptyList(),
    val darkTheme: Boolean = true,
    val baseUrl: String = "",
    val busyMessage: String? = null,
    val snack: String? = null,
    val keyBroken: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appCtx: Context,
    private val prefs: AppPrefs,
    private val ai: AIProvider,
    private val backup: BackupManager
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        _state.update {
            it.copy(
                model = prefs.model,
                models = prefs.cachedModels() ?: emptyList(),
                temperature = prefs.temperature,
                topP = prefs.topP,
                maxTokens = prefs.maxTokens,
                persona = prefs.persona,
                presets = prefs.personaPresets(),
                darkTheme = prefs.themeDark,
                baseUrl = prefs.baseUrl,
                keyBroken = KeyVault.broken
            )
        }
    }

    fun consumeSnack() = _state.update { it.copy(snack = null) }

    fun refreshModels() {
        viewModelScope.launch {
            _state.update { it.copy(modelsLoading = true, busyMessage = "Mengambil daftar model…") }
            try {
                val ids = withContext(Dispatchers.IO) { ai.models() }
                _state.update {
                    it.copy(
                        models = ids,
                        modelsLoading = false,
                        busyMessage = null,
                        snack = "Ditemukan ${ids.size} model."
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        modelsLoading = false,
                        busyMessage = null,
                        snack = (e.message ?: "Gagal mengambil model.").take(200)
                    )
                }
            }
        }
    }

    fun setModel(m: String) {
        prefs.model = m
        _state.update { it.copy(model = m) }
    }

    fun setTemperature(v: Float) {
        val c = v.coerceIn(0f, 2f)
        prefs.temperature = c
        _state.update { it.copy(temperature = c) }
    }

    fun setTopP(v: Float) {
        val c = v.coerceIn(0f, 1f)
        prefs.topP = c
        _state.update { it.copy(topP = c) }
    }

    fun setMaxTokens(v: Int) {
        val c = v.coerceIn(0, 8192)
        prefs.maxTokens = c
        _state.update { it.copy(maxTokens = c) }
    }

    fun setPersona(text: String) {
        prefs.persona = text
        _state.update { it.copy(persona = text) }
    }

    fun applyPreset(p: PersonaPreset) = setPersona(p.t)

    fun addPreset(name: String, text: String) {
        val n = name.trim()
        val t = text.trim()
        if (n.isEmpty() || t.isEmpty()) {
            _state.update { it.copy(snack = "Nama dan isi preset wajib diisi.") }
            return
        }
        val list = prefs.personaPresets().toMutableList()
        list.removeAll { it.n == n }
        list.add(PersonaPreset(n, t))
        prefs.savePersonaPresets(list)
        _state.update { it.copy(presets = list, snack = "Preset \"$n\" disimpan.") }
    }

    fun deletePreset(p: PersonaPreset) {
        val list = prefs.personaPresets().filter { it.n != p.n }
        prefs.savePersonaPresets(list)
        _state.update { it.copy(presets = list) }
    }

    fun setDarkTheme(dark: Boolean) {
        prefs.themeDark = dark
        _state.update { it.copy(darkTheme = dark) }
    }

    fun setBaseUrl(url: String) {
        val u = url.trim().trimEnd('/')
        prefs.baseUrl = u.ifBlank { id.sukiapps.hermes.domain.Prefs.DEFAULT_BASE }
        _state.update { it.copy(baseUrl = prefs.baseUrl, snack = "Base URL disimpan.") }
    }

    fun resetBaseUrl() = setBaseUrl(id.sukiapps.hermes.domain.Prefs.DEFAULT_BASE)

    fun clearKey(onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            KeyVault.clearKey(appCtx)
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun doBackup() {
        viewModelScope.launch {
            _state.update { it.copy(busyMessage = "Membuat backup…") }
            val r = withContext(Dispatchers.IO) { backup.backup() }
            _state.update {
                it.copy(busyMessage = null, snack = r.message)
            }
        }
    }

    fun listBackups(): List<File> = backup.listBackups()

    /** Restore dari file ZIP; kembalikan true bila aplikasi harus di-restart. */
    fun doRestore(file: File, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busyMessage = "Me-restore backup…") }
            val r = withContext(Dispatchers.IO) { backup.restore(file) }
            _state.update { it.copy(busyMessage = null, snack = r.message) }
            onDone(r.ok)
        }
    }
}

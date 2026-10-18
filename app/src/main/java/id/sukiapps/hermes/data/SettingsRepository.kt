package id.sukiapps.hermes.data

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Abstraksi penyimpanan secret (API key, token).
 * Implementasikan dengan KeyVault / EncryptedPrefs yang sudah ada di app.
 * Contoh adaptor ada di CATATAN-FASEB.md.
 */
interface SecretStore {
    fun getSecret(key: String): String?
    fun saveSecret(key: String, value: String)
    fun deleteSecret(key: String)
    fun hasSecret(key: String): Boolean = !getSecret(key).isNullOrEmpty()
}

// ---------- state ----------

data class ConnectionState(val baseUrl: String = "")

data class ModelState(
    val modelId: String = "",
    val availableModels: List<String> = emptyList(),
    val lastFetchMs: Long = 0L
)

data class Parameters(
    val temperature: Float = 0.7f,
    val topP: Float = 1.0f,
    val maxTokens: Int = 2048,
    val seed: Int = 0 // 0 = acak
)

data class PersonaPreset(
    val id: String,
    val name: String,
    val body: String,
    val createdAt: Long
)

data class PersonaState(
    val systemPrompt: String = "",
    val presets: List<PersonaPreset> = emptyList()
)

enum class ConnectorId(val key: String) {
    GITHUB("github"),
    TELEGRAM("telegram"),
    TERMUX("termux"),
    UPIN("upin"),
    SERVERLESS("serverless")
}

data class ConnectorState(
    val configured: Boolean = false,
    val lastTestOk: Boolean = false,
    val lastTestMs: Long = 0L,
    val lastTestMessage: String = ""
)

data class AppearanceState(
    /** null = ikut sistem */
    val darkMode: Boolean? = null,
    val contentStyle: String = "muse", // "muse" | "premium"
    val density: String = "compact", // "compact" | "comfortable"
    val accentKey: String = "coral", // "teal" | "sand" | "coral"
    val fontScale: Float = 1.0f
)

/** Kunci preferensi — satu tempat, tidak ada string tersebar. */
object PrefKey {
    const val BASE_URL = "base_url"
    const val MODEL_ID = "model_id"
    const val TEMPERATURE = "temperature"
    const val TOP_P = "top_p"
    const val MAX_TOKENS = "max_tokens"
    const val SEED = "seed"
    const val SYSTEM_PROMPT = "system_prompt"
    const val DARK_MODE = "dark_mode" // "system" | "dark" | "light"
    const val CONTENT_STYLE = "content_style"
    const val SETTINGS_DENSITY = "settings_density"
    const val ACCENT_KEY = "accent_key"
    const val FONT_SCALE = "font_scale"
    const val API_KEY_VERIFIED_AT = "api_key_verified_at"
    const val DIRTY_KEYS = "settings_dirty"
    // Secret (via SecretStore, BUKAN prefs biasa)
    const val SEC_API_KEY = "api_key"
    const val SEC_GH_PAT = "gh_pat"
    const val SEC_TG_BOT_TOKEN = "tg_bot_token"
    const val SEC_TG_CHAT_ID = "tg_chat_id"
    const val SEC_UPIN_SECRET = "upin_secret"
}

/**
 * Satu-satunya pintu untuk baca/tulis SEMUA pengaturan v14.
 * UI membaca StateFlow (auto-update), tulis via set() + commitAll().
 */
class SettingsRepository(
    private val prefs: SharedPreferences,
    private val secrets: SecretStore,
    private val modelsStore: JsonStore,
    private val personaStore: JsonStore,
    private val scope: CoroutineScope
) {

    private val _connection = MutableStateFlow(loadConnection())
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _model = MutableStateFlow(ModelState())
    val model: StateFlow<ModelState> = _model.asStateFlow()

    private val _parameters = MutableStateFlow(loadParameters())
    val parameters: StateFlow<Parameters> = _parameters.asStateFlow()

    private val _persona = MutableStateFlow(PersonaState())
    val persona: StateFlow<PersonaState> = _persona.asStateFlow()

    private val _connectors =
        MutableStateFlow(ConnectorId.values().associateWith { ConnectorState() })
    val connectors: StateFlow<Map<ConnectorId, ConnectorState>> =
        _connectors.asStateFlow()

    private val _appearance = MutableStateFlow(loadAppearance())
    val appearance: StateFlow<AppearanceState> = _appearance.asStateFlow()

    /** Jumlah perubahan belum di-commit (untuk UnsavedChangesDialog). */
    private val _dirtyCount = MutableStateFlow(loadDirty().size)
    val dirtyCount: StateFlow<Int> = _dirtyCount.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            _model.value = loadModelState()
            _persona.value = loadPersonaState()
            _connectors.value = loadConnectorStates()
        }
    }

    // ---------- baca awal ----------

    private fun loadConnection() = ConnectionState(
        baseUrl = prefs.getString(PrefKey.BASE_URL, "").orEmpty()
    )

    private fun loadParameters() = Parameters(
        temperature = prefs.getFloat(PrefKey.TEMPERATURE, 0.7f),
        topP = prefs.getFloat(PrefKey.TOP_P, 1.0f),
        maxTokens = prefs.getInt(PrefKey.MAX_TOKENS, 2048),
        seed = prefs.getInt(PrefKey.SEED, 0)
    )

    private fun loadAppearance(): AppearanceState {
        val darkRaw = prefs.getString(PrefKey.DARK_MODE, "system")
        return AppearanceState(
            darkMode = when (darkRaw) {
                "dark" -> true
                "light" -> false
                else -> null
            },
            contentStyle = prefs.getString(PrefKey.CONTENT_STYLE, "muse").orEmpty()
                .ifEmpty { "muse" },
            density = prefs.getString(PrefKey.SETTINGS_DENSITY, "compact").orEmpty()
                .ifEmpty { "compact" },
            accentKey = prefs.getString(PrefKey.ACCENT_KEY, "coral").orEmpty()
                .ifEmpty { "coral" },
            fontScale = prefs.getFloat(PrefKey.FONT_SCALE, 1.0f).coerceIn(0.85f, 1.30f)
        )
    }

    private suspend fun loadModelState(): ModelState {
        val cached = modelsStore.getRaw("models_cache")
        val (items, fetchedAt) = if (cached != null) {
            try {
                val o = JSONObject(cached)
                val arr = o.optJSONArray("items")
                val list = mutableListOf<String>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.optString("id")?.let { list.add(it) }
                    }
                }
                list to o.optLong("fetchedAt", 0L)
            } catch (_: Exception) {
                emptyList<String>() to 0L
            }
        } else {
            emptyList<String>() to 0L
        }
        return ModelState(
            modelId = prefs.getString(PrefKey.MODEL_ID, "").orEmpty(),
            availableModels = items,
            lastFetchMs = fetchedAt
        )
    }

    private suspend fun loadPersonaState(): PersonaState {
        val prompt = prefs.getString(PrefKey.SYSTEM_PROMPT, "").orEmpty()
        val raw = personaStore.getRaw("presets")
        val presets = mutableListOf<PersonaPreset>()
        if (raw != null) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    presets.add(
                        PersonaPreset(
                            id = o.optString("id"),
                            name = o.optString("name"),
                            body = o.optString("body"),
                            createdAt = o.optLong("createdAt", 0L)
                        )
                    )
                }
            } catch (_: Exception) {
                // preset korup sebagian → abaikan, jangan crash
            }
        }
        return PersonaState(systemPrompt = prompt, presets = presets)
    }

    private fun loadConnectorStates(): Map<ConnectorId, ConnectorState> {
        return ConnectorId.values().associateWith { id ->
            val secretKey = when (id) {
                ConnectorId.GITHUB -> PrefKey.SEC_GH_PAT
                ConnectorId.TELEGRAM -> PrefKey.SEC_TG_BOT_TOKEN
                ConnectorId.TERMUX -> null // tidak pakai secret
                ConnectorId.UPIN -> PrefKey.SEC_UPIN_SECRET
                ConnectorId.SERVERLESS -> null
            }
            val configured = if (secretKey != null) {
                secrets.hasSecret(secretKey)
            } else {
                when (id) {
                    ConnectorId.TERMUX ->
                        prefs.getString("termux_bin", "").orEmpty().isNotEmpty()
                    ConnectorId.SERVERLESS ->
                        prefs.getString("sv_dir", "").orEmpty().isNotEmpty()
                    else -> false
                }
            }
            ConnectorState(
                configured = configured,
                lastTestOk = prefs.getBoolean("${id.key}_test_ok", false),
                lastTestMs = prefs.getLong("${id.key}_test_at", 0L),
                lastTestMessage = prefs.getString("${id.key}_test_msg", "").orEmpty()
            )
        }
    }

    private fun loadDirty(): MutableSet<String> {
        val raw = prefs.getString(PrefKey.DIRTY_KEYS, "[]").orEmpty()
        return try {
            val arr = JSONArray(raw)
            MutableSetFrom(arr)
        } catch (_: Exception) {
            mutableSetOf()
        }
    }

    private fun MutableSetFrom(arr: JSONArray): MutableSet<String> {
        val s = mutableSetOf<String>()
        for (i in 0 until arr.length()) s.add(arr.optString(i))
        return s
    }

    private fun saveDirty(set: Set<String>) {
        val arr = JSONArray()
        set.forEach { arr.put(it) }
        prefs.edit().putString(PrefKey.DIRTY_KEYS, arr.toString()).apply()
        _dirtyCount.value = set.size
    }

    private fun markDirty(key: String) {
        val s = loadDirty().toMutableSet()
        s.add(key)
        saveDirty(s)
    }

    // ---------- tulis (masuk dirty, belum commit) ----------

    /** Set nilai — masuk daftar dirty, emit StateFlow langsung. */
    fun set(key: String, value: Any) {
        when (key) {
            PrefKey.BASE_URL -> {
                prefs.edit().putString(key, value as String).apply()
                _connection.value = _connection.value.copy(baseUrl = value)
            }
            PrefKey.MODEL_ID -> {
                prefs.edit().putString(key, value as String).apply()
                _model.value = _model.value.copy(modelId = value)
            }
            PrefKey.TEMPERATURE -> {
                val v = (value as Number).toFloat()
                prefs.edit().putFloat(key, v).apply()
                _parameters.value = _parameters.value.copy(temperature = v)
            }
            PrefKey.TOP_P -> {
                val v = (value as Number).toFloat()
                prefs.edit().putFloat(key, v).apply()
                _parameters.value = _parameters.value.copy(topP = v)
            }
            PrefKey.MAX_TOKENS -> {
                val v = (value as Number).toInt()
                prefs.edit().putInt(key, v).apply()
                _parameters.value = _parameters.value.copy(maxTokens = v)
            }
            PrefKey.SEED -> {
                val v = (value as Number).toInt()
                prefs.edit().putInt(key, v).apply()
                _parameters.value = _parameters.value.copy(seed = v)
            }
            PrefKey.SYSTEM_PROMPT -> {
                prefs.edit().putString(key, value as String).apply()
                _persona.value = _persona.value.copy(systemPrompt = value)
            }
            PrefKey.DARK_MODE -> {
                prefs.edit().putString(key, value as String).apply()
                _appearance.value = _appearance.value.copy(
                    darkMode = when (value) {
                        "dark" -> true
                        "light" -> false
                        else -> null
                    }
                )
            }
            PrefKey.CONTENT_STYLE,
            PrefKey.SETTINGS_DENSITY,
            PrefKey.ACCENT_KEY -> {
                prefs.edit().putString(key, value as String).apply()
                refreshAppearance()
            }
            PrefKey.FONT_SCALE -> {
                val v = (value as Number).toFloat().coerceIn(0.85f, 1.30f)
                prefs.edit().putFloat(key, v).apply()
                _appearance.value = _appearance.value.copy(fontScale = v)
            }
            else -> {
                // kunci konektor / lain: simpan generik sebagai string
                prefs.edit().putString(key, value.toString()).apply()
            }
        }
        markDirty(key)
    }

    private fun refreshAppearance() {
        _appearance.value = loadAppearance()
    }

    /** Simpan secret (API key, token) — langsung tulis, reset status verifikasi. */
    fun setSecret(key: String, value: String) {
        secrets.saveSecret(key, value)
        if (key == PrefKey.SEC_API_KEY) {
            prefs.edit().remove(PrefKey.API_KEY_VERIFIED_AT).apply()
        }
        markDirty(key)
        scope.launch(Dispatchers.IO) {
            _connectors.value = loadConnectorStates()
        }
    }

    fun deleteSecret(key: String) {
        secrets.deleteSecret(key)
        markDirty(key)
        scope.launch(Dispatchers.IO) {
            _connectors.value = loadConnectorStates()
        }
    }

    fun hasSecret(key: String): Boolean = secrets.hasSecret(key)

    /** Baca API key (untuk ChatRepository). "" bila belum diisi. */
    fun getApiKey(): String = secrets.getSecret(PrefKey.SEC_API_KEY).orEmpty()

    // ---------- commit / discard ----------

    /** Tandai semua dirty sebagai tersimpan (nilai sudah di prefs). */
    suspend fun commitAll(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            saveDirty(emptySet())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Buang perubahan dirty: kembalikan StateFlow dari prefs.
     * (prefs sudah ditulis saat set(); pola ini untuk UI yang menunda
     * tulis sampai Simpan — gunakan beginEdit() di UI bila perlu.)
     */
    suspend fun discardAll() = withContext(Dispatchers.IO) {
        saveDirty(emptySet())
        _connection.value = loadConnection()
        _parameters.value = loadParameters()
        _appearance.value = loadAppearance()
        _model.value = loadModelState()
        _persona.value = loadPersonaState()
        _connectors.value = loadConnectorStates()
    }

    // ---------- model cache ----------

    suspend fun saveModelsCache(items: List<Pair<String, String>>) {
        val o = JSONObject()
        o.put("fetchedAt", System.currentTimeMillis())
        val arr = JSONArray()
        items.forEach { (id, ownedBy) ->
            arr.put(JSONObject().put("id", id).put("owned_by", ownedBy))
        }
        o.put("items", arr)
        modelsStore.putRaw("models_cache", o.toString())
        _model.value = _model.value.copy(
            availableModels = items.map { it.first },
            lastFetchMs = System.currentTimeMillis()
        )
    }

    // ---------- persona presets ----------

    suspend fun savePresets(presets: List<PersonaPreset>): Result<Unit> {
        return try {
            val arr = JSONArray()
            presets.forEach { p ->
                arr.put(
                    JSONObject()
                        .put("id", p.id)
                        .put("name", p.name)
                        .put("body", p.body)
                        .put("createdAt", p.createdAt)
                )
            }
            personaStore.putRaw("presets", arr.toString())
            _persona.value = _persona.value.copy(presets = presets)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------- status tes konektor ----------

    fun saveConnectorTest(
        id: ConnectorId,
        ok: Boolean,
        message: String
    ) {
        prefs.edit()
            .putBoolean("${id.key}_test_ok", ok)
            .putLong("${id.key}_test_at", System.currentTimeMillis())
            .putString("${id.key}_test_msg", message)
            .apply()
        val cur = _connectors.value.toMutableMap()
        val old = cur[id] ?: ConnectorState()
        cur[id] = old.copy(
            lastTestOk = ok,
            lastTestMs = System.currentTimeMillis(),
            lastTestMessage = message
        )
        _connectors.value = cur
    }
}

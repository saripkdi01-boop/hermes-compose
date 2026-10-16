package id.sukiapps.hermes.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import id.sukiapps.hermes.domain.Persona
import id.sukiapps.hermes.domain.PersonaPreset
import id.sukiapps.hermes.domain.Prefs
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Akses SharedPreferences "hermes_chat" — key KONTRAK dari legacy.
 * Semua I/O prefs ringan (XML kecil); pemanggilan berat dibungkus
 * Dispatchers.IO oleh pemanggil bila perlu.
 */
@Singleton
class AppPrefs @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString(Prefs.KEY_BASE_URL, Prefs.DEFAULT_BASE)
            ?: Prefs.DEFAULT_BASE
        set(v) = prefs.edit().putString(Prefs.KEY_BASE_URL, v).apply()

    var model: String
        get() = prefs.getString(Prefs.KEY_MODEL, "") ?: ""
        set(v) = prefs.edit().putString(Prefs.KEY_MODEL, v).apply()

    var persona: String
        get() = prefs.getString(Prefs.KEY_PERSONA, "") ?: ""
        set(v) = prefs.edit().putString(Prefs.KEY_PERSONA, v).apply()

    var temperature: Float
        get() = try {
            prefs.getFloat(Prefs.KEY_TEMPERATURE, Prefs.DEFAULT_TEMPERATURE)
        } catch (e: Exception) { Prefs.DEFAULT_TEMPERATURE }
        set(v) = prefs.edit().putFloat(Prefs.KEY_TEMPERATURE, v).apply()

    var topP: Float
        get() = try {
            prefs.getFloat(Prefs.KEY_TOP_P, Prefs.DEFAULT_TOP_P)
        } catch (e: Exception) { Prefs.DEFAULT_TOP_P }
        set(v) = prefs.edit().putFloat(Prefs.KEY_TOP_P, v).apply()

    var maxTokens: Int
        get() = try { prefs.getInt(Prefs.KEY_MAX_TOKENS, 0) } catch (e: Exception) { 0 }
        set(v) = prefs.edit().putInt(Prefs.KEY_MAX_TOKENS, v).apply()

    var themeDark: Boolean
        get() = prefs.getString(Prefs.KEY_THEME, "dark") != "light"
        set(v) = prefs.edit().putString(Prefs.KEY_THEME, if (v) "dark" else "light").apply()

    var onboarded: Boolean
        get() = prefs.getBoolean(Prefs.KEY_ONBOARDED, false)
        set(v) = prefs.edit().putBoolean(Prefs.KEY_ONBOARDED, v).apply()

    /** v9.2: direktori project Telegram Serverless (prefs biasa, bukan secret). */
    var serverlessDir: String
        get() = prefs.getString(Prefs.KEY_SERVERLESS_DIR, "") ?: ""
        set(v) = prefs.edit().putString(Prefs.KEY_SERVERLESS_DIR, v).apply()

    /** v9.4: base URL konektor Upin (prefs biasa, bukan secret). */
    var upinUrl: String
        get() = prefs.getString(Prefs.KEY_UPIN_URL, "") ?: ""
        set(v) = prefs.edit().putString(Prefs.KEY_UPIN_URL, v).apply()

    var currentConvId: Long
        get() = prefs.getLong(Prefs.KEY_CURRENT_CONV, -1L)
        set(v) = prefs.edit().putLong(Prefs.KEY_CURRENT_CONV, v).apply()

    fun personaPresets(): List<PersonaPreset> =
        Persona.parse(prefs.getString(Prefs.KEY_PERSONA_PRESETS, ""))

    fun savePersonaPresets(list: List<PersonaPreset>) =
        prefs.edit().putString(Prefs.KEY_PERSONA_PRESETS, Persona.toJson(list)).apply()

    /** Cache daftar model 24 jam (perbaikan v7.1). */
    fun cachedModels(): List<String>? {
        val at = prefs.getLong(Prefs.KEY_MODELS_CACHE_AT, 0L)
        if (at <= 0L || System.currentTimeMillis() - at > Prefs.MODELS_CACHE_TTL_MS) return null
        val raw = prefs.getString(Prefs.KEY_MODELS_CACHE, "") ?: ""
        if (raw.isBlank()) return null
        return raw.split("\n").filter { it.isNotBlank() }
    }

    fun saveModelsCache(models: List<String>) {
        prefs.edit()
            .putString(Prefs.KEY_MODELS_CACHE, models.joinToString("\n"))
            .putLong(Prefs.KEY_MODELS_CACHE_AT, System.currentTimeMillis())
            .apply()
    }

    /** Semua prefs untuk backup — KECUALI api_key & api_key_enc_v1 (kontrak). */
    fun exportForBackup(): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for ((k, v) in prefs.all) {
            if (k == Prefs.KEY_API_PLAINTEXT || k == Prefs.KEY_API_ENC) continue
            out[k] = v
        }
        return out
    }

    /** Restore prefs dari backup (tidak menyentuh API key). */
    fun importFromBackup(map: Map<String, Any?>) {
        val e = prefs.edit()
        for ((k, v) in map) {
            if (k == Prefs.KEY_API_PLAINTEXT || k == Prefs.KEY_API_ENC) continue
            when (v) {
                is String -> e.putString(k, v)
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is Float -> e.putFloat(k, v)
                is Double -> e.putFloat(k, v.toFloat())
                else -> e.putString(k, v?.toString())
            }
        }
        e.apply()
    }
}

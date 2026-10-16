package id.sukiapps.hermes.domain

/**
 * Konstanta SharedPreferences — KONTRAK, jangan diubah.
 * File: "hermes_chat" (dipertahankan dari legacy Ui.PREFS).
 */
object Prefs {
    const val FILE = "hermes_chat"

    const val KEY_API_PLAINTEXT = "api_key"       // plaintext lama (migrasi)
    const val KEY_API_ENC = "api_key_enc_v1"      // ciphertext KeyVault
    const val KEY_BASE_URL = "base_url"
    const val KEY_MODEL = "model"
    const val KEY_PERSONA = "persona"
    const val KEY_CURRENT_CONV = "current_conv"
    const val KEY_TEMPERATURE = "temperature"     // float 0..2
    const val KEY_MAX_TOKENS = "max_tokens"       // int, 0 = unset
    const val KEY_TOP_P = "top_p"                // float 0..1
    const val KEY_THEME = "theme"                // "dark" | "light"
    const val KEY_ONBOARDED = "onboarded"         // boolean
    const val KEY_PERSONA_PRESETS = "persona_presets" // JSON [{"n":..,"t":..}]

    // Cache daftar model (internal, 24 jam — perbaikan v7.1).
    const val KEY_MODELS_CACHE = "models_cache"
    const val KEY_MODELS_CACHE_AT = "models_cache_at"

    // v9.2: direktori project Telegram Serverless di Termux (bukan secret).
    const val KEY_SERVERLESS_DIR = "serverless_dir"

    // v9.4: base URL konektor Upin (bukan secret; default loopback SSH forward).
    const val KEY_UPIN_URL = "upin_url"

    const val DEFAULT_BASE = "https://je.jerouter.web.id/v1"
    const val DEFAULT_TEMPERATURE = 1.0f
    const val DEFAULT_TOP_P = 1.0f
    const val MODELS_CACHE_TTL_MS = 24L * 60 * 60 * 1000
}

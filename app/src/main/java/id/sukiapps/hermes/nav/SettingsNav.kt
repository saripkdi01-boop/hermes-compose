package id.sukiapps.hermes.nav

/**
 * Route Pengaturan v14 — tiga tingkat: Hub → Detail Fitur → (panduan
 * sebagai expandable card, bukan route baru).
 *
 * Fungsi settingsGraph() menyusul di FASE F setelah layar-layar
 * settings jadi (tidak boleh stub di fase ini).
 */
object SettingsRoutes {
    const val HUB = "settings/hub"
    const val SETUP = "settings/setup"
    const val MODEL = "settings/model"
    const val PARAMETERS = "settings/parameters"
    const val PERSONA = "settings/persona"
    const val SOUL = "settings/soul"
    const val CONNECTION = "settings/connection"
    const val API_KEY = "settings/apikey"
    const val CONNECTOR_GITHUB = "settings/connector/github"
    const val CONNECTOR_TELEGRAM = "settings/connector/telegram"
    const val CONNECTOR_TERMUX = "settings/connector/termux"
    const val CONNECTOR_UPIN = "settings/connector/upin"
    const val CONNECTOR_SERVERLESS = "settings/connector/serverless"
    const val APPEARANCE = "settings/appearance"
    const val TTS = "settings/tts"
    const val BACKUP = "settings/backup"
    const val AUDIT = "settings/audit"

    val ALL = listOf(
        HUB, SETUP, MODEL, PARAMETERS, PERSONA, SOUL,
        CONNECTION, API_KEY, CONNECTOR_GITHUB, CONNECTOR_TELEGRAM,
        CONNECTOR_TERMUX, CONNECTOR_UPIN, CONNECTOR_SERVERLESS,
        APPEARANCE, TTS, BACKUP, AUDIT
    )

    /** Urutan setup logis untuk SetupChecklist (BAGIAN 6 masterprompt). */
    val SETUP_ORDER = listOf(
        CONNECTION, // 1. Base URL — tanpa ini semua gagal
        API_KEY, // 2. API key
        MODEL, // 3. Muat & pilih model
        PARAMETERS, // 4-5. Uji + parameter (digabung di UI)
        PERSONA, // 6. Persona
        CONNECTOR_GITHUB, // 7.
        CONNECTOR_TELEGRAM, // 8.
        CONNECTOR_UPIN, // 9.
        CONNECTOR_TERMUX, // 10.
        CONNECTOR_SERVERLESS, // 11.
        SOUL, // jiwa (setelah persona)
        APPEARANCE, // 12.
        TTS, // 13.
        BACKUP, // 14. sangat disarankan
        AUDIT // 15. read-only
    )

    /** 4 langkah wajib pertama — bobot 60% progress. */
    val REQUIRED = listOf(CONNECTION, API_KEY, MODEL, PARAMETERS)
}

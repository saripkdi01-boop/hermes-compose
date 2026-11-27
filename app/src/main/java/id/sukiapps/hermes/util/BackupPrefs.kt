package id.sukiapps.hermes.util

import id.sukiapps.hermes.domain.Prefs
import org.json.JSONArray
import org.json.JSONObject

/**
 * v19.1 (audit P0) — aturan prefs.json di backup ZIP, murni (tanpa Android)
 * supaya bisa di-unit-test. Format file TIDAK berubah (objek JSON datar
 * key → nilai); yang diperbaiki adalah TIPE saat restore dan daftar key
 * yang tidak boleh ikut backup.
 *
 * Masalah lama: JSON tidak membawa tipe. `current_conv` (Long) bernilai
 * kecil dibaca balik org.json sebagai Integer → di-restore dengan putInt
 * → `getLong("current_conv")` melempar ClassCastException di init
 * ChatViewModel → aplikasi crash tiap dibuka setelah restore. Hal sama
 * untuk Float bernilai bulat (temperature 1.0 → "1" → Integer) dan
 * StringSet (JSONArray → dulu jadi String).
 */
object BackupPrefs {

    /** Prefix rahasia konektor KeyVault.putSecret (token GitHub/Telegram, key cadangan). */
    const val SECRET_PREFIX = "secret_enc_"

    /** Key bertipe Long (kontrak + internal). */
    val LONG_KEYS = setOf(Prefs.KEY_CURRENT_CONV, Prefs.KEY_MODELS_CACHE_AT)

    /** Key bertipe Float. */
    val FLOAT_KEYS = setOf(Prefs.KEY_TEMPERATURE, Prefs.KEY_TOP_P, Prefs.KEY_TTS_SPEED)

    /** Key bertipe Int. */
    val INT_KEYS = setOf(Prefs.KEY_MAX_TOKENS)

    /**
     * true = key TIDAK boleh keluar/masuk lewat backup. Kontrak lama:
     * api_key + api_key_enc_v1. Ditambah v19.1: semua rahasia konektor
     * (`secret_enc_*`) — ciphertext-nya terikat AndroidKeyStore HP asal
     * (tak berguna di HP lain) dan me-restore-nya di HP yang sama bisa
     * menimpa token yang lebih baru dengan token lama.
     */
    fun isExcluded(key: String): Boolean =
        key == Prefs.KEY_API_PLAINTEXT ||
            key == Prefs.KEY_API_ENC ||
            key.startsWith(SECRET_PREFIX)

    /** Nilai SharedPreferences → nilai JSON (perilaku lama dipertahankan). */
    fun toJsonValue(v: Any?): Any = when (v) {
        null -> JSONObject.NULL
        is Boolean, is Int, is Long, is String -> v
        is Float -> v.toDouble()
        is Double -> v
        is Set<*> -> JSONArray(v.map { it?.toString() })
        else -> v.toString()
    }

    /**
     * Isi prefs.json → peta siap ditulis ke SharedPreferences, dengan tipe
     * yang benar. Urutan penentu tipe: skema key yang dikenal → tipe nilai
     * yang sekarang ada di HP ([existing]) → inferensi dari JSON.
     */
    fun decode(raw: String, existing: Map<String, Any?> = emptyMap()): Map<String, Any?> {
        val jo = JSONObject(raw)
        val out = LinkedHashMap<String, Any?>()
        val keys = jo.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (isExcluded(k)) continue
            out[k] = coerce(k, jo.get(k), existing[k])
        }
        return out
    }

    internal fun coerce(key: String, v: Any?, hint: Any?): Any? {
        if (v == null || v == JSONObject.NULL) return null
        if (v is JSONArray) {
            return (0 until v.length()).mapNotNull { i ->
                v.opt(i)?.takeIf { it != JSONObject.NULL }?.toString()
            }.toSet()
        }
        val num = v as? Number
        when {
            key in LONG_KEYS || hint is Long -> return num?.toLong() ?: v.toString().toLongOrNull()
            key in FLOAT_KEYS || hint is Float -> return num?.toFloat() ?: v.toString().toFloatOrNull()
            key in INT_KEYS || hint is Int -> return num?.toInt() ?: v.toString().toIntOrNull()
            hint is Boolean -> return v as? Boolean ?: v.toString().toBooleanStrictOrNull()
            hint is String -> return v.toString()
        }
        return when (v) {
            is Boolean, is Int, is Long, is String -> v
            // Perilaku lama: angka pecahan tak dikenal → Float.
            is Double -> v.toFloat()
            is Number -> v.toLong()
            else -> v.toString()
        }
    }
}

package id.sukiapps.hermes.ui.settings

import id.sukiapps.hermes.data.JsonStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistensi progres "Cara setup" per fitur (Fase G).
 * Format: { "<featureId>": ["step1", "step2"] } di satu file JSON.
 * Gagal baca/tulis TIDAK boleh crash — kembali kosong / abaikan.
 */
class GuidanceProgressStore(private val store: JsonStore) {

    suspend fun load(featureId: String): Set<String> = withContext(Dispatchers.IO) {
        try {
            val raw = store.getRaw(KEY) ?: return@withContext emptySet<String>()
            val arr = JSONObject(raw).optJSONArray(featureId)
                ?: return@withContext emptySet<String>()
            buildSet {
                for (i in 0 until arr.length()) {
                    val id = arr.optString(i)
                    if (id.isNotEmpty()) add(id)
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    suspend fun save(featureId: String, done: Set<String>) {
        withContext(Dispatchers.IO) {
            try {
                val raw = store.getRaw(KEY)
                val obj = if (raw != null) JSONObject(raw) else JSONObject()
                val arr = JSONArray()
                done.forEach { arr.put(it) }
                obj.put(featureId, arr)
                store.putRaw(KEY, obj.toString())
            } catch (_: Exception) {
                // persistensi progres tidak boleh crash app
            }
        }
    }

    companion object {
        private const val KEY = "guide_progress"
    }
}

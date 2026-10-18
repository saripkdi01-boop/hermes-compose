package id.sukiapps.hermes.net

import id.sukiapps.hermes.data.JsonStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Riwayat izin — BUKTI bahwa tidak ada konektor yang jalan otomatis.
 * Dipanggil dari dialog konfirmasi setiap user allow/deny aksi konektor.
 * Append-only, rotasi 500 entri. rawCommand Termux WAJIB tercatat.
 */
class AuditLog(private val store: JsonStore) {

    companion object {
        const val MAX_ENTRIES = 500
        private const val KEY = "entries"
    }

    data class Entry(
        val id: String = "a${System.currentTimeMillis()}",
        val ts: Long = System.currentTimeMillis(),
        val actor: String = "user", // "user" | "app"
        val action: String,
        val target: String,
        val rawCommand: String? = null,
        val decision: String, // "allow" | "deny"
        val result: String = "", // "ok" | "error" | ""
        val durationMs: Long = 0L
    )

    /** Tulis entri audit. */
    suspend fun record(entry: Entry) = withContext(Dispatchers.IO) {
        try {
            val raw = store.getRaw(KEY)
            val arr = try {
                if (raw != null) JSONArray(raw) else JSONArray()
            } catch (_: Exception) {
                JSONArray()
            }
            arr.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("ts", entry.ts)
                    .put("actor", entry.actor)
                    .put("action", entry.action)
                    .put("target", entry.target)
                    .put("rawCommand", entry.rawCommand)
                    .put("decision", entry.decision)
                    .put("result", entry.result)
                    .put("durationMs", entry.durationMs)
            )
            while (arr.length() > MAX_ENTRIES) arr.remove(0)
            store.putRaw(KEY, arr.toString())
        } catch (_: Exception) {
            // audit tidak boleh crash app
        }
    }

    /** Ambil entri dengan filter opsional. */
    suspend fun query(
        filter: String = "semua", // semua|konektor|ditolak|error
        keyword: String = "",
        limit: Int = 200
    ): List<JSONObject> = withContext(Dispatchers.IO) {
        val raw = store.getRaw(KEY) ?: return@withContext emptyList()
        try {
            val arr = JSONArray(raw)
            val out = mutableListOf<JSONObject>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val decision = o.optString("decision")
                val result = o.optString("result")
                val matchFilter = when (filter) {
                    "konektor" -> o.optString("target")
                        .startsWith("connector", ignoreCase = true)
                    "ditolak" -> decision == "deny"
                    "error" -> result == "error"
                    else -> true
                }
                if (!matchFilter) continue
                if (keyword.isNotBlank()) {
                    val hay = o.toString().lowercase()
                    if (!hay.contains(keyword.trim().lowercase())) continue
                }
                out.add(o)
                if (out.size >= limit) break
            }
            out.reversed()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        try {
            val raw = store.getRaw(KEY) ?: return@withContext 0
            JSONArray(raw).length()
        } catch (_: Exception) {
            0
        }
    }
}

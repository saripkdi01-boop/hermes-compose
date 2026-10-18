package id.sukiapps.hermes.net

import id.sukiapps.hermes.data.JsonStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pencatat aktivitas app — untuk layar Aktivitas & debugging user.
 * Append-only, rotasi 500 entri. Secret SELALU di-redact.
 */
class ActivityLogger(
    private val store: JsonStore,
    private val scope: CoroutineScope
) {

    sealed interface ActivityEvent {
        data class SettingsChanged(
            val key: String, val from: String, val to: String
        ) : ActivityEvent

        data class ModelsReloaded(val count: Int, val ms: Long) : ActivityEvent

        data class TestResult(
            val target: String, val ok: Boolean,
            val ms: Long, val reason: String?
        ) : ActivityEvent

        data class ConnectorExecuted(
            val id: String, val action: String,
            val decision: String, val ms: Long
        ) : ActivityEvent

        data class BackupCreated(val path: String, val sizeKb: Int) : ActivityEvent

        data class BackupRestored(val path: String, val ok: Boolean) : ActivityEvent

        data class RestoreFailed(val path: String, val error: String) : ActivityEvent

        data class ChatRequest(
            val model: String, val ms: Long,
            val tokens: Int?, val ok: Boolean
        ) : ActivityEvent
    }

    companion object {
        const val MAX_ENTRIES = 500
        private const val KEY = "events"
    }

    /** Catat event — fire and forget, tidak blokir UI. */
    fun log(event: ActivityEvent) {
        scope.launch(Dispatchers.IO) {
            try {
                val entry = JSONObject()
                    .put("ts", System.currentTimeMillis())
                    .put("type", event.javaClass.simpleName)
                    .put("data", eventToJson(event))
                append(entry)
            } catch (_: Exception) {
                // logging tidak boleh crash app
            }
        }
    }

    private fun eventToJson(event: ActivityEvent): JSONObject {
        val o = JSONObject()
        when (event) {
            is ActivityEvent.SettingsChanged -> {
                o.put("key", redact(event.key))
                o.put("from", redact(event.from))
                o.put("to", redact(event.to))
            }
            is ActivityEvent.ModelsReloaded -> {
                o.put("count", event.count)
                o.put("ms", event.ms)
            }
            is ActivityEvent.TestResult -> {
                o.put("target", redact(event.target))
                o.put("ok", event.ok)
                o.put("ms", event.ms)
                o.put("reason", event.reason?.let { redact(it) })
            }
            is ActivityEvent.ConnectorExecuted -> {
                o.put("id", redact(event.id))
                o.put("action", redact(event.action))
                o.put("decision", event.decision)
                o.put("ms", event.ms)
            }
            is ActivityEvent.BackupCreated -> {
                o.put("path", event.path)
                o.put("sizeKb", event.sizeKb)
            }
            is ActivityEvent.BackupRestored -> {
                o.put("path", event.path)
                o.put("ok", event.ok)
            }
            is ActivityEvent.RestoreFailed -> {
                o.put("path", event.path)
                o.put("error", redact(event.error))
            }
            is ActivityEvent.ChatRequest -> {
                o.put("model", redact(event.model))
                o.put("ms", event.ms)
                o.put("tokens", event.tokens)
                o.put("ok", event.ok)
            }
        }
        return o
    }

    private suspend fun append(entry: JSONObject) {
        val raw = store.getRaw(KEY)
        val arr = try {
            if (raw != null) JSONArray(raw) else JSONArray()
        } catch (_: Exception) {
            JSONArray()
        }
        arr.put(entry)
        // rotasi: buang yang terlama
        while (arr.length() > MAX_ENTRIES) arr.remove(0)
        store.putRaw(KEY, arr.toString())
    }

    /** Ambil N event terakhir (untuk layar Aktivitas). */
    suspend fun last(n: Int = 100): List<JSONObject> {
        val raw = store.getRaw(KEY) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<JSONObject>()
            val start = maxOf(0, arr.length() - n)
            for (i in start until arr.length()) {
                out.add(arr.getJSONObject(i))
            }
            out.reversed() // terbaru dulu
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Redact: ganti nilai setelah kata kunci sensitif dengan "***".
     * Berlaku untuk key/token/secret/password/authorization/bearer.
     */
    fun redact(text: String): String {
        var out = text
        val keys = listOf(
            "api_key", "apikey", "token", "secret", "password",
            "authorization", "bearer", "pat"
        )
        for (k in keys) {
            // pola "kunci": "nilai" atau kunci=nilai
            out = out.replace(
                Regex("(?i)($k[\"'\\s:=]+)([^\"',\\s}]{4,})"),
                "$1***"
            )
        }
        return out
    }
}

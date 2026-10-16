package id.sukiapps.hermes.data.store

import org.json.JSONArray
import org.json.JSONObject

/**
 * v12 F6 — Live Review: ring buffer event eksekusi (tanpa Room).
 *
 * Alur: pipeline eksekusi (prompt/llm, command, tool, file, approval)
 * memanggil ExecutionLog.log(...) di titik-titiknya → UI ActivityScreen
 * membaca via getRecentSync() dan menampilkan timeline live.
 *
 * Skema execution_log.json:
 * {"version":1,
 *  "events":[{"id":"","ts":0,"type":"prompt|command|tool|file|approval|error",
 *             "title":"","detail":"","status":"start|ok|fail"}]}
 *
 * Kapasitas: 200 event terakhir (ring buffer).
 * Semua tulis lewat JsonStore.mutate (thread io, atomik).
 */
object ExecutionLog {
    private const val STORE = "execution_log"
    const val MAX_EVENTS = 200

    data class Event(
        val id: String,
        val ts: Long,
        val type: String,
        val title: String,
        val detail: String,
        val status: String
    )

    private fun fromJson(o: JSONObject) = Event(
        id = o.optString("id"),
        ts = o.optLong("ts"),
        type = o.optString("type"),
        title = o.optString("title"),
        detail = o.optString("detail"),
        status = o.optString("status", "ok")
    )

    private fun toJson(e: Event) = JSONObject()
        .put("id", e.id)
        .put("ts", e.ts)
        .put("type", e.type)
        .put("title", e.title)
        .put("detail", e.detail)
        .put("status", e.status)

    /**
     * Catat event. type: prompt|command|tool|file|approval|error.
     * status: start|ok|fail.
     */
    fun log(
        type: String,
        title: String,
        detail: String = "",
        status: String = "ok",
        onDone: (() -> Unit)? = null
    ) {
        val now = System.currentTimeMillis()
        val id = "e${now}_${(0..9999).random()}"
        JsonStore.mutate(STORE, { json ->
            val arr = json.optJSONArray("events") ?: JSONArray().also { json.put("events", it) }
            arr.put(toJson(Event(id, now, type, title, detail, status)))
            // Ring buffer: buang yang tertua.
            while (arr.length() > MAX_EVENTS) arr.remove(0)
        }, { onDone?.invoke() })
    }

    /** Ambil event terbaru dulu (sinkron — panggil dari thread io). */
    fun getRecentSync(limit: Int = 100): List<Event> {
        val arr = JsonStore.load(STORE).optJSONArray("events") ?: return emptyList()
        val out = ArrayList<Event>(arr.length())
        for (i in 0 until arr.length()) out.add(fromJson(arr.getJSONObject(i)))
        return out.sortedByDescending { it.ts }.take(limit)
    }

    /** Bersihkan log (sinkron via mutate). */
    fun clear(onDone: (() -> Unit)? = null) {
        JsonStore.mutate(STORE, { json ->
            json.put("events", JSONArray())
        }, { onDone?.invoke() })
    }
}

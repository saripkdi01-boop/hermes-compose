package id.sukiapps.hermes.data.store

import org.json.JSONArray
import org.json.JSONObject

/**
 * v12 F2 — Approval Queue di atas JsonStore (tanpa Room).
 *
 * Skema approval_queue.json:
 * {"version":1,"trust":{"termux":0},
 *  "items":[{"id":"a1","type":"termux|telegram|github|file_jiwa|tool_create",
 *    "title":"","detail":"","payload":{},"status":"pending|allowed|denied",
 *    "created_at":0,"decided_at":0,"trust_until":0}]}
 *
 * Semua tulis lewat JsonStore.mutate (thread io, atomik).
 */
object ApprovalStore {
    private const val STORE = "approval_queue"
    const val TRUST_DURATION_MS = 3600_000L // "Izin 1 jam"

    data class ApprovalItem(
        val id: String,
        val type: String,
        val title: String,
        val detail: String,
        val payload: JSONObject,
        val status: String,
        val createdAt: Long,
        val decidedAt: Long,
        val trustUntil: Long
    )

    private fun fromJson(o: JSONObject) = ApprovalItem(
        id = o.optString("id"),
        type = o.optString("type"),
        title = o.optString("title"),
        detail = o.optString("detail"),
        payload = o.optJSONObject("payload") ?: JSONObject(),
        status = o.optString("status", "pending"),
        createdAt = o.optLong("created_at"),
        decidedAt = o.optLong("decided_at"),
        trustUntil = o.optLong("trust_until")
    )

    private fun toJson(it: ApprovalItem) = JSONObject()
        .put("id", it.id)
        .put("type", it.type)
        .put("title", it.title)
        .put("detail", it.detail)
        .put("payload", it.payload)
        .put("status", it.status)
        .put("created_at", it.createdAt)
        .put("decided_at", it.decidedAt)
        .put("trust_until", it.trustUntil)

    /** Ambil semua item pending (baca sinkron — panggil dari thread io). */
    fun getPendingSync(): List<ApprovalItem> {
        val json = JsonStore.load(STORE)
        val items = json.optJSONArray("items") ?: JSONArray()
        val out = mutableListOf<ApprovalItem>()
        for (i in 0 until items.length()) {
            val item = fromJson(items.getJSONObject(i))
            if (item.status == "pending") out.add(item)
        }
        return out.sortedBy { it.createdAt }
    }

    /** Cek trust: trust[type] > now → auto-allowed. */
    fun isTrusted(type: String): Boolean {
        val json = JsonStore.load(STORE)
        val trust = json.optJSONObject("trust") ?: return false
        return trust.optLong(type, 0) > System.currentTimeMillis()
    }

    /** Minta persetujuan baru. Callback di main thread dengan id. */
    fun request(
        type: String,
        title: String,
        detail: String,
        payload: JSONObject = JSONObject(),
        onDone: ((String) -> Unit)? = null
    ) {
        // Auto-allow bila masih dalam masa trust.
        if (isTrusted(type)) {
            val id = "a${System.currentTimeMillis()}"
            JsonStore.mutate(STORE, { json ->
                val items = json.optJSONArray("items") ?: JSONArray().also { json.put("items", it) }
                items.put(toJson(ApprovalItem(
                    id = id, type = type, title = title, detail = detail,
                    payload = payload, status = "allowed",
                    createdAt = System.currentTimeMillis(), decidedAt = System.currentTimeMillis(),
                    trustUntil = 0
                )))
            }, { onDone?.invoke(id) })
            return
        }
        val id = "a${System.currentTimeMillis()}"
        JsonStore.mutate(STORE, { json ->
            val items = json.optJSONArray("items") ?: JSONArray().also { json.put("items", it) }
            items.put(toJson(ApprovalItem(
                id = id, type = type, title = title, detail = detail,
                payload = payload, status = "pending",
                createdAt = System.currentTimeMillis(), decidedAt = 0, trustUntil = 0
            )))
        }, { onDone?.invoke(id) })
    }

    /** Putuskan: allowed/denied. trustOneHour=true → trust[type] = now+1 jam. */
    fun decide(id: String, allowed: Boolean, trustOneHour: Boolean = false, onDone: (() -> Unit)? = null) {
        JsonStore.mutate(STORE, { json ->
            val items = json.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val o = items.getJSONObject(i)
                if (o.optString("id") == id) {
                    o.put("status", if (allowed) "allowed" else "denied")
                    o.put("decided_at", System.currentTimeMillis())
                    if (trustOneHour && allowed) {
                        val trust = json.optJSONObject("trust") ?: JSONObject().also { json.put("trust", it) }
                        trust.put(o.optString("type"), System.currentTimeMillis() + TRUST_DURATION_MS)
                    }
                    break
                }
            }
        }, { onDone?.invoke() })
    }
}

package id.sukiapps.hermes.data.store

import org.json.JSONArray
import org.json.JSONObject

/**
 * v12 F1 — Tool Synthesis Engine di atas JsonStore (tanpa Room).
 *
 * Skema tools_catalog.json:
 * {"version":1,
 *  "items":[{"name":"","description":"","path":"","created_at":0,
 *            "sellable":false,"price_idr":0}],
 *  "drafts":[{"id":"","name":"","payload":"","status":"draft","created_at":0}]}
 */
object ToolStore {
    private const val STORE = "tools_catalog"

    data class ToolItem(
        val name: String,
        val description: String,
        val path: String,
        val createdAt: Long,
        val sellable: Boolean,
        val priceIdr: Long
    )

    data class ToolDraft(
        val id: String,
        val name: String,
        val payload: String,
        val status: String,
        val createdAt: Long
    )

    private fun toolFromJson(o: JSONObject) = ToolItem(
        name = o.optString("name"),
        description = o.optString("description"),
        path = o.optString("path"),
        createdAt = o.optLong("created_at"),
        sellable = o.optBoolean("sellable"),
        priceIdr = o.optLong("price_idr")
    )

    private fun toolToJson(t: ToolItem) = JSONObject()
        .put("name", t.name)
        .put("description", t.description)
        .put("path", t.path)
        .put("created_at", t.createdAt)
        .put("sellable", t.sellable)
        .put("price_idr", t.priceIdr)

    private fun draftFromJson(o: JSONObject) = ToolDraft(
        id = o.optString("id"),
        name = o.optString("name"),
        payload = o.optString("payload"),
        status = o.optString("status", "draft"),
        createdAt = o.optLong("created_at")
    )

    private fun draftToJson(d: ToolDraft) = JSONObject()
        .put("id", d.id)
        .put("name", d.name)
        .put("payload", d.payload)
        .put("status", d.status)
        .put("created_at", d.createdAt)

    /** Daftar tool published (sinkron — panggil dari thread io). */
    fun getToolsSync(): List<ToolItem> {
        val items = JsonStore.load(STORE).optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).map { toolFromJson(items.getJSONObject(it)) }
    }

    /** Daftar draft (sinkron). */
    fun getDraftsSync(): List<ToolDraft> {
        val arr = JsonStore.load(STORE).optJSONArray("drafts") ?: return emptyList()
        return (0 until arr.length()).map { draftFromJson(arr.getJSONObject(it)) }
    }

    /** Simpan draft baru. */
    fun saveDraft(name: String, payload: String, onDone: ((String) -> Unit)? = null) {
        val id = "d${System.currentTimeMillis()}"
        JsonStore.mutate(STORE, { json ->
            val arr = json.optJSONArray("drafts") ?: JSONArray().also { json.put("drafts", it) }
            // Pastikan items juga ada.
            if (json.optJSONArray("items") == null) json.put("items", JSONArray())
            arr.put(draftToJson(ToolDraft(id, name, payload, "draft", System.currentTimeMillis())))
        }, { onDone?.invoke(id) })
    }

    /** Publish draft menjadi tool (pindah drafts → items). */
    fun publishDraft(draftId: String, path: String, onDone: ((Boolean) -> Unit)? = null) {
        JsonStore.mutate(STORE, { json ->
            val drafts = json.optJSONArray("drafts") ?: return@mutate false
            val items = json.optJSONArray("items") ?: JSONArray().also { json.put("items", it) }
            for (i in 0 until drafts.length()) {
                val d = drafts.getJSONObject(i)
                if (d.optString("id") == draftId) {
                    val draft = draftFromJson(d)
                    items.put(toolToJson(ToolItem(
                        name = draft.name, description = "",
                        path = path, createdAt = System.currentTimeMillis(),
                        sellable = false, priceIdr = 0
                    )))
                    drafts.remove(i)
                    return@mutate true
                }
            }
            false
        }, { onDone?.invoke(it) })
    }

    /** Tandai tool sebagai sellable dengan harga. */
    fun setSellable(name: String, priceIdr: Long, onDone: (() -> Unit)? = null) {
        JsonStore.mutate(STORE, { json ->
            val items = json.optJSONArray("items") ?: return@mutate
            for (i in 0 until items.length()) {
                val o = items.getJSONObject(i)
                if (o.optString("name") == name) {
                    o.put("sellable", true)
                    o.put("price_idr", priceIdr)
                    break
                }
            }
        }, { onDone?.invoke() })
    }
}

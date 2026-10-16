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

    /** Publish draft menjadi tool: tulis payload ke filesDir/tools/<nama>.py (atomik),
     *  lalu pindah drafts → items. Gagal tulis = tidak publish. */
    fun publishDraft(draftId: String, onDone: ((Boolean) -> Unit)? = null) {
        JsonStore.mutate(STORE, { json ->
            val drafts = json.optJSONArray("drafts") ?: return@mutate false
            val idx = (0 until drafts.length())
                .firstOrNull { drafts.getJSONObject(it).optString("id") == draftId }
                ?: return@mutate false
            val draft = draftFromJson(drafts.getJSONObject(idx))
            if (draft.payload.isBlank()) return@mutate false

            // Tulis file tool dulu — gagal tulis berarti publish dibatalkan.
            val toolFile = writeToolFile(draft) ?: return@mutate false

            val items = json.optJSONArray("items") ?: JSONArray().also { json.put("items", it) }
            items.put(toolToJson(ToolItem(
                name = draft.name,
                description = deriveDescription(draft),
                path = toolFile.absolutePath,
                createdAt = System.currentTimeMillis(),
                sellable = false, priceIdr = 0
            )))
            drafts.remove(idx)
            true
        }, { onDone?.invoke(it) })
    }

    /** Nama file aman: hanya [A-Za-z0-9_-], maks 48 char + suffix id. */
    private fun safeFileName(name: String, id: String): String {
        val base = name.replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_').take(48)
        return "${base.ifBlank { "tool" }}_${id.takeLast(6)}.py"
    }

    /** Tulis payload ke filesDir/tools/ secara atomik (.tmp → rename). */
    private fun writeToolFile(draft: ToolDraft): java.io.File? {
        return try {
            val toolsDir = java.io.File(JsonStore.appFilesDir(), "tools").apply { mkdirs() }
            val target = java.io.File(toolsDir, safeFileName(draft.name, draft.id))
            val tmp = java.io.File(toolsDir, target.name + ".tmp")
            tmp.writeText(draft.payload)
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
            target
        } catch (_: Exception) {
            null
        }
    }

    /** Deskripsi: baris berarti pertama dari payload (komentar/docstring), maks 140 char. */
    private fun deriveDescription(draft: ToolDraft): String {
        val line = draft.payload.lineSequence()
            .map {
                it.trim()
                    .removePrefix("#").removePrefix("\"\"\"").removeSuffix("\"\"\"")
                    .trim()
            }
            .firstOrNull {
                it.isNotBlank() && !it.startsWith("import ") && !it.startsWith("from ")
            }
        return line?.take(140) ?: "Tool: ${draft.name}"
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

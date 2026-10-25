package id.sukiapps.hermes.data.store
import org.json.JSONArray
import org.json.JSONObject
/**
 * v14.16 — Ideas: bank ide yang bisa langsung dieksekusi.
 *
 * Bedanya dengan catatan biasa: tiap ide WAJIB punya status dan,
 * sebelum boleh ditandai READY, harus punya langkah eksekusi.
 * Itu yang bikin "ide" tidak menggantung jadi wishlist.
 *
 * Skema ideas.json:
 * {"version":1,"items":[{...}]}
 *
 * Tulis hanya lewat mutate() (thread io, atomik).
 */
object IdeasStore {
    enum class Status(val id: String, val label: String) {
        DRAFT("draft", "Draft"),
        READY("ready", "Siap eksekusi"),
        DOING("doing", "Sedang dikerjakan"),
        DONE("done", "Selesai"),
        DROPPED("dropped", "Dibuang");
        companion object {
            fun byId(id: String) = values().firstOrNull { it.id == id } ?: DRAFT
        }
    }
    data class Idea(
        val id: String,
        val title: String,
        val note: String,
        val steps: List<String>,
        val tags: List<String>,
        val status: String,
        val createdAt: Long,
        val updatedAt: Long
    ) {
        fun statusEnum(): Status = Status.byId(status)
        /** Ide layar dieksekusi hanya kalau punya langkah nyata. */
        fun isExecutable(): Boolean =
            status == Status.READY.id && steps.any { it.isNotBlank() }
    }
    private const val STORE = "ideas"
    private fun fromJson(o: JSONObject): Idea {
        val steps = o.optJSONArray("steps") ?: JSONArray()
        val tags = o.optJSONArray("tags") ?: JSONArray()
        return Idea(
            id = o.optString("id"),
            title = o.optString("title"),
            note = o.optString("note"),
            steps = (0 until steps.length()).map { steps.optString(it) },
            tags = (0 until tags.length()).map { tags.optString(it) },
            status = o.optString("status", Status.DRAFT.id),
            createdAt = o.optLong("created_at"),
            updatedAt = o.optLong("updated_at")
        )
    }
    private fun toJson(e: Idea) = JSONObject()
        .put("id", e.id)
        .put("title", e.title)
        .put("note", e.note)
        .put("steps", JSONArray().also { a -> e.steps.forEach { a.put(it) } })
        .put("tags", JSONArray().also { a -> e.tags.forEach { a.put(it) } })
        .put("status", e.status)
        .put("created_at", e.createdAt)
        .put("updated_at", e.updatedAt)
    /** Daftar ide terbaru dulu. Sinkron — panggil dari thread io/background. */
    fun listSync(status: String? = null, query: String = ""): List<Idea> {
        val arr = JsonStore.load(STORE).optJSONArray("items") ?: return emptyList()
        val q = query.trim().lowercase()
        val out = ArrayList<Idea>(arr.length())
        for (i in 0 until arr.length()) {
            val e = fromJson(arr.getJSONObject(i))
            if (status != null && e.status != status) continue
            if (q.isNotEmpty()) {
                val hay = (e.title + " " + e.note + " " + e.tags.joinToString(" ")).lowercase()
                if (!hay.contains(q)) continue
            }
            out.add(e)
        }
        return out.sortedByDescending { it.updatedAt }
    }
    /**
     * Tambah ide baru. Judul kosong ditolak.
     * @return id ide, atau null bila gagal validasi.
     */
    fun add(title: String, note: String = "", steps: List<String> = emptyList(),
            tags: List<String> = emptyList(), onDone: (String?) -> Unit) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            onDone(null)
            return
        }
        val now = System.currentTimeMillis()
        val id = "i${now}_${(0..9999).random()}"
        val idea = Idea(
            id = id,
            title = cleanTitle.take(200),
            note = note.trim().take(4000),
            steps = steps.map { it.trim() }.filter { it.isNotBlank() }.take(30),
            tags = tags.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct().take(10),
            status = Status.DRAFT.id,
            createdAt = now,
            updatedAt = now
        )
        JsonStore.mutate(STORE, { json ->
            val arr = json.optJSONArray("items") ?: JSONArray().also { json.put("items", it) }
            arr.put(toJson(idea))
        }, { onDone(id) })
    }
    /** Ubah status ide (mis. draft → ready → done). */
    fun setStatus(id: String, status: String, onDone: (Boolean) -> Unit) {
        val target = Status.byId(status).id
        JsonStore.mutate(STORE, { json ->
            val arr = json.optJSONArray("items") ?: return@mutate false
            var changed = false
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("id") == id) {
                    o.put("status", target)
                    o.put("updated_at", System.currentTimeMillis())
                    changed = true
                    break
                }
            }
            changed
        }, { onDone(it) })
    }
    /** Tambah langkah eksekusi ke ide yang ada. */
    fun addStep(id: String, step: String, onDone: (Boolean) -> Unit) {
        val clean = step.trim()
        if (clean.isEmpty()) {
            onDone(false)
            return
        }
        JsonStore.mutate(STORE, { json ->
            val arr = json.optJSONArray("items") ?: return@mutate false
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("id") == id) {
                    val steps = o.optJSONArray("steps") ?: JSONArray().also { o.put("steps", it) }
                    steps.put(clean.take(500))
                    o.put("updated_at", System.currentTimeMillis())
                    return@mutate true
                }
            }
            false
        }, { onDone(it) })
    }
    /** Hapus ide permanen. */
    fun delete(id: String, onDone: (() -> Unit)? = null) {
        JsonStore.mutate(STORE, { json ->
            val arr = json.optJSONArray("items") ?: return@mutate
            val next = JSONArray()
            for (i in 0 until arr.length()) {
                if (arr.getJSONObject(i).optString("id") != id) next.put(arr.get(i))
            }
            json.put("items", next)
        }, { onDone?.invoke() })
    }
    /** Logika murni (tanpa Android) — bisa di-unit-test di JVM. */
    fun isExecutable(steps: List<String>, status: String): Boolean =
        Status.byId(status) == Status.READY && steps.any { it.isNotBlank() }
}

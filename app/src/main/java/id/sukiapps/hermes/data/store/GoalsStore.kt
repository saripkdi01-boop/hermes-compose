package id.sukiapps.hermes.data.store
import org.json.JSONArray
import org.json.JSONObject
/**
 * v14.16 — Goals: target jangka panjang dengan progres terukur.
 *
 * Progres dihitung dari angka (current terhadap targetValue) bila
 * metrik diisi; tanpa metrik, progres hanya 0% atau 100% (belum/selesai).
 * Tenggat opsional (0 = tanpa tenggat).
 *
 * Skema goals.json:
 * {"version":1,"items":[{...}]}
 */
object GoalsStore {
    enum class Status(val id: String, val label: String) {
        ACTIVE("active", "Aktif"),
        DONE("done", "Tercapai"),
        ARCHIVED("archived", "Diarsipkan");
        companion object {
            fun byId(id: String) = values().firstOrNull { it.id == id } ?: ACTIVE
        }
    }
    data class Goal(
        val id: String,
        val title: String,
        val why: String,
        val current: Double,
        val targetValue: Double,
        val unit: String,
        val deadline: Long,
        val status: String,
        val createdAt: Long,
        val updatedAt: Long
    ) {
        fun statusEnum(): Status = Status.byId(status)
        fun hasMetric(): Boolean = targetValue > 0.0
        /** Progres 0..100 (pembulatan ke bawah). */
        fun progress(): Int {
            if (status == Status.DONE.id) return 100
            if (!hasMetric()) return 0
            val ratio = (current / targetValue).coerceIn(0.0, 1.0)
            return (ratio * 100).toInt()
        }
    }
    private const val STORE = "goals"
    private fun fromJson(o: JSONObject) = Goal(
        id = o.optString("id"),
        title = o.optString("title"),
        why = o.optString("why"),
        current = o.optDouble("current", 0.0),
        targetValue = o.optDouble("target_value", 0.0),
        unit = o.optString("unit"),
        deadline = o.optLong("deadline"),
        status = o.optString("status", Status.ACTIVE.id),
        createdAt = o.optLong("created_at"),
        updatedAt = o.optLong("updated_at")
    )
    private fun toJson(e: Goal) = JSONObject()
        .put("id", e.id)
        .put("title", e.title)
        .put("why", e.why)
        .put("current", e.current)
        .put("target_value", e.targetValue)
        .put("unit", e.unit)
        .put("deadline", e.deadline)
        .put("status", e.status)
        .put("created_at", e.createdAt)
        .put("updated_at", e.updatedAt)
    /** Goal aktif dulu, lalu yang paling baru diperbarui. */
    fun listSync(status: String? = null, query: String = ""): List<Goal> {
        val arr = JsonStore.load(STORE).optJSONArray("items") ?: return emptyList()
        val q = query.trim().lowercase()
        val out = ArrayList<Goal>(arr.length())
        for (i in 0 until arr.length()) {
            val e = fromJson(arr.getJSONObject(i))
            if (status != null && e.status != status) continue
            if (q.isNotEmpty() && !(e.title + " " + e.why).lowercase().contains(q)) continue
            out.add(e)
        }
        val rank = { s: String -> if (s == Status.ACTIVE.id) 0 else 1 }
        return out.sortedWith(
            compareBy<Goal> { rank(it.status) }.thenByDescending { it.updatedAt }
        )
    }
    /**
     * Tambah goal. targetValue boleh 0 (tanpa metrik angka).
     * @return id goal, atau null bila judul kosong.
     */
    fun add(
        title: String,
        why: String = "",
        current: Double = 0.0,
        targetValue: Double = 0.0,
        unit: String = "",
        deadline: Long = 0L,
        onDone: (String?) -> Unit
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            onDone(null)
            return
        }
        val now = System.currentTimeMillis()
        val id = "g${now}_${(0..9999).random()}"
        val goal = Goal(
            id = id,
            title = cleanTitle.take(200),
            why = why.trim().take(4000),
            current = current.coerceAtLeast(0.0),
            targetValue = targetValue.coerceAtLeast(0.0),
            unit = unit.trim().take(20),
            deadline = deadline.coerceAtLeast(0L),
            status = Status.ACTIVE.id,
            createdAt = now,
            updatedAt = now
        )
        JsonStore.mutate(STORE, { json ->
            val arr = json.optJSONArray("items") ?: JSONArray().also { json.put("items", it) }
            arr.put(toJson(goal))
        }, { onDone(id) })
    }
    /** Perbarui angka progres. current >= targetValue otomatis ditandai DONE. */
    fun setProgress(id: String, current: Double, onDone: (Boolean) -> Unit) {
        JsonStore.mutate(STORE, { json ->
            val arr = json.optJSONArray("items") ?: return@mutate false
            var changed = false
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("id") == id) {
                    val value = current.coerceAtLeast(0.0)
                    o.put("current", value)
                    val target = o.optDouble("target_value", 0.0)
                    if (target > 0.0 && value >= target) {
                        o.put("status", Status.DONE.id)
                    }
                    o.put("updated_at", System.currentTimeMillis())
                    changed = true
                    break
                }
            }
            changed
        }, { onDone(it) })
    }
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
    fun progressOf(current: Double, targetValue: Double, done: Boolean): Int {
        if (done) return 100
        if (targetValue <= 0.0) return 0
        val ratio = (current / targetValue).coerceIn(0.0, 1.0)
        return (ratio * 100).toInt()
    }
}

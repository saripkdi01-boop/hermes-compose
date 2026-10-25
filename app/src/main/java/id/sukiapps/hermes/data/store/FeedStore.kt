package id.sukiapps.hermes.data.store
import org.json.JSONArray
import org.json.JSONObject
/**
 * v14.16 — Feed: tulisan editorial terjadwal (paritas "Feed" Upin).
 *
 * Post punya jadwal publish (epoch millis). Post yang sudah lewat
 * tenggatnya dan belum ditandai terbit = "siap tayang"; layar Feed
 * menampilkannya sebagai draft terjadwal, bukan mengirim apa pun
 * ke luar tanpa persetujuan user.
 *
 * Skema feed.json:
 * {"version":1,"items":[{...}]}
 */
object FeedStore {
    data class Post(
        val id: String,
        val title: String,
        val body: String,
        val publishAt: Long,
        val published: Boolean,
        val createdAt: Long
    ) {
        /** Sudah waktunya tayang tapi belum ditandai terbit. */
        fun isDue(now: Long): Boolean = !published && publishAt in 1..now
        fun isScheduled(now: Long): Boolean = !published && publishAt > now
    }
    private const val STORE = "feed"
    private fun fromJson(o: JSONObject) = Post(
        id = o.optString("id"),
        title = o.optString("title"),
        body = o.optString("body"),
        publishAt = o.optLong("publish_at"),
        published = o.optBoolean("published", false),
        createdAt = o.optLong("created_at")
    )
    private fun toJson(e: Post) = JSONObject()
        .put("id", e.id)
        .put("title", e.title)
        .put("body", e.body)
        .put("publish_at", e.publishAt)
        .put("published", e.published)
        .put("created_at", e.createdAt)
    /** Terjadwal dulu (paling dekat), lalu terbit terbaru. */
    fun listSync(query: String = ""): List<Post> {
        val arr = JsonStore.load(STORE).optJSONArray("items") ?: return emptyList()
        val q = query.trim().lowercase()
        val out = ArrayList<Post>(arr.length())
        for (i in 0 until arr.length()) {
            val e = fromJson(arr.getJSONObject(i))
            if (q.isNotEmpty() && !(e.title + " " + e.body).lowercase().contains(q)) continue
            out.add(e)
        }
        return out.sortedWith(
            compareBy<Post> { it.published }.thenBy { it.publishAt }
        )
    }
    /**
     * Jadwalkan tulisan. publishAt = 0 berarti draft tanpa jadwal.
     * @return id post, atau null bila judul kosong.
     */
    fun add(title: String, body: String, publishAt: Long = 0L, onDone: (String?) -> Unit) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            onDone(null)
            return
        }
        val now = System.currentTimeMillis()
        val id = "f${now}_${(0..9999).random()}"
        val post = Post(
            id = id,
            title = cleanTitle.take(200),
            body = body.trim().take(20_000),
            publishAt = publishAt.coerceAtLeast(0L),
            published = false,
            createdAt = now
        )
        JsonStore.mutate(STORE, { json ->
            val arr = json.optJSONArray("items") ?: JSONArray().also { json.put("items", it) }
            arr.put(toJson(post))
        }, { onDone(id) })
    }
    /** Tandai sudah terbit (tidak mengirim apa pun ke luar). */
    fun markPublished(id: String, onDone: (Boolean) -> Unit) {
        JsonStore.mutate(STORE, { json ->
            val arr = json.optJSONArray("items") ?: return@mutate false
            var changed = false
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("id") == id) {
                    o.put("published", true)
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
    fun dueCount(posts: List<Post>, now: Long): Int =
        posts.count { it.isDue(now) }
}

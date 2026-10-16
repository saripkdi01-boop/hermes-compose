package id.sukiapps.hermes.data.store

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * v12.1 — Gudang File: repositori file terkategorisasi (tanpa Room).
 *
 * Kategori:
 * - ui_visual  🎨  Desain, mockup, screenshot
 * - database   🗄️  Skema, migrasi
 * - backend    ⚙️  Kode server, worker
 * - api        🔌  Spesifikasi & dokumentasi endpoint
 * - frontend   💻  Kode UI
 * - podcast    🎙️  Episode podcast (dari VM Muse)
 * - skill      🛠️  Skill kustom (dari VM Muse)
 * - dokumen    📄  Dokumen lain (laporan, arsip)
 * - riwayat    📜  Arsip percakapan & file lama
 *
 * File fisik: filesDir/warehouse/<kategori>/<nama_file>
 * Metadata: warehouse.json via JsonStore.
 */
object WarehouseStore {

    enum class Category(val id: String, val label: String, val emoji: String) {
        UI_VISUAL("ui_visual", "UI Visual", "🎨"),
        DATABASE("database", "Database", "🗄️"),
        BACKEND("backend", "Backend", "⚙️"),
        API("api", "API", "🔌"),
        FRONTEND("frontend", "Frontend", "💻"),
        PODCAST("podcast", "Podcast", "🎙️"),
        SKILL("skill", "Skill", "🛠️"),
        DOKUMEN("dokumen", "Dokumen", "📄"),
        RIWAYAT("riwayat", "Riwayat", "📜");

        companion object {
            fun byId(id: String) = values().firstOrNull { it.id == id } ?: DOKUMEN
        }
    }

    data class Entry(
        val id: String,
        val category: String,
        val name: String,
        val fileName: String,
        val mime: String,
        val sizeBytes: Long,
        val addedAt: Long,
        val note: String
    )

    private const val STORE = "warehouse"

    private fun warehouseDir(): File {
        val dir = File(JsonStore.appFilesDir(), "warehouse")
        dir.mkdirs()
        return dir
    }

    private fun fromJson(o: JSONObject) = Entry(
        id = o.optString("id"),
        category = o.optString("category"),
        name = o.optString("name"),
        fileName = o.optString("file_name"),
        mime = o.optString("mime"),
        sizeBytes = o.optLong("size_bytes"),
        addedAt = o.optLong("added_at"),
        note = o.optString("note")
    )

    private fun toJson(e: Entry) = JSONObject()
        .put("id", e.id)
        .put("category", e.category)
        .put("name", e.name)
        .put("file_name", e.fileName)
        .put("mime", e.mime)
        .put("size_bytes", e.sizeBytes)
        .put("added_at", e.addedAt)
        .put("note", e.note)

    private fun safeFileName(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        val clean = base.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
        return clean.ifBlank { "file" }
    }

    /**
     * Import file dari [source] ke kategori. Callback di thread pemanggil.
     * @return Entry atau null bila gagal.
     */
    fun importFile(
        category: String,
        source: File,
        displayName: String,
        note: String = "",
        onDone: (Entry?) -> Unit
    ) {
        Thread {
            try {
                val cat = Category.byId(category)
                val dir = File(warehouseDir(), cat.id).apply { mkdirs() }
                val safe = safeFileName(displayName.ifBlank { source.name })
                // Hindari tabrakan nama: tambah timestamp bila perlu.
                var dest = File(dir, safe)
                if (dest.exists()) {
                    val stem = safe.substringBeforeLast('.', safe)
                    val ext = safe.substringAfterLast('.', "")
                    dest = File(dir, "${stem}_${System.currentTimeMillis()}.$ext")
                }
                source.copyTo(dest, overwrite = false)

                val now = System.currentTimeMillis()
                val entry = Entry(
                    id = "w${now}_${(0..9999).random()}",
                    category = cat.id,
                    name = displayName.ifBlank { source.name },
                    fileName = dest.name,
                    mime = guessMime(dest.name),
                    sizeBytes = dest.length(),
                    addedAt = now,
                    note = note
                )
                JsonStore.mutate(STORE, { json ->
                    val arr = json.optJSONArray("entries")
                        ?: JSONArray().also { json.put("entries", it) }
                    arr.put(toJson(entry))
                }, { onDone(entry) })
            } catch (_: Exception) {
                onDone(null)
            }
        }.start()
    }

    /** Daftar entry, opsional filter kategori + pencarian nama. */
    fun listSync(category: String? = null, query: String = ""): List<Entry> {
        val arr = JsonStore.load(STORE).optJSONArray("entries") ?: return emptyList()
        val q = query.trim().lowercase()
        val out = ArrayList<Entry>(arr.length())
        for (i in 0 until arr.length()) {
            val e = fromJson(arr.getJSONObject(i))
            if (category != null && e.category != category) continue
            if (q.isNotEmpty() && !e.name.lowercase().contains(q)) continue
            out.add(e)
        }
        return out.sortedByDescending { it.addedAt }
    }

    /** File fisik untuk entry. */
    fun fileFor(entry: Entry): File =
        File(File(warehouseDir(), entry.category), entry.fileName)

    /** Hapus entry + file fisiknya. */
    fun delete(entry: Entry, onDone: (() -> Unit)? = null) {
        Thread {
            runCatching { fileFor(entry).delete() }
            JsonStore.mutate(STORE, { json ->
                val arr = json.optJSONArray("entries") ?: return@mutate
                val next = JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (o.optString("id") != entry.id) next.put(o)
                }
                json.put("entries", next)
            }, { onDone?.invoke() })
        }.start()
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "png", "jpg", "jpeg", "gif", "webp" -> "image/$ext"
            "mp3", "m4a", "wav", "ogg" -> "audio/$ext"
            "mp4", "mkv", "webm" -> "video/$ext"
            "pdf" -> "application/pdf"
            "md" -> "text/markdown"
            "txt", "log" -> "text/plain"
            "json" -> "application/json"
            "zip" -> "application/zip"
            "kt", "java", "py", "js", "ts" -> "text/x-source"
            else -> "application/octet-stream"
        }
    }
}

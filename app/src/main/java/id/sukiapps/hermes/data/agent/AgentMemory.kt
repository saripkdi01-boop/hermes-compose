package id.sukiapps.hermes.data.agent

import android.content.Context
import id.sukiapps.hermes.data.store.JsonStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * AgentMemory — ingatan persisten agen (Sprint 1 Gate 4).
 * Sumber data: JsonStore "agent_memory" (JSON atomik, TANPA Room) +
 * file reflection berekstensi .md di filesDir/reflection.
 * Export/import = satu dokumen JSON berisi keduanya (keputusan Suki:
 * cakupan agent_memory + reflection).
 */
object AgentMemory {

    private const val STORE = "agent_memory"

    fun list(): List<String> {
        val items = JsonStore.load(STORE).optJSONArray("items") ?: return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until items.length()) {
            items.optString(i).takeIf { it.isNotBlank() }?.let { out.add(it) }
        }
        return out
    }

    fun add(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val root = JsonStore.load(STORE)
        val items = root.optJSONArray("items") ?: JSONArray()
        items.put(clean)
        // Jaga ukuran wajar: simpan 200 catatan terakhir.
        val trimmed = JSONArray()
        val start = maxOf(0, items.length() - 200)
        for (i in start until items.length()) trimmed.put(items.opt(i))
        root.put("items", trimmed)
        JsonStore.save(STORE, root)
    }

    fun clear() {
        JsonStore.save(STORE, JSONObject().put("version", 1).put("items", JSONArray()))
    }

    private fun reflectionDir(): File =
        File(JsonStore.appFilesDir(), "reflection")

    /** Dokumen export lengkap sebagai teks JSON. */
    fun exportJson(): String {
        val refl = JSONArray()
        val dir = reflectionDir()
        if (dir.exists()) {
            dir.listFiles { f -> f.name.endsWith(".md") }
                ?.sortedBy { it.name }
                ?.forEach { f ->
                    refl.put(
                        JSONObject()
                            .put("name", f.name)
                            .put("content", runCatching { f.readText() }.getOrDefault(""))
                    )
                }
        }
        return JSONObject()
            .put("format", "hermes-memory-v1")
            .put("exported_at", System.currentTimeMillis())
            .put("agent_memory", JsonStore.load(STORE))
            .put("reflection", refl)
            .toString(2)
    }

    /**
     * Hasil impor format Upin — rinci, bukan sekadar true/false, supaya
     * UI bisa menampilkan jumlah file, yang dilewati, dan alasan gagal
     * yang sebenarnya (bukan lagi "format tidak dikenal" untuk semua
     * kegagalan).
     */
    data class ImportResult(
        val ok: Boolean,
        val imported: Int = 0,
        val skipped: List<String> = emptyList(),
        /** true bila MEMORY.md ekspor berhasil ditulis ke agent/memory.md. */
        val summaryApplied: Boolean = false,
        /** Folder backup ingatan lama, null bila tidak ada yang dibackup. */
        val backupDir: String? = null,
        val error: String = "",
    )

    /**
     * Import ingatan format "upin-memory-export" (ekspor memori Muse).
     *
     * Yang dilakukan, berurutan:
     * 1. Parse & validasi dulu (UpinMemoryImport) — gagal di sini =
     *    TIDAK ADA yang ditulis sama sekali.
     * 2. Backup agent/memory.md lama ke imported-memory/_backup-<waktu>/
     *    bila ada isinya. Ingatan lama TIDAK pernah ditimpa tanpa backup.
     * 3. Tulis semua file ke filesDir/imported-memory/ dengan struktur
     *    path aslinya (people/, groups/, catatan harian, dst), lengkap
     *    dengan INDEX.md daftar isinya.
     * 4. Tulis MEMORY.md ekspor ke agent/memory.md — inilah yang dibaca
     *    SoulPrompt saat merakit prompt agen, jadi impor ini benar-benar
     *    terasa di chat berikutnya, bukan sekadar file tersimpan.
     * 5. Tambah SATU catatan di store agent_memory sebagai penanda
     *    (tidak menimpa catatan lama yang sudah ada).
     *
     * Suspend + I/O di Dispatchers.IO: file ekspor nyata ~26 MB, jangan
     * pernah dipanggil dari main thread.
     */
    suspend fun importUpin(ctx: Context, text: String): ImportResult =
        withContext(Dispatchers.IO) {
            val plan = UpinMemoryImport.parse(text)
            if (!plan.ok) {
                return@withContext ImportResult(
                    ok = false,
                    skipped = plan.skipped,
                    error = plan.error,
                )
            }
            val base = File(JsonStore.appFilesDir(), "imported-memory")
                .apply { mkdirs() }
            var backupDir: String? = null
            val oldMemory = File(AgentFiles.dir(ctx), AgentFiles.MEMORY)
            if (oldMemory.exists() && oldMemory.length() > 0) {
                val bdir = File(base, "_backup-${System.currentTimeMillis()}")
                runCatching {
                    bdir.mkdirs()
                    oldMemory.copyTo(File(bdir, AgentFiles.MEMORY), true)
                    backupDir = bdir.absolutePath
                }
            }
            val canonicalBase = base.canonicalFile.path
            val skipped = plan.skipped.toMutableList()
            var written = 0
            for (f in plan.files) {
                val target = File(base, f.relativePath)
                // Guard kedua (selain validasi parser): target harus
                // tetap di dalam base, apapun isi path-nya.
                if (!target.canonicalFile.path.startsWith("$canonicalBase${File.separator}")) {
                    skipped.add("${f.relativePath}: di luar folder impor")
                    continue
                }
                runCatching {
                    target.parentFile?.mkdirs()
                    target.writeText(f.content)
                    written++
                }.onFailure {
                    skipped.add("${f.relativePath}: ${it.message}")
                }
            }
            runCatching {
                File(base, "INDEX.md").writeText(
                    buildString {
                        appendLine("# Ingatan Upin yang diimpor")
                        appendLine()
                        appendLine("Diimpor: ${System.currentTimeMillis()}")
                        appendLine("File tertulis: $written")
                        appendLine("Dilewati: ${skipped.size}")
                        plan.files.forEach { appendLine("- ${it.relativePath}") }
                    }
                )
            }
            var summaryApplied = false
            val summary = plan.summary
            if (!summary.isNullOrBlank()) {
                runCatching {
                    AgentFiles.write(ctx, AgentFiles.MEMORY, summary)
                    summaryApplied = true
                }
            }
            runCatching {
                add(
                    "Ingatan Upin diimpor: $written file di " +
                        "imported-memory/; ringkasan di agent/memory.md."
                )
            }
            ImportResult(
                ok = true,
                imported = written,
                skipped = skipped,
                summaryApplied = summaryApplied,
                backupDir = backupDir,
            )
        }

    /** Import dari teks export. true bila format dikenali & diterapkan. */
    fun importJson(text: String): Boolean {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return false
        if (root.optString("format") != "hermes-memory-v1") return false
        val mem = root.optJSONObject("agent_memory") ?: return false
        JsonStore.save(STORE, mem)
        val refl = root.optJSONArray("reflection")
        if (refl != null) {
            val dir = reflectionDir().apply { mkdirs() }
            for (i in 0 until refl.length()) {
                val o = refl.optJSONObject(i) ?: continue
                val name = o.optString("name")
                // Hanya nama file .md sederhana (anti path traversal).
                if (name.isBlank() || name.contains("/") || name.contains("\\") ||
                    !name.endsWith(".md")
                ) continue
                runCatching {
                    File(dir, name).writeText(o.optString("content"))
                }
            }
        }
        return true
    }
}

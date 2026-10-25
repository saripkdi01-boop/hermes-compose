package id.sukiapps.hermes.data.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parser MURNI (tanpa Context, tanpa I/O) untuk impor ingatan format
 * "upin-memory-export" — format yang dihasilkan ekspor memori Muse (Upin):
 *   { "format": "upin-memory-export", "files": [
 *       { "path": "MEMORY.md", "content": "..." },
 *       { "path": "memori/people/roni-sianturi.md", "content": "..." } ] }
 *
 * Kenapa file ini ada: import lama (AgentMemory.importJson) HANYA menerima
 * format "hermes-memory-v1" dan HANYA menulis ke store agent_memory +
 * reflection/ nama-file-sederhana. File ekspor Upin (52 file, ~26 MB,
 * path bersarang) selalu ditolak dengan "format tidak dikenal", dan
 * kalaupun diterima, ringkasannya tidak pernah sampai ke
 * agent/memory.md — file yang benar-benar dibaca SoulPrompt saat
 * merakit prompt agen. Jadi impor lama "berhasil" pun tidak terasa.
 *
 * Sengaja pakai kotlinx.serialization (bukan org.json): org.json di
 * unit test lokal Android hanya stub dan melempar, sedangkan
 * kotlinx.serialization sudah dipakai proyek ini (lihat Persona) dan
 * jalan di testReleaseUnitTest CI.
 *
 * Aturan keamanan (tidak boleh dilonggarkan):
 * - Hanya path relatif berakhiran .md, tanpa backslash, tanpa segmen "..",
 *   tanpa awalan "/". Entri yang melanggar DILEWATI (bukan digagalkan
 *   seluruhnya) dan dicatat di [ImportPlan.skipped].
 * - Batas total karakter default 40.000.000 (~40 MB teks) — ekspor Upin
 *   nyata ~25,7 juta karakter, jadi lolos; file raksasa ditolak sebelum
 *   ditulis, bukan crash kehabisan memori di tengah jalan.
 * - Parser ini TIDAK menulis apa pun. Penulisan dilakukan
 *   [AgentMemory.importUpin] yang membackup ingatan lama dulu.
 */
object UpinMemoryImport {

    const val FORMAT = "upin-memory-export"
    const val DEFAULT_MAX_TOTAL_CHARS = 40_000_000
    const val DEFAULT_MAX_FILES = 500

    private val json = Json { ignoreUnknownKeys = true }

    data class ImportedFile(val relativePath: String, val content: String)

    data class ImportPlan(
        val ok: Boolean,
        val files: List<ImportedFile> = emptyList(),
        val skipped: List<String> = emptyList(),
        /** Isi MEMORY.md bila ada di ekspor, null bila tidak ada. */
        val summary: String? = null,
        val totalChars: Int = 0,
        /** Alasan gagal bila ok=false, kosong bila ok=true. */
        val error: String = "",
    )

    fun parse(text: String): ImportPlan =
        parse(text, DEFAULT_MAX_TOTAL_CHARS, DEFAULT_MAX_FILES)

    /** maxChars/maxFiles sebagai parameter agar bisa diuji tanpa file 40 MB. */
    internal fun parse(text: String, maxChars: Int, maxFiles: Int): ImportPlan {
        if (text.isBlank()) return ImportPlan(false, error = "File kosong.")
        val root: JsonObject = runCatching {
            json.parseToJsonElement(text).jsonObject
        }.getOrNull() ?: return ImportPlan(false, error = "Bukan JSON yang valid.")
        val format = root["format"]?.jsonPrimitive?.contentOrNull
        if (format != FORMAT) {
            return ImportPlan(
                false,
                error = "Format tidak dikenal: '$format'. " +
                    "Yang diterima: '$FORMAT' atau 'hermes-memory-v1'."
            )
        }
        val arr: JsonArray = root["files"]?.jsonArray
            ?: return ImportPlan(false, error = "Field 'files' tidak ditemukan.")
        if (arr.isEmpty()) {
            return ImportPlan(false, error = "Field 'files' kosong.")
        }
        if (arr.size > maxFiles) {
            return ImportPlan(
                false,
                error = "Terlalu banyak file: ${arr.size} (maks $maxFiles)."
            )
        }
        val files = mutableListOf<ImportedFile>()
        val skipped = mutableListOf<String>()
        var total = 0
        for ((i, el) in arr.withIndex()) {
            val o = runCatching { el.jsonObject }.getOrNull()
            if (o == null) {
                skipped.add("entri #$i: bukan objek")
                continue
            }
            val path = o["path"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val reason = validatePath(path)
            if (reason != null) {
                skipped.add("$path: $reason")
                continue
            }
            val content = o["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
            total += content.length
            if (total > maxChars) {
                return ImportPlan(
                    false,
                    error = "File terlalu besar: total $total karakter " +
                        "(maks $maxChars). Impor dibatalkan, tidak ada " +
                        "yang ditulis."
                )
            }
            files.add(ImportedFile(path, content))
        }
        if (files.isEmpty()) {
            return ImportPlan(
                false,
                skipped = skipped,
                error = "Tidak ada file .md yang valid untuk diimpor."
            )
        }
        val summary = files.firstOrNull {
            it.relativePath == "MEMORY.md" ||
                it.relativePath.endsWith("/MEMORY.md")
        }?.content
        return ImportPlan(
            ok = true,
            files = files,
            skipped = skipped,
            summary = summary,
            totalChars = total,
        )
    }

    /** null = path valid, selain itu = alasan penolakan. */
    internal fun validatePath(path: String): String? {
        if (path.isBlank()) return "path kosong"
        if (!path.endsWith(".md")) return "bukan file .md"
        if (path.startsWith("/")) return "path absolut tidak diizinkan"
        if (path.contains("\\")) return "backslash tidak diizinkan"
        if (path.split("/").any { it == ".." || it.isEmpty() }) {
            return "segmen '..' atau '//' tidak diizinkan"
        }
        return null
    }
}

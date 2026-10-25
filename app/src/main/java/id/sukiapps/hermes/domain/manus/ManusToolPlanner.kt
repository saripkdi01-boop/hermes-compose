package id.sukiapps.hermes.domain.manus

import org.json.JSONObject

/**
 * ManusToolPlanner — penerjemah goal → rencana langkah + tool nyata.
 *
 * Bagian dari upgrade Manus Live Console (14.16). Planner deterministik
 * (tanpa LLM) supaya tidak menambah biaya token dan tidak butuh API key:
 * memetakan kata kunci goal ke tool yang tersedia di ToolLoop.
 */
object ManusToolPlanner {

    data class PlannedStep(
        val title: String,
        val toolName: String,
        val args: JSONObject,
        val detail: String
    )

    /** Tool default yang tersedia (lihat ToolLoop.execute). */
    val AVAILABLE_TOOLS = listOf(
        "web_fetch", "shell_exec", "file_read", "file_write",
        "net_scan", "conn_health", "github_read_file", "github_repos",
        "supabase_query", "vercel_projects", "memory_list", "system_info"
    )

    /**
     * Bangun rencana dari goal bahasa biasa.
     * Mengembalikan daftar langkah yang bisa langsung dieksekusi.
     */
    fun plan(goal: String): List<PlannedStep> {
        val g = goal.lowercase().trim()
        val steps = mutableListOf<PlannedStep>()

        // 1. Selalu mulai dengan snapshot lingkungan (murah, aman, informatif)
        steps += PlannedStep(
            title = "Snapshot lingkungan",
            toolName = "system_info",
            args = JSONObject(),
            detail = "Baca model perangkat, versi Android, memori, isi sandbox."
        )

        // 2. Deteksi URL → tarik isi halaman
        val urls = extractUrls(goal)
        urls.take(2).forEach { url ->
            steps += PlannedStep(
                title = "Ambil halaman: ${shorten(url)}",
                toolName = "web_fetch",
                args = JSONObject().put("url", url),
                detail = "Tarik konten halaman sebagai teks untuk dibaca."
            )
        }

        // 3. Deteksi kata kunci repo/GitHub → baca repo
        if (g.containsAny("github", "repo", "repository", "pull request", "commit")) {
            steps += PlannedStep(
                title = "Daftar repo GitHub",
                toolName = "github_repos",
                args = JSONObject(),
                detail = "Ambil daftar repo milik user (read-only, dari PAT tersimpan)."
            )
        }

        // 4. Deteksi kata kunci file → baca file sandbox
        val filePath = extractFilePath(goal)
        if (filePath != null) {
            steps += PlannedStep(
                title = "Baca file: $filePath",
                toolName = "file_read",
                args = JSONObject().put("path", filePath),
                detail = "Baca isi file teks di dalam sandbox."
            )
        }

        // 5. Deteksi kata kunci shell/perintah → jalankan shell
        val cmd = extractCommand(goal)
        if (cmd != null) {
            steps += PlannedStep(
                title = "Jalankan perintah shell",
                toolName = "shell_exec",
                args = JSONObject().put("command", cmd),
                detail = "Eksekusi perintah di sandbox terminal (timeout 30 detik)."
            )
        }

        // 6. Deteksi kata kunci scan/port → scan jaringan lokal
        if (g.containsAny("scan", "port", "buka port", "cek server")) {
            steps += PlannedStep(
                title = "Scan port localhost",
                toolName = "net_scan",
                args = JSONObject().put("host", "127.0.0.1"),
                detail = "Cek port TCP standar di host allowlist."
            )
        }

        // 7. Deteksi kata kunci supabase/tabel → query data
        if (g.containsAny("supabase", "tabel", "database", "baris")) {
            val table = extractWordAfter(goal, listOf("tabel", "table", "supabase"))
            if (table != null) {
                steps += PlannedStep(
                    title = "Query Supabase: $table",
                    toolName = "supabase_query",
                    args = JSONObject().put("table", table),
                    detail = "Baca baris dari tabel Supabase via PostgREST."
                )
            }
        }

        // 8. Deteksi kata kunci vercel/deploy → cek deployment
        if (g.containsAny("vercel", "deploy", "hosting")) {
            steps += PlannedStep(
                title = "Daftar project Vercel",
                toolName = "vercel_projects",
                args = JSONObject(),
                detail = "Ambil daftar project Vercel milik user."
            )
        }

        // 9. Deteksi kata kunci ingatan/memory → baca catatan
        if (g.containsAny("ingatan", "memory", "catatan", "fakta")) {
            steps += PlannedStep(
                title = "Baca ingatan agen",
                toolName = "memory_list",
                args = JSONObject(),
                detail = "Tampilkan semua catatan persisten antar sesi."
            )
        }

        // 10. Selalu akhiri dengan rangkuman goal
        steps += PlannedStep(
            title = "Rangkum hasil",
            toolName = "memory_save",
            args = JSONObject().put(
                "text",
                "Tugas Manus: ${goal.take(120)}"
            ),
            detail = "Simpan ringkasan tugas ke ingatan persisten."
        )

        return steps
    }

    // ---------- helper ----------

    private val URL_REGEX = Regex("https?://[^\\s]+")

    private fun extractUrls(text: String): List<String> =
        URL_REGEX.findAll(text).map { it.value.trimEnd('.', ',', ')') }.toList()

    private fun shorten(url: String, max: Int = 40): String =
        if (url.length <= max) url else url.take(max - 3) + "..."

    private fun extractFilePath(goal: String): String? {
        val patterns = listOf(
            Regex("(?:baca|read|file)\\s+([\\w./-]+\\.[a-zA-Z]{1,5})"),
            Regex("([\\w./-]+\\.[a-zA-Z]{1,5})")
        )
        for (p in patterns) {
            val m = p.find(goal) ?: continue
            val candidate = m.groupValues[1].trimEnd('.', ',', ')')
            if (candidate.contains("/") || candidate.contains(".")) return candidate
        }
        return null
    }

    private fun extractCommand(goal: String): String? {
        val patterns = listOf(
            Regex("(?:jalankan|run|eksekusi|perintah)\\s+[`'\"]?([^`'\"]+)[`'\"]?"),
            Regex("\\b(ls|cat|pwd|git status|git log|df|ps|uname)\\b[^.]*")
        )
        for (p in patterns) {
            val m = p.find(goal) ?: continue
            val cmd = m.groupValues[1].trim()
            if (cmd.isNotBlank()) return cmd
        }
        return null
    }

    private fun extractWordAfter(goal: String, keywords: List<String>): String? {
        for (kw in keywords) {
            val idx = goal.lowercase().indexOf(kw)
            if (idx < 0) continue
            val after = goal.substring(idx + kw.length).trim()
            val first = after.split(Regex("\\s+")).firstOrNull { it.isNotBlank() }
            if (first != null) return first.trim(',', '.', ';', ':')
        }
        return null
    }

    private fun String.containsAny(vararg words: String): Boolean =
        words.any { this.contains(it) }
}

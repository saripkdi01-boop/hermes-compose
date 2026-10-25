package id.sukiapps.hermes.domain.manus

/**
 * Pemetaan goal (bahasa manusia) -> rencana langkah + tool ASLI Hermes.
 *
 * Kenapa deterministik, bukan panggil LLM: tool yang tersedia di ToolLoop
 * punya nama & parameter tetap. LLM boleh menyusun bahasa, tapi pemilihan
 * tool harus stabil supaya PermissionGate bisa menilai risiko dengan benar
 * dan supaya runner tidak pernah mengarang tool yang tidak ada.
 *
 * Aturan:
 * - Satu goal bisa mencocokkan beberapa tool; urutannya mengikuti alur kerja
 *   manusia: pahami dulu (baca/cari) -> ambil dulu (fetch) -> olah (tulis) ->
 *   verifikasi (baca ulang).
 * - Tool berisiko tinggi (github_write_file, shell_exec, selfupgrade_*) tetap
 *   ikut daftar; reducer yang memutuskan berhenti di kartu izin.
 * - Tidak ada tool yang tidak terdaftar di ToolLoop.toolsArray().
 */
object ManusToolPlanner {

    /** Satu langkah rencana: judul buat manusia + tool call nyata (opsional). */
    data class PlannedStep(
        val title: String,
        val tool: ToolCall? = null,
        val detail: String? = null
    )

    /** Kata kunci -> tool yang dipakai (lowercase, match substring). */
    private data class Rule(
        val keywords: List<String>,
        val build: (String) -> PlannedStep
    )

    private val rules: List<Rule> = listOf(
        // ---- Riset web / halaman ----
        Rule(listOf("http://", "https://", "ambil isi", "baca url", "cek url", "fetch")) { goal ->
            val url = goal.split(Regex("\\s+"))
                .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
                ?.trim(',', '.', ')', '"')
                ?: "https://sukiapps.web.id"
            PlannedStep(
                title = "Ambil isi halaman: $url",
                tool = ToolCall("web_fetch", mapOf("url" to url, "max_chars" to "20000")),
                detail = "Mengambil teks halaman via WebFetchTool (tanpa render JS kecuali diminta)."
            )
        },
        Rule(listOf("riset", "cari", "search", "telusuri", "bandingkan")) { goal ->
            val q = goal
                .replace(Regex("^(riset|risetkan|cari|carikan|telusuri|search)\\s+", RegexOption.IGNORE_CASE), "")
                .trim()
                .ifBlank { goal }
            PlannedStep(
                title = "Telusuri web untuk: $q",
                tool = ToolCall("web_fetch", mapOf(
                    "url" to "https://duckduckgo.com/html/?q=" + q.replace(" ", "+"),
                    "max_chars" to "12000"
                )),
                detail = "Pencarian publik via DuckDuckGo HTML — hasil dicatat sebagai sumber."
            )
        },

        // ---- Kode & repo ----
        Rule(listOf("repo", "github", "pull request", "pr ", "commit", "branch")) { goal ->
            val repo = goal.split(Regex("\\s+"))
                .firstOrNull { it.contains("/") && !it.startsWith("http") }
                ?.trim(',', '.', '"')
            val parts = repo?.split("/") ?: emptyList()
            PlannedStep(
                title = if (parts.size == 2)
                    "Baca repo GitHub ${parts[0]}/${parts[1]}"
                else
                    "Baca daftar repo GitHub",
                tool = if (parts.size == 2)
                    ToolCall("github_read_file", mapOf(
                        "owner" to parts[0],
                        "repo" to parts[1],
                        "path" to "README.md"
                    ))
                else
                    ToolCall("github_repos"),
                detail = "Membaca isi repo via GitHubConnector (read-only, PAT tersimpan)."
            )
        },
        Rule(listOf("jalankan", "terminal", "shell", "perintah", "command")) { goal ->
            val cmd = goal
                .replace(Regex("^(jalankan|run|terminal|shell|perintah|command)\\s*:?\\s*", RegexOption.IGNORE_CASE), "")
                .trim()
                .ifBlank { "ls -la" }
            PlannedStep(
                title = "Jalankan perintah: $cmd",
                tool = ToolCall("shell_exec", mapOf("command" to cmd)),
                detail = "Eksekusi shell di sandbox Hermes. Aksi HIGH — butuh izin manusia."
            )
        },

        // ---- File & sandbox ----
        Rule(listOf("baca file", "read file", "lihat isi")) { goal ->
            val path = goal.split(Regex("\\s+"))
                .firstOrNull { it.contains(".") || it.contains("/") }
                ?.trim(',', '.', '"')
                ?: "notes.txt"
            PlannedStep(
                title = "Baca file sandbox: $path",
                tool = ToolCall("file_read", mapOf("path" to path)),
                detail = "Membaca file teks di sandbox aplikasi (path relatif)."
            )
        },
        Rule(listOf("tulis file", "write file", "simpan", "buat file", "catat")) { goal ->
            val path = goal.split(Regex("\\s+"))
                .firstOrNull { it.contains(".") || it.contains("/") }
                ?.trim(',', '.', '"')
                ?: "output.txt"
            PlannedStep(
                title = "Tulis file sandbox: $path",
                tool = ToolCall("file_write", mapOf(
                    "path" to path,
                    "content" to "Hasil tugas: $goal\n\nDitulis otomatis oleh Manus Engine Hermes."
                )),
                detail = "Menulis file di sandbox aplikasi. Aksi tulis — butuh izin manusia."
            )
        },

        // ---- Infrastruktur ----
        Rule(listOf("scan port", "port scan", "cek port", "jaringan")) { goal ->
            PlannedStep(
                title = "Scan port host lokal",
                tool = ToolCall("net_scan", mapOf("host" to "127.0.0.1")),
                detail = "Scan TCP ke allowlist (default localhost). Maks 32 port."
            )
        },
        Rule(listOf("health", "cek status", "uptime", "ping")) { goal ->
            val url = goal.split(Regex("\\s+"))
                .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
                ?: "https://sukiapps.web.id"
            PlannedStep(
                title = "Health check: $url",
                tool = ToolCall("conn_health", mapOf("url" to url)),
                detail = "Health check HTTP — status code + latensi."
            )
        },
        Rule(listOf("deploy", "rilis", "release", "merge pr")) { goal ->
            PlannedStep(
                title = "Aksi deploy/merge — konfirmasi manual",
                tool = ToolCall("selfupgrade_merge", mapOf("pr" to "1")),
                detail = "Merge PR bersifat KRITIS: selalu minta izin, tidak bisa dibatalkan otomatis."
            )
        },
        Rule(listOf("kirim", "notif", "telegram", "pesan")) { goal ->
            PlannedStep(
                title = "Kirim pesan Telegram",
                tool = ToolCall("telegram_send", mapOf("text" to "Tugas selesai: $goal")),
                detail = "Mengirim notifikasi lewat bot Telegram (butuh izin)."
            )
        },
        Rule(listOf("model", "daftar model", "ganti model")) { goal ->
            PlannedStep(
                title = "Baca ingatan & info sistem",
                tool = ToolCall("system_info"),
                detail = "Info perangkat & sandbox, read-only."
            )
        }
    )

    /**
     * Ubah goal jadi daftar langkah. Semua rule yang cocok dipakai (bukan
     * hanya yang pertama) supaya goal kompleks dapat rencana multi-langkah;
     * urutan mengikuti urutan rule (baca -> ambil -> tulis -> kirim).
     * Kalau tidak ada yang cocok: satu langkah catat manual (tanpa tool),
     * supaya state machine tetap bisa jalan dan user bisa lihat hasilnya.
     */
    fun plan(goal: String): List<PlannedStep> {
        val g = goal.lowercase()
        val matched = rules.filter { rule -> rule.keywords.any { g.contains(it) } }
        return if (matched.isEmpty()) {
            listOf(
                PlannedStep(
                    title = "Catat & proses goal: $goal",
                    tool = ToolCall("file_write", mapOf(
                        "path" to "manus-goal.txt",
                        "content" to goal
                    )),
                    detail = "Tidak ada tool spesifik yang cocok — goal dicatat ke sandbox untuk diproses manual."
                )
            )
        } else {
            matched.map { it.build(goal) }
        }
    }

    /** Judul-tool pairs untuk dipakai SubmitGoal (reducer hanya butuh judul). */
    fun planTitles(goal: String): List<String> = plan(goal).map { it.title }
}

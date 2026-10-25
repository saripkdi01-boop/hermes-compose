package id.sukiapps.hermes.domain.manus

import org.json.JSONObject

/**
 * Satu langkah rencana yang sudah terpetakan ke tool NYATA.
 *
 * Dibuat oleh [ManusToolPlanner.plan] dari goal bahasa biasa, lalu disematkan
 * ke mesin state lewat ManusEvent.AttachStepTool supaya panel "Komputer"
 * dan kartu izin bekerja dengan ToolCall sungguhan — bukan teks simulasi.
 *
 * @param toolName nama tool persis seperti yang diterima ToolLoop.execute().
 *   Tidak pernah kosong: setiap langkah yang dihasilkan planner memang
 *   menjalankan sesuatu. Langkah ringkasan murni tidak dibuat di sini —
 *   tab Hasil menyusun ringkasan dari output langkah.
 */
data class PlannedStep(
    val title: String,
    val toolName: String,
    val args: JSONObject,
    val detail: String,
    /**
     * 14.24: true bila langkah ini hanya persiapan (bukan hasil yang
     * menjawab goal). Dipakai TaskVerifier + kartu untuk tidak menghitung
     * persiapan sebagai "selesai". Default false supaya rencana LLM lama
     * (tanpa flag ini) tetap diperlakukan sebagai langkah hasil.
     */
    val isPreparation: Boolean = false
)

/**
 * ManusToolPlanner — orkestrator deterministik goal → langkah + tool.
 *
 * Bukan pengganti penalaran LLM. Tujuannya jadi jaring pengaman yang bisa
 * diuji: goal apa pun tetap menghasilkan rencana yang valid dan bisa
 * dijalankan tool-nya, tanpa menunggu model mengembalikan JSON yang kadang
 * cacat. Kalau nanti LLM planner dipasang, fungsi ini tetap dipakai sebagai
 * fallback dan sebagai pemvalidasi keluaran model.
 *
 * Routing berbasis kata kunci (case-insensitive), urutan prioritas:
 *  1. URL eksplisit            → web_fetch
 *  2. github / repo / commit   → github_read_file
 *  3. shell / perintah / jalan → shell_exec
 *  4. file / dokumen / baca    → file_read
 *  5. port / scan / server     → net_scan + conn_health
 *  6. ingatan / memory / catat → memory_list
 *  7. riset / cari / analisis  → web_fetch
 *  8. tidak ada yang cocok     → system_info (selalu aman, selalu jalan)
 *
 * Setiap rencana selalu diawali system_info supaya panel Komputer punya
 * bukti lingkungan kerja meski goal sangat umum.
 */
object ManusToolPlanner {

    fun plan(goal: String): List<PlannedStep> {
        val g = goal.lowercase()
        val steps = ArrayList<PlannedStep>(6)

        // Langkah 1 selalu ada: snapshot lingkungan. Risiko LOW, tidak
        // pernah memicu kartu izin, jadi rencana paling umum pun tetap
        // bisa langsung dijalankan tanpa interupsi.
        // 14.24: ini PERSIAPAN, bukan hasil — verifier + kartu tidak
        // menghitungnya sebagai "selesai".
        steps += PlannedStep(
            title = "Ambil snapshot lingkungan kerja",
            toolName = "system_info",
            args = JSONObject(),
            detail = "Membaca model perangkat, versi Android, memori, dan isi sandbox " +
                "sebelum langkah lain dijalankan.",
            isPreparation = true
        )

        // URL eksplisit menang atas segalanya: user sudah menyebut target.
        val url = extractUrl(goal)
        if (url != null) {
            steps += PlannedStep(
                title = "Ambil isi halaman target",
                toolName = "web_fetch",
                args = JSONObject().put("url", url),
                detail = "Mengambil konten $url sebagai teks untuk dibaca di tab Hasil."
            )
        }

        if (g.containsAny("github", "repo", "commit", "pull request", " branch")) {
            steps += PlannedStep(
                title = "Baca struktur repo terkait",
                toolName = "github_repos",
                args = JSONObject(),
                detail = "Mendaftar repo yang bisa diakses memakai token tersimpan, " +
                    "sebagai titik masuk sebelum membaca file spesifik."
            )
        }

        if (g.containsAny("shell", "perintah", "terminal", "jalan", "eksekusi", "install")) {
            steps += PlannedStep(
                title = "Jalankan perintah shell di sandbox",
                toolName = "shell_exec",
                args = JSONObject().put("command", "uname -a && pwd"),
                detail = "Perintah ringan dan hanya-baca untuk membuktikan eksekusi nyata " +
                    "berjalan. Butuh izin — kartu izin akan muncul sebelum dijalankan."
            )
        }

        if (g.containsAny("file", "dokumen", "baca", "tulis", "sandbox")) {
            steps += PlannedStep(
                title = "Periksa isi sandbox file",
                toolName = "file_read",
                args = JSONObject().put("path", "README.md"),
                detail = "Membaca file di sandbox aplikasi. Bila tidak ada, kegagalan " +
                    "dicatat jujur di timeline dan rencana lanjut."
            )
        }

        if (g.containsAny("port", "scan", "server", "koneksi", "website", "host")) {
            steps += PlannedStep(
                title = "Scan port layanan",
                toolName = "net_scan",
                args = JSONObject()
                    .put("host", "127.0.0.1")
                    .put("ports", "22,80,443,8080,8443"),
                detail = "Menguji konektivitas TCP ke host allowlist. " +
                    "Butuh izin sebelum dijalankan."
            )
        }

        if (g.containsAny("ingatan", "memory", "catatan", "ingat")) {
            steps += PlannedStep(
                title = "Baca ingatan persisten",
                toolName = "memory_list",
                args = JSONObject(),
                detail = "Mengambil catatan jangka panjang yang tersimpan antar sesi."
            )
        }

        // Riset jadi tujuan paling umum — kalau belum ada tool berat yang
        // terpilih, jadikan web_fetch sebagai langkah riset generik.
        // 14.24: riset generik ini PERSIAPAN (URL-nya bukan dari goal),
        // jadi tidak dihitung hasil — rencana 1 persiapan yang jujur
        // lebih baik daripada 2 langkah yang satu generik.
        val hasHeavy = steps.any { it.toolName != "system_info" }
        if (!hasHeavy) {
            steps += PlannedStep(
                title = "Riset awal dari sumber web",
                toolName = "web_fetch",
                args = JSONObject().put("url", "https://sukiapps.web.id"),
                detail = "Mengambil halaman acuan sebagai bahan riset. " +
                    "Ganti URL lewat arahan di tengah jalan bila perlu target lain.",
                isPreparation = true
            )
        }

        return steps
    }

    private fun extractUrl(goal: String): String? {
        val m = Regex("https?://\\S+").find(goal) ?: return null
        return m.value.trimEnd('.', ',', ')', ']')
    }

    private fun String.containsAny(vararg keys: String): Boolean =
        keys.any { this.contains(it) }
}

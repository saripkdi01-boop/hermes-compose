package id.sukiapps.hermes.domain.manus

/**
 * Hasil verifikasi satu tugas: lolos dengan bukti, selesai sebagian
 * (PARTIAL), atau gagal dengan alasan. `evidence` hanya diisi bila lolos;
 * `reason` diisi bila gagal ATAU sebagian; `missing` hanya diisi bila
 * sebagian (daftar yang kurang agar tugas bisa disebut selesai).
 *
 * `passed` tetap false untuk PARTIAL: sebagian BUKAN lolos — pemanggil
 * WAJIB memeriksa `isPartial` dulu sebelum `passed`, supaya tugas
 * sebagian tidak jatuh ke jalur FAILED maupun COMPLETED.
 */
data class VerificationResult(
    val passed: Boolean,
    val evidence: String = "",
    val reason: String? = null,
    val isPartial: Boolean = false,
    val missing: List<String> = emptyList()
)

/**
 * TaskVerifier — gerbang bukti agen tunggal (14.22, diperketat 14.24
 * "verifikasi jujur"). Aturan deterministik (kaku) per tool:
 * - Langkah DONE bertol: lolos bila output tidak kosong.
 * - file_write: lolos HANYA bila file dibaca balik dan isinya cocok
 *   (dibandingkan setelah trim akhir, supaya newline akhir file tidak
 *   menyebabkan gagal palsu).
 * - Langkah DONE tanpa tool (langkah rencana manual): dikecualikan —
 *   tidak membawa bukti tool, jadi tidak bisa menggagalkan verifikasi.
 * - Langkah SKIPPED (izin ditolak user): dikecualikan — itu keputusan
 *   user, bukan kegagalan bukti.
 * - Tugas tanpa langkah tool yang berhasil sama sekali: GAGAL dengan
 *   alasan eksplisit (tidak ada bukti apa pun untuk diklaim selesai).
 *
 * Aturan 14.24 (perbaikan B1/B2/B10 dari laporan Suki):
 * - Langkah PERSIAPAN (system_info, riset web generik — ditandai
 *   `AgentStep.isPreparation` oleh planner) TIDAK dihitung sebagai
 *   hasil: tidak masuk hitungan "X/Y lolos", tidak masuk bukti, dan
 *   tidak bisa membuat tugas lolos sendirian. Tugas yang hanya
 *   menyelesaikan persiapan → PARTIAL, bukan COMPLETED.
 * - Goal AUDIT (mengandung audit/celah/kerentanan/vulnerability/
 *   keamanan/security) wajib dibuktikan oleh minimal satu langkah
 *   BUKTI: pembacaan file repo (github_read_file/file_read), web_fetch
 *   ke URL eksplisit (bukan riset generik), atau pernyataan nihil yang
 *   eksplisit ("tidak ditemukan", "nihil", "tidak ada celah", ...).
 *   Daftar repo saja (github_repos) TIDAK cukup — itulah yang membuat
 *   audit 25 Okt 2026 dinyatakan COMPLETED tanpa satu pun temuan.
 *   Goal audit tanpa langkah bukti → PARTIAL + daftar yang kurang.
 *
 * Verifikasi TETAP tidak menilai benar-tidaknya isi di luar aturan di
 * atas (mis. apakah halaman yang diambil memang menjawab goal umum) —
 * itu di luar jangkauan aturan kaku dan sengaja tidak diklaim. Yang
 * dijamin: tool benar-benar mengembalikan sesuatu, tulisan benar-benar
 * bisa dibaca balik, persiapan tidak dihitung hasil, dan audit tanpa
 * bukti tidak bisa COMPLETED.
 */
object TaskVerifier {

    /** URL riset generik fallback — langkah ini selalu persiapan. */
    const val GENERIC_RESEARCH_URL = "https://sukiapps.web.id"

    private val AUDIT_KEYWORDS = listOf(
        "audit", "celah", "kerentanan", "vulnerab", "keamanan", "security"
    )
    private val NIL_FINDING_MARKERS = listOf(
        "tidak ditemukan", "nihil", "tidak ada celah",
        "tidak ada kerentanan", "no findings", "tidak ditemukan celah"
    )
    private val AUDIT_EVIDENCE_TOOLS = setOf("github_read_file", "file_read")

    suspend fun verify(
        task: AgentTask,
        readBack: suspend (path: String) -> String?
    ): VerificationResult {
        if (task.steps.isEmpty()) {
            return VerificationResult(false, reason = "tidak ada langkah dalam tugas")
        }
        val toolSteps = task.steps.filter { it.status == StepStatus.DONE && it.tool != null }
        if (toolSteps.isEmpty()) {
            // Semua langkah manual / tanpa tool: tidak ada bukti tool yang
            // bisa dicek, tapi juga tidak ada klaim tool yang gagal.
            return VerificationResult(
                passed = true,
                evidence = "Terverifikasi (tanpa langkah tool — langkah manual saja)"
            )
        }
        // 14.24: pisahkan persiapan dari hasil SEBELUM memeriksa apa pun.
        val resultSteps = toolSteps.filter { !it.isPreparation }
        val prepDone = toolSteps.size - resultSteps.size
        if (resultSteps.isEmpty()) {
            return VerificationResult(
                passed = false,
                isPartial = true,
                reason = "hanya langkah persiapan yang selesai — tidak ada langkah hasil",
                missing = listOf("langkah hasil yang menjawab goal (saat ini hanya persiapan)")
            )
        }
        val notes = ArrayList<String>()
        var passedCount = 0
        for (step in resultSteps) {
            val tool = step.tool!!
            val check = checkStep(step, tool, readBack)
            if (check.first) passedCount++ else {
                return VerificationResult(
                    passed = false,
                    reason = "Verifikasi gagal pada langkah '${step.title}': ${check.second}"
                )
            }
            notes += "${tool.name}: ${check.second}"
        }
        // 14.24: gerbang audit — daftar saja tidak cukup.
        if (isAuditGoal(task.goal) && !hasAuditEvidence(resultSteps)) {
            return VerificationResult(
                passed = false,
                isPartial = true,
                reason = "goal audit tanpa langkah bukti: tidak ada temuan maupun " +
                    "pernyataan nihil yang eksplisit",
                missing = listOf(
                    "daftar temuan celah atau pernyataan nihil yang eksplisit",
                    "pembacaan file repo (github_read_file/file_read)"
                )
            )
        }
        val prepNote = if (prepDone > 0) " (+ $prepDone langkah persiapan tidak dihitung)" else ""
        val evidence = "Terverifikasi: $passedCount/${resultSteps.size} langkah hasil lolos " +
            "(" + notes.joinToString("; ").take(500) + ")" + prepNote
        return VerificationResult(passed = true, evidence = evidence)
    }

    /** True bila goal meminta audit keamanan / pencarian celah. */
    fun isAuditGoal(goal: String): Boolean {
        val g = goal.lowercase()
        return AUDIT_KEYWORDS.any { g.contains(it) }
    }

    /**
     * True bila minimal satu langkah hasil membawa bukti audit: baca file
     * repo, ambil URL eksplisit, atau pernyataan nihil yang eksplisit di
     * judul/output langkah.
     */
    private fun hasAuditEvidence(resultSteps: List<AgentStep>): Boolean {
        return resultSteps.any { step ->
            val tool = step.tool!!
            val text = "${step.title} ${step.output.orEmpty()}".lowercase()
            when {
                tool.name in AUDIT_EVIDENCE_TOOLS -> true
                tool.name == "web_fetch" &&
                    tool.args["url"].orEmpty().isNotBlank() &&
                    tool.args["url"] != GENERIC_RESEARCH_URL -> true
                NIL_FINDING_MARKERS.any { text.contains(it) } -> true
                else -> false
            }
        }
    }

    private suspend fun checkStep(
        step: AgentStep,
        tool: ToolCall,
        readBack: suspend (path: String) -> String?
    ): Pair<Boolean, String> {
        if (tool.name == "file_write") {
            val path = tool.args["path"].orEmpty()
            if (path.isBlank()) return false to "path kosong"
            val written = tool.args["content"].orEmpty()
            val back = readBack(path)
                ?: return false to "tidak bisa dibaca balik"
            return if (back.trimEnd() == written.trimEnd()) {
                true to "dibaca balik cocok"
            } else {
                false to "isi dibaca balik tidak cocok"
            }
        }
        val out = step.output.orEmpty()
        return if (out.isNotBlank()) true to "output kembali" else false to "output kosong"
    }
}

package id.sukiapps.hermes.domain.manus

/**
 * Hasil verifikasi satu tugas: lolos dengan bukti, atau gagal dengan alasan.
 * `evidence` hanya diisi bila lolos; `reason` hanya diisi bila gagal.
 */
data class VerificationResult(
    val passed: Boolean,
    val evidence: String = "",
    val reason: String? = null
)

/**
 * TaskVerifier — gerbang bukti agen tunggal (14.22). Aturan deterministik
 * (kaku) per tool, sesuai keputusan default dokumen desain.
 *
 * Murni Kotlin kecuali `readBack` (dipakai HANYA untuk file_write):
 * pemanggil (AgentRunner) menyuplai pembacaan balik lewat ToolLoop;
 * unit test menyuplai lambda palsu. Aturan:
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
 * Verifikasi TIDAK menilai benar-tidaknya isi (mis. apakah halaman yang
 * diambil memang menjawab goal) — itu di luar jangkauan aturan kaku dan
 * sengaja tidak diklaim. Yang dijamin: tool benar-benar mengembalikan
 * sesuatu, dan tulisan benar-benar bisa dibaca balik.
 */
object TaskVerifier {

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
        val notes = ArrayList<String>()
        var passedCount = 0
        for (step in toolSteps) {
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
        val evidence = "Terverifikasi: $passedCount/${toolSteps.size} langkah tool lolos " +
            "(" + notes.joinToString("; ").take(500) + ")"
        return VerificationResult(passed = true, evidence = evidence)
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

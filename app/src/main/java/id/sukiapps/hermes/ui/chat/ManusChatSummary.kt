package id.sukiapps.hermes.ui.chat

import id.sukiapps.hermes.domain.manus.AgentTask
import id.sukiapps.hermes.domain.manus.StepStatus
import id.sukiapps.hermes.domain.manus.TaskStatus

/**
 * Ringkasan tugas Manus untuk ditempel sebagai pesan asisten di riwayat chat.
 * Murni Kotlin (tanpa Compose) supaya bisa di-unit-test di JVM.
 *
 * Dipakai saat tugas yang dimulai dari chat utama selesai/gagal/dibatalkan:
 * hasil akhir tetap tercatat di percakapan, bukan hanya di tab Agent.
 *
 * 14.24 ("verifikasi jujur", B1/B2/B10):
 * - Hitungan memakai langkah HASIL saja: langkah persiapan (snapshot,
 *   riset generik) tidak dihitung "selesai" dan output-nya tidak ditempel
 *   sebagai hasil. Bila ada persiapan, hitungan membawa suffix
 *   "(+ N persiapan)" supaya tidak ada yang hilang diam-diam.
 * - Label rencana selalu dicantumkan bila ada — ringkasan lama tidak
 *   menyebutnya sama sekali, sehingga tugas fallback bisa dibaca sebagai
 *   tugas LLM.
 * - Tugas fallback yang COMPLETED membawa peringatan yang sama
 *   menonjolnya dengan labelnya ("tanpa LLM — hasil belum tentu
 *   tuntas"), supaya kartu tidak sekaligus berkata "tanpa LLM" dan
 *   "TERVERIFIKASI" tanpa kualifikasi.
 * - Tugas PARTIAL menampilkan alasan + daftar yang kurang, bukan klaim
 *   selesai.
 */
fun manusSummary(task: AgentTask): String {
    val done = task.resultDoneCount
    val skipped = task.steps.count { it.status == StepStatus.SKIPPED }
    val failed = task.steps.count { it.status == StepStatus.FAILED }
    val prep = task.preparationCount
    val head = StringBuilder()
        .append("🤖 Manus selesai: ${task.goal}\n")
        .append("Status: ${task.status} • $done selesai, $skipped dilewati, ")
        .append("$failed gagal, dari ${task.resultStepCount} langkah")
    if (prep > 0) head.append(" (+ $prep persiapan)")
    val extra = ArrayList<String>()
    // 14.28 (penanda sumber): ringkasan membawa label sumber eksplisit
    // supaya tidak bisa dibaca sebagai balasan chat (LLM).
    extra += "Sumber: Ringkasan agen (output tool)"
    if (task.planLabel.isNotBlank()) extra += "Rencana: ${task.planLabel}"
    // 14.27 (B8): usage di ringkasan — asli bila provider mengirimnya.
    task.usageLine()?.let { extra += it }
    if (task.status == TaskStatus.STOPPED && !task.stopReason.isNullOrBlank()) {
        extra += "Alasan berhenti: ${task.stopReason}"
    }
    if (task.status == TaskStatus.COMPLETED && task.isFallbackPlan) {
        extra += "⚠ Rencana fallback (tanpa LLM) — hasil belum tentu tuntas, periksa kembali."
    }
    if (task.status == TaskStatus.PARTIAL) {
        if (!task.partialReason.isNullOrBlank()) extra += "Alasan: ${task.partialReason}"
        if (task.missingItems.isNotEmpty()) {
            extra += "Yang kurang: ${task.missingItems.joinToString("; ")}"
        }
    }
    val lines = task.steps
        .filter { it.status == StepStatus.DONE && !it.isPreparation && !it.output.isNullOrBlank() }
        .map { step ->
            val out = step.output!!.replace('\n', ' ').take(200)
            "- ${step.title}: $out"
        }
    val out = StringBuilder(head)
    for (line in extra) out.append("\n").append(line)
    if (lines.isNotEmpty()) out.append("\n").append(lines.joinToString("\n"))
    return out.toString()
}

/** Ikon status langkah untuk kartu timeline inline di chat. */
fun manusStepIcon(status: StepStatus): String = when (status) {
    StepStatus.PENDING -> "⏳"
    StepStatus.RUNNING -> "▶"
    StepStatus.DONE -> "✓"
    StepStatus.FAILED -> "✗"
    StepStatus.BLOCKED -> "⛔"
    StepStatus.SKIPPED -> "⊘"
}

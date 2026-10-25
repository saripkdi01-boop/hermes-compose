package id.sukiapps.hermes.ui.chat

import id.sukiapps.hermes.domain.manus.AgentTask
import id.sukiapps.hermes.domain.manus.StepStatus

/**
 * Ringkasan tugas Manus untuk ditempel sebagai pesan asisten di riwayat chat.
 * Murni Kotlin (tanpa Compose) supaya bisa di-unit-test di JVM.
 *
 * Dipakai saat tugas yang dimulai dari chat utama selesai/gagal/dibatalkan:
 * hasil akhir tetap tercatat di percakapan, bukan hanya di tab Agent.
 */
fun manusSummary(task: AgentTask): String {
    val done = task.steps.count { it.status == StepStatus.DONE }
    val skipped = task.steps.count { it.status == StepStatus.SKIPPED }
    val failed = task.steps.count { it.status == StepStatus.FAILED }
    val head = StringBuilder()
        .append("🤖 Manus selesai: ${task.goal}\n")
        .append("Status: ${task.status} • $done selesai, $skipped dilewati, ")
        .append("$failed gagal, dari ${task.steps.size} langkah")
    val lines = task.steps
        .filter { it.status == StepStatus.DONE && !it.output.isNullOrBlank() }
        .map { step ->
            val out = step.output!!.replace('\n', ' ').take(200)
            "- ${step.title}: $out"
        }
    if (lines.isEmpty()) return head.toString()
    return head.append("\n").append(lines.joinToString("\n")).toString()
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

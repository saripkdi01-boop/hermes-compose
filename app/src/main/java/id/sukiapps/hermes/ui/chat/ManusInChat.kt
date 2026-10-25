package id.sukiapps.hermes.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.sukiapps.hermes.domain.manus.AgentTask
import id.sukiapps.hermes.domain.manus.StepStatus
import id.sukiapps.hermes.domain.manus.TaskStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Label status tugas dalam Bahasa Indonesia yang jujur — dipakai pil chat,
 * kartu inline, dan tab Agent supaya satu tugas tidak disebut berbeda di
 * tempat berbeda. Ikon + teks, bukan warna saja (aksesibilitas).
 */
fun agentTaskStatusLabel(status: TaskStatus): String = when (status) {
    TaskStatus.IDLE -> "⏸ Siap"
    TaskStatus.PLANNING -> "⏳ Merencanakan"
    TaskStatus.RUNNING -> "▶ BERJALAN"
    TaskStatus.WAITING_PERMISSION -> "⛔ MENUNGGU IZIN"
    TaskStatus.PAUSED -> "⏸ Dijeda"
    TaskStatus.VERIFYING -> "🔍 Memverifikasi bukti"
    TaskStatus.COMPLETED -> "✓ TERVERIFIKASI"
    TaskStatus.PARTIAL -> "◐ SELESAI SEBAGIAN — hasil belum tentu tuntas"
    TaskStatus.FAILED -> "✗ GAGAL"
    TaskStatus.CANCELLED -> "⊘ Dibatalkan"
    TaskStatus.STOPPED -> "⚠ TERHENTI — bukti gagal"
}

/**
 * Kartu timeline agen di chat utama — RINGKAS (14.22).
 *
 * Prinsip: chat menampilkan goal, progres x/N, langkah yang sedang berjalan
 * (+ "masih berjalan: <tool> sejak <waktu>"), dan tombol aksi. SEMUA langkah
 * + argumen persis + output penuh + bukti verifikasi + riwayat izin ada di
 * tab Agent ("Buka di Agent"). Kartu lama menampilkan semua langkah dengan
 * output 300 karakter — itu detail tab Agent yang bocor ke chat.
 */
@Composable
fun ManusTaskCard(
    task: AgentTask,
    onCancel: () -> Unit,
    onOpenAgent: () -> Unit,
    onDismissFinished: () -> Unit,
    onResume: () -> Unit = {},
    showResume: Boolean = false,
    modifier: Modifier = Modifier
) {
    val done = task.resultDoneCount
    val skipped = task.steps.count { it.status == StepStatus.SKIPPED }
    val failed = task.steps.count { it.status == StepStatus.FAILED }
    val prep = task.preparationCount
    // 14.24: hitungan jujur — persiapan tidak dihitung "selesai".
    val countText = "${agentTaskStatusLabel(task.status)} • $done selesai, " +
        "$skipped dilewati, $failed gagal, dari ${task.resultStepCount} langkah" +
        if (prep > 0) " (+ $prep persiapan)" else ""
    val runningStep = task.steps.firstOrNull { it.status == StepStatus.RUNNING }
        ?: task.steps.firstOrNull { it.status == StepStatus.PENDING }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "🤖 Agen: ${task.goal}",
                style = MaterialTheme.typography.titleSmall
            )
            if (task.planLabel.isNotBlank()) {
                Text(
                    text = "Rencana: ${task.planLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 14.27 (B8): baris usage — asli bila provider mengirim
            // usage, estimasi kasar bila tidak. Tidak pernah tampil
            // seolah presisi.
            task.usageLine()?.let { usage ->
                Text(
                    text = usage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = countText,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { task.progress },
                modifier = Modifier.fillMaxWidth()
            )
            // Langkah berjalan: tool + waktu mulai, supaya "masih berjalan"
            // itu terukur, bukan sekadar spinner.
            if (runningStep != null && task.status.isActive) {
                Spacer(modifier = Modifier.height(6.dp))
                val toolText = runningStep.tool?.let {
                    "${manusStepIcon(runningStep.status)} ${it.name}"
                } ?: "⏳ ${runningStep.title}"
                val startedAt = SimpleDateFormat("HH:mm", Locale("id", "ID"))
                    .format(Date(runningStep.startedAt.takeIf { it > 0 } ?: task.updatedAt))
                Text(
                    text = "Masih berjalan: $toolText (mulai $startedAt)",
                    style = MaterialTheme.typography.bodySmall
                )
                if (runningStep.tool != null) {
                    Text(
                        text = runningStep.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Hasil verifikasi / alasan berhenti — jujur, bukan hilang.
            // 14.24: TERVERIFIKASI hanya untuk COMPLETED. Tugas fallback
            // yang COMPLETED tetap membawa peringatan fallback yang sama
            // menonjolnya dengan label rencana (B2); tugas PARTIAL
            // menampilkan daftar yang kurang, bukan bukti lolos.
            if (task.status == TaskStatus.COMPLETED && task.isFallbackPlan) {
                Text(
                    text = "⚠ Rencana fallback (tanpa LLM) — hasil belum tentu tuntas, periksa kembali.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (task.status == TaskStatus.COMPLETED && !task.verificationEvidence.isNullOrBlank()) {
                Text(
                    text = task.verificationEvidence!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (task.status == TaskStatus.PARTIAL) {
                if (!task.partialReason.isNullOrBlank()) {
                    Text(
                        text = "Alasan: ${task.partialReason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (task.missingItems.isNotEmpty()) {
                    Text(
                        text = "Yang kurang: ${task.missingItems.joinToString("; ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (task.status == TaskStatus.STOPPED && !task.stopReason.isNullOrBlank()) {
                Text(
                    text = "Alasan: ${task.stopReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (task.status.isActive) {
                Text(
                    text = "Saklar Mode Agen hanya mengatur kiriman baru — tugas ini tetap berjalan sampai selesai atau dibatalkan.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (task.status.isActive) {
                    OutlinedButton(onClick = onCancel) { Text("Batal") }
                }
                if (showResume && (task.status == TaskStatus.STOPPED || task.status.isActive)) {
                    Button(onClick = onResume) { Text("Lanjutkan") }
                }
                OutlinedButton(onClick = onOpenAgent) { Text("Buka di Agent") }
                if (task.isFinished) {
                    TextButton(onClick = onDismissFinished) { Text("Tutup") }
                }
            }
        }
    }
}

package id.sukiapps.hermes.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
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

/**
 * Kartu timeline tugas Manus INLINE di percakapan chat utama.
 *
 * Memakai ulang state mesin Manus yang sama (AgentTask dari ManusViewModel),
 * bukan mesin baru: daftar langkah + status tiap langkah + output singkat
 * + hasil akhir. Tombol Batal memanggil cancelTask yang sama dengan tab
 * Agent; tombol "Buka di Agent" hanya pindah layar, bukan menyalin state.
 *
 * Mematikan saklar Mode Manus TIDAK membatalkan tugas yang sedang berjalan
 * (saklar hanya mengatur kiriman baru) — keterangan itu ditulis di kartu
 * saat tugas masih aktif, supaya user tidak mengira tugasnya ikut berhenti.
 */
@Composable
fun ManusTaskCard(
    task: AgentTask,
    onCancel: () -> Unit,
    onOpenAgent: () -> Unit,
    onDismissFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val done = task.steps.count { it.status == StepStatus.DONE }
    val skipped = task.steps.count { it.status == StepStatus.SKIPPED }
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("🤖 Manus: ${task.goal}", style = MaterialTheme.typography.titleSmall)
            Text(
                "Status: ${task.status} • $done selesai, $skipped dilewati, " +
                    "dari ${task.steps.size} langkah",
                style = MaterialTheme.typography.bodySmall
            )
            LinearProgressIndicator(
                progress = { task.progress },
                modifier = Modifier.fillMaxWidth()
            )
            task.steps.forEach { step ->
                Column {
                    Text(
                        "${manusStepIcon(step.status)} ${step.title}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    step.tool?.let {
                        Text(
                            it.summary(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    step.output?.let {
                        Text(
                            it.replace('\n', ' ').take(300),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    step.error?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            if (task.status.isActive) {
                Text(
                    "Saklar Mode Manus hanya mengatur kiriman baru — tugas ini " +
                        "tetap berjalan sampai selesai atau dibatalkan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (task.status.isActive) {
                    OutlinedButton(onClick = onCancel) { Text("Batal") }
                }
                OutlinedButton(onClick = onOpenAgent) { Text("Buka di Agent") }
                if (task.status == TaskStatus.COMPLETED ||
                    task.status == TaskStatus.FAILED ||
                    task.status == TaskStatus.CANCELLED
                ) {
                    TextButton(onClick = onDismissFinished) { Text("Tutup") }
                }
            }
        }
    }
}

package id.sukiapps.hermes.ui.manus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import id.sukiapps.hermes.domain.manus.TaskStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
/**
 * Layar Manus — workspace agent:
 * - input goal + rencana (satu langkah per baris)
 * - kartu task aktif: status, progress, tombol Mulai / Jeda / Lanjut / Batal
 * - daftar langkah dengan tool call dan status tiap langkah
 * - riwayat task selesai + tombol bersihkan
 * - timeline log append-only
 * - dialog izin blocking saat agent minta aksi berisiko
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManusScreen(
    onBack: () -> Unit,
    vm: ManusViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    var goalInput by remember { mutableStateOf("") }
    var planInput by remember { mutableStateOf("") }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manus Engine") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("‹ Kembali") }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = goalInput,
                    onValueChange = { goalInput = it },
                    label = { Text("Goal") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = planInput,
                    onValueChange = { planInput = it },
                    label = { Text("Rencana — satu langkah per baris") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Button(
                    onClick = {
                        val plan = planInput.lines().filter { it.isNotBlank() }
                        if (goalInput.isNotBlank() && plan.isNotEmpty()) {
                            vm.submitGoal(goalInput, plan)
                            goalInput = ""
                            planInput = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Kirim Goal") }
            }
            state.activeTask?.let { task ->
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                task.goal,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Status: ${task.status}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Progress: ${task.doneCount}/${task.steps.size} langkah",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (task.status == TaskStatus.IDLE) {
                                    Button(onClick = { vm.startTask(task.id) }) {
                                        Text("Mulai")
                                    }
                                }
                                if (task.status == TaskStatus.RUNNING) {
                                    OutlinedButton(onClick = { vm.pauseTask(task.id) }) {
                                        Text("Jeda")
                                    }
                                }
                                if (task.status == TaskStatus.PAUSED) {
                                    OutlinedButton(onClick = { vm.resumeTask(task.id) }) {
                                        Text("Lanjut")
                                    }
                                }
                                if (task.status.isActive) {
                                    OutlinedButton(onClick = { vm.cancelTask(task.id) }) {
                                        Text("Batal")
                                    }
                                }
                                OutlinedButton(onClick = { vm.finishActiveStep() }) {
                                    Text("Selesai Langkah")
                                }
                            }
                        }
                    }
                }
                items(task.steps, key = { it.id }) { step ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(step.title, style = MaterialTheme.typography.bodyLarge)
                            step.tool?.let { tool ->
                                Text(
                                    tool.summary(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                "Status: ${step.status}",
                                style = MaterialTheme.typography.labelSmall
                            )
                            step.error?.let {
                                Text(
                                    "Error: $it",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
            if (state.history.isNotEmpty()) {
                item {
                    Text("Riwayat", style = MaterialTheme.typography.titleMedium)
                }
                items(state.history, key = { it.id }) { t ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(t.goal, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${t.status} — ${t.doneCount}/${t.steps.size}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                item {
                    TextButton(onClick = { vm.clearHistory() }) {
                        Text("Bersihkan Riwayat")
                    }
                }
            }
            item {
                Text("Log", style = MaterialTheme.typography.titleMedium)
            }
            items(state.logs, key = { it.id }) { log ->
                Text(
                    "[${timeFmt.format(Date(log.timestamp))}] ${log.level}: ${log.message}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
    state.pendingPermission?.let { req ->
        AlertDialog(
            onDismissRequest = { vm.denyPermission(req.taskId) },
            title = { Text("Izin Diperlukan") },
            text = {
                Column {
                    Text("Tool: ${req.tool.summary()}")
                    Text("Alasan: ${req.reason}")
                    Text("Risiko: ${req.risk}")
                }
            },
            confirmButton = {
                Button(onClick = { vm.grantPermission(req.taskId) }) {
                    Text("Izinkan")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { vm.denyPermission(req.taskId) }) {
                    Text("Tolak")
                }
            }
        )
    }
}

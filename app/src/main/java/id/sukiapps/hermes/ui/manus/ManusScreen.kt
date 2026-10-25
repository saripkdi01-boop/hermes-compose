package id.sukiapps.hermes.ui.manus
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import id.sukiapps.hermes.domain.manus.StepStatus
import id.sukiapps.hermes.domain.manus.TaskStatus
import id.sukiapps.hermes.domain.manus.risk
import id.sukiapps.hermes.ui.chat.agentTaskStatusLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
/**
 * Layar Manus — workspace agent gaya Manus, diadaptasi ke HP:
 *
 * - Strip status LIVE selalu terlihat di atas (task aktif tidak pernah hilang).
 * - Tab [Rencana | Komputer | Hasil] menggantikan layout 2-panel desktop:
 *   di HP satu kolom, tapi tidak ada panel yang hilang total.
 * - Rencana: checklist langkah HIDUP — klik langkah untuk melihat detailnya,
 *   pill aksi melompat ke bukti di tab Komputer.
 * - Komputer: sub-tab Browser / Terminal / File untuk langkah yang dipilih.
 *   EKSEKUSI NYATA SUDAH TERSAMBUNG: output di tab Terminal berasal dari
 *   tool asli (web_fetch, shell_exec, file_read, github_read_file, dll)
 *   yang dipanggil AgentRunner — bukan simulasi.
 * - Hasil: deliverable, sumber, dan riwayat yang bisa dibuka ulang (replay)
 *   karena state tersimpan di perangkat (lihat ManusViewModel).
 * - Kartu izin: dialog blocking dengan perintah persis, target persis,
 *   scope sekali-saja, dan rollback. Menolak = langkah dilewati, tugas lanjut.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManusScreen(
    onBack: () -> Unit,
    // Activity-scope: SATU instance ManusViewModel untuk tab Agent DAN chat
    // utama, sehingga tugas yang dimulai dari chat terlihat di sini (dan
    // sebaliknya) lewat StateFlow yang sama, bukan hanya lewat file
    // "manus_state" saat layar dibuat ulang.
    vm: ManusViewModel = hiltViewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity
    )
) {
    val state by vm.state.collectAsState()
    // 14.22: tugas restore yang menggantung tanpa pelaksana.
    val resumeNeeded by vm.resumeNeeded.collectAsState()
    var goalInput by remember { mutableStateOf("") }
    var planInput by remember { mutableStateOf("") }
    var steerInput by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    var compTab by remember { mutableStateOf(0) }
    val task = state.activeTask
    var selectedStepId by remember(task?.id) { mutableStateOf<String?>(null) }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    val tabs = listOf("Rencana", "Komputer", "Hasil")
    val compTabs = listOf("Browser", "Terminal", "File")
    val selectedStep = task?.steps?.firstOrNull { it.id == selectedStepId }
        ?: task?.steps?.firstOrNull { it.status == StepStatus.RUNNING }
        ?: task?.steps?.firstOrNull { it.status == StepStatus.PENDING }
        ?: task?.steps?.firstOrNull()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agen") },
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
            // ---- Strip status live: selalu terlihat, di tab apa pun. ----
            task?.let { t ->
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(t.goal, style = MaterialTheme.typography.titleMedium)
                            if (t.isDemo) {
                                Text(
                                    "CONTOH bawaan — bukan tugas nyata",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            val done = t.steps.count { it.status == StepStatus.DONE }
                            val skipped = t.steps.count { it.status == StepStatus.SKIPPED }
                            Text(
                                "Status: ${agentTaskStatusLabel(t.status)} • $done selesai, $skipped dilewati, dari ${t.steps.size} langkah",
                                style = MaterialTheme.typography.bodySmall
                            )
                            // 14.22: asal rencana + bukti verifikasi + alasan
                            // berhenti — detail penuh ada di tab ini, bukan
                            // hanya di kartu ringkas chat.
                            if (t.planLabel.isNotBlank()) {
                                Text(
                                    "Rencana: ${t.planLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!t.verificationEvidence.isNullOrBlank()) {
                                Text(
                                    "Bukti: ${t.verificationEvidence}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!t.stopReason.isNullOrBlank()) {
                                Text(
                                    "Alasan berhenti: ${t.stopReason}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            val current = t.steps.firstOrNull { it.status == StepStatus.RUNNING }
                                ?: t.steps.firstOrNull { it.status == StepStatus.PENDING }
                            Text(
                                "Sedang: ${current?.title ?: "Semua langkah selesai / dilewati"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            // Indikator eksekusi nyata: tampil hanya kalau langkah RUNNING punya tool asli.
                            if (current != null && current.status == StepStatus.RUNNING && current.tool != null) {
                                Text(
                                    "⚙ Menjalankan tool: ${current.tool.summary()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { t.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            )
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (t.status == TaskStatus.IDLE) {
                                    Button(onClick = { vm.startTask(t.id) }) { Text("Mulai") }
                                }
                                if (t.status == TaskStatus.RUNNING) {
                                    OutlinedButton(onClick = { vm.pauseTask(t.id) }) { Text("Jeda") }
                                }
                                if (t.status == TaskStatus.PAUSED) {
                                    OutlinedButton(onClick = { vm.resumeTask(t.id) }) { Text("Lanjut") }
                                }
                                // 14.22: lanjutkan dari TERHENTI (verifikasi
                                // ulang) atau dari tugas restore yang
                                // menggantung tanpa pelaksana.
                                if (t.status == TaskStatus.STOPPED) {
                                    Button(onClick = { vm.resumeStopped(t.id) }) { Text("Lanjutkan") }
                                }
                                if (resumeNeeded && t.status.isActive) {
                                    Button(onClick = { vm.resumeRestored(t.id) }) { Text("Lanjutkan") }
                                }
                                if (t.status.isActive) {
                                    OutlinedButton(onClick = { vm.cancelTask(t.id) }) { Text("Batal") }
                                }
                            }
                            if (t.status == TaskStatus.RUNNING) {
                                Row(modifier = Modifier.padding(top = 8.dp)) {
                                    OutlinedButton(onClick = { vm.finishActiveStep() }) {
                                        Text("Selesai Langkah (manual)")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { i, label ->
                        Tab(
                            selected = selectedTab == i,
                            onClick = { selectedTab = i },
                            text = { Text(label) }
                        )
                    }
                }
            }
            when (selectedTab) {
                // ---------------- TAB RENCANA ----------------
                0 -> {
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
                            label = { Text("Rencana — satu langkah per baris (opsional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Button(
                            onClick = {
                                if (goalInput.isNotBlank()) {
                                    vm.submitAndRun(goalInput)
                                    goalInput = ""
                                    planInput = ""
                                }
                            },
                            enabled = goalInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Kirim & Jalankan — rencana LLM + eksekusi tool nyata") }
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                val plan = planInput.lines().filter { it.isNotBlank() }
                                if (goalInput.isNotBlank() && plan.isNotEmpty()) {
                                    vm.submitGoal(goalInput, plan)
                                    goalInput = ""
                                    planInput = ""
                                }
                            },
                            enabled = goalInput.isNotBlank() && planInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Kirim Goal (rencana manual)") }
                    }
                    if (task != null) {
                        items(task.steps, key = { it.id }) { step ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedStepId = step.id }
                            ) {
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
                                    if (selectedStepId == step.id || step.status == StepStatus.RUNNING) {
                                        step.detail?.let {
                                            Text(it, style = MaterialTheme.typography.bodySmall)
                                        }
                                        step.output?.let {
                                            Text(
                                                "Output: $it",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        step.error?.let {
                                            Text(
                                                "Catatan: $it",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        if (step.sources.isNotEmpty()) {
                                            Text(
                                                "Sumber: ${step.sources.joinToString("; ")}",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                        if (step.artifacts.isNotEmpty()) {
                                            Text(
                                                "File: ${step.artifacts.joinToString("; ")}",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(top = 8.dp)
                                        ) {
                                            OutlinedButton(onClick = { compTab = 0; selectedTab = 1 }) {
                                                Text("Lihat di Browser")
                                            }
                                            OutlinedButton(onClick = { compTab = 1; selectedTab = 1 }) {
                                                Text("Lihat di Terminal")
                                            }
                                            OutlinedButton(onClick = { compTab = 2; selectedTab = 1 }) {
                                                Text("Lihat di File")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            OutlinedTextField(
                                value = steerInput,
                                onValueChange = { steerInput = it },
                                label = { Text("Arahkan agen di tengah jalan…") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            Button(
                                onClick = {
                                    if (steerInput.isNotBlank()) {
                                        vm.steerTask(task.id, steerInput)
                                        steerInput = ""
                                    }
                                },
                                enabled = task.status.isActive,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Kirim arahan") }
                            Text(
                                "Arahan dicatat di timeline. Arahan tidak mengubah rencana " +
                                    "diam-diam dan tidak menjalankan tool apa pun sendiri.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    item {
                        Text("Timeline log", style = MaterialTheme.typography.titleMedium)
                    }
                    items(state.logs, key = { it.id }) { log ->
                        Text(
                            "[${timeFmt.format(Date(log.timestamp))}] ${log.level}: ${log.message}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                // ---------------- TAB KOMPUTER ----------------
                1 -> {
                    item {
                        TabRow(selectedTabIndex = compTab) {
                            compTabs.forEachIndexed { i, label ->
                                Tab(
                                    selected = compTab == i,
                                    onClick = { compTab = i },
                                    text = { Text(label) }
                                )
                            }
                        }
                    }
                    item {
                        Text(
                            "Panel bukti untuk langkah: ${selectedStep?.title ?: "—"}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "Eksekusi tool NYATA sudah tersambung. Output di tab Terminal " +
                                "berasal dari tool asli (web_fetch, shell_exec, file_read, " +
                                "github_read_file, dan lainnya) yang dipanggil AgentRunner — " +
                                "bukan simulasi.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                val step = selectedStep
                                if (step == null) {
                                    Text("Belum ada langkah. Buat goal dulu di tab Rencana.")
                                } else when (compTab) {
                                    0 -> {
                                        if (step.tool?.name == "web_fetch") {
                                            Text(
                                                "Ambil halaman: ${step.tool.args["url"] ?: step.tool.summary()}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        } else if (step.tool?.name == "web_search") {
                                            Text(
                                                "Pencarian: ${step.tool.args["q"] ?: step.tool.summary()}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        } else {
                                            Text(
                                                "Halaman / bukti untuk: ${step.title}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        Text(
                                            step.detail ?: "Belum ada detail untuk langkah ini.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        if (step.sources.isNotEmpty()) {
                                            Text(
                                                "Sumber: ${step.sources.joinToString("; ")}",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                    1 -> {
                                        Text(
                                            "Perintah persis: ${step.tool?.summary() ?: "(tidak ada tool — langkah rencana biasa)"}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        step.tool?.let {
                                            Text(
                                                "Risiko: ${it.risk()}",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                        Text(
                                            "Output: ${step.output ?: "Belum ada output. Langkah ini akan dieksekusi otomatis saat task berjalan; tekan 'Selesai Langkah (manual)' di strip atas untuk menandai selesai tanpa tool."}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        step.error?.let {
                                            Text(
                                                "Catatan: $it",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                    else -> {
                                        if (step.artifacts.isEmpty()) {
                                            Text(
                                                "Belum ada file dari langkah ini.",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        } else {
                                            step.artifacts.forEach { file ->
                                                Text("• $file", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        Text(
                                            "File di sini adalah catatan dari langkah (sandbox). " +
                                                "File belum keluar ke perangkat / Drive tanpa izin.",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                // ---------------- TAB HASIL ----------------
                else -> {
                    if (task == null) {
                        item { Text("Belum ada tugas. Buat goal dulu di tab Rencana.") }
                    } else {
                        item {
                            Text("Deliverable", style = MaterialTheme.typography.titleMedium)
                            val artifacts = task.steps.flatMap { it.artifacts }.distinct()
                            if (artifacts.isEmpty()) {
                                Text(
                                    "Belum ada file deliverable dari tugas ini.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                artifacts.forEach { file ->
                                    Text("• $file", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            val doneOutputs = task.steps.filter { it.status == StepStatus.DONE && it.output != null }
                            doneOutputs.forEach { step ->
                                Text(
                                    "Output ${step.title}: ${step.output}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            val sources = task.steps.flatMap { it.sources }.distinct()
                            if (sources.isNotEmpty()) {
                                Text(
                                    "Sumber: ${sources.joinToString("; ")}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        item {
                            Text("Riwayat — ketuk untuk membuka ulang (replay)", style = MaterialTheme.typography.titleMedium)
                        }
                        if (state.history.isEmpty()) {
                            item {
                                Text(
                                    "Belum ada tugas selesai. Riwayat tersimpan di perangkat " +
                                        "dan tampil lagi setelah aplikasi dibuka ulang.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else {
                            items(state.history, key = { it.id }) { t ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { vm.selectTask(t.id); selectedTab = 0 }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(t.goal, style = MaterialTheme.typography.bodyMedium)
                                        val done = t.steps.count { it.status == StepStatus.DONE }
                                        val skipped = t.steps.count { it.status == StepStatus.SKIPPED }
                                        Text(
                                            "${t.status} — $done selesai, $skipped dilewati, dari ${t.steps.size}",
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
                    }
                }
            }
        }
    }
    // Dialog izin bersama dengan chat utama (satu kontrak, satu komponen).
    state.pendingPermission?.let { req ->
        ManusPermissionDialog(
            request = req,
            onGrant = { vm.grantPermission(req.taskId) },
            onDeny = { vm.denyPermission(req.taskId) }
        )
    }
}

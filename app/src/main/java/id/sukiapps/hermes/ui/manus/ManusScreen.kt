package id.sukiapps.hermes.ui.manus
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import id.sukiapps.hermes.domain.manus.LogEntry
import id.sukiapps.hermes.domain.manus.LogLevel
import id.sukiapps.hermes.domain.manus.StepStatus
import id.sukiapps.hermes.domain.manus.TaskStatus
import id.sukiapps.hermes.domain.manus.risk
import id.sukiapps.hermes.tool.ToolLoop
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
 *
 * Polish UI (3a–3d): ikon Material + chip status berikon/warna di semua
 * tempat, kartu "Kemampuan Agen" (daftar tool NYATA dari ToolLoop) dan
 * kartu contoh "Langganan VPS Cloud". Tidak ada perubahan logika.
 */
// Kuning peringatan yang terbaca di tema terang maupun gelap.
private val WarningAmber = Color(0xFF8A6D00)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val tabIcons = listOf(
        Icons.Filled.Checklist,
        Icons.Filled.Computer,
        Icons.Filled.FolderOpen
    )
    val compTabs = listOf("Browser", "Terminal", "File")
    val compTabIcons = listOf(
        Icons.Filled.Language,
        Icons.Filled.Terminal,
        Icons.Filled.Folder
    )
    val selectedStep = task?.steps?.firstOrNull { it.id == selectedStepId }
        ?: task?.steps?.firstOrNull { it.status == StepStatus.RUNNING }
        ?: task?.steps?.firstOrNull { it.status == StepStatus.PENDING }
        ?: task?.steps?.firstOrNull()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.SmartToy,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Agen")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
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
                            val done = t.resultDoneCount
                            val skipped = t.steps.count { it.status == StepStatus.SKIPPED }
                            val prep = t.preparationCount
                            // 3a/3d: status sebagai chip berikon + warna,
                            // hitungan langkah tetap sebagai teks.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                TaskStatusChip(t.status)
                                Text(
                                    "• $done selesai, $skipped dilewati, dari ${t.resultStepCount} langkah" +
                                        if (prep > 0) " (+ $prep persiapan)" else "",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
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
                            // 14.27 (B8): baris usage (asli/estimasi kasar).
                            t.usageLine()?.let { usage ->
                                Text(
                                    usage,
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        "Alasan berhenti: ${t.stopReason}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            if (t.status == TaskStatus.PARTIAL) {
                                if (!t.partialReason.isNullOrBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Error,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            "Alasan sebagian: ${t.partialReason}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                if (t.missingItems.isNotEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Error,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            "Yang kurang: ${t.missingItems.joinToString("; ")}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                            val current = t.steps.firstOrNull { it.status == StepStatus.RUNNING }
                                ?: t.steps.firstOrNull { it.status == StepStatus.PENDING }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    when {
                                        current?.status == StepStatus.RUNNING -> Icons.Filled.PlayArrow
                                        current?.status == StepStatus.PENDING -> Icons.Filled.Schedule
                                        else -> Icons.Filled.CheckCircle
                                    },
                                    contentDescription = null,
                                    tint = when {
                                        current?.status == StepStatus.RUNNING -> MaterialTheme.colorScheme.primary
                                        current?.status == StepStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                                        else -> MaterialTheme.colorScheme.tertiary
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "Sedang: ${current?.title ?: "Semua langkah selesai / dilewati"}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            // Indikator eksekusi nyata: tampil hanya kalau langkah RUNNING punya tool asli.
                            if (current != null && current.status == StepStatus.RUNNING && current.tool != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Settings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        "Menjalankan tool: ${current.tool.summary()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                            // 3d: progress + label persen yang terbaca.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { t.progress },
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${(t.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // FlowRow: tombol berikon membungkus ke baris
                            // berikut di HP 720px, bukan terpotong.
                            FlowRow(
                                modifier = Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (t.status == TaskStatus.IDLE) {
                                    Button(onClick = { vm.startTask(t.id) }) {
                                        Icon(
                                            Icons.Filled.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Mulai")
                                    }
                                }
                                if (t.status == TaskStatus.RUNNING) {
                                    OutlinedButton(onClick = { vm.pauseTask(t.id) }) {
                                        Icon(
                                            Icons.Filled.Pause,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Jeda")
                                    }
                                }
                                if (t.status == TaskStatus.PAUSED) {
                                    OutlinedButton(onClick = { vm.resumeTask(t.id) }) {
                                        Icon(
                                            Icons.Filled.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Lanjut")
                                    }
                                }
                                // 14.22: lanjutkan dari TERHENTI (verifikasi
                                // ulang) atau dari tugas restore yang
                                // menggantung tanpa pelaksana.
                                if (t.status == TaskStatus.STOPPED) {
                                    Button(onClick = { vm.resumeStopped(t.id) }) {
                                        Icon(
                                            Icons.Filled.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Lanjutkan")
                                    }
                                }
                                if (resumeNeeded && t.status.isActive) {
                                    Button(onClick = { vm.resumeRestored(t.id) }) {
                                        Icon(
                                            Icons.Filled.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Lanjutkan")
                                    }
                                }
                                if (t.status.isActive) {
                                    OutlinedButton(onClick = { vm.cancelTask(t.id) }) {
                                        Icon(
                                            Icons.Filled.Cancel,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Batal")
                                    }
                                }
                            }
                            if (t.status == TaskStatus.RUNNING) {
                                Row(modifier = Modifier.padding(top = 8.dp)) {
                                    OutlinedButton(onClick = { vm.finishActiveStep() }) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Selesai Langkah (manual)")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // ---- 3b: kartu Kemampuan Agen (selalu terlihat). ----
            item {
                AgentCapabilitiesCard()
            }
            // ---- 3c: kartu contoh Langganan VPS Cloud (selalu terlihat). ----
            item {
                VpsCloudCard()
            }
            item {
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { i, label ->
                        Tab(
                            selected = selectedTab == i,
                            onClick = { selectedTab = i },
                            text = { Text(label) },
                            icon = {
                                Icon(
                                    tabIcons[i],
                                    contentDescription = null
                                )
                            }
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
                        ) {
                            Icon(
                                Icons.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Kirim & Jalankan — rencana LLM + eksekusi tool nyata")
                        }
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
                        ) {
                            Icon(
                                Icons.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Kirim Goal (rencana manual)")
                        }
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
                                    // 3a: chip status berikon + warna,
                                    // bukan teks "Status: ..." mentah.
                                    Row(modifier = Modifier.padding(top = 4.dp)) {
                                        StepStatusChip(step.status)
                                    }
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
                                        // FlowRow: tiga tombol membungkus
                                        // di HP 720px, bukan terpotong.
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(top = 8.dp)
                                        ) {
                                            OutlinedButton(onClick = { compTab = 0; selectedTab = 1 }) {
                                                Icon(
                                                    Icons.Filled.Language,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text("Lihat di Browser")
                                            }
                                            OutlinedButton(onClick = { compTab = 1; selectedTab = 1 }) {
                                                Icon(
                                                    Icons.Filled.Terminal,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text("Lihat di Terminal")
                                            }
                                            OutlinedButton(onClick = { compTab = 2; selectedTab = 1 }) {
                                                Icon(
                                                    Icons.Filled.Folder,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
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
                            ) {
                                Icon(
                                    Icons.Filled.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Kirim arahan")
                            }
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
                        LogRow(log = log, timeFmt = timeFmt)
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
                                    text = { Text(label) },
                                    icon = {
                                        Icon(
                                            compTabIcons[i],
                                            contentDescription = null
                                        )
                                    }
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
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.InsertDriveFile,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(file, style = MaterialTheme.typography.bodySmall)
                                                }
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.InsertDriveFile,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(file, style = MaterialTheme.typography.bodySmall)
                                    }
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
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(t.goal, style = MaterialTheme.typography.bodyMedium)
                                            val done = t.steps.count { it.status == StepStatus.DONE }
                                            val skipped = t.steps.count { it.status == StepStatus.SKIPPED }
                                            Text(
                                                "${agentTaskStatusLabel(t.status)} — $done selesai, $skipped dilewati, dari ${t.steps.size}",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            }
                            item {
                                TextButton(onClick = { vm.clearHistory() }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
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

/** Chip kecil berikon + label (dipakai chip status langkah & tugas). */
@Composable
private fun StatusChip(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    showProgress: Boolean = false
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = contentColor
                )
            } else {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Chip status langkah: ikon + warna per StepStatus, bukan teks mentah. */
@Composable
private fun StepStatusChip(status: StepStatus) {
    val scheme = MaterialTheme.colorScheme
    when (status) {
        StepStatus.DONE -> StatusChip(
            Icons.Filled.CheckCircle, "Selesai",
            scheme.tertiaryContainer, scheme.onTertiaryContainer
        )
        StepStatus.RUNNING -> StatusChip(
            Icons.Filled.PlayArrow, "Berjalan",
            scheme.primaryContainer, scheme.onPrimaryContainer,
            showProgress = true
        )
        StepStatus.PENDING -> StatusChip(
            Icons.Filled.Schedule, "Menunggu",
            scheme.surfaceVariant, scheme.onSurfaceVariant
        )
        StepStatus.SKIPPED -> StatusChip(
            Icons.Filled.SkipNext, "Dilewati",
            scheme.surfaceVariant, scheme.onSurfaceVariant
        )
        StepStatus.FAILED -> StatusChip(
            Icons.Filled.Error, "Gagal",
            scheme.errorContainer, scheme.onErrorContainer
        )
        StepStatus.BLOCKED -> StatusChip(
            Icons.Filled.Block, "Diblokir",
            scheme.errorContainer, scheme.onErrorContainer
        )
    }
}

/** Chip status tugas: ikon + warna per TaskStatus. */
@Composable
private fun TaskStatusChip(status: TaskStatus) {
    val scheme = MaterialTheme.colorScheme
    when (status) {
        TaskStatus.IDLE -> StatusChip(
            Icons.Filled.Schedule, agentTaskStatusLabel(status),
            scheme.surfaceVariant, scheme.onSurfaceVariant
        )
        TaskStatus.PLANNING -> StatusChip(
            Icons.Filled.AutoAwesome, agentTaskStatusLabel(status),
            scheme.primaryContainer, scheme.onPrimaryContainer
        )
        TaskStatus.RUNNING -> StatusChip(
            Icons.Filled.PlayArrow, agentTaskStatusLabel(status),
            scheme.primaryContainer, scheme.onPrimaryContainer,
            showProgress = true
        )
        TaskStatus.WAITING_PERMISSION -> StatusChip(
            Icons.Filled.Lock, agentTaskStatusLabel(status),
            scheme.secondaryContainer, scheme.onSecondaryContainer
        )
        TaskStatus.PAUSED -> StatusChip(
            Icons.Filled.Pause, agentTaskStatusLabel(status),
            scheme.surfaceVariant, scheme.onSurfaceVariant
        )
        TaskStatus.VERIFYING -> StatusChip(
            Icons.Filled.Search, agentTaskStatusLabel(status),
            scheme.primaryContainer, scheme.onPrimaryContainer
        )
        TaskStatus.COMPLETED -> StatusChip(
            Icons.Filled.CheckCircle, agentTaskStatusLabel(status),
            scheme.tertiaryContainer, scheme.onTertiaryContainer
        )
        TaskStatus.FAILED -> StatusChip(
            Icons.Filled.Error, agentTaskStatusLabel(status),
            scheme.errorContainer, scheme.onErrorContainer
        )
        TaskStatus.CANCELLED -> StatusChip(
            Icons.Filled.Cancel, agentTaskStatusLabel(status),
            scheme.surfaceVariant, scheme.onSurfaceVariant
        )
        TaskStatus.PARTIAL -> StatusChip(
            Icons.Filled.Warning, agentTaskStatusLabel(status),
            scheme.secondaryContainer, scheme.onSecondaryContainer
        )
        TaskStatus.STOPPED -> StatusChip(
            Icons.Filled.Error, agentTaskStatusLabel(status),
            scheme.errorContainer, scheme.onErrorContainer
        )
    }
}

/** Satu baris timeline: ikon per level + warna, teks "[jam] level: pesan". */
@Composable
private fun LogRow(log: LogEntry, timeFmt: SimpleDateFormat) {
    val scheme = MaterialTheme.colorScheme
    val (icon, tint) = when (log.level) {
        LogLevel.DEBUG -> Icons.Filled.BugReport to scheme.onSurfaceVariant
        LogLevel.INFO -> Icons.Filled.Info to scheme.primary
        LogLevel.WARN -> Icons.Filled.Warning to WarningAmber
        LogLevel.ERROR -> Icons.Filled.Error to scheme.error
    }
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            icon,
            contentDescription = log.level.name,
            tint = tint,
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp)
        )
        Text(
            "[${timeFmt.format(Date(log.timestamp))}] ${log.level}: ${log.message}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 3b: Kartu "Kemampuan Agen" — daftar tool NYATA dari ToolLoop.toolsArray()
 * (cara ekstraksi nama sama persis dengan ManusViewModel.submitAndRun).
 * Nama tool TIDAK di-hardcode: yang tampil adalah yang benar-benar
 * tersedia saat aplikasi jalan (termasuk tool baru dari registry).
 * Ikon per nama hanya pemetaan tampilan; nama yang belum dikenal memakai
 * ikon generik + nama aslinya, bukan disembunyikan.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentCapabilitiesCard() {
    val toolNames = remember {
        runCatching {
            val toolsArr = ToolLoop.toolsArray()
            (0 until toolsArr.length()).mapNotNull { i ->
                runCatching {
                    toolsArr.getJSONObject(i).getJSONObject("function").getString("name")
                }.getOrNull()
            }
        }.getOrElse { emptyList() }
    }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text("Kemampuan Agen", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Agen ini bisa: ambil web, jalankan shell, baca/tulis file, " +
                    "pakai GitHub, Telegram, Supabase, Vercel, dan lainnya — " +
                    "sesuai izin. ${toolNames.size} tool aktif.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                toolNames.forEach { name ->
                    ToolChip(name = name)
                }
            }
        }
    }
}

/** Satu chip tool: ikon + nama asli tool. */
@Composable
private fun ToolChip(name: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                toolIconFor(name),
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Text(name, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Pemetaan tampilan nama tool → ikon. Bukan daftar tool (itu dari ToolLoop). */
private fun toolIconFor(name: String): ImageVector = when (name) {
    "web_fetch" -> Icons.Filled.Language
    "web_search" -> Icons.Filled.Search
    "shell_exec" -> Icons.Filled.Terminal
    "file_read" -> Icons.Filled.Description
    "file_write" -> Icons.Filled.Edit
    "net_scan" -> Icons.Filled.NetworkCheck
    "conn_health" -> Icons.Filled.HealthAndSafety
    "code_run" -> Icons.Filled.Code
    "code_search" -> Icons.Filled.Search
    "github_repos" -> Icons.Filled.Folder
    "github_runs" -> Icons.Filled.Timeline
    "github_read_file" -> Icons.Filled.Code
    "github_write_file" -> Icons.Filled.Edit
    "github_dispatch" -> Icons.Filled.PlayArrow
    "telegram_send" -> Icons.Filled.Send
    "upin_ask" -> Icons.Filled.QuestionAnswer
    "supabase_query" -> Icons.Filled.Storage
    "vercel_projects" -> Icons.Filled.Web
    "vercel_deployments" -> Icons.Filled.CloudUpload
    "memory_save" -> Icons.Filled.Save
    "memory_list" -> Icons.Filled.List
    "system_info" -> Icons.Filled.Info
    "calc" -> Icons.Filled.Calculate
    "time_now" -> Icons.Filled.Schedule
    "selfupgrade_branch", "selfupgrade_write_file", "selfupgrade_pr",
    "selfupgrade_ci", "selfupgrade_merge", "selfupgrade_release" ->
        Icons.Filled.AutoAwesome
    else -> Icons.Filled.Extension
}

/**
 * 3c: Kartu contoh "Langganan VPS Cloud" — HANYA tampilan, tanpa logika
 * billing. Label "Contoh" menegaskan ini bukan status langganan nyata.
 */
@Composable
private fun VpsCloudCard() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("VPS Cloud", style = MaterialTheme.typography.titleMedium)
                    StatusChip(
                        Icons.Filled.CheckCircle, "Aktif",
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        "Contoh",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Agen berjalan di cloud — tidak perlu HP menyala.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

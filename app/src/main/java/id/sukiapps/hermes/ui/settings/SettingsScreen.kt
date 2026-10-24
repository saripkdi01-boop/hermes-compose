package id.sukiapps.hermes.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import id.sukiapps.hermes.AppInfo
import id.sukiapps.hermes.BuildConfig
import id.sukiapps.hermes.ui.components.ModelPickerDialog
import java.io.File

// ============================================================
// ENI / SAE UPGRADE — SettingsScreen Redesign v3
// Tab Navigation + Adaptive Grid + Modern Section Cards
// Full backward compatible — semua logic VM tetap sama
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsState()

    // ---- STATE ----
    var showBaseUrlDialog by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showGithubPatDialog by remember { mutableStateOf(false) }
    var showGithubRepoDialog by remember { mutableStateOf(false) }
    var showGithubWorkflowDialog by remember { mutableStateOf(false) }
    var showGithubActionsDialog by remember { mutableStateOf(false) }
    var showTelegramDialog by remember { mutableStateOf(false) }
    var showTermuxDialog by remember { mutableStateOf(false) }
    var showBackupsDialog by remember { mutableStateOf(false) }
    var showDevModeDialog by remember { mutableStateOf(false) }
    var showImportMemoryDialog by remember { mutableStateOf(false) }
    var showExportMemoryDialog by remember { mutableStateOf(false) }
    var showMemoryImportMenu by remember { mutableStateOf(false) }
    var showAddModelDialog by remember { mutableStateOf(false) }
    var showSoulDialog by remember { mutableStateOf(false) }
    var showUpgradeLog by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    // ---- TAB STATE ----
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        TabItem("AI", Icons.Filled.Star),
        TabItem("Tampilan", Icons.Filled.Settings),
        TabItem("Koneksi", Icons.Filled.Share),
        TabItem("Agen", Icons.Filled.Warning),
        TabItem("Sistem", Icons.Filled.Info)
    )

    // ---- BASE URL DIALOG ----
    if (showBaseUrlDialog) {
        var baseUrl by remember { mutableStateOf(state.baseUrl) }
        AlertDialog(
            onDismissRequest = { showBaseUrlDialog = false },
            title = { Text("Server AI (Jerouter)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Endpoint OpenAI-compatible. Default: http://10.0.2.2:8766/v1 (emulator).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveBaseUrl(baseUrl)
                    showBaseUrlDialog = false
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { showBaseUrlDialog = false }) { Text("Batal") }
            }
        )
    }

    // ---- SOUL DIALOG ----
    if (showSoulDialog) {
        var soul by remember { mutableStateOf(state.soulPrompt) }
        AlertDialog(
            onDismissRequest = { showSoulDialog = false },
            title = { Text("Jiwa Hermes (System Prompt)") },
            text = {
                OutlinedTextField(
                    value = soul,
                    onValueChange = { soul = it },
                    label = { Text("Instruksi sistem") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveSoul(soul)
                    showSoulDialog = false
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { showSoulDialog = false }) { Text("Batal") }
            }
        )
    }

    // ---- TELEGRAM DIALOG ----
    if (showTelegramDialog) {
        var tgToken by remember { mutableStateOf(state.telegramToken) }
        var tgChatId by remember { mutableStateOf(state.telegramChatId) }
        AlertDialog(
            onDismissRequest = { showTelegramDialog = false },
            title = { Text("Konektor Telegram") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tgToken,
                        onValueChange = { tgToken = it },
                        label = { Text("Bot Token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = tgChatId,
                        onValueChange = { tgChatId = it },
                        label = { Text("Chat ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveTelegram(tgToken, tgChatId)
                    showTelegramDialog = false
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { showTelegramDialog = false }) { Text("Batal") }
            }
        )
    }

    // ---- GITHUB PAT DIALOG ----
    if (showGithubPatDialog) {
        var pat by remember { mutableStateOf(state.githubPat) }
        AlertDialog(
            onDismissRequest = { showGithubPatDialog = false },
            title = { Text("GitHub PAT") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Personal Access Token untuk akses repo & Actions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pat,
                        onValueChange = { pat = it },
                        label = { Text("Token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveGithubPat(pat)
                    showGithubPatDialog = false
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { showGithubPatDialog = false }) { Text("Batal") }
            }
        )
    }

    // ---- GITHUB REPO DIALOG ----
    if (showGithubRepoDialog) {
        var ghOwner by remember { mutableStateOf(state.githubOwner) }
        var ghRepo by remember { mutableStateOf(state.githubRepo) }
        AlertDialog(
            onDismissRequest = { showGithubRepoDialog = false },
            title = { Text("Repo GitHub") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ghOwner,
                        onValueChange = { ghOwner = it },
                        label = { Text("Owner") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ghRepo,
                        onValueChange = { ghRepo = it },
                        label = { Text("Repo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveGithubRepo(ghOwner, ghRepo)
                    showGithubRepoDialog = false
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { showGithubRepoDialog = false }) { Text("Batal") }
            }
        )
    }

    // ---- GITHUB WORKFLOW DIALOG ----
    if (showGithubWorkflowDialog) {
        var wfFile by remember { mutableStateOf(state.githubWorkflow) }
        AlertDialog(
            onDismissRequest = { showGithubWorkflowDialog = false },
            title = { Text("Workflow File") },
            text = {
                OutlinedTextField(
                    value = wfFile,
                    onValueChange = { wfFile = it },
                    label = { Text("Nama file .yml") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveGithubWorkflow(wfFile)
                    showGithubWorkflowDialog = false
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { showGithubWorkflowDialog = false }) { Text("Batal") }
            }
        )
    }

    // ---- GITHUB ACTIONS DIALOG ----
    if (showGithubActionsDialog) {
        AlertDialog(
            onDismissRequest = { showGithubActionsDialog = false },
            title = { Text("Trigger GitHub Actions") },
            text = { Text("Jalankan workflow ${state.githubWorkflow} di ${state.githubOwner}/${state.githubRepo}?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.triggerGithubActions()
                    showGithubActionsDialog = false
                }) { Text("Jalankan") }
            },
            dismissButton = {
                TextButton(onClick = { showGithubActionsDialog = false }) { Text("Batal") }
            }
        )
    }

    // ---- TERMUX DIALOG ----
    if (showTermuxDialog) {
        var cmd by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTermuxDialog = false },
            title = { Text("Konektor Termux") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Perintah shell di Termux. SETIAP perintah wajib konfirmasi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = cmd,
                        onValueChange = { cmd = it },
                        label = { Text("Command") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (cmd.isNotBlank()) {
                        vm.executeTermuxCommand(cmd)
                    }
                    showTermuxDialog = false
                }) { Text("Jalankan") }
            },
            dismissButton = {
                TextButton(onClick = { showTermuxDialog = false }) { Text("Batal") }
            }
        )
    }

    // ---- BACKUPS DIALOG ----
    if (showBackupsDialog) {
        AlertDialog(
            onDismissRequest = { showBackupsDialog = false },
            title = { Text("Backup & Restore") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Buat backup ZIP (database chat + preferensi). Restore dari file ZIP.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { vm.createBackup(ctx) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Buat Backup ZIP")
                    }
                    if (state.backupFiles.isNotEmpty()) {
                        Divider()
                        Text("Backup tersedia:", style = MaterialTheme.typography.labelMedium)
                        state.backupFiles.takeLast(5).forEach { f ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.restoreBackup(ctx, f) }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(f.name, style = MaterialTheme.typography.bodySmall)
                                Text("Restore", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackupsDialog = false }) { Text("Tutup") }
            }
        )
    }

    // ---- DEV MODE DIALOG ----
    if (showDevModeDialog) {
        AlertDialog(
            onDismissRequest = { showDevModeDialog = false },
            title = { Text("Mode Developer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Fitur untuk debugging dan pengembangan aktif.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Debug logging")
                        Switch(
                            checked = state.debugLogging,
                            onCheckedChange = { vm.setDebugLogging(it) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Verbose network")
                        Switch(
                            checked = state.verboseNetwork,
                            onCheckedChange = { vm.setVerboseNetwork(it) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDevModeDialog = false }) { Text("Tutup") }
            }
        )
    }

    // ---- IMPORT MEMORY DIALOG ----
    if (showImportMemoryDialog) {
        AlertDialog(
            onDismissRequest = { showImportMemoryDialog = false },
            title = { Text("Import Ingatan") },
            text = { Text("Paste isi memory.md untuk mengimpor ingatan persisten.") },
            confirmButton = {
                TextButton(onClick = { showImportMemoryDialog = false }) { Text("Tutup") }
            }
        )
    }

    // ---- EXPORT MEMORY DIALOG ----
    if (showExportMemoryDialog) {
        AlertDialog(
            onDismissRequest = { showExportMemoryDialog = false },
            title = { Text("Export Ingatan") },
            text = { Text("Isi memory.md akan dibagikan via share intent.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.exportMemory(ctx)
                    showExportMemoryDialog = false
                }) { Text("Export") }
            },
            dismissButton = {
                TextButton(onClick = { showExportMemoryDialog = false }) { Text("Batal") }
            }
        )
    }

    // ---- ADD MODEL DIALOG ----
    if (showAddModelDialog) {
        var modelId by remember { mutableStateOf("") }
        var modelLabel by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddModelDialog = false },
            title = { Text("Tambah Model") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = modelId,
                        onValueChange = { modelId = it },
                        label = { Text("Model ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = modelLabel,
                        onValueChange = { modelLabel = it },
                        label = { Text("Label (opsional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (modelId.isNotBlank()) {
                        vm.addCustomModel(modelId, modelLabel.ifBlank { modelId })
                    }
                    showAddModelDialog = false
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { showAddModelDialog = false }) { Text("Batal") }
            }
        )
    }

    // ---- ABOUT DIALOG ----
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("Tentang Hermes") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Hermes — aplikasi chat AI Android native.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Versi ${AppInfo.versionName} (${AppInfo.versionCode})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Backend: OpenAI-compatible (Jerouter). UI: Jetpack Compose + Material 3.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Engine: Suki Autonomous Engine (SAE).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("Tutup") }
            }
        )
    }

    // ---- MODEL PICKER ----
    if (showModelPicker) {
        ModelPickerDialog(
            models = state.models,
            currentModelId = state.modelId,
            onModelSelected = { model ->
                vm.saveModel(model.id)
                showModelPicker = false
            },
            onDismiss = { showModelPicker = false },
            onRefresh = { vm.refreshModels() },
            onAddModel = { showModelPicker = false; showAddModelDialog = true },
            isLoading = state.modelsLoading
        )
    }

    // ================================================================
    // MAIN LAYOUT — Tab + Adaptive Grid
    // ================================================================
    Column(modifier = Modifier.fillMaxSize()) {
        // Top App Bar
        TopAppBar(
            title = {
                Column {
                    Text(
                        "Pengaturan",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Hermes v${AppInfo.versionName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            actions = {
                IconButton(onClick = { showAbout = true }) {
                    Icon(Icons.Filled.Info, contentDescription = "Tentang")
                }
            }
        )

        // StatsBanner — hero section
        StatsBanner(
            modelName = state.modelLabel.ifBlank { state.modelId },
            temperature = state.temperature,
            maxTokens = state.maxTokens,
            themeLabel = when (state.themeMode) {
                                "light" -> "Terang"
                                "dark" -> "Gelap"
                                else -> "Sistem"
                            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Tab Row
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            tab.label,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = {
                        Icon(
                            tab.icon,
                            contentDescription = tab.label,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            }
        }

        // Tab Content — Adaptive Grid
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (selectedTab) {
                // ---- TAB 0: AI ----
                0 -> {
                    item {
                        SettingsGroup(
                            title = "Model AI",
                            icon = Icons.Filled.Star,
                            subtitle = "Pilih model yang aktif"
                        ) {
                            SettingsItem(
                                title = "Model aktif",
                                subtitle = state.modelLabel.ifBlank { state.modelId },
                                onClick = { showModelPicker = true }
                            ) {
                                if (state.modelsLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                                }
                            }
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SettingsItem(
                                title = "Tambah model custom",
                                subtitle = "Daftarkan model ID manual",
                                onClick = { showAddModelDialog = true }
                            ) {
                                Icon(Icons.Filled.Star, contentDescription = null)
                            }
                        }
                    }
                    item {
                        SettingsGroup(
                            title = "Parameter Generasi",
                            icon = Icons.Filled.Settings,
                            subtitle = "Atur sampling & batas output"
                        ) {
                            SliderRow(
                                label = "Temperature",
                                subtitle = "Kreativitas (0=faktual, 2=liar)",
                                value = state.temperature,
                                valueRange = 0f..2f,
                                onValueChange = { vm.saveTemperature(it) },
                                valueLabel = String.format("%.1f", state.temperature)
                            )
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SliderRow(
                                label = "Top P",
                                subtitle = "Nucleus sampling",
                                value = state.topP,
                                valueRange = 0.05f..1f,
                                onValueChange = { vm.saveTopP(it) },
                                valueLabel = String.format("%.2f", state.topP)
                            )
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SliderRow(
                                label = "Max Tokens",
                                subtitle = "Batas panjang jawaban",
                                value = state.maxTokens.toFloat(),
                                valueRange = 256f..8192f,
                                onValueChange = { vm.saveMaxTokens(it.toInt()) },
                                valueLabel = state.maxTokens.toString()
                            )
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SliderRow(
                                label = "Max Context",
                                subtitle = "Jendela konteks percakapan",
                                value = state.maxContext.toFloat(),
                                valueRange = 1024f..32768f,
                                onValueChange = { vm.saveMaxContext(it.toInt()) },
                                valueLabel = state.maxContext.toString()
                            )
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SliderRow(
                                label = "Timeout",
                                subtitle = "Batas tunggu respons (detik)",
                                value = state.timeoutSeconds.toFloat(),
                                valueRange = 15f..180f,
                                onValueChange = { vm.saveTimeoutSeconds(it.toInt()) },
                                valueLabel = "${state.timeoutSeconds}s"
                            )
                        }
                    }
                    item {
                        SettingsGroup(
                            title = "Persona & Soul",
                            icon = Icons.Filled.Edit,
                            subtitle = "System prompt aktif"
                        ) {
                            SettingsItem(
                                title = "Jiwa Hermes",
                                subtitle = state.soulPrompt.take(60).ifBlank { "Belum diatur" },
                                onClick = { showSoulDialog = true }
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null)
                            }
                        }
                    }
                }

                // ---- TAB 1: TAMPILAN ----
                1 -> {
                    item {
                        SettingsGroup(
                            title = "Tema",
                            icon = Icons.Filled.Settings,
                            subtitle = "Mode tampilan aplikasi"
                        ) {
                            SettingsItem(
                                title = "Mode tema",
                                subtitle = when (state.themeMode) {
                                    "light" -> "Terang"
                                    "dark" -> "Gelap"
                                    else -> "Ikuti sistem"
                                },
                                onClick = {
                                    val next = when (state.themeMode) {
                                        "light" -> "dark"
                                        "dark" -> "system"
                                        else -> "light"
                                    }
                                    vm.saveThemeMode(next)
                                }
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                            }
                        }
                    }
                    item {
                        SettingsGroup(
                            title = "Bahasa & Gaya",
                            icon = Icons.Filled.Info,
                            subtitle = "Preferensi penampilan"
                        ) {
                            SettingsItem(
                                title = "Bahasa",
                                subtitle = "Bahasa Indonesia",
                                onClick = { }
                            ) {
                                Icon(Icons.Filled.Info, contentDescription = null)
                            }
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SettingsItem(
                                title = "Gaya chat",
                                subtitle = "Bubble modern",
                                onClick = { }
                            ) {
                                Icon(Icons.Filled.Star, contentDescription = null)
                            }
                        }
                    }
                }

                // ---- TAB 2: KONEKSI ----
                2 -> {
                    item {
                        SettingsGroup(
                            title = "Server AI",
                            icon = Icons.Filled.Share,
                            subtitle = "Endpoint Jerouter"
                        ) {
                            SettingsItem(
                                title = "Base URL",
                                subtitle = state.baseUrl,
                                onClick = { showBaseUrlDialog = true }
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null)
                            }
                        }
                    }
                    item {
                        SettingsGroup(
                            title = "Konektor Telegram",
                            icon = Icons.Filled.Notifications,
                            subtitle = if (state.telegramToken.isNotBlank()) "Terhubung" else "Belum diatur"
                        ) {
                            SettingsItem(
                                title = "Bot Token & Chat ID",
                                subtitle = if (state.telegramToken.isNotBlank()) "Tersimpan" else "Belum diatur",
                                onClick = { showTelegramDialog = true }
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null)
                            }
                        }
                    }
                    item {
                        SettingsGroup(
                            title = "Konektor GitHub",
                            icon = Icons.Filled.Share,
                            subtitle = if (state.githubPat.isNotBlank()) "PAT aktif" else "Belum diatur"
                        ) {
                            SettingsItem(
                                title = "Personal Access Token",
                                subtitle = if (state.githubPat.isNotBlank()) "Tersimpan" else "Belum diatur",
                                onClick = { showGithubPatDialog = true }
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null)
                            }
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SettingsItem(
                                title = "Owner / Repo",
                                subtitle = "${state.githubOwner}/${state.githubRepo}",
                                onClick = { showGithubRepoDialog = true }
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null)
                            }
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SettingsItem(
                                title = "Workflow File",
                                subtitle = state.githubWorkflow,
                                onClick = { showGithubWorkflowDialog = true }
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null)
                            }
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SettingsItem(
                                title = "Trigger Actions",
                                subtitle = "Jalankan workflow CI/CD",
                                onClick = { showGithubActionsDialog = true }
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                            }
                        }
                    }
                    item {
                        SettingsGroup(
                            title = "Konektor Termux",
                            icon = Icons.Filled.Warning,
                            subtitle = "Eksekusi shell HP"
                        ) {
                            SettingsItem(
                                title = "Jalankan perintah",
                                subtitle = "Wajib konfirmasi tiap eksekusi",
                                onClick = { showTermuxDialog = true }
                            ) {
                                Icon(Icons.Filled.Warning, contentDescription = null)
                            }
                        }
                    }
                }

                // ---- TAB 3: AGEN ----
                3 -> {
                    item {
                        SettingsGroup(
                            title = "Batas Agen",
                            icon = Icons.Filled.Warning,
                            subtitle = "Limit eksekusi otomatis"
                        ) {
                            SettingsItem(
                                title = "Batas langkah agen",
                                subtitle = "Maksimal ${state.agentMaxSteps} langkah per tugas",
                                onClick = { }
                            ) {
                                Icon(Icons.Filled.Warning, contentDescription = null)
                            }
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SettingsItem(
                                title = "Auto-approve tool call",
                                subtitle = if (state.autoApprove) "Aktif" else "Nonaktif",
                                onClick = { vm.setAutoApprove(!state.autoApprove) }
                            ) {
                                Switch(
                                    checked = state.autoApprove,
                                    onCheckedChange = { vm.setAutoApprove(it) }
                                )
                            }
                        }
                    }
                    item {
                        SettingsGroup(
                            title = "Upgrade Diri Otomatis",
                            icon = Icons.Filled.Star,
                            subtitle = if (state.selfUpgradeEnabled) "Aktif" else "Nonaktif"
                        ) {
                            SettingsItem(
                                title = "Fitur upgrade otomatis",
                                subtitle = "Branch + PR otomatis ke repo Hermes",
                                onClick = { vm.setSelfUpgradeEnabled(!state.selfUpgradeEnabled) }
                            ) {
                                Switch(
                                    checked = state.selfUpgradeEnabled,
                                    onCheckedChange = { vm.setSelfUpgradeEnabled(it) }
                                )
                            }
                        }
                    }
                    item {
                        SettingsGroup(
                            title = "Ingatan Persisten",
                            icon = Icons.Filled.Info,
                            subtitle = "memory.md — maks 3 fakta/sesi"
                        ) {
                            SettingsItem(
                                title = "Export ingatan",
                                subtitle = "Bagikan memory.md",
                                onClick = { showExportMemoryDialog = true }
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null)
                            }
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SettingsItem(
                                title = "Import ingatan",
                                subtitle = "Paste dari file eksternal",
                                onClick = { showImportMemoryDialog = true }
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null)
                            }
                        }
                    }
                }

                // ---- TAB 4: SISTEM ----
                4 -> {
                    item {
                        SettingsGroup(
                            title = "Backup & Restore",
                            icon = Icons.Filled.Star,
                            subtitle = "ZIP database + preferensi"
                        ) {
                            SettingsItem(
                                title = "Kelola backup",
                                subtitle = "${state.backupFiles.size} file tersimpan",
                                onClick = { showBackupsDialog = true }
                            ) {
                                Icon(Icons.Filled.Star, contentDescription = null)
                            }
                        }
                    }
                    item {
                        SettingsGroup(
                            title = "Keamanan & Dev",
                            icon = Icons.Filled.Lock,
                            subtitle = "Opsi lanjutan"
                        ) {
                            SettingsItem(
                                title = "Mode Developer",
                                subtitle = if (state.devMode) "Aktif" else "Nonaktif",
                                onClick = { showDevModeDialog = true }
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = null)
                            }
                        }
                    }
                    item {
                        SettingsGroup(
                            title = "Tentang",
                            icon = Icons.Filled.Info,
                            subtitle = "Versi & info aplikasi"
                        ) {
                            SettingsItem(
                                title = "Hermes v${AppInfo.versionName}",
                                subtitle = "Build ${AppInfo.versionCode} — SAE Engine",
                                onClick = { showAbout = true }
                            ) {
                                Icon(Icons.Filled.Info, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// DATA & COMPOSABLE HELPERS
// ============================================================

data class TabItem(val label: String, val icon: ImageVector)

@Composable
fun StatsBanner(
    modelName: String,
    temperature: Float,
    maxTokens: Int,
    themeLabel: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Model aktif",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    modelName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1
                )
            }
            StatChip(
                label = "Temp",
                value = String.format("%.1f", temperature)
            )
            Spacer(Modifier.width(8.dp))
            StatChip(
                label = "Max",
                value = maxTokens.toString()
            )
            Spacer(Modifier.width(8.dp))
            StatChip(
                label = "Tema",
                value = themeLabel
            )
        }
    }
}

@Composable
fun StatChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SettingsGroup(
    title: String,
    icon: ImageVector? = null,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                if (icon != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            content()
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val mod = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    }
    Row(
        modifier = mod,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun SliderRow(
    label: String,
    subtitle: String? = null,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    valueLabel: String
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    valueLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

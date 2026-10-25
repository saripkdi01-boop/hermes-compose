package id.sukiapps.hermes.ui.settings

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import id.sukiapps.hermes.tool.ToolAllowlist
import id.sukiapps.hermes.data.audit.DevMode
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.sukiapps.hermes.data.connectors.ServerlessConnector
import id.sukiapps.hermes.data.connectors.TermuxConnector
import id.sukiapps.hermes.data.connectors.UpinConnector
import id.sukiapps.hermes.domain.PersonaPreset
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenKeySetup: () -> Unit,
    onOpenSoul: () -> Unit,
    onOpenAudit: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }

    var showModelPicker by remember { mutableStateOf(false) }
    var baseUrlEdit by remember { mutableStateOf(state.baseUrl) }
    // Sprint 1 Gate 2: editor allowlist web_fetch (satu domain per baris).
    var allowlistEdit by remember {
        mutableStateOf(ToolAllowlist.current().joinToString("\n"))
    }
    // Sprint 1 Gate 2: Mode Developer (PIN 6 digit + kredensial perangkat).
    // Sprint 1 Gate 2: editor allowlist scan (net_scan / conn_health).
    var scanEdit by remember {
        mutableStateOf(ToolAllowlist.scanCurrent(ctx).joinToString("\n"))
    }
    var devPinSet by remember { mutableStateOf(DevMode.hasPin(ctx)) }
    var devOn by remember { mutableStateOf(DevMode.isEnabled(ctx)) }
    var devPin by remember { mutableStateOf("") }
    var devPin2 by remember { mutableStateOf("") }
    var devMsg by remember { mutableStateOf<String?>(null) }
    val keyguardLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == android.app.Activity.RESULT_OK) {
            DevMode.setEnabled(ctx, true)
            devOn = true
            devMsg = "Mode Developer aktif."
        } else {
            devMsg = "Konfirmasi perangkat dibatalkan."
        }
    }
    // v14.10: launcher izin notifikasi (API 33+).
    var notifGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notifGranted = granted }
    var presetName by remember { mutableStateOf("") }
    var presetText by remember { mutableStateOf("") }
    var showPresetDialog by remember { mutableStateOf(false) }
    var showBackups by remember { mutableStateOf(false) }
    var backups by remember { mutableStateOf(emptyList<File>()) }
    var confirmClearKey by remember { mutableStateOf(false) }
    var restartNeeded by remember { mutableStateOf(false) }
    // v9.1: field konektor (terisi otomatis setelah token termuat dari Keystore)
    var ghPatEdit by remember(state.githubPat, state.tgBotToken, state.tgChatId) {
        mutableStateOf(state.githubPat)
    }
    var tgTokenEdit by remember(state.githubPat, state.tgBotToken, state.tgChatId) {
        mutableStateOf(state.tgBotToken)
    }
    var tgChatEdit by remember(state.githubPat, state.tgBotToken, state.tgChatId) {
        mutableStateOf(state.tgChatId)
    }
    // Sprint 1 Gate 3: field Supabase + Vercel (read-only dari chat)
    var sbUrlEdit by remember(state.supabaseUrl, state.supabaseKey, state.vercelToken) {
        mutableStateOf(state.supabaseUrl)
    }
    var sbKeyEdit by remember(state.supabaseUrl, state.supabaseKey, state.vercelToken) {
        mutableStateOf(state.supabaseKey)
    }
    var vcTokenEdit by remember(state.supabaseUrl, state.supabaseKey, state.vercelToken) {
        mutableStateOf(state.vercelToken)
    }
    var tmuxPath by remember { mutableStateOf("/data/data/com.termux/files/usr/bin/bash") }
    var tmuxArgs by remember { mutableStateOf("-lc \"echo halo dari Hermes\"") }
    var tmuxWorkdir by remember { mutableStateOf("/data/data/com.termux/files/home") }
    var tmuxBg by remember { mutableStateOf(false) }
    var tmuxInstalled by remember { mutableStateOf<Boolean?>(null) }
    // v9.4: field konektor Upin
    var upinUrlEdit by remember(state.upinUrl, state.upinSecret) {
        mutableStateOf(state.upinUrl)
    }
    var upinSecretEdit by remember(state.upinUrl, state.upinSecret) {
        mutableStateOf(state.upinSecret)
    }

    LaunchedEffect(state.snack) {
        state.snack?.let {
            snack.showSnackbar(it)
            vm.consumeSnack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { pad ->
        // v14.18 restyle: enter fade + slide up 24dp dalam 300ms.
        val density = LocalDensity.current
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(300)) +
                slideInVertically(
                    animationSpec = tween(300),
                    initialOffsetY = { with(density) { 24.dp.roundToPx() } },
                ),
            modifier = Modifier.padding(pad),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header identitas: tanpa nama/email karangan — app tidak
            // menyimpan keduanya; yang tampil hanya model + base URL nyata.
            SettingsProfileHeader(
                model = state.model,
                baseUrl = state.baseUrl,
            )
            if (state.keyBroken) {
                Card {
                    Text(
                        text = "Peringatan: Android Keystore bermasalah di HP ini. " +
                            "API key disimpan biasa (tetap privat di aplikasi).",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // ---- Grup: Model ----
            // v9.3: dropdown (Box+Popup) diganti dialog picker — pola popup
            // terbukti rapuh di device (tidak bisa switch model).
            SettingsSectionCard(
                "Model AI",
                trailing = {
                    StatusDot(
                        connected = state.models.isNotEmpty(),
                        connecting = state.modelsLoading,
                    )
                },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showModelPicker = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Model aktif",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            state.model.ifBlank { "Belum dipilih" },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Pilih model")
                }
                if (showModelPicker) {
                    ModelPickerDialog(
                        models = state.models,
                        current = state.model,
                        statuses = state.modelStatuses,
                        onStartStatusChecks = { vm.startModelStatusChecks() },
                        onStopStatusChecks = { vm.stopModelStatusChecks() },
                        onPick = { m ->
                            showModelPicker = false
                            vm.selectModel(m)
                        },
                        onDismiss = { showModelPicker = false }
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { vm.refreshModels() },
                    enabled = !state.modelsLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.modelsLoading) "Memuat…" else "Muat ulang daftar model"
                    )
                }
                if (state.modelsRefreshedAt.isNotBlank()) {
                    Text(
                        "Diperbarui: ${state.modelsRefreshedAt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // ---- Grup: Parameter ----
            SettingsSectionCard("Parameter Generasi") {
                SettingsSlider(
                    label = "Temperature",
                    valueText = String.format("%.2f", state.temperature),
                    value = state.temperature,
                    range = 0f..2f,
                    onChange = vm::setTemperature,
                    steps = 19
                )
                SettingsSlider(
                    label = "Top-P",
                    valueText = String.format("%.2f", state.topP),
                    value = state.topP,
                    range = 0f..1f,
                    onChange = vm::setTopP
                )
                SettingsSlider(
                    label = "Max Tokens (0 = otomatis)",
                    valueText = state.maxTokens.toString(),
                    value = state.maxTokens.toFloat(),
                    range = 0f..8192f,
                    onChange = { vm.setMaxTokens(it.toInt()) }
                )
            }

            // ---- Grup: Persona ----
            SettingsSectionCard("Persona") {
                OutlinedTextField(
                    value = state.persona,
                    onValueChange = vm::setPersona,
                    label = { Text("System prompt aktif") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 6
                )
                Spacer(Modifier.height(8.dp))
                Text("Preset tersimpan:", style = MaterialTheme.typography.labelLarge)
                state.presets.forEach { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { vm.applyPreset(p) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                p.n,
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 1
                            )
                        }
                        IconButton(onClick = { vm.deletePreset(p) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Hapus preset")
                        }
                    }
                }
                OutlinedButton(
                    onClick = { showPresetDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Simpan persona saat ini sebagai preset")
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Suara",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                SettingsSlider(
                    label = "Kecepatan baca",
                    valueText = String.format("%.1fx", state.ttsSpeed),
                    value = state.ttsSpeed,
                    range = 0.5f..2.0f,
                    steps = 14,
                    onChange = vm::setTtsSpeed
                )
                Text(
                    "Berlaku untuk pembacaan pesan berikutnya.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // ---- Grup: Tampilan ----
            SettingsSectionCard("Tampilan") {
                Text("Mode tema", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                SettingsThemeSegmented(
                    selected = state.themeMode,
                    onSelect = vm::setThemeMode
                )
                Spacer(Modifier.height(12.dp))
                Text("Warna aksen", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                SettingsAccentRow(
                    selected = state.accentColor,
                    onSelect = vm::setAccentColor
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tema & aksen diterapkan saat kembali dari Pengaturan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // ---- Grup: Notifikasi & Izin (v14.10) ----
            SettingsSectionCard("Notifikasi & Izin") {
                SettingsItemSwitch(
                    icon = Icons.Filled.Notifications,
                    title = "Notifikasi",
                    subtitle = "Tampilkan notifikasi aplikasi",
                    checked = state.notifEnabled,
                    onCheckedChange = vm::setNotifEnabled
                )
                Spacer(Modifier.height(2.dp))
                SettingsItemSwitch(
                    icon = Icons.Filled.PlayArrow,
                    title = "Eksekusi",
                    subtitle = "Tool, prompt, dan respons",
                    checked = state.notifExec,
                    onCheckedChange = vm::setNotifExec,
                    enabled = state.notifEnabled
                )
                Spacer(Modifier.height(2.dp))
                SettingsItemSwitch(
                    icon = Icons.Filled.Security,
                    title = "Izin & persetujuan",
                    subtitle = "Minta konfirmasi sebelum aksi sensitif",
                    checked = state.notifApproval,
                    onCheckedChange = vm::setNotifApproval,
                    enabled = state.notifEnabled
                )
                Spacer(Modifier.height(12.dp))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !notifGranted
                ) {
                    Button(
                        onClick = {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Minta Izin Notifikasi") }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        }
                        ctx.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Buka Pengaturan Notifikasi") }
            }

            // ---- Grup: Koneksi ----
            SettingsSectionCard("Koneksi") {
                // Preset provider (Sprint 1 tambahan): Muse by Meta = Meta Model API.
                Text(
                    "Preset provider",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            baseUrlEdit =
                                id.sukiapps.hermes.domain.Prefs.DEFAULT_BASE
                            vm.setBaseUrl(baseUrlEdit)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Jerouter") }
                    OutlinedButton(
                        onClick = {
                            baseUrlEdit = "https://api.meta.ai/v1"
                            vm.setBaseUrl(baseUrlEdit)
                            vm.selectModel("muse-spark-1.3")
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Muse by Meta") }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            baseUrlEdit = "https://openrouter.ai/api/v1"
                            vm.setBaseUrl(baseUrlEdit)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("OpenRouter") }
                    OutlinedButton(
                        onClick = {
                            baseUrlEdit =
                                "https://chat.decentralizedcompute.company/v1"
                            vm.setBaseUrl(baseUrlEdit)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("DCompute") }
                }
                Text(
                    "Preset mengisi Base URL (Muse by Meta juga menyetel " +
                        "model awal muse-spark-1.3). Setelah memilih preset: ketuk " +
                        "\u201cGanti API key\u2026\u201d di bawah untuk menempel API key " +
                        "Meta milikmu, lalu di grup Model AI ketuk \u201cMuat ulang " +
                        "daftar model\u201d.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = baseUrlEdit,
                    onValueChange = { baseUrlEdit = it },
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.setBaseUrl(baseUrlEdit) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Simpan") }
                    OutlinedButton(onClick = {
                        vm.resetBaseUrl()
                        baseUrlEdit = id.sukiapps.hermes.domain.Prefs.DEFAULT_BASE
                    }) { Text("Reset") }
                }
            }

            // ---- Grup: Tool Web (Sprint 1 Gate 2) ----
            SettingsSectionCard("Tool Web") {
                Text(
                    "Allowlist domain web_fetch — satu domain per baris. " +
                        "Kosongkan untuk mengizinkan semua domain publik " +
                            "(akses IP privat tetap diblokir).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = allowlistEdit,
                    onValueChange = { allowlistEdit = it },
                    label = { Text("Domain diizinkan") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        ToolAllowlist.setDomains(
                            ctx,
                            allowlistEdit.split("\n")
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Simpan allowlist") }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Allowlist scan untuk net_scan & conn_health — satu host per baris. " +
                        "Kosongkan untuk default localhost saja.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = scanEdit,
                    onValueChange = { scanEdit = it },
                    label = { Text("Host scan diizinkan") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        ToolAllowlist.setScanHosts(
                            ctx,
                            scanEdit.split("\n")
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Simpan allowlist scan") }
            }

            // ---- Grup: Agen (Sprint 1 Gate 4) ----
            SettingsSectionCard("Agen") {
                val limitsNow = id.sukiapps.hermes.data.agent.AgentLimits.get(ctx)
                var iterEdit by remember { mutableStateOf(limitsNow.maxIterations.toString()) }
                var minEdit by remember { mutableStateOf(limitsNow.timeoutMinutes.toString()) }
                var budgetEdit by remember { mutableStateOf(limitsNow.tokenBudget.toString()) }
                var memMsg by remember { mutableStateOf<String?>(null) }
                val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts
                        .CreateDocument("application/json")
                ) { uri ->
                    if (uri != null) {
                        val ok = runCatching {
                            ctx.contentResolver.openOutputStream(uri)?.use { s ->
                                s.write(
                                    id.sukiapps.hermes.data.agent.AgentMemory
                                        .exportJson().toByteArray(Charsets.UTF_8)
                                )
                            }
                        }.isSuccess
                        memMsg = if (ok) "Export ingatan tersimpan."
                        else "Export gagal."
                    }
                }
                val importScope = rememberCoroutineScope()
                val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        // File ekspor nyata ~26 MB: baca + tulis di IO,
                        // jangan blokir main thread (ANR) seperti dulu.
                        memMsg = "Mengimpor ingatan…"
                        importScope.launch {
                            val text = withContext(Dispatchers.IO) {
                                runCatching {
                                    ctx.contentResolver.openInputStream(uri)
                                        ?.bufferedReader()?.readText().orEmpty()
                                }.getOrDefault("")
                            }
                            // Format lama hermes-memory-v1 tetap lewat
                            // importJson (jalur lama, tidak diubah); format
                            // Upin lewat importUpin yang rinci + backup.
                            // Penanda format ada di awal kedua dokumen,
                            // jadi cek string murah ini cukup — tidak
                            // perlu parse JSON 26 MB dua kali.
                            if (text.contains("hermes-memory-v1")) {
                                val ok = runCatching {
                                    id.sukiapps.hermes.data.agent
                                        .AgentMemory.importJson(text)
                                }.getOrDefault(false)
                                memMsg = if (ok) "Import ingatan diterapkan."
                                else "Import gagal — format tidak dikenal."
                            } else {
                                val result = runCatching {
                                    id.sukiapps.hermes.data.agent
                                        .AgentMemory.importUpin(ctx, text)
                                }.getOrElse {
                                    id.sukiapps.hermes.data.agent
                                        .AgentMemory.ImportResult(
                                            ok = false,
                                            error = it.message
                                                ?: "gagal membaca file.",
                                        )
                                }
                                memMsg = if (result.ok) {
                                    buildString {
                                        append("Import berhasil: ")
                                        append("${result.imported} file")
                                        if (result.skipped.isNotEmpty()) {
                                            append(", ${result.skipped.size} dilewati")
                                        }
                                        append(".")
                                        if (result.summaryApplied) {
                                            append(" Ringkasan masuk ingatan agen.")
                                        }
                                    }
                                } else {
                                    "Import gagal: ${result.error}"
                                }
                            }
                        }
                    }
                }
                Text(
                    "Batas sesi Mode Agen (chat). Token = estimasi atas isi " +
                        "percakapan (provider tidak mengirim usage asli).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = iterEdit,
                    onValueChange = { iterEdit = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("Maks iterasi per sesi (1–25)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = minEdit,
                    onValueChange = { minEdit = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("Timeout per sesi, menit (1–30)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = budgetEdit,
                    onValueChange = { budgetEdit = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("Budget token per sesi (10rb–500rb)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        id.sukiapps.hermes.data.agent.AgentLimits.save(
                            ctx,
                            iterEdit.toIntOrNull() ?: 10,
                            minEdit.toIntOrNull() ?: 5,
                            budgetEdit.toIntOrNull() ?: 100_000
                        )
                        memMsg = "Batas agen tersimpan."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Simpan batas agen") }
                Spacer(Modifier.height(8.dp))
                var suOn by remember {
                    mutableStateOf(
                        id.sukiapps.hermes.data.agent.SelfUpgradePrefs
                            .enabled(ctx)
                    )
                }
                SettingsItemSwitch(
                    icon = Icons.Filled.SmartToy,
                    title = "Upgrade Diri Otomatis",
                    subtitle = "Agen boleh branch, tulis, merge, dan buat " +
                        "rilis draft HANYA lewat Mode Agen (kartu izin " +
                        "HIGH/CRITICAL tetap berlaku; tidak tersedia di " +
                        "chat biasa). Matikan untuk menghentikan loop " +
                        "(kill switch). Signing + lampir APK tetap oleh " +
                        "maintainer.",
                    checked = suOn,
                    onCheckedChange = {
                        suOn = it
                        id.sukiapps.hermes.data.agent.SelfUpgradePrefs
                            .setEnabled(ctx, it)
                        memMsg = if (it) {
                            "Upgrade Diri Otomatis AKTIF."
                        } else {
                            "Upgrade Diri Otomatis nonaktif."
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch("hermes-memory-export.json") },
                        modifier = Modifier.weight(1f)
                    ) { Text("Export ingatan") }
                    OutlinedButton(
                        // Beberapa file manager melabeli .json sebagai
                        // text/plain — terima keduanya supaya file tidak
                        // "tidak terlihat" di pemilih.
                        onClick = {
                            importLauncher.launch(
                                arrayOf("application/json", "text/plain")
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Import ingatan") }
                }
                memMsg?.let {
                    Spacer(Modifier.height(4.dp))
                    if (it.startsWith("Import berhasil") ||
                        it.endsWith("tersimpan.") ||
                        it.endsWith("tersimpan")
                    ) {
                        SettingsSuccessNote(it)
                    } else {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // ---- Grup: Mode Developer (Sprint 1 Gate 2) ----
            SettingsSectionCard("Mode Developer") {
                Text(
                    if (devOn) {
                        "AKTIF — approval LOW/MEDIUM dilewati otomatis; HIGH tetap wajib popup."
                    } else {
                        "Nonaktif — tool sensitif selalu meminta izin."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                if (!devPinSet) {
                    OutlinedTextField(
                        value = devPin,
                        onValueChange = { devPin = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("Buat PIN (6 digit)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = devPin2,
                        onValueChange = { devPin2 = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("Ulangi PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            when {
                                devPin.length != 6 -> devMsg = "PIN harus 6 digit."
                                devPin != devPin2 -> devMsg = "PIN tidak sama."
                                else -> {
                                    DevMode.setPin(ctx, devPin)
                                    devPinSet = true
                                    devPin = ""
                                    devPin2 = ""
                                    devMsg = "PIN tersimpan. Masukkan PIN untuk mengaktifkan."
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Simpan PIN") }
                } else {
                    OutlinedTextField(
                        value = devPin,
                        onValueChange = { devPin = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("PIN developer") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (!DevMode.verifyPin(ctx, devPin)) {
                                devMsg = "PIN salah."
                            } else if (devOn) {
                                DevMode.setEnabled(ctx, false)
                                devOn = false
                                devPin = ""
                                devMsg = "Mode Developer dimatikan."
                            } else {
                                val km = ctx.getSystemService(Context.KEYGUARD_SERVICE)
                                    as KeyguardManager
                                val intent = km.createConfirmDeviceCredentialIntent(
                                    "Mode Developer",
                                    "Konfirmasi untuk mengaktifkan Mode Developer"
                                )
                                if (intent != null) {
                                    keyguardLauncher.launch(intent)
                                } else {
                                    DevMode.setEnabled(ctx, true)
                                    devOn = true
                                    devMsg = "Mode Developer aktif (perangkat tanpa kunci layar)."
                                }
                                devPin = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (devOn) "Matikan Mode Developer"
                            else "Aktifkan Mode Developer"
                        )
                    }
                }
                devMsg?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // ---- Grup: Backup ----
            SettingsSectionCard("Backup & Restore") {
                Button(
                    onClick = { vm.doBackup() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Buat backup sekarang") }
                OutlinedButton(
                    onClick = {
                        backups = vm.listBackups()
                        showBackups = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Restore dari backup…") }
                if (state.busyMessage != null) {
                    Text(
                        state.busyMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // ---- Grup: API Key ----
            SettingsSectionCard("API Key") {
                OutlinedButton(
                    onClick = onOpenKeySetup,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Ganti API key…") }
                SettingsDangerBox {
                    Text(
                        "Zona berbahaya",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Menghapus API key berarti kamu harus memasukkan " +
                            "key baru sebelum bisa memakai aplikasi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { confirmClearKey = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Hapus API key",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // ---- Grup: Jiwa Hermes (v9.1) ----
            SettingsSectionCard("Jiwa Hermes") {
                Text(
                    "Persona tetap, ingatan jangka panjang, dan todo — " +
                        "disusun otomatis menjadi system prompt.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpenSoul,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Buka Jiwa Hermes…") }
            }

            // ---- Grup: Konektor (v9.1, aksi manual) ----
            SettingsSectionCard("Konektor") {
                Text(
                    "Aksi manual — setiap tombol di bawah ditekan olehmu, " +
                        "tidak pernah berjalan otomatis. Token tersimpan " +
                        "terenkripsi di Keystore.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = ghPatEdit,
                    onValueChange = { ghPatEdit = it },
                    label = { Text("GitHub PAT (scope repo + workflow)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tgTokenEdit,
                    onValueChange = { tgTokenEdit = it },
                    label = { Text("Token bot Telegram") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tgChatEdit,
                    onValueChange = { tgChatEdit = it },
                    label = { Text("Chat ID Telegram") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.saveConnectorTokens(ghPatEdit, tgTokenEdit, tgChatEdit) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Simpan token") }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Supabase & Vercel (read-only dari chat)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = sbUrlEdit,
                    onValueChange = { sbUrlEdit = it },
                    label = { Text("URL Supabase (https://xxx.supabase.co)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = sbKeyEdit,
                    onValueChange = { sbKeyEdit = it },
                    label = { Text("Key Supabase (anon/service)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = vcTokenEdit,
                    onValueChange = { vcTokenEdit = it },
                    label = { Text("Token Vercel") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.saveCloudTokens(sbUrlEdit, sbKeyEdit, vcTokenEdit) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Simpan Supabase & Vercel") }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { vm.testGitHub() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Tes GitHub") }
                    OutlinedButton(
                        onClick = { vm.testTelegram() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Tes Telegram") }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Termux",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    tmuxInstalled?.let {
                        if (it) "Termux:API terdeteksi ✅"
                        else "Termux:API tidak terinstal."
                    } ?: "Status Termux:API belum dicek.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { tmuxInstalled = TermuxConnector.isApiInstalled(ctx) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cek Termux:API") }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tmuxPath,
                    onValueChange = { tmuxPath = it },
                    label = { Text("Path perintah") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tmuxArgs,
                    onValueChange = { tmuxArgs = it },
                    label = { Text("Argumen (pisah spasi)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tmuxWorkdir,
                    onValueChange = { tmuxWorkdir = it },
                    label = { Text("Workdir") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = tmuxBg,
                        onCheckedChange = { tmuxBg = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Jalan di background")
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val args = tmuxArgs.split(Regex("\\s+")).filter { it.isNotEmpty() }
                        vm.runTermux(
                            tmuxPath.trim(), args, tmuxWorkdir.trim(), tmuxBg
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Jalankan di Termux…") }
                Text(
                    "Setiap eksekusi selalu minta izin popup (tidak bisa " +
                        "diingat) & tercatat di Riwayat Izin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Telegram Serverless",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Deploy backend bot JS ke infrastruktur Telegram via " +
                        "CLI tgcloud di Termux.\nJalankan `npx tgcloud login` " +
                        "sekali secara manual di Termux (tempel CLI access " +
                        "token dari BotFather → bot → Serverless → CLI " +
                        "Access). Token dipegang Termux, tidak disimpan di app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.serverlessDir,
                    onValueChange = vm::setServerlessDir,
                    label = { Text("Direktori project Serverless") },
                    placeholder = { Text("~/my-bot") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            vm.runServerless(ServerlessConnector.Action.CHECK_INSTALL)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Cek instalasi") }
                    OutlinedButton(
                        onClick = {
                            vm.runServerless(ServerlessConnector.Action.STATUS)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Status") }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            vm.runServerless(ServerlessConnector.Action.PUSH)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Push") }
                    OutlinedButton(
                        onClick = {
                            vm.runServerless(ServerlessConnector.Action.MIGRATE)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Migrate") }
                }
            }

            // ---- Grup: Konektor Upin (v9.4) ----
            SettingsSectionCard("Konektor Upin") {
                Text(
                    "Tanya-jawab dengan agent Upin di VM via SSH forward. " +
                        "Secret tersimpan terenkripsi di Keystore.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = upinUrlEdit,
                    onValueChange = { upinUrlEdit = it },
                    label = { Text("URL Upin") },
                    placeholder = { Text(UpinConnector.DEFAULT_URL) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = upinSecretEdit,
                    onValueChange = { upinSecretEdit = it },
                    label = { Text("Secret Upin") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { vm.saveUpinSettings(upinUrlEdit, upinSecretEdit) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Simpan") }
                    OutlinedButton(
                        onClick = { vm.testUpin() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Test koneksi") }
                }
                if (state.upinStatus != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.upinStatus!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.upinStatus == "Terhubung ✓")
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Di Termux jalankan:\nssh -L 8766:127.0.0.1:8766 muse-vm -N",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---- Grup: Tentang (v14.18) ----
            SettingsSectionCard("Tentang") {
                val pkgInfo = runCatching {
                    ctx.packageManager.getPackageInfo(ctx.packageName, 0)
                }.getOrNull()
                Text(
                    "Hermes ${pkgInfo?.versionName ?: "—"} " +
                        "(build ${pkgInfo?.longVersionCode ?: 0})",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Paket: ${ctx.packageName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        ctx.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(
                                    "https://github.com/saripkdi01-boop/hermes-compose"
                                )
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Buka repositori") }
            }

            // ---- Grup: Izin & Riwayat (v9.2) ----
            SettingsSectionCard("Izin & Riwayat") {
                Text(
                    "Semua aksi sensitif (Termux, Telegram, GitHub, tulis " +
                        "ingatan) tercatat — termasuk yang kamu tolak.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpenAudit,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Buka Riwayat Izin…") }
            }
            // Ruang bawah 96dp agar konten tidak tertutup navigation bar.
            Spacer(Modifier.height(96.dp))
        }
        }
    }

    // Dialog tambah preset.
    if (showPresetDialog) {
        AlertDialog(
            onDismissRequest = { showPresetDialog = false },
            title = { Text("Preset baru") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text("Nama preset") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = presetText,
                        onValueChange = { presetText = it },
                        label = { Text("Isi system prompt") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 8
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addPreset(presetName, presetText)
                    presetName = ""
                    presetText = ""
                    showPresetDialog = false
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { showPresetDialog = false }) { Text("Batal") }
            }
        )
    }

    // Dialog daftar backup.
    if (showBackups) {
        AlertDialog(
            onDismissRequest = { showBackups = false },
            title = { Text("Pilih backup") },
            text = {
                Column {
                    if (backups.isEmpty()) {
                        Text("Belum ada file backup.")
                    } else {
                        backups.forEach { f ->
                            TextButton(
                                onClick = {
                                    showBackups = false
                                    vm.doRestore(f) { needsRestart ->
                                        if (needsRestart) restartNeeded = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "${f.name} (${f.length() / 1024} KB)",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBackups = false }) { Text("Tutup") }
            }
        )
    }

    // Konfirmasi hapus key.
    if (confirmClearKey) {
        AlertDialog(
            onDismissRequest = { confirmClearKey = false },
            title = { Text("Hapus API key?") },
            text = { Text("Kamu harus memasukkan API key baru untuk memakai aplikasi.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearKey = false
                    vm.clearKey(onOpenKeySetup)
                }) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearKey = false }) { Text("Batal") }
            }
        )
    }

    // Restore berhasil → wajib restart agar Room membuka DB baru.
    if (restartNeeded) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Restore berhasil") },
            text = {
                Text(
                    "Tutup dan buka ulang aplikasi agar data yang di-restore " +
                        "dimuat dengan benar."
                )
            },
            confirmButton = {
                TextButton(onClick = { restartNeeded = false }) { Text("Mengerti") }
            }
        )
    }
}


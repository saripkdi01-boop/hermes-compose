package id.sukiapps.hermes.ui.settings

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
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import id.sukiapps.hermes.tool.ToolAllowlist
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
            SettingsGroup("Model AI") {
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
            SettingsGroup("Parameter Generasi") {
                SliderRow(
                    label = "Temperature",
                    valueText = String.format("%.2f", state.temperature),
                    value = state.temperature,
                    range = 0f..2f,
                    onChange = vm::setTemperature
                )
                SliderRow(
                    label = "Top-P",
                    valueText = String.format("%.2f", state.topP),
                    value = state.topP,
                    range = 0f..1f,
                    onChange = vm::setTopP
                )
                SliderRow(
                    label = "Max Tokens (0 = otomatis)",
                    valueText = state.maxTokens.toString(),
                    value = state.maxTokens.toFloat(),
                    range = 0f..8192f,
                    onChange = { vm.setMaxTokens(it.toInt()) }
                )
            }

            // ---- Grup: Persona ----
            SettingsGroup("Persona") {
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
            }

            // ---- Grup: Tampilan ----
            SettingsGroup("Tampilan") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tema gelap", modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.darkTheme,
                        onCheckedChange = vm::setDarkTheme,
                        // Fix uji fisik Gate 1: warna default switch nyaris
                        // menyatu dengan kartu di tema terang (ghost switch).
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor =
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor =
                                MaterialTheme.colorScheme.surfaceVariant,
                            uncheckedBorderColor =
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            // ---- Grup: Koneksi ----
            SettingsGroup("Koneksi") {
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
            SettingsGroup("Tool Web") {
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
            }

            // ---- Grup: Backup ----
            SettingsGroup("Backup & Restore") {
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
            SettingsGroup("API Key") {
                OutlinedButton(
                    onClick = onOpenKeySetup,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Ganti API key…") }
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

            // ---- Grup: Jiwa Hermes (v9.1) ----
            SettingsGroup("Jiwa Hermes") {
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
            SettingsGroup("Konektor") {
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
            SettingsGroup("Konektor Upin") {
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

            // ---- Grup: Izin & Riwayat (v9.2) ----
            SettingsGroup("Izin & Riwayat") {
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

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f))
            Text(
                valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range
        )
    }
}

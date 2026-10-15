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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.sukiapps.hermes.domain.PersonaPreset
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenKeySetup: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }

    var modelMenu by remember { mutableStateOf(false) }
    var baseUrlEdit by remember { mutableStateOf(state.baseUrl) }
    var presetName by remember { mutableStateOf("") }
    var presetText by remember { mutableStateOf("") }
    var showPresetDialog by remember { mutableStateOf(false) }
    var showBackups by remember { mutableStateOf(false) }
    var backups by remember { mutableStateOf(emptyList<File>()) }
    var confirmClearKey by remember { mutableStateOf(false) }
    var restartNeeded by remember { mutableStateOf(false) }

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
            SettingsGroup("Model AI") {
                ExposedDropdownMenuBox(
                    expanded = modelMenu,
                    onExpandedChange = { modelMenu = it }
                ) {
                    OutlinedTextField(
                        value = state.model.ifBlank { "Pilih model…" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Model") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(modelMenu)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = modelMenu,
                        onDismissRequest = { modelMenu = false }
                    ) {
                        state.models.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                onClick = {
                                    vm.setModel(m)
                                    modelMenu = false
                                }
                            )
                        }
                        if (state.models.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Belum ada daftar — muat dulu.") },
                                onClick = { modelMenu = false }
                            )
                        }
                    }
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
                        onCheckedChange = vm::setDarkTheme
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

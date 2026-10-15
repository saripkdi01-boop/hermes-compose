package id.sukiapps.hermes.ui.keysetup

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeySetupScreen(
    onDone: () -> Unit,
    vm: KeySetupViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showKey by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Selamat datang di Hermes",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Masukkan API key Jerouter untuk mulai chat. " +
                "Key disimpan terenkripsi di HP ini saja.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.keystoreBroken) {
            Card {
                Text(
                    text = "Peringatan: Android Keystore bermasalah di HP ini, " +
                        "key akan disimpan biasa (tetap privat di aplikasi).",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        OutlinedTextField(
            value = state.apiKey,
            onValueChange = vm::setApiKey,
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (showKey) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        if (showKey) Icons.Filled.VisibilityOff
                        else Icons.Filled.Visibility,
                        contentDescription = if (showKey) "Sembunyikan" else "Tampilkan"
                    )
                }
            }
        )

        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = vm::setBaseUrl,
            label = { Text("Base URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { vm.testConnection() },
                enabled = !state.testing,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (state.testing) "Menghubungi…" else "Test koneksi")
            }
        }

        state.testMessage?.let { msg ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.testOk) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = msg,
                    color = if (state.testOk) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (state.models.isNotEmpty()) {
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
                }
            }
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = { vm.save(onDone) },
            enabled = !state.saving && state.apiKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.saving) "Menyimpan…" else "Simpan & mulai")
        }
        Spacer(Modifier.height(16.dp))
    }
}

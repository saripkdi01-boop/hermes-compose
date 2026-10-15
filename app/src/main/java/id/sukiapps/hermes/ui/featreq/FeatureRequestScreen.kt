package id.sukiapps.hermes.ui.featreq

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * "Minta Fitur" — tulis permintaan (natural) → AI buatkan draf file →
 * review → push ke GitHub → Actions build otomatis.
 * Batas jujur: draf untuk perubahan KECIL; selalu review sebelum push.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureRequestScreen(
    onBack: () -> Unit,
    vm: FeatureRequestViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(state.snack) {
        state.snack?.let {
            snack.showSnackbar(it)
            vm.consumeSnack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Minta Fitur") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Tulis maumu dengan bahasa biasa — AI buatkan draf file-nya. " +
                    "AI hanya kenal ringkasan fitur (bukan full source), jadi " +
                    "ini cocok untuk perubahan KECIL. Selalu review draf " +
                    "sebelum push.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = state.request,
                onValueChange = vm::setRequest,
                label = { Text("Permintaan fitur") },
                placeholder = { Text("Contoh: tambahkan tombol salin di setiap pesan") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.owner,
                    onValueChange = vm::setOwner,
                    label = { Text("Owner") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.repo,
                    onValueChange = vm::setRepo,
                    label = { Text("Repo") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Button(
                onClick = { vm.generate() },
                enabled = !state.generating && !state.pushing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.generating) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(if (state.generating) "Membuat draf…" else "Buatkan draf")
            }

            if (state.files.isNotEmpty()) {
                Text(
                    "Draf (${state.files.size} file) — review dulu:",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                state.files.forEach { f ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                f.path,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                f.content,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = { vm.push() },
                    enabled = !state.pushing && !state.generating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.pushing) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Text(
                        if (state.pushing) "Pushing…"
                        else "Push ke GitHub (${state.files.size} file)"
                    )
                }
            }
        }
    }
}

package id.sukiapps.hermes.ui.agent

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.sukiapps.hermes.ApkShareProvider
import id.sukiapps.hermes.data.agent.AgentFiles
import kotlinx.coroutines.launch

/**
 * Editor "Jiwa Hermes": tiap file (soul/memory/todo/agents) berupa kartu
 * dengan judul + area edit + tombol Simpan sendiri. Di bawahnya ada
 * export/import ZIP seluruh folder agent/.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoulScreen(
    onBack: () -> Unit,
    vm: AgentViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // rev naik tiap reload (mis. setelah import) → editor ikut segar.
    var soul by remember(state.rev) { mutableStateOf(state.soul) }
    var memory by remember(state.rev) { mutableStateOf(state.memory) }
    var todo by remember(state.rev) { mutableStateOf(state.todo) }
    var agents by remember(state.rev) { mutableStateOf(state.agents) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { vm.importZip(it) }
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
                title = { Text("Jiwa Hermes") },
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
            Text(
                "Persona tetap, ingatan jangka panjang, todo, dan aturan operasi. " +
                    "Disusun otomatis menjadi system prompt setiap chat dimulai.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FileCard(
                fileName = AgentFiles.SOUL,
                title = "SOUL.md — siapa Hermes",
                value = soul,
                onChange = { soul = it },
                onSave = { vm.saveOne(AgentFiles.SOUL, soul) }
            )
            FileCard(
                fileName = AgentFiles.MEMORY,
                title = "MEMORY.md — ingatan tentangmu",
                value = memory,
                onChange = { memory = it },
                onSave = { vm.saveOne(AgentFiles.MEMORY, memory) }
            )
            FileCard(
                fileName = AgentFiles.TODO,
                title = "TODO.md — tugas aktif",
                value = todo,
                onChange = { todo = it },
                onSave = { vm.saveOne(AgentFiles.TODO, todo) }
            )
            FileCard(
                fileName = AgentFiles.AGENTS,
                title = "AGENTS.md — aturan operasi",
                value = agents,
                onChange = { agents = it },
                onSave = { vm.saveOne(AgentFiles.AGENTS, agents) }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val file = vm.exportZip()
                            if (file != null) {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(
                                        Intent.EXTRA_STREAM,
                                        ApkShareProvider.zipUriFor(file)
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                val chooser =
                                    Intent.createChooser(intent, "Bagikan jiwa Hermes")
                                if (ctx !is Activity) {
                                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                ctx.startActivity(chooser)
                            } else {
                                vm.note("Export gagal.")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Export ZIP") }
                OutlinedButton(
                    onClick = { importLauncher.launch("application/zip") },
                    modifier = Modifier.weight(1f)
                ) { Text("Import ZIP") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Satu kartu file: judul + area edit + tombol Simpan. */
@Composable
private fun FileCard(
    fileName: String,
    title: String,
    value: String,
    onChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                minLines = 4
            )
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Simpan $fileName") }
        }
    }
}

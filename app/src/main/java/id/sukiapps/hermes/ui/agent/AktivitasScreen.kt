package id.sukiapps.hermes.ui.agent

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
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Layar Aktivitas: tugas dari TODO.md dibagi "Sedang dikerjakan" vs
 * "Selesai". Tap checkbox untuk toggle; tanggal tampil bila ada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AktivitasScreen(
    onBack: () -> Unit,
    onOpenSoul: () -> Unit,
    vm: AgentViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val active = state.todos.filter { !it.checked }
    val done = state.todos.filter { it.checked }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aktivitas") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TodoSection(
                title = "Sedang dikerjakan (${active.size})",
                items = state.todos.mapIndexedNotNull { i, t ->
                    if (!t.checked) i to t else null
                },
                emptyText = "Belum ada tugas aktif. Tambah baris \"- [ ] ...\" di TODO.md.",
                onToggle = vm::toggleTodo
            )
            TodoSection(
                title = "Selesai (${done.size})",
                items = state.todos.mapIndexedNotNull { i, t ->
                    if (t.checked) i to t else null
                },
                emptyText = "Belum ada tugas selesai.",
                onToggle = vm::toggleTodo
            )
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = onOpenSoul,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Kelola TODO.md di Jiwa Hermes") }
        }
    }
}

@Composable
private fun TodoSection(
    title: String,
    items: List<Pair<Int, TodoItem>>,
    emptyText: String,
    onToggle: (Int) -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    if (items.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    } else {
        items.forEach { (index, item) ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = item.checked,
                        onCheckedChange = { onToggle(index) }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.text.ifBlank { "(tanpa teks)" },
                            style = MaterialTheme.typography.bodyMedium,
                            textDecoration =
                                if (item.checked) TextDecoration.LineThrough else null
                        )
                        item.date?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

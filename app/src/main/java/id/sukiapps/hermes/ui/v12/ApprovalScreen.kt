package id.sukiapps.hermes.ui.v12

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.sukiapps.hermes.data.store.ApprovalStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v12 UI — Layar daftar approval.
 * Tanpa ViewModel/Hilt (langsung ke ApprovalStore) agar minim dependensi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<ApprovalStore.ApprovalItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    fun refresh() {
        scope.launch {
            loading = true
            items = withContext(Dispatchers.IO) { ApprovalStore.getPendingSync() }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Persetujuan") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("‹ Kembali") }
                }
            )
        }
    ) { pad ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("Tidak ada permintaan menunggu.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    ApprovalCard(item = item, onDecided = { refresh() })
                }
            }
        }
    }
}

@Composable
private fun ApprovalCard(
    item: ApprovalStore.ApprovalItem,
    onDecided: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    fun decide(allowed: Boolean, trustHour: Boolean) {
        scope.launch {
            busy = true
            withContext(Dispatchers.IO) {
                ApprovalStore.decide(item.id, allowed, trustHour)
            }
            busy = false
            onDecided()
        }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "[${item.type}] ${item.detail}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { decide(true, false) }) { Text("Izinkan") }
                    OutlinedButton(onClick = { decide(true, true) }) { Text("1 Jam") }
                    TextButton(onClick = { decide(false, false) }) { Text("Tolak") }
                }
            }
        }
    }
}

package id.sukiapps.hermes.ui.v12

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.sukiapps.hermes.data.store.ExecutionLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v12 UI — Layar aktivitas live (timeline eksekusi).
 * Menampilkan event dari ExecutionLog: prompt, command, tool, file, approval, error.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var events by remember { mutableStateOf<List<ExecutionLog.Event>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var expanded by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            events = withContext(Dispatchers.IO) { ExecutionLog.getRecentSync() }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aktivitas") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("‹ Kembali") }
                },
                actions = {
                    TextButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { ExecutionLog.clear() }
                            refresh()
                        }
                    }) { Text("Bersihkan") }
                }
            )
        }
    ) { pad ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (events.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text(
                    "Belum ada aktivitas.\nEvent eksekusi akan muncul di sini secara live.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(events, key = { it.id }) { ev ->
                    ElevatedCard(
                        onClick = { expanded = if (expanded == ev.id) null else ev.id },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    ev.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    timeFmt.format(Date(ev.ts)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(onClick = {}, label = { Text(ev.type) })
                                val (dot, label) = when (ev.status) {
                                    "start" -> "●" to "jalan"
                                    "fail" -> "●" to "gagal"
                                    else -> "●" to "ok"
                                }
                                val color = when (ev.status) {
                                    "fail" -> MaterialTheme.colorScheme.error
                                    "start" -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                Text(
                                    "$dot $label",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = color
                                )
                            }
                            if (expanded == ev.id && ev.detail.isNotBlank()) {
                                Text(
                                    ev.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

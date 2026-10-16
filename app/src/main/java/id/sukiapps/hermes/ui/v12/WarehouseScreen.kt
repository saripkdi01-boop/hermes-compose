package id.sukiapps.hermes.ui.v12

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.sukiapps.hermes.data.store.WarehouseStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v12.1 UI — Gudang File: repositori terkategorisasi.
 * Kategori: UI Visual, Database, Backend, API, Frontend, Podcast, Skill, Dokumen, Riwayat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarehouseScreen(
    onBack: () -> Unit,
    onOpenFile: (WarehouseStore.Entry) -> Unit = {}
) {
    var selected by remember { mutableStateOf<WarehouseStore.Category?>(null) }
    var query by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<WarehouseStore.Entry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    fun refresh() {
        loading = true
        Thread {
            val list = WarehouseStore.listSync(selected?.id, query)
            // Kembali ke main thread via state (aman: hanya tulis state).
            entries = list
            loading = false
        }.start()
    }

    LaunchedEffect(selected, query) { refresh() }

    val dateFmt = remember { SimpleDateFormat("d MMM yyyy", Locale("id")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gudang File") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("‹ Kembali") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            // Pencarian
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Cari file...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            // Chip kategori
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selected == null,
                        onClick = { selected = null },
                        label = { Text("Semua") }
                    )
                }
                items(WarehouseStore.Category.values()) { cat ->
                    FilterChip(
                        selected = selected == cat,
                        onClick = { selected = if (selected == cat) null else cat },
                        label = { Text("${cat.emoji} ${cat.label}") }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // Daftar
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Gudang masih kosong.\nImport file dari penyimpanan HP untuk mulai mengisi.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entries, key = { it.id }) { e ->
                        val cat = WarehouseStore.Category.byId(e.category)
                        ElevatedCard(
                            onClick = { onOpenFile(e) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(cat.emoji, style = MaterialTheme.typography.headlineSmall)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${cat.label} • ${formatSize(e.sizeBytes)} • ${dateFmt.format(Date(e.addedAt))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (e.note.isNotBlank()) {
                                        Text(
                                            e.note,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

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
import id.sukiapps.hermes.data.store.ToolStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * v12 UI — Layar katalog tools (published + draft).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolCatalogScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tools by remember { mutableStateOf<List<ToolStore.ToolItem>>(emptyList()) }
    var drafts by remember { mutableStateOf<List<ToolStore.ToolDraft>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf(0) }

    fun refresh() {
        scope.launch {
            loading = true
            val t = withContext(Dispatchers.IO) { ToolStore.getToolsSync() }
            val d = withContext(Dispatchers.IO) { ToolStore.getDraftsSync() }
            tools = t; drafts = d
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Katalog Tools") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("‹ Kembali") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Tools (${tools.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Draft (${drafts.size})") })
            }
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (tab == 0) {
                if (tools.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Belum ada tool.")
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(tools, key = { "${it.name}:${it.createdAt}" }) { tool ->
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(tool.name, style = MaterialTheme.typography.titleMedium)
                                    if (tool.description.isNotBlank()) {
                                        Text(tool.description, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(
                                        if (tool.sellable) "Rp${tool.priceIdr}" else "Internal",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                if (drafts.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Belum ada draft.")
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(drafts, key = { it.id }) { draft ->
                            var busy by remember { mutableStateOf(false) }
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(draft.name, style = MaterialTheme.typography.titleMedium)
                                    Text("draft", style = MaterialTheme.typography.labelSmall)
                                    if (busy) {
                                        LinearProgressIndicator(Modifier.fillMaxWidth())
                                    } else {
                                        Button(onClick = {
                                            scope.launch {
                                                busy = true
                                                withContext(Dispatchers.IO) {
                                                    // v12: publish lewat persetujuan —
                                                    // file .py ditulis setelah pengguna mengetuk Izinkan.
                                                    ApprovalStore.request(
                                                        type = "tool_create",
                                                        title = "Publish tool: ${draft.name}",
                                                        detail = "Menulis '${draft.name}' ke katalog tool.",
                                                        payload = JSONObject()
                                                            .put("action", "publish_tool")
                                                            .put("draft_id", draft.id)
                                                    )
                                                }
                                                busy = false
                                                refresh()
                                            }
                                        }) { Text("Ajukan Publish") }
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

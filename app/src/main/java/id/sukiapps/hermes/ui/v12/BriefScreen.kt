package id.sukiapps.hermes.ui.v12

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * v12 UI — Layar baca morning brief (file .md).
 * Tanpa markdown renderer — tampil sebagai teks biasa.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BriefScreen(filesDir: File, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var briefs by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            val list = withContext(Dispatchers.IO) {
                val dir = File(filesDir, "brief")
                if (!dir.exists()) return@withContext emptyList()
                dir.listFiles { f -> f.name.endsWith(".md") }
                    ?.sortedByDescending { it.name }
                    ?.take(14)
                    ?.map { it.name to it.readText() }
                    ?: emptyList()
            }
            briefs = list
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selected == null) "Morning Brief" else selected!!) },
                navigationIcon = {
                    TextButton(onClick = {
                        if (selected != null) selected = null else onBack()
                    }) { Text("‹ Kembali") }
                }
            )
        }
    ) { pad ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (selected != null) {
            val content = briefs.find { it.first == selected }?.second ?: ""
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(16.dp)
            ) {
                item { Text(content, style = MaterialTheme.typography.bodyMedium) }
            }
        } else if (briefs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("Belum ada brief.\nBrief dibuat otomatis tiap pagi jam 7.",
                    style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(briefs, key = { it.first }) { (name, _) ->
                    ElevatedCard(
                        onClick = { selected = name },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            name.removeSuffix(".md"),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }
    }
}

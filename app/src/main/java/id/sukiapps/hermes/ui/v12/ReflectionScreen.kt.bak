package id.sukiapps.hermes.ui.v12

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
 * v12 UI — Layar baca reflection mingguan (filesDir/reflection/weekly-*.md).
 * Tanpa markdown renderer — tampil sebagai teks biasa.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReflectionScreen(filesDir: File, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            val list = withContext(Dispatchers.IO) {
                val dir = File(filesDir, "reflection")
                if (!dir.exists()) return@withContext emptyList()
                dir.listFiles { f -> f.name.endsWith(".md") }
                    ?.sortedByDescending { it.name }
                    ?.take(26)
                    ?.map { it.name to it.readText() }
                    ?: emptyList()
            }
            files = list
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selected == null) "Reflection" else selected!!) },
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
            val content = files.firstOrNull { it.first == selected }?.second ?: ""
            Column(
                Modifier.fillMaxSize().padding(pad).padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(content)
            }
        } else if (files.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("Belum ada refleksi.\nRefleksi mingguan dibuat otomatis.",
                    style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files, key = { it.first }) { (name, _) ->
                    ElevatedCard(onClick = { selected = name }, modifier = Modifier.fillMaxWidth()) {
                        Text(name, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

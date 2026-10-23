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

/** Batas karakter isi refleksi yang dimuat ke memori (anti-ANR/crash file besar). */
private const val MAX_REFLECTION_CHARS = 200_000

/**
 * v12 UI — Layar baca reflection mingguan (filesDir/reflection/weekly-*.md).
 * Tanpa markdown renderer — tampil sebagai teks biasa.
 *
 * Gate 1 (Sprint 1) hardening: daftar hanya memuat NAMA file (bukan seluruh
 * isi seperti sebelumnya — 26 file dibaca penuh ke memori sekaligus).
 * Isi dimuat malas saat file dipilih, dibatasi MAX_REFLECTION_CHARS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReflectionScreen(filesDir: File, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var names by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<String?>(null) }
    var selectedContent by remember { mutableStateOf<String?>(null) }
    var loadingContent by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            loading = true
            names = withContext(Dispatchers.IO) {
                val dir = File(filesDir, "reflection")
                if (!dir.exists()) return@withContext emptyList()
                dir.listFiles { f -> f.name.endsWith(".md") }
                    ?.sortedByDescending { it.name }
                    ?.take(26)
                    ?.map { it.name }
                    ?: emptyList()
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    LaunchedEffect(selected) {
        val name = selected
        selectedContent = null
        if (name == null) return@LaunchedEffect
        loadingContent = true
        selectedContent = withContext(Dispatchers.IO) {
            val f = File(File(filesDir, "reflection"), name)
            if (!f.exists()) {
                "(File tidak ditemukan.)"
            } else {
                val text = f.readText()
                if (text.length > MAX_REFLECTION_CHARS) {
                    text.take(MAX_REFLECTION_CHARS) +
                        "\n\n[Isi dipotong — file asli ${text.length} karakter, " +
                        "batas tampil $MAX_REFLECTION_CHARS karakter.]"
                } else {
                    text
                }
            }
        }
        loadingContent = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selected ?: "Reflection") },
                navigationIcon = {
                    TextButton(onClick = {
                        if (selected != null) selected = null else onBack()
                    }) { Text("‹ Kembali") }
                }
            )
        }
    ) { pad ->
        when {
            loading -> {
                Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            selected != null -> {
                if (loadingContent || selectedContent == null) {
                    Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().padding(pad).padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(selectedContent.orEmpty())
                    }
                }
            }
            names.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    Text("Belum ada refleksi.\nRefleksi mingguan dibuat otomatis.",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            else -> {
                LazyColumn(
                    Modifier.fillMaxSize().padding(pad).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(names, key = { it }) { name ->
                        ElevatedCard(onClick = { selected = name }, modifier = Modifier.fillMaxWidth()) {
                            Text(name, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

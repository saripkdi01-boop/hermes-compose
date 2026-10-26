package id.sukiapps.hermes.ui.v12

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import id.sukiapps.hermes.data.store.ApprovalStore
import id.sukiapps.hermes.data.store.ToolRunLog
import id.sukiapps.hermes.data.store.ToolStore
import id.sukiapps.hermes.data.tool.CatalogToolRunner
import id.sukiapps.hermes.domain.manus.PermissionRequest
import id.sukiapps.hermes.domain.manus.RiskLevel
import id.sukiapps.hermes.domain.manus.ToolCall
import id.sukiapps.hermes.ui.manus.ManusPermissionDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v12 UI — Layar katalog tools (published + draft).
 *
 * D1 (14.30): kartu tool + draft dapat menampilkan isi (penampil isi, maks
 * 50.000 karakter tampilan + penanda "dipotong"), dan tool published dapat
 * dijalankan via Chaquopy lewat kartu izin HIGH (ManusPermissionDialog).
 * Alur publish draft TIDAK diubah.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolCatalogScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var tools by remember { mutableStateOf<List<ToolStore.ToolItem>>(emptyList()) }
    var drafts by remember { mutableStateOf<List<ToolStore.ToolDraft>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf(0) }
    var viewer by remember { mutableStateOf<ViewerData?>(null) }

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

    viewer?.let { v ->
        AlertDialog(
            onDismissRequest = { viewer = null },
            title = { Text("Isi: ${v.title}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(v.content, fontFamily = FontFamily.Monospace)
                    if (v.truncated) {
                        Text(
                            "… (dipotong, file utuh tetap di disk)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(v.content))
                }) { Text("Salin") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, v.content)
                        }
                        context.startActivity(
                            Intent.createChooser(send, "Bagikan isi tool")
                        )
                    }) { Text("Bagikan") }
                    TextButton(onClick = { viewer = null }) { Text("Tutup") }
                }
            }
        )
    }

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
                            var running by remember { mutableStateOf(false) }
                            var pendingPermission by remember { mutableStateOf(false) }
                            var lastRun by remember { mutableStateOf<ToolRunLog.RunEntry?>(null) }
                            LaunchedEffect(tool.name) {
                                lastRun = withContext(Dispatchers.IO) {
                                    ToolRunLog.lastFor(tool.name)
                                }
                            }
                            if (pendingPermission) {
                                ManusPermissionDialog(
                                    request = PermissionRequest(
                                        taskId = "catalog",
                                        stepId = "catalog-run:${tool.name}:${tool.createdAt}",
                                        tool = ToolCall(
                                            name = "catalog_run",
                                            args = mapOf("tool" to tool.name)
                                        ),
                                        reason = "Menjalankan skrip Python '${tool.name}' " +
                                            "di HP ini (Chaquopy, maks 30 detik, " +
                                            "output maks 20.000 karakter).",
                                        risk = RiskLevel.HIGH,
                                        targets = listOf(tool.path),
                                        rollback = "Eksekusi tidak dapat dibatalkan " +
                                            "setelah berjalan; file katalog tidak diubah."
                                    ),
                                    onGrant = {
                                        pendingPermission = false
                                        scope.launch {
                                            running = true
                                            // Hasil tampil lewat lastRun (log) setelah refresh.
                                            CatalogToolRunner.run(tool, context)
                                            running = false
                                            lastRun = withContext(Dispatchers.IO) {
                                                ToolRunLog.lastFor(tool.name)
                                            }
                                        }
                                    },
                                    onDeny = { pendingPermission = false }
                                )
                            }
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
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = {
                                            scope.launch {
                                                val text = withContext(Dispatchers.IO) {
                                                    try {
                                                        File(tool.path).readText()
                                                    } catch (e: Exception) {
                                                        "Gagal membaca file: ${e.message}"
                                                    }
                                                }
                                                viewer = ViewerData(
                                                    title = tool.name,
                                                    content = CatalogToolRunner.capDisplay(text),
                                                    truncated = text.length >
                                                        CatalogToolRunner.MAX_DISPLAY_CHARS
                                                )
                                            }
                                        }) { Text("Lihat isi") }
                                        // v1: tombol Jalankan hanya untuk tool published.
                                        Button(
                                            onClick = { pendingPermission = true },
                                            enabled = !running
                                        ) { Text(if (running) "Menjalankan…" else "Jalankan") }
                                    }
                                    if (running) {
                                        LinearProgressIndicator(Modifier.fillMaxWidth())
                                        Text(
                                            "Menjalankan (maks 30 detik)…",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    lastRun?.let { run ->
                                        val date = SimpleDateFormat(
                                            "dd MMM HH:mm", Locale("id", "ID")
                                        ).format(Date(run.at))
                                        Text(
                                            "Output terakhir • $date • " +
                                                if (run.ok) "Berhasil" else "Gagal",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        val shown = (if (run.ok) run.output else run.error)
                                            .take(500)
                                        if (shown.isNotBlank()) {
                                            Text(shown, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
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
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = {
                                            viewer = ViewerData(
                                                title = draft.name,
                                                content = CatalogToolRunner.capDisplay(
                                                    draft.payload
                                                ),
                                                truncated = draft.payload.length >
                                                    CatalogToolRunner.MAX_DISPLAY_CHARS
                                            )
                                        }) { Text("Lihat isi") }
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
}

private data class ViewerData(
    val title: String,
    val content: String,
    val truncated: Boolean
)

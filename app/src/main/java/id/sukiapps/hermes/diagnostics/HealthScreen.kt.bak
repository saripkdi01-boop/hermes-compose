package id.sukiapps.hermes.diagnostics

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.sukiapps.hermes.ui.theme.HermesColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * HealthScreen — layar "Kesehatan App": hasil audit + kirim laporan bug.
 *
 * Daftarkan sebagai route, mis. "health", dari menu Pengaturan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<HealthResult>?>(null) }
    var running by remember { mutableStateOf(false) }

    fun runCheck() {
        running = true
        results = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { HealthCheck.run(context) }
            results = r
            running = false
        }
    }

    LaunchedEffect(Unit) { runCheck() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kesehatan App", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.Refresh, "Kembali", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { runCheck() }) {
                        Icon(Icons.Outlined.Refresh, "Periksa ulang", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HermesColors.appBar())
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                running -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator(Modifier.size(32.dp)) }
                    Text("Memeriksa…", color = HermesColors.textSecondary())
                }
                results != null -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(results!!, key = { it.name }) { r ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (r.ok) Icons.Outlined.CheckCircle else Icons.Outlined.Error,
                                    contentDescription = if (r.ok) "OK" else "Bermasalah",
                                    tint = if (r.ok) HermesColors.streamingDot() else HermesColors.destructive(),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        r.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = HermesColors.textPrimary()
                                    )
                                    Text(
                                        r.detail,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = HermesColors.textSecondary()
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val crashes = remember(results) { CrashReporter.pendingCrashes(context) }
                    Button(
                        onClick = {
                            val report = buildString {
                                appendLine("LAPORAN BUG HERMES")
                                appendLine("=".repeat(30))
                                results!!.forEach { appendLine("[${if (it.ok) "OK" else "FAIL"}] ${it.name}: ${it.detail}") }
                                appendLine()
                                crashes.forEach { f ->
                                    appendLine("--- ${f.name} ---")
                                    appendLine(runCatching { f.readText().take(2000) }.getOrDefault(""))
                                }
                            }
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, report)
                                putExtra(Intent.EXTRA_SUBJECT, "Laporan Bug Hermes")
                            }
                            context.startActivity(Intent.createChooser(send, "Kirim via"))
                            CrashReporter.clearSent(crashes)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Kirim laporan bug (${crashes.size} crash)")
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val update = withContext(Dispatchers.IO) {
                                    id.sukiapps.hermes.update.UpdateChecker.check(context)
                                }
                                // TODO Tahap lanjut: tampilkan dialog konfirmasi update.
                                // Untuk sekarang hasil cek dicatat ke log.
                                android.util.Log.i(
                                    "HermesUpdate",
                                    "Hasil cek: ${update?.versionName ?: "tidak ada update"}"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Cek update") }
                }
            }
        }
    }
}

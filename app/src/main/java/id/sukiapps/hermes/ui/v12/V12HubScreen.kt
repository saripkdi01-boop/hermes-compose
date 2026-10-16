package id.sukiapps.hermes.ui.v12

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * v12 UI — Hub (daftar fitur v12).
 * Tambahkan ke NavHost untuk akses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V12HubScreen(
    onBack: () -> Unit,
    onApproval: () -> Unit,
    onTools: () -> Unit,
    onBrief: () -> Unit,
    onReflection: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hermes v12") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("‹ Kembali") }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Fitur Agent Engine",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            ElevatedCard(onClick = onApproval, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Persetujuan", style = MaterialTheme.typography.titleSmall)
                    Text("Kelola permintaan aksi sensitif",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            ElevatedCard(onClick = onTools, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Katalog Tools", style = MaterialTheme.typography.titleSmall)
                    Text("Draft & publish tool",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            ElevatedCard(onClick = onBrief, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Morning Brief", style = MaterialTheme.typography.titleSmall)
                    Text("Ringkasan harian otomatis",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            ElevatedCard(onClick = onReflection, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Reflection", style = MaterialTheme.typography.titleSmall)
                    Text("Refleksi mingguan otomatis",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

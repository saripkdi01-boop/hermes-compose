package id.sukiapps.hermes.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Dialog pemilih model — pengganti pola Box+OutlinedTextField+DropdownMenu
 * yang terbukti rapuh di device (Popup/focus quirks Material3 1.3.x).
 * Ada search filter + tanda centang pada model aktif.
 *
 * Status per model: titik bundar di kiri nama (kuning = online, merah =
 * mati, abu-abu = meragukan/belum dicek) + teks kecepatan bila online.
 * Pengecekan dimulai otomatis saat dialog dibuka (onStartStatusChecks)
 * dan dibatalkan saat dialog ditutup (onStopStatusChecks) — klik pilih
 * model tetap berperilaku seperti sebelumnya.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerDialog(
    models: List<String>,
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    statuses: Map<String, ModelStatus> = emptyMap(),
    onStartStatusChecks: () -> Unit = {},
    onStopStatusChecks: () -> Unit = {}
) {
    LaunchedEffect(Unit) { onStartStatusChecks() }
    DisposableEffect(Unit) { onDispose { onStopStatusChecks() } }
    var query by remember { mutableStateOf("") }
    val filtered = remember(models, query) {
        if (query.isBlank()) models
        else models.filter { it.contains(query, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text("Pilih model") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Cari…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    Text(
                        if (models.isEmpty()) "Belum ada daftar — muat dulu."
                        else "Tidak ada model yang cocok.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                    ) {
                        items(filtered, key = { it }) { m ->
                            val status = statuses[m] ?: ModelStatus()
                            // Kuning keemasan (bukan kuning pucat) agar titik
                            // terbaca di latar terang maupun gelap; merah dan
                            // abu-abu standar juga terbaca di kedua tema.
                            val dotColor = when (status.state) {
                                ModelStatusState.ONLINE -> Color(0xFFC79A00)
                                ModelStatusState.DEAD -> Color(0xFFD32F2F)
                                ModelStatusState.UNKNOWN -> Color(0xFF9E9E9E)
                            }
                            val dotDesc = when (status.state) {
                                ModelStatusState.ONLINE -> "Online"
                                ModelStatusState.DEAD -> "Mati"
                                ModelStatusState.UNKNOWN -> "Meragukan"
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(m) }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                        .semantics { contentDescription = dotDesc }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = m,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                ModelStatusHelper.formatLatency(status.latencyMs)?.let { lat ->
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = lat,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (m == current) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Aktif",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

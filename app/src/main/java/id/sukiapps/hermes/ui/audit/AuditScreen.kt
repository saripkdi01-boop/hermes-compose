package id.sukiapps.hermes.ui.audit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Riwayat Izin" — bukti tiap aksi sensitif: Termux, Telegram, GitHub,
 * tulis ingatan, ambil web. Kronologis, bisa difilter Diizinkan/Ditolak.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditScreen(
    onBack: () -> Unit,
    vm: AuditViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val fmt = SimpleDateFormat("d MMM yyyy HH:mm", Locale("id", "ID"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Riwayat Izin") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Muat ulang")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp)
        ) {
            Text(
                "Setiap aksi sensitif tercatat di sini — termasuk yang " +
                    "kamu tolak. Tidak ada yang berjalan diam-diam.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.filter == AuditFilter.ALL,
                    onClick = { vm.setFilter(AuditFilter.ALL) },
                    label = { Text("Semua") }
                )
                FilterChip(
                    selected = state.filter == AuditFilter.GRANTED,
                    onClick = { vm.setFilter(AuditFilter.GRANTED) },
                    label = { Text("Diizinkan") }
                )
                FilterChip(
                    selected = state.filter == AuditFilter.DENIED,
                    onClick = { vm.setFilter(AuditFilter.DENIED) },
                    label = { Text("Ditolak") }
                )
            }
            Spacer(Modifier.height(12.dp))
            if (state.loading) {
                Text("Memuat…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val items = vm.visible()
                if (items.isEmpty()) {
                    Text(
                        "Belum ada riwayat.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(items, key = { it.ts }) { e ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "${e.type} • ${fmt.format(Date(e.ts))}",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        val granted = e.decision == "granted"
                                        AssistChip(
                                            onClick = {},
                                            label = {
                                                Text(if (granted) "Diizinkan" else "Ditolak")
                                            },
                                            colors = AssistChipDefaults.assistChipColors(
                                                labelColor = if (granted)
                                                    MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.error
                                            )
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        e.detail,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 3
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        when (e.via) {
                                            "popup" -> "via dialog izin"
                                            "remembered" -> "otomatis (pernah diingat)"
                                            else -> "otomatis (aksi sistem)"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
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
}

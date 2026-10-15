package id.sukiapps.hermes.ui.audit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.sukiapps.hermes.data.audit.DangerLevel
import id.sukiapps.hermes.data.audit.PermissionGate

/**
 * Satu-satunya dialog izin di aplikasi — dirender di MainActivity agar selalu
 * ada selama app hidup. Inilah jembatan UI untuk PermissionGate: setiap aksi
 * sensitif (Termux/Telegram/GitHub) WAJIB lewat sini, tidak ada jalur lain.
 * Menutup dialog tanpa memilih = ditolak.
 */
@Composable
fun PermissionGateDialog(gate: PermissionGate) {
    val req by gate.pending.collectAsStateWithLifecycle()
    val current = req ?: return
    var remember by remember(current.id) { mutableStateOf(false) }

    val (riskText, riskColor) = when (current.level) {
        DangerLevel.HIGH -> "Risiko TINGGI — selalu minta izin" to MaterialTheme.colorScheme.error
        DangerLevel.MEDIUM -> "Risiko SEDANG" to MaterialTheme.colorScheme.tertiary
        DangerLevel.LOW -> "Risiko RENDAH" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    AlertDialog(
        onDismissRequest = { gate.resolve(current, granted = false, remember = false) },
        title = { Text("Izinkan ${current.type}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    riskText,
                    color = riskColor,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    current.detail,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (current.rememberAllowed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = remember,
                            onCheckedChange = { remember = it }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Ingat untuk ${current.type}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    Text(
                        "Izin ini tidak bisa diingat — selalu ditanyakan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { gate.resolve(current, granted = true, remember) }) {
                Text("Izinkan")
            }
        },
        dismissButton = {
            TextButton(onClick = { gate.resolve(current, granted = false, remember = false) }) {
                Text("Tolak")
            }
        }
    )
}

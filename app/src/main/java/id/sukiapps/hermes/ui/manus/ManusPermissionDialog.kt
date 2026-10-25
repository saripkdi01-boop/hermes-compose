package id.sukiapps.hermes.ui.manus

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import id.sukiapps.hermes.domain.manus.PermissionRequest

/**
 * Kartu izin Manus — dipakai BERSAMA oleh layar Manus dan chat utama.
 * Kontrak yang dipertahankan dari layar Manus: dialog blocking, perintah
 * persis, target persis, scope sekali-saja, rollback. Menutup dialog =
 * Tolak: langkah dilewati, tugas lanjut, tool TIDAK dijalankan.
 */
@Composable
fun ManusPermissionDialog(
    request: PermissionRequest,
    onGrant: () -> Unit,
    onDeny: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDeny,
        title = { Text("Izin Diperlukan — Risiko ${request.risk}") },
        text = {
            Column {
                Text("Perintah persis: ${request.tool.summary()}")
                Text("Alasan: ${request.reason}")
                Text("Target: ${request.targets.joinToString("; ")}")
                Text("Scope: Sekali saja — hanya langkah ini, tidak berlaku untuk langkah lain.")
                Text("Rollback: ${request.rollback ?: "-"}")
                Text(
                    "Menutup dialog = Tolak. Langkah ini dilewati dan tugas lanjut ke langkah berikutnya; tool di atas TIDAK dijalankan.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(onClick = onGrant) {
                Text("Izinkan sekali")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDeny) {
                Text("Tolak (lewati langkah)")
            }
        }
    )
}

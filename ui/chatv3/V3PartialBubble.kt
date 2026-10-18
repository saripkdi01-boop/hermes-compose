package id.sukiapps.hermes.ui.chatv3

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sukiapps.hermes.ui.theme.LocalChatPalette

/**
 * V3PartialBubble — bubble untuk pesan AI yang TERPOTONG karena SSE terputus
 * (edge case E4 / Tahap 7: "SSE disconnect partial + continue button").
 *
 * Menampilkan teks yang sempat diterima + tombol "Lanjutkan" (sambung dari
 * posisi terakhir, bila backend mendukung) dan "Coba lagi" (kirim ulang penuh).
 *
 * Semua warna dari LocalChatPalette (tanpa hardcode), teks minimal 12sp,
 * target sentuh 48dp. 3 string UI ("Lanjutkan"/"Coba lagi"/pesan terpotong)
 * untuk sementara hardcode mengikuti pola file Fase D/E/F; kunci
 * strings-v14.xml sudah disiapkan untuk migrasi (lihat CATATAN-FASEH.md).
 *
 * INTEGRASI (belum di-wire, catat di CATATAN-FASEH.md):
 * - V3LiveScreen: bila ChatRepository mengembalikan partial, tampilkan ini
 *   sebagai pengganti bubble AI terakhir.
 * - onContinue: panggil ChatRepository.sendStreaming dengan pesan "lanjutkan".
 */
@Composable
fun V3PartialBubble(
    partialText: String,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChatPalette.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .v3StatusSemantics("Pesan AI", "terpotong, koneksi terputus"),
        shape = RoundedCornerShape(12.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.errorColor.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (partialText.isNotBlank()) {
                Text(
                    text = v3SafeSnippet(partialText, 2000),
                    color = palette.textPrimary,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                )
            }
            Text(
                text = "Pesan terpotong karena koneksi terputus.",
                color = palette.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onContinue,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accent,
                        contentColor = palette.textPrimary,
                    ),
                ) {
                    Text(text = "Lanjutkan", fontSize = 14.sp)
                }
                OutlinedButton(onClick = onRetry) {
                    Text(
                        text = "Coba lagi",
                        fontSize = 14.sp,
                        color = palette.textPrimary,
                    )
                }
            }
        }
    }
}

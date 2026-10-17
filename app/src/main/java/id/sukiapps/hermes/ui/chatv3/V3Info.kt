package id.sukiapps.hermes.ui.chatv3

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.sukiapps.hermes.ui.theme.HermesColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Info percakapan: jumlah pesan, model, tanggal dibuat, estimasi token, ekspor.
 */
data class V3ChatInfo(
    val messageCount: Int,
    val model: String = "",
    val createdAt: Long = 0L,
    val starredCount: Int = 0
)

/** Estimasi kasar: 1 token ≈ 4 karakter. */
fun v3EstimateTokens(messages: List<V3Message>): Int {
    val chars = messages.sumOf { it.text.length }
    return chars / 4
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V3InfoSheet(
    info: V3ChatInfo,
    messages: List<V3Message>,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(
                text = "Info Percakapan",
                style = MaterialTheme.typography.titleLarge,
                color = HermesColors.textPrimary()
            )
            Spacer(modifier = Modifier.height(16.dp))
            V3InfoRow("Jumlah pesan", "${info.messageCount}")
            if (info.model.isNotBlank()) {
                V3InfoRow("Model", info.model)
            }
            if (info.createdAt > 0) {
                val date = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("id", "ID"))
                    .format(Date(info.createdAt))
                V3InfoRow("Dibuat", date)
            }
            V3InfoRow("Estimasi token", "±${v3EstimateTokens(messages)}")
            V3InfoRow("Ditandai", "${info.starredCount}")
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onExport,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null)
                Spacer(modifier = Modifier.height(0.dp))
                Text(text = "Ekspor percakapan")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun V3InfoRow(label: String, value: String) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = HermesColors.textSecondary(),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = HermesColors.textPrimary()
        )
    }
}

package id.sukiapps.hermes.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.sukiapps.hermes.ui.theme.PillShape

/** Saran cepat — nada Hermes, bukan Muse generik. */
private val SUGGESTIONS = listOf(
    "Tulis ulang pesan ini biar lebih mantap",
    "Audit repo hermes-compose, cari celah",
    "Bantu rencana deploy Hermes ke muse-vm",
    "Rancang strategi cuan bulan ini"
)

/**
 * Layar kosong ala Muse: logo bulat, sapaan serif, chip saran.
 * Tap chip → teks masuk ke composer (user review dulu sebelum kirim).
 */
@Composable
fun ChatEmptyState(
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = scheme.surfaceVariant,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = scheme.primary
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Ada yang bisa Hermes bantu?",
            style = MaterialTheme.typography.displayLarge,
            color = scheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Kirim instruksi, tempel URL, atau pilih salah satu di bawah.",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            SUGGESTIONS.forEach { s ->
                Surface(
                    shape = PillShape,
                    color = scheme.surface,
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .clickable { onSuggestion(s) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = scheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = s,
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
/* akhir ChatEmptyState */

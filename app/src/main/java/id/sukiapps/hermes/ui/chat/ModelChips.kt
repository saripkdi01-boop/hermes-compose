package id.sukiapps.hermes.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * v16 — Deretan chip model di layar chat (geser horizontal). Chip yang
 * aktif = model yang sedang dipakai. Ketuk chip untuk ganti model —
 * pilihan langsung tersimpan sebagai preferensi (prefs.model).
 * Hanya tampil bila daftar model tersedia (cache); bila kosong, baris
 * disembunyikan dan pemilihan model tetap lewat Pengaturan seperti dulu.
 */
@Composable
fun ModelChipRow(
    models: List<String>,
    current: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (models.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(models, key = { it }) { model ->
            FilterChip(
                selected = model == current,
                onClick = { onSelect(model) },
                label = { Text(text = model, maxLines = 1) }
            )
        }
    }
}

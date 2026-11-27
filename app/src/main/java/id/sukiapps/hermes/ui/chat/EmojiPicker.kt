package id.sukiapps.hermes.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * v19.2 — panel emoji di atas composer (pengganti keyboard emoji).
 * Tab kategori berupa chip; "Terakhir" muncul bila ada riwayat pemakaian.
 * Sel 48 dp (target sentuh aksesibel), tinggi panel tetap 240 dp supaya
 * daftar pesan tidak melompat saat berganti kategori.
 */
@Composable
fun EmojiPickerPanel(
    recent: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = remember(recent) {
        buildList {
            if (recent.isNotEmpty()) add(Emoji.Category("Terakhir", recent))
            addAll(Emoji.CATEGORIES)
        }
    }
    var selected by remember(categories.size) { mutableIntStateOf(0) }
    val current = categories[selected.coerceIn(0, categories.lastIndex)]
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
            ) {
                itemsIndexed(categories, key = { _, c -> c.label }) { i, c ->
                    FilterChip(
                        selected = i == selected,
                        onClick = { selected = i },
                        label = { Text(c.label) }
                    )
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 48.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(196.dp)
                    .padding(horizontal = 4.dp)
            ) {
                items(current.emojis, key = { it }) { e ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button) { onPick(e) }
                            .semantics { contentDescription = "Sisipkan $e" }
                    ) {
                        Text(text = e, fontSize = 24.sp)
                    }
                }
            }
        }
    }
}

/**
 * v19.2 — baris reaksi cepat (dipakai di menu tekan-lama pesan).
 * Emoji yang sedang terpasang diberi latar sorot; ketuk lagi = lepas.
 */
@Composable
fun QuickReactionRow(
    current: String?,
    onReact: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        for (e in Emoji.QUICK_REACTIONS) {
            val on = e == current
            Surface(
                shape = RoundedCornerShape(50),
                color = if (on) MaterialTheme.colorScheme.primaryContainer
                else androidx.compose.ui.graphics.Color.Transparent,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(role = Role.Button) { onReact(e) }
                    .semantics {
                        contentDescription = if (on) "Lepas reaksi $e" else "Beri reaksi $e"
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = e, fontSize = 22.sp)
                }
            }
        }
    }
}

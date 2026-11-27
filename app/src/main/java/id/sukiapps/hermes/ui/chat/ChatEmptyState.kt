package id.sukiapps.hermes.ui.chat

import androidx.compose.runtime.remember
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.sukiapps.hermes.ui.theme.PillShape

/** Saran cepat — nada Hermes, bukan Muse generik. Ikon beda per saran. */
private val SUGGESTIONS = listOf(
    "Tulis ulang pesan ini biar lebih mantap" to Icons.Filled.Edit,
    "Audit repo hermes-compose, cari celah" to Icons.Filled.BugReport,
    "Bantu rencana deploy Hermes ke muse-vm" to Icons.Filled.RocketLaunch,
    "Rancang strategi cuan bulan ini" to Icons.Filled.TrendingUp
)

/**
 * Layar kosong ala Muse: logo bulat, sapaan serif, chip saran.
 * Tap chip → teks masuk ke composer (user review dulu sebelum kirim).
 * Fase 2: bila belum ada model aktif, tampilkan baris status + jalan ke
 * Pengaturan — mencegah kegagalan sesudah saran diketuk.
 */
@Composable
fun ChatEmptyState(
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
    model: String = "",
    onOpenSettings: (() -> Unit)? = null,
    // v19.3 (Jiwa): tugas aktif dari todo.md → saran proaktif.
    todoSuggestions: List<String> = emptyList()
) {
    val scheme = MaterialTheme.colorScheme
    // v19.3: sapaan mengikuti jam perangkat (dihitung sekali per tampil).
    val greeting = remember {
        greetingForHour(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
    }
    // v19.3: bisa digulir — chip saran + tugas tidak terpotong di HP kecil /
    // saat keyboard terbuka — tetapi tetap rata tengah bila kontennya pendek
    // (heightIn(min = tinggi viewport)).
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val viewport = maxHeight
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .heightIn(min = viewport)
            .padding(horizontal = 28.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = scheme.primaryContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = scheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = greeting,
            style = MaterialTheme.typography.titleMedium,
            color = scheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Ada yang bisa Hermes bantu?",
            style = MaterialTheme.typography.displayLarge,
            color = scheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Kirim instruksi, tempel URL, atau pilih salah satu di bawah.",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (model.isBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Belum ada model aktif.",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant
                )
                if (onOpenSettings != null) {
                    TextButton(onClick = onOpenSettings) {
                        Text("Buka Pengaturan")
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // v19.3: saran proaktif dari tugas aktif (maks 2) di urutan
            // teratas — Hermes "ingat" apa yang sedang dikerjakan user.
            todoSuggestions.forEach { todo ->
                SuggestionPill(
                    text = "Bantu aku: $todo",
                    icon = Icons.Filled.TaskAlt,
                    highlighted = true,
                    onClick = { onSuggestion("Bantu aku menyelesaikan tugas ini: $todo") }
                )
            }
            SUGGESTIONS.forEach { (text, icon) ->
                Surface(
                    shape = PillShape,
                    color = scheme.surface,
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .clickable { onSuggestion(text) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = scheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onSurface
                        )
                    }
                }
            }
        }
    }
    }
}
/** Pil saran (dipakai saran tetap + saran tugas aktif). */
@Composable
private fun SuggestionPill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = PillShape,
        color = if (highlighted) scheme.primaryContainer else scheme.surface,
        border = if (highlighted) null else BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier
            .widthIn(max = 320.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (highlighted) scheme.onPrimaryContainer else scheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (highlighted) scheme.onPrimaryContainer else scheme.onSurface,
                maxLines = 2
            )
        }
    }
}

/** v19.3: sapaan Indonesia sesuai jam (0–23). Murni → bisa di-unit-test. */
internal fun greetingForHour(hour: Int): String = when (hour) {
    in 4..10 -> "Selamat pagi ☀️"
    in 11..14 -> "Selamat siang"
    in 15..17 -> "Selamat sore"
    else -> "Selamat malam 🌙"
}
/* akhir ChatEmptyState */


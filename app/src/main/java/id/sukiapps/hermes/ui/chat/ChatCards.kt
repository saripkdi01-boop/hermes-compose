package id.sukiapps.hermes.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.sukiapps.hermes.util.FileUtil

/**
 * Metadata berkas untuk kotak metadata di bubble (unduhan APK, berkas
 * gudang yang dibagikan). Dipakai [FileMetaBox]; produsen pesan berkas
 * mengisi field ini saat membuat [UiMessage].
 */
data class FileMeta(
    val name: String,
    val sizeBytes: Long,
    val visibility: String,
    val expiresAt: String
)

/**
 * Kartu opsi pilihan di dalam chat — pola widget dua tombol dari
 * tangkapan layar: opsi terpilih = bingkai tegas + ikon centang,
 * opsi lain redup. Ketuk opsi = kirim pilihan itu sebagai pesan
 * berikutnya (lewat [onSelect]). Satu kartu = satu item berkunci
 * stabil di daftar.
 */
@Composable
fun ChatOptionCard(
    options: List<String>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { index, option ->
            val selected = selectedIndex == index
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (selected) scheme.surface else scheme.surfaceVariant,
                border = BorderStroke(
                    if (selected) 2.dp else 1.dp,
                    if (selected) scheme.primary else scheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Pilih $option"
                    ) { onSelect(index) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Terpilih",
                            tint = scheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) scheme.onSurface
                        else scheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Kotak metadata berkas di dalam bubble (nama/ukuran/visibilitas/kedaluwarsa). */
@Composable
fun FileMetaBox(
    meta: FileMeta,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = scheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Description,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = scheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = meta.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${FileUtil.formatSize(meta.sizeBytes)} · " +
                        "${meta.visibility} · Berlaku sampai ${meta.expiresAt}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Lencana reaksi kecil yang menempel di sudut bubble (tampil saja). */
@Composable
fun ReactionBadge(
    reaction: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = reaction,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/**
 * Kutipan balasan di atas bubble: header "Membalas" + cuplikan pesan
 * asal. Ketuk cuplikan = lompat ke pesan asal (lewat [onJump]).
 */
@Composable
fun ReplyQuote(
    snippet: String,
    onJump: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClickLabel = "Lompat ke pesan asal") {
                onJump()
            }
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = "Membalas",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = snippet,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

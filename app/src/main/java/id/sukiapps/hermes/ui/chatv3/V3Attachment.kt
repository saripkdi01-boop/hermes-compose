package id.sukiapps.hermes.ui.chatv3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.sukiapps.hermes.ui.theme.HermesColors

/**
 * Jenis lampiran yang didukung.
 */
enum class V3AttachmentType { IMAGE, PDF, DOCX, ZIP, OTHER }

data class V3Attachment(
    val id: String,
    val type: V3AttachmentType,
    val name: String,
    val sizeBytes: Long = 0L,
    val localPath: String? = null
)

fun v3FormatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
}

private fun iconFor(type: V3AttachmentType): ImageVector = when (type) {
    V3AttachmentType.IMAGE -> Icons.Outlined.Image
    V3AttachmentType.PDF, V3AttachmentType.DOCX -> Icons.Outlined.Description
    V3AttachmentType.ZIP -> Icons.Outlined.FolderZip
    V3AttachmentType.OTHER -> Icons.Outlined.Description
}

/**
 * Kartu lampiran: ikon + nama + ukuran + aksi unduh/bagikan.
 */
@Composable
fun V3AttachmentCard(
    attachment: V3Attachment,
    onDownload: () -> Unit = {},
    onShare: () -> Unit = {},
    onOpen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        color = HermesColors.textSecondary().copy(alpha = 0.1f),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = iconFor(attachment.type),
                contentDescription = "Lampiran ${attachment.type}",
                tint = HermesColors.badgeUnread(),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = HermesColors.textPrimary(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = v3FormatSize(attachment.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = HermesColors.textSecondary()
                )
            }
            IconButton(onClick = onDownload) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = "Unduh",
                    tint = HermesColors.textSecondary()
                )
            }
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = "Bagikan",
                    tint = HermesColors.textSecondary()
                )
            }
        }
    }
}

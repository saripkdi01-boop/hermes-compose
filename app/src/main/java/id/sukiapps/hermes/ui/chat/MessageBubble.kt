package id.sukiapps.hermes.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/**
 * v9.5 — Bubble ala Muse: pesan assistant FLAT (tanpa bubble, teks langsung
 * di atas background + ikon sparkle kecil), pesan user dalam bubble hangat
 * membulat. Tekan lama → menu aksi (salin/edit/regenerate).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: UiMessage,
    isLastAssistant: Boolean,
    isSpeaking: Boolean,
    onSpeak: () -> Unit,
    onStopSpeak: () -> Unit,
    onCopy: () -> Unit = {},
    onEdit: () -> Unit = {},
    onRegenerate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"
    var menuOpen by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    if (isUser) {
        // ---- Pesan user: bubble hangat rata kanan ----
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp, 6.dp, 20.dp, 20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .combinedClickable(onClick = {}, onLongClick = { menuOpen = true })
                    .clip(RoundedCornerShape(20.dp, 6.dp, 20.dp, 20.dp))
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    AttachmentSummary(message)
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    } else {
        // ---- Pesan assistant: flat ala Muse, tanpa bubble ----
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .combinedClickable(onClick = {}, onLongClick = { menuOpen = true })
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Hermes",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(4.dp))
            AttachmentSummary(message)
            MarkdownText(
                text = message.content,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (isSpeaking) onStopSpeak() else onSpeak() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Filled.VolumeOff
                        else Icons.Filled.VolumeUp,
                        contentDescription = if (isSpeaking) "Hentikan bacaan"
                        else "Bacakan pesan",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(message.content))
                        onCopy()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Salin jawaban",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isLastAssistant) {
                    IconButton(
                        onClick = onRegenerate,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Buat ulang jawaban",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    DropdownMenu(
        expanded = menuOpen,
        onDismissRequest = { menuOpen = false }
    ) {
        DropdownMenuItem(
            text = { Text("Salin") },
            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
            onClick = {
                menuOpen = false
                clipboard.setText(AnnotatedString(message.content))
                onCopy()
            }
        )
        if (isUser) {
            DropdownMenuItem(
                text = { Text("Edit & kirim ulang") },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = { menuOpen = false; onEdit() }
            )
        }
        if (!isUser && isLastAssistant) {
            DropdownMenuItem(
                text = { Text("Buat ulang jawaban") },
                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                onClick = { menuOpen = false; onRegenerate() }
            )
        }
    }
}

@Composable
private fun AttachmentSummary(message: UiMessage) {
    if (message.attachmentSummary == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Description,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = message.attachmentSummary,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(6.dp))
}

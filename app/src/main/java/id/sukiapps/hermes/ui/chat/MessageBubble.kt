package id.sukiapps.hermes.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import id.sukiapps.hermes.ui.theme.UserBubbleShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v15 — Bubble ala Muse: pesan assistant FLAT dengan avatar bulat sparkle
 * di atas nama, pesan user dalam bubble hangat rata kanan (maksi 320dp).
 * Tekan lama → menu aksi (salin/edit/regenerate).
 *
 * 14.23 — kepadatan: padding bubble dipadatkan (user 16/10 → 12/8 dp,
 * kolom assistant 6 → 4 dp); area sentuh TIDAK dipadatkan (tombol aksi
 * tetap 32 dp seperti sebelumnya, tombol kirim/FAB 48 dp). Footer waktu
 * per bubble (HH:mm) dari [UiMessage.timestamp]; kutipan balasan,
 * lencana reaksi, kartu opsi, dan kotak metadata berkas dirender bila
 * datanya ada di pesan.
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
    onJumpToReply: (Long) -> Unit = {},
    onOptionSelect: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"
    var menuOpen by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val timeLabel = remember(message.timestamp) {
        if (message.timestamp > 0L) {
            SimpleDateFormat("HH:mm", Locale("id", "ID")).format(
                Date(message.timestamp)
            )
        } else {
            null
        }
    }
    if (isUser) {
        // ---- Pesan user: bubble hangat rata kanan ----
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            if (message.replyToSnippet != null) {
                ReplyQuote(
                    snippet = message.replyToSnippet,
                    onJump = { message.replyToId?.let(onJumpToReply) },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .widthIn(max = 320.dp)
                )
                Spacer(Modifier.height(4.dp))
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .widthIn(max = 320.dp)
            ) {
                Surface(
                    shape = UserBubbleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { menuOpen = true }
                        )
                        .clip(UserBubbleShape)
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        AttachmentSummary(message)
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (message.fileMeta != null) {
                            Spacer(Modifier.height(6.dp))
                            FileMetaBox(meta = message.fileMeta)
                        }
                        if (message.options != null) {
                            Spacer(Modifier.height(6.dp))
                            ChatOptionCard(
                                options = message.options,
                                selectedIndex = message.selectedOption,
                                onSelect = { onOptionSelect(message.options[it]) }
                            )
                        }
                    }
                }
                if (message.reaction != null) {
                    ReactionBadge(
                        reaction = message.reaction,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
            if (timeLabel != null) {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 16.dp, top = 2.dp)
                )
            }
        }
    } else {
        // ---- Pesan assistant: flat ala Muse, tanpa bubble ----
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .combinedClickable(onClick = {}, onLongClick = { menuOpen = true })
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Hermes",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(4.dp))
            // 14.28 (penanda sumber): setiap balasan asisten berlabel
            // sumber — balasan chat (LLM) vs ringkasan agen (output tool).
            Text(
                text = "Sumber: ${assistantSourceLabel(message.content)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            if (message.replyToSnippet != null) {
                ReplyQuote(
                    snippet = message.replyToSnippet,
                    onJump = { message.replyToId?.let(onJumpToReply) }
                )
                Spacer(Modifier.height(4.dp))
            }
            AttachmentSummary(message)
            MarkdownText(
                text = message.content,
                modifier = Modifier.fillMaxWidth()
            )
            if (message.fileMeta != null) {
                Spacer(Modifier.height(6.dp))
                FileMetaBox(meta = message.fileMeta)
            }
            if (message.options != null) {
                Spacer(Modifier.height(6.dp))
                ChatOptionCard(
                    options = message.options,
                    selectedIndex = message.selectedOption,
                    onSelect = { onOptionSelect(message.options[it]) }
                )
            }
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
                if (message.reaction != null) {
                    Spacer(Modifier.weight(1f))
                    ReactionBadge(reaction = message.reaction)
                }
            }
            if (timeLabel != null) {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
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
/* akhir MessageBubble */

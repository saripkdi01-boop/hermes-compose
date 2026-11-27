package id.sukiapps.hermes.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import id.sukiapps.hermes.ui.theme.currentChatPalette
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
    // Fase 2: true saat bubble ini adalah pratinjau streaming — tampilkan
    // kursor berkedip di ujung teks (token streamCaret yang selama ini
    // tidak dipakai).
    isStreaming: Boolean = false,
    // v19.2: reaksi emoji. null = fitur reaksi tidak ditampilkan
    // (mis. bubble pratinjau streaming).
    onReact: ((String) -> Unit)? = null,
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
                    // Fase 2: warna bubble hangat dari palet (userBubble),
                    // bukan surfaceVariant yang sama dengan kartu biasa.
                    // Kontras teks onSurface: >7:1 di terang maupun gelap.
                    color = currentChatPalette().userBubble,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { menuOpen = true }
                        )
                        .clip(UserBubbleShape)
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        AttachmentSummary(message)
                        ExpandableMessageContent(text = message.content) { shown ->
                            CompositionLocalProvider(
                                LocalContentColor provides MaterialTheme.colorScheme.onSurface
                            ) {
                                MarkdownText(
                                    text = shown,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
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
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .reactionToggle(message.reaction, onReact)
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
                // 14.28 (penanda sumber): setiap balasan asisten berlabel
                // sumber — balasan chat (LLM) vs ringkasan agen (output tool).
                // v19.2: label sejajar nama (hemat satu baris per pesan).
                Text(
                    text = "  ·  ${assistantSourceLabel(message.content)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(6.dp))
            if (message.replyToSnippet != null) {
                ReplyQuote(
                    snippet = message.replyToSnippet,
                    onJump = { message.replyToId?.let(onJumpToReply) }
                )
                Spacer(Modifier.height(4.dp))
            }
            AttachmentSummary(message)
            if (isStreaming) {
                MarkdownText(
                    text = Emoji.replaceShortcodes(message.content),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                StreamingCaret()
            } else {
                ExpandableMessageContent(text = message.content) { shown ->
                    // v19.2: shortcode :rocket: → 🚀 (blok kode tidak disentuh).
                    val pretty = remember(shown) { Emoji.replaceShortcodes(shown) }
                    MarkdownText(
                        text = pretty,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
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
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically,
                // Kompensasi padding sentuh 4 dp tombol pertama supaya ikon
                // tetap sejajar dengan teks jawaban.
                modifier = Modifier.offset(x = (-8).dp)
            ) {
                // v19.2 (a11y): ukuran bawaan IconButton (40 dp, area sentuh
                // 48 dp) — dulu dipaksa 32 dp, di bawah batas aksesibilitas.
                IconButton(
                    onClick = { if (isSpeaking) onStopSpeak() else onSpeak() }
                ) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.AutoMirrored.Filled.VolumeOff
                        else Icons.AutoMirrored.Filled.VolumeUp,
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
                    }
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
                        onClick = onRegenerate
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
                    ReactionBadge(
                        reaction = message.reaction,
                        modifier = Modifier.reactionToggle(message.reaction, onReact)
                    )
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
        if (onReact != null && message.id > 0) {
            QuickReactionRow(
                current = message.reaction,
                onReact = { e ->
                    menuOpen = false
                    onReact(e)
                }
            )
            HorizontalDivider()
        }
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

/**
 * Fase 2: kursor streaming — blok 2×18 dp warna streamCaret yang
 * berkedip 530 ms (cursorPulse). Menandai jawaban masih mengalir,
 * tanpa menggeser layout (inline di bawah teks).
 */
@Composable
private fun StreamingCaret() {
    val transition = rememberInfiniteTransition(label = "caret")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(530),
            repeatMode = RepeatMode.Reverse
        ),
        label = "caretBlink"
    )
    Box(
        modifier = Modifier
            .size(width = 2.dp, height = 18.dp)
            .alpha(alpha)
            .background(currentChatPalette().streamCaret)
    )
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
/** v19.2: ketuk lencana reaksi = lepas reaksi itu (bila reaksi diaktifkan). */
private fun Modifier.reactionToggle(reaction: String, onReact: ((String) -> Unit)?): Modifier =
    if (onReact == null) this
    else this
        .clip(CircleShape)
        .clickable(onClickLabel = "Lepas reaksi") { onReact(reaction) }
/* akhir MessageBubble */

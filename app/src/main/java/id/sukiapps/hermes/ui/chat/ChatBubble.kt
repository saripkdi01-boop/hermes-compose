package id.sukiapps.hermes.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.sukiapps.hermes.R
import id.sukiapps.hermes.ui.theme.HermesColors
import id.sukiapps.hermes.ui.theme.ThemeTokens
import id.sukiapps.hermes.ui.theme.dateSeparatorShape
import id.sukiapps.hermes.ui.theme.incomingBubbleShape
import id.sukiapps.hermes.ui.theme.outgoingBubbleShape
import id.sukiapps.hermes.ui.theme.reactionChipShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatTime(ts: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))

@Composable
private fun StatusIcon(status: MessageStatus) {
    val (icon, desc, tint) = when (status) {
        MessageStatus.SENDING -> Triple(Icons.Outlined.Schedule, R.string.hermes_msg_sending, HermesColors.textSecondary())
        MessageStatus.SENT -> Triple(Icons.Outlined.DoneAll, R.string.hermes_msg_sent, HermesColors.textSecondary())
        MessageStatus.FAILED -> Triple(Icons.Outlined.Close, R.string.hermes_msg_failed, HermesColors.destructive())
        MessageStatus.STOPPED -> Triple(Icons.Outlined.Check, R.string.hermes_msg_stopped, HermesColors.textSecondary())
    }
    Icon(icon, stringResource(desc), tint = tint, modifier = Modifier.size(14.dp))
}

/**
 * Satu bubble pesan.
 *
 * @param grouped true jika grup dengan pesan sebelumnya (gap rapat, tanpa ekor).
 * @param isLastInGroup true jika bubble terakhir dalam grup (dapat ekor).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: ChatMessage,
    grouped: Boolean,
    isLastInGroup: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onReplyTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isMine = message.sender == "user"
    val maxWidth = (LocalConfiguration.current.screenWidthDp * 0.78f).dp
    val bubbleColor = when (message.sender) {
        "user" -> HermesColors.bubbleOut()
        "upin" -> HermesColors.badgeUnread().copy(alpha = 0.25f) // aksen Upin berbeda
        else -> HermesColors.bubbleIn()
    }
    val shape = if (isMine) {
        outgoingBubbleShape(grouped = grouped || !isLastInGroup)
    } else {
        incomingBubbleShape(grouped = grouped || !isLastInGroup)
    }

    val bg = if (selected) HermesColors.badgeUnread().copy(alpha = 0.15f) else Color.Transparent

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .padding(
                horizontal = ThemeTokens.Dimen.screenPadding,
                vertical = if (grouped) ThemeTokens.Dimen.grid2 else ThemeTokens.Dimen.grid4
            ),
        contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .clip(shape)
                .background(bubbleColor)
                .combinedClickable(onClick = onClick, onLongClick = onLongPress)
                .padding(
                    horizontal = 12.dp,
                    vertical = 8.dp
                ),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
            // Label pengirim (pesan pertama grup, bukan milik sendiri).
            if (!isMine && !grouped && message.senderName.isNotBlank()) {
                Text(
                    message.senderName,
                    style = MaterialTheme.typography.labelMedium,
                    color = HermesColors.badgeUnread(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
            }

            // Kutipan balasan.
            if (message.replyToId != null) {
                Surface(
                    onClick = onReplyTap,
                    color = HermesColors.textSecondary().copy(alpha = 0.12f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Row(Modifier.padding(6.dp)) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(32.dp)
                                .background(HermesColors.badgeUnread())
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(
                                message.replyToName ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                color = HermesColors.badgeUnread(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                message.replyToPreview ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = HermesColors.textSecondary(),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Isi pesan (kode = monospace card).
            if (message.isCode) {
                Surface(
                    color = HermesColors.textPrimary().copy(alpha = 0.08f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        message.text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = HermesColors.textPrimary(),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            } else {
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = HermesColors.textPrimary()
                )
            }

            // Baris bawah: bintang + waktu + status.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (message.starred) {
                    Icon(
                        Icons.Outlined.Star, stringResource(R.string.hermes_cd_starred),
                        tint = HermesColors.badgeUnread(), modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    formatTime(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = HermesColors.textSecondary()
                )
                if (isMine) {
                    Spacer(Modifier.width(4.dp))
                    StatusIcon(message.status)
                }
            }

            // Reaksi sebagai chip di tepi bawah.
            if (message.reactions.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row {
                    message.reactions.entries.take(6).forEach { (emoji, count) ->
                        val mine = message.myReaction == emoji
                        Surface(
                            shape = reactionChipShape,
                            color = if (mine) HermesColors.badgeUnread().copy(alpha = 0.2f)
                            else HermesColors.textSecondary().copy(alpha = 0.12f),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                "$emoji $count",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Pemisah tanggal — pill kecil di tengah. */
@Composable
fun DateSeparator(label: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = dateSeparatorShape,
            color = HermesColors.textSecondary().copy(alpha = 0.15f),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = HermesColors.textSecondary(),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

/** Pesan sistem — pill tengah, bukan bubble. */
@Composable
fun SystemPill(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = dateSeparatorShape,
            color = HermesColors.badgeUnread().copy(alpha = 0.12f),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = HermesColors.textSecondary(),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

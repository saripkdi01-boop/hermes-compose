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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.sukiapps.hermes.ui.theme.HermesColors
import id.sukiapps.hermes.ui.theme.incomingBubbleShape
import id.sukiapps.hermes.ui.theme.outgoingBubbleShape

@Composable
fun V3Bubble(
    message: V3Message,
    grouped: Boolean,
    lastInGroup: Boolean,
    modifier: Modifier = Modifier
) {
    val isMine = message.sender == "user"
    val maxW = (LocalConfiguration.current.screenWidthDp * 0.78f).dp
    val bg = when (message.sender) {
        "user" -> HermesColors.bubbleOut()
        "upin" -> HermesColors.badgeUnread().copy(alpha = 0.25f)
        else -> HermesColors.bubbleIn()
    }
    val shape = if (isMine) outgoingBubbleShape(grouped = grouped || !lastInGroup)
    else incomingBubbleShape(grouped = grouped || !lastInGroup)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = 16.dp,
                vertical = if (grouped) 2.dp else 4.dp
            ),
        contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxW)
                .clip(shape)
                .background(bg)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (!isMine && !grouped && message.senderName.isNotBlank()) {
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.labelMedium,
                    color = HermesColors.badgeUnread(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            if (message.replyToId != null) {
                Surface(
                    color = HermesColors.textSecondary().copy(alpha = 0.12f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Row(modifier = Modifier.padding(6.dp)) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(32.dp)
                                .background(HermesColors.badgeUnread())
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = message.replyToName ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                color = HermesColors.badgeUnread(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = message.replyToPreview ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = HermesColors.textSecondary(),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            if (message.isCode) {
                Surface(
                    color = HermesColors.textPrimary().copy(alpha = 0.08f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = HermesColors.textPrimary(),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            } else {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = HermesColors.textPrimary()
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = v3Time(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = HermesColors.textSecondary()
                )
            }
            if (message.reactions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    message.reactions.entries.take(6).forEach { (emoji, count) ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = HermesColors.textSecondary().copy(alpha = 0.12f),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = "$emoji $count",
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

@Composable
fun V3DateSep(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = HermesColors.textSecondary().copy(alpha = 0.15f),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = HermesColors.textSecondary(),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun V3SysPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = HermesColors.badgeUnread().copy(alpha = 0.12f),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = HermesColors.textSecondary(),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

// Ukuran ikon kecil untuk status.
private val V3StatusIconSize = 14.dp

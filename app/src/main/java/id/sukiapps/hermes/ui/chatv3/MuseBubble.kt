package id.sukiapps.hermes.ui.chatv3

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import id.sukiapps.hermes.ui.theme.MuseChatDark

/**
 * Bubble gaya Muse: user = bubble subtle kanan, AI = teks bersih kiri.
 * Tanpa ekor, tanpa border berat — premium minimal.
 */
@Composable
fun MuseBubble(
    message: V3Message,
    modifier: Modifier = Modifier
) {
    val isUser = message.sender == "user"
    val maxW = (LocalConfiguration.current.screenWidthDp * 0.85f).dp
    val c = MuseChatDark // TODO: dukung light via isSystemInDarkTheme()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        if (isUser) {
            // User: bubble subtle di kanan.
            Box(
                modifier = Modifier
                    .widthIn(max = maxW)
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.userBubble)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.textPrimary,
                    lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.5f
                )
            }
        } else {
            // AI: teks bersih full-width, tanpa bubble.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                if (message.senderName.isNotBlank()) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelMedium,
                        color = c.accent,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                MuseText(text = message.text)
            }
        }
    }
}

/**
 * Blok kode gaya Muse: background gelap, border tipis, monospace.
 */
@Composable
fun MuseCodeBlock(
    code: String,
    modifier: Modifier = Modifier
) {
    val c = MuseChatDark
    // Bersihkan markdown fence jika ada.
    val clean = code.trim().removePrefix("```").removeSuffix("```").trim()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.codeBackground)
            .padding(16.dp)
    ) {
        Text(
            text = clean,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textPrimary,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.5f
        )
    }
}

/**
 * Indikator streaming gaya Muse: kursor pulse + teks mengalir.
 */
@Composable
fun MuseStreamingIndicator(
    partialText: String,
    modifier: Modifier = Modifier
) {
    val c = MuseChatDark
    val alpha by rememberInfiniteTransition(label = "museCursor").animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(530),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor"
    )
    Row(modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            text = partialText,
            style = MaterialTheme.typography.bodyLarge,
            color = c.textPrimary,
            lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.6f,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(20.dp)
                .alpha(alpha)
                .background(c.cursorColor)
                .align(Alignment.CenterVertically)
        )
    }
}

/**
 * Empty state gaya Muse: sapaan + kartu sugesti.
 */
@Composable
fun MuseEmptyState(
    onSuggestionClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val c = MuseChatDark
    val suggestions = listOf(
        "Jelaskan konsep ini dengan sederhana",
        "Bantu saya menulis kode",
        "Apa yang bisa kamu lakukan?"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Halo, saya Hermes",
            style = MaterialTheme.typography.headlineSmall,
            color = c.textPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Ada yang bisa saya bantu?",
            style = MaterialTheme.typography.bodyLarge,
            color = c.textSecondary
        )
        Spacer(modifier = Modifier.height(24.dp))
        suggestions.forEach { s ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.surface)
                    .padding(16.dp)
            ) {
                Text(
                    text = s,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

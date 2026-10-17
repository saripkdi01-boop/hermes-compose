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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import id.sukiapps.hermes.ui.theme.HermesColors
import id.sukiapps.hermes.ui.theme.incomingBubbleShape

/**
 * Indikator "mengetik..." — tiga titik berdenyut.
 */
@Composable
fun V3TypingDots(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, delayMillis = i * 170),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$i"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(HermesColors.streamingDot())
            )
        }
    }
}

/**
 * Bubble streaming: teks parsial + kursor berkedip + tombol Hentikan.
 */
@Composable
fun V3StreamingBubble(
    info: V3StreamInfo,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val maxW = (LocalConfiguration.current.screenWidthDp * 0.78f).dp
    val cursorAlpha by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(530),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxW)
                .clip(incomingBubbleShape(grouped = false))
                .background(HermesColors.bubbleIn())
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (info.partialText.isNotEmpty()) {
                Text(
                    text = info.partialText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = HermesColors.textPrimary()
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                if (info.state == V3StreamState.STREAMING) {
                    // Kursor berkedip.
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(16.dp)
                            .alpha(cursorAlpha)
                            .background(HermesColors.badgeUnread())
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HermesColors.destructive()
                        )
                    ) {
                        Icon(Icons.Outlined.Stop, contentDescription = "Hentikan")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Hentikan")
                    }
                }
            }
        }
    }
}

/**
 * Bubble gagal: bendera merah + tombol coba lagi. Tidak pernah hilang.
 */
@Composable
fun V3FailedBubble(
    error: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val maxW = (LocalConfiguration.current.screenWidthDp * 0.78f).dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxW)
                .clip(incomingBubbleShape(grouped = false))
                .background(HermesColors.destructive().copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = error ?: "Gagal memuat respons.",
                style = MaterialTheme.typography.bodyMedium,
                color = HermesColors.destructive()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onRetry) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Coba lagi")
            }
        }
    }
}

/**
 * Bubble progresif Tanya Upin: tahap + elapsed + tombol Batal.
 */
@Composable
fun V3UpinProgressBubble(
    job: V3UpinJob,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val maxW = (LocalConfiguration.current.screenWidthDp * 0.78f).dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxW)
                .clip(incomingBubbleShape(grouped = false))
                .background(HermesColors.badgeUnread().copy(alpha = 0.15f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Upin",
                style = MaterialTheme.typography.labelMedium,
                color = HermesColors.badgeUnread()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = v3UpinStageLabel(job.stage),
                style = MaterialTheme.typography.bodyMedium,
                color = HermesColors.textPrimary()
            )
            if (job.stage == V3UpinStage.PROCESSING) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${job.elapsedMs / 1000} dtk",
                    style = MaterialTheme.typography.labelSmall,
                    color = HermesColors.textSecondary()
                )
                V3TypingDots()
            }
            if (job.stage == V3UpinStage.QUEUED || job.stage == V3UpinStage.PROCESSING) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onCancel) {
                    Text(text = "Batal", color = HermesColors.destructive())
                }
            }
            if (job.stage == V3UpinStage.FAILED) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = job.error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = HermesColors.destructive()
                )
            }
        }
    }
}

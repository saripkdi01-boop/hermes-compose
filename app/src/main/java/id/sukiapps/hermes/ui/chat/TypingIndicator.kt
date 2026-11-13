package id.sukiapps.hermes.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Indikator status jujur (pengganti animasi typing legacy):
 * "Menyiapkan… / Menghubungi model… / Menulis…".
 */
@Composable
fun TypingIndicator(
    status: String,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "dots")
    // Fase 2: tiap titik berdenyut dengan jeda 0/150/300 ms — gelombang
    // berjalan kiri→kanan (sebelumnya ketiga titik berkedip serempak).
    // (Tiga pemanggilan eksplisit: animateFloat adalah @Composable dan
    // tidak boleh dipanggil dari dalam lambda List.)
    @Composable
    fun dotAlpha(delay: Int) = transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, delayMillis = delay),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse$delay"
    )
    val alpha0 by dotAlpha(0)
    val alpha1 by dotAlpha(150)
    val alpha2 by dotAlpha(300)
    val alphas = listOf(alpha0, alpha1, alpha2)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(3) { i ->
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .alpha(alphas[i].value)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

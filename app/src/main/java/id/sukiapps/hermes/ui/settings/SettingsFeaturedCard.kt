package id.sukiapps.hermes.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sukiapps.hermes.ui.theme.LocalSettingsDensity
import id.sukiapps.hermes.ui.theme.LocalSettingsMotion
import id.sukiapps.hermes.ui.theme.LocalSettingsShape
import id.sukiapps.hermes.ui.theme.LocalSettingPalette

/**
 * KOMP-1: Kartu unggulan status koneksi.
 * Gradien accent→accentPressed, radius 24dp, ikon status besar + pulse saat working,
 * 3 chip ringkasan, progress sirkular %, ketuk → Setup Checklist.
 */
@Composable
fun SettingsFeaturedCard(
    modelName: String?,
    connectorsReady: Int,
    connectorsTotal: Int,
    apiKeySaved: Boolean,
    overallStatus: FeatureStatus,
    setupProgress: Float, // 0f..1f
    setupLabel: String, // mis. "3 dari 4 wajib siap"
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSettingPalette.current
    val density = LocalSettingsDensity.current
    val motion = LocalSettingsMotion.current

    val entranceScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(motion.entranceMs),
        label = "entrance",
    )

    val statusIcon: ImageVector
    val statusText: String
    when (overallStatus) {
        is FeatureStatus.Ready -> { statusIcon = Icons.Filled.CheckCircle; statusText = "Semua siap" }
        is FeatureStatus.Error -> { statusIcon = Icons.Filled.Error; statusText = "Ada masalah" }
        is FeatureStatus.Working -> { statusIcon = Icons.Filled.Sync; statusText = "Memeriksa…" }
        else -> { statusIcon = Icons.Filled.Sync; statusText = "Belum lengkap" }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(0.96f + 0.04f * entranceScale)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(palette.featuredGradientStart, palette.featuredGradientEnd)
                )
            )
            .clickable(onClick = onClick)
            .padding(density.cardPadding)
            .semantics {
                contentDescription = "Status koneksi: $statusText. $setupLabel. Ketuk untuk checklist setup."
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Ikon status besar + pulse ring saat working
            Box(contentAlignment = Alignment.Center) {
                if (overallStatus is FeatureStatus.Working) {
                    val pulse by animateFloatAsState(
                        targetValue = 1.2f,
                        animationSpec = tween(1600),
                        label = "pulse",
                    )
                    Box(
                        Modifier.size(56.dp).scale(pulse)
                            .clip(CircleShape)
                            .background(palette.textOnAccent.copy(alpha = 0.2f)),
                    )
                }
                Icon(
                    statusIcon, contentDescription = null,
                    tint = palette.textOnAccent,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = statusText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textOnAccent,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = setupLabel,
                    fontSize = 13.sp,
                    color = palette.textOnAccent.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(8.dp))
                // 3 chip ringkasan
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SummaryChip("Model: ${modelName ?: "—"}")
                    SummaryChip("Konektor: $connectorsReady/$connectorsTotal siap")
                    SummaryChip(if (apiKeySaved) "API: tersimpan" else "API: belum")
                }
            }
            // Progress sirkular
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
                CircularProgressIndicator(
                    progress = { setupProgress },
                    modifier = Modifier.size(52.dp),
                    color = palette.textOnAccent,
                    trackColor = palette.textOnAccent.copy(alpha = 0.25f),
                    strokeWidth = 5.dp,
                )
                Text(
                    text = "${(setupProgress * 100).toInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textOnAccent,
                )
            }
        }
    }
}

@Composable
private fun SummaryChip(text: String) {
    val palette = LocalSettingPalette.current
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(palette.textOnAccent.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text, fontSize = 11.sp, color = palette.textOnAccent)
    }
}

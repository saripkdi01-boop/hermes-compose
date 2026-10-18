package id.sukiapps.hermes.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sukiapps.hermes.ui.theme.LocalSettingsDensity
import id.sukiapps.hermes.ui.theme.LocalSettingsMotion
import id.sukiapps.hermes.ui.theme.LocalSettingsShape
import id.sukiapps.hermes.ui.theme.LocalSettingPalette

// ===== KOMP-3: FeatureStatusChip =====

/** Status fitur untuk chip. */
sealed interface FeatureStatus {
    data object Ready : FeatureStatus
    data object Pending : FeatureStatus
    data class Error(val message: String = "") : FeatureStatus
    data object Working : FeatureStatus
    data object Unknown : FeatureStatus
}

/** Label Indonesian untuk status. */
internal fun FeatureStatus.label(): String = when (this) {
    is FeatureStatus.Ready -> "Siap"
    is FeatureStatus.Pending -> "Belum"
    is FeatureStatus.Error -> "Salah"
    is FeatureStatus.Working -> "Memeriksa"
    is FeatureStatus.Unknown -> "Tidak tahu"
}

@Composable
fun FeatureStatusChip(
    status: FeatureStatus,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSettingPalette.current
    val motion = LocalSettingsMotion.current
    val shape = LocalSettingsShape.current

    val bgColor = when (status) {
        is FeatureStatus.Ready -> palette.statusReady
        is FeatureStatus.Pending -> palette.statusPending
        is FeatureStatus.Error -> palette.statusError
        is FeatureStatus.Working -> palette.statusWorking
        is FeatureStatus.Unknown -> palette.textTertiary
    }
    val animBg by animateColorAsState(
        targetValue = bgColor.copy(alpha = 0.14f),
        animationSpec = tween(motion.chipChangeMs),
        label = "chipBg",
    )
    val animBorder by animateColorAsState(
        targetValue = bgColor.copy(alpha = 0.32f),
        animationSpec = tween(motion.chipChangeMs),
        label = "chipBorder",
    )
    val animText by animateColorAsState(
        targetValue = bgColor,
        animationSpec = tween(motion.chipChangeMs),
        label = "chipText",
    )

    // Bungkus 48dp agar target sentuh memenuhi syarat
    Box(
        modifier = modifier.heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(shape.chip)
                .background(animBg)
                .border(1.dp, animBorder, shape.chip)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .semantics { contentDescription = "Status: ${status.label()}" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (status) {
                is FeatureStatus.Working -> CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = animText,
                )
                is FeatureStatus.Ready -> Icon(
                    Icons.Filled.CheckCircle, contentDescription = null,
                    tint = animText, modifier = Modifier.size(14.dp),
                )
                is FeatureStatus.Error -> Icon(
                    Icons.Filled.Error, contentDescription = null,
                    tint = animText, modifier = Modifier.size(14.dp),
                )
                is FeatureStatus.Pending -> Icon(
                    Icons.Filled.Schedule, contentDescription = null,
                    tint = animText, modifier = Modifier.size(14.dp),
                )
                is FeatureStatus.Unknown -> Icon(
                    Icons.Filled.HelpOutline, contentDescription = null,
                    tint = animText, modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = status.label(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = animText,
            )
        }
    }
}

// ===== KOMP-2: SettingsRow =====

@Composable
fun SettingsRow(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String? = null,
    status: FeatureStatus = FeatureStatus.Unknown,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSettingPalette.current
    val density = LocalSettingsDensity.current
    val shape = LocalSettingsShape.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(density.listItemHeight)
            .clip(shape.cardSmall)
            .clickable(onClick = onClick)
            .padding(horizontal = density.cardPadding)
            .semantics {
                contentDescription = "$title, ${status.label()}. Ketuk untuk membuka."
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tile ikon 40dp
        Box(
            modifier = Modifier
                .size(density.listIconSize)
                .clip(shape.iconTile)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint,
                modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        FeatureStatusChip(status = status)
        Icon(
            Icons.Filled.ChevronRight, contentDescription = null,
            tint = palette.textTertiary, modifier = Modifier.size(20.dp),
        )
    }
}

// ===== KOMP-4: ActionBarPair =====

/**
 * Maksimal 2 tombol aksi: kiri tonal, kanan filled. Rasio 1:1 atau 2:1.
 */
@Composable
fun ActionBarPair(
    leftLabel: String,
    onLeftClick: () -> Unit,
    rightLabel: String,
    onRightClick: () -> Unit,
    leftEnabled: Boolean = true,
    rightEnabled: Boolean = true,
    ratio: Float = 1f, // lebar kiri : kanan = ratio : 1
    modifier: Modifier = Modifier,
) {
    val palette = LocalSettingPalette.current
    val shape = LocalSettingsShape.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(
            onClick = onLeftClick,
            enabled = leftEnabled,
            shape = shape.button,
            modifier = Modifier
                .weight(ratio)
                .height(48.dp),
        ) {
            Text(leftLabel, fontSize = 14.sp, color = palette.accent)
        }
        Button(
            onClick = onRightClick,
            enabled = rightEnabled,
            shape = shape.button,
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.accent,
                contentColor = palette.textOnAccent,
                disabledContainerColor = palette.textTertiary.copy(alpha = 0.3f),
            ),
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
        ) {
            Text(rightLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ===== KOMP-7: CollapsibleSection =====

@Composable
fun CollapsibleSection(
    title: String,
    status: FeatureStatus = FeatureStatus.Unknown,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSettingPalette.current
    val density = LocalSettingsDensity.current
    val shape = LocalSettingsShape.current
    val motion = LocalSettingsMotion.current

    // Auto-expand bila status Error
    var expanded by remember(status) {
        mutableStateOf(
            initiallyExpanded || status is FeatureStatus.Error
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape.card)
            .background(palette.surface)
            .border(1.dp, palette.border, shape.card),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(density.cardPadding)
                .semantics {
                    contentDescription = "$title, ${if (expanded) "terbuka" else "tertutup"}"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
                modifier = Modifier.weight(1f),
            )
            FeatureStatusChip(status = status)
            Spacer(Modifier.width(4.dp))
            val rotation by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                animationSpec = tween(motion.expandMs),
                label = "chevron",
            )
            Icon(
                Icons.Filled.ChevronRight, contentDescription = null,
                tint = palette.textSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = rotation },
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = expanded,
            enter = androidx.compose.animation.expandVertically(
                animationSpec = tween(motion.expandMs)
            ),
            exit = androidx.compose.animation.shrinkVertically(
                animationSpec = tween(motion.collapseMs)
            ),
        ) {
            Column {
                androidx.compose.material3.Divider(
                    color = palette.dividerSoft,
                    thickness = 1.dp,
                    modifier = Modifier.padding(start = 68.dp),
                )
                Box(Modifier.padding(density.cardPadding)) {
                    content()
                }
            }
        }
    }
}

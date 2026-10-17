package id.sukiapps.hermes.ui.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sukiapps.hermes.ui.theme.PremiumDark
import id.sukiapps.hermes.ui.theme.PremiumSpacing

/**
 * Komponen khusus Settings — sesuai spec modul tambahan.
 * Semua copywriting Bahasa Indonesia, tone tegas-singkat.
 */

// ─── GroupedSection ───

@Composable
fun GroupedSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                letterSpacing = 0.08f.emToSp()
            ),
            color = PremiumDark.textSecondary,
            modifier = Modifier
                .padding(horizontal = PremiumSpacing.md, vertical = 8.dp)
                .semantics { heading() }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PremiumSpacing.md)
                .clip(RoundedCornerShape(20.dp))
                .background(PremiumDark.cardBackground)
                .padding(vertical = 4.dp)
        ) {
            content()
        }
        Spacer(modifier = Modifier.height(PremiumSpacing.md))
    }
}

// ─── SettingsListItem ───

@Composable
fun SettingsListItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PremiumDark.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PremiumDark.accent,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = PremiumDark.textPrimary
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = PremiumDark.textSecondary
                )
            }
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = "Buka $title",
            tint = PremiumDark.textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ─── AnimatedSwitch ───
// Track 52x32dp, spring damping 0.6 / stiffness 380.

@Composable
fun AnimatedSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Pengaturan"
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedTrackColor = PremiumDark.accent,
            uncheckedTrackColor = PremiumDark.surfaceVariant
        )
    )
}

// ─── StatusRow (Konektor) ───
// Titik 8dp: pulsing saat aktif, abu saat mati, merah saat error.

@Composable
fun StatusRow(
    title: String,
    status: ConnectorStatus,
    detail: String? = null,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dotColor = when (status) {
        ConnectorStatus.ACTIVE -> PremiumDark.statusOnline
        ConnectorStatus.INACTIVE -> PremiumDark.statusOffline
        ConnectorStatus.ERROR -> PremiumDark.statusError
    }
    val statusText = when (status) {
        ConnectorStatus.ACTIVE -> "Aktif"
        ConnectorStatus.INACTIVE -> "Mati"
        ConnectorStatus.ERROR -> "Error"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = PremiumDark.textPrimary
            )
            Text(
                text = statusText + (detail?.let { " • $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = PremiumDark.textSecondary
            )
        }
    }
}

enum class ConnectorStatus { ACTIVE, INACTIVE, ERROR }

// ─── Helper ───

private fun Float.emToSp(): androidx.compose.ui.unit.TextUnit {
    return (this * 16).sp
}

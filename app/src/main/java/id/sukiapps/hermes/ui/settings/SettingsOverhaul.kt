package id.sukiapps.hermes.ui.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Visual restyle for Settings (original master prompt, applied as a restyle,
 * not a rewrite): the same 14 groups and the same ViewModel calls, rendered
 * with the spec tokens — 20dp horizontal padding, 24dp between sections,
 * 20dp card radius, 14dp chip/button radius, titleMedium SemiBold with
 * 0.15sp letter spacing, bodySmall descriptions at 0.7 alpha, tnum numbers,
 * surfaceContainerLow sections, surfaceContainerHigh on press (200ms
 * FastOutSlowInEasing), errorContainer danger boxes and tertiaryContainer
 * success notes. No new behaviour is invented here: every toggle still
 * persists through SettingsViewModel, every destructive action still goes
 * through its existing confirmation dialog.
 */

/** Section container: title plus optional trailing, then the section body. */
@Composable
fun SettingsSectionCard(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .animateContentSize(
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f)
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    letterSpacing = 0.15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) trailing()
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/**
 * One settings row: 24dp leading icon tinted primary, bodyLarge title,
 * bodySmall subtitle at 0.7 alpha, and a caller-supplied trailing slot
 * (switch, chevron or value text). Minimum touch target 48dp. Pressing
 * animates the row background to surfaceContainerHigh in 200ms.
 */
@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val rowBg by animateColorAsState(
        targetValue = if (pressed) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            Color.Transparent
        },
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "settingsItemBg",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(rowBg)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .semantics {
                contentDescription = buildString {
                    append(title)
                    if (subtitle != null) append(". $subtitle")
                }
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

/** Settings row whose trailing slot is a switch with animated track/thumb. */
@Composable
fun SettingsItemSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val track by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "switchTrack",
    )
    val thumb by animateColorAsState(
        targetValue = if (checked) {
            Color.White
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "switchThumb",
    )
    SettingsItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = { if (enabled) onCheckedChange(!checked) },
        modifier = modifier.semantics { },
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = track,
                checkedThumbColor = thumb,
                uncheckedTrackColor = track,
                uncheckedThumbColor = thumb,
                uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

/** Settings row whose trailing slot is a chevron (navigates elsewhere). */
@Composable
fun SettingsItemChevron(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Label on the left, live value on the right (tnum, 150ms Crossfade),
 * slider below. Steps follow Material 3 semantics: the number of discrete
 * intervals between min and max, minus one (temperature 0.0-2.0 step 0.1
 * means steps = 19).
 */
@Composable
fun SettingsSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    steps: Int = 0,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Crossfade(
                targetState = valueText,
                animationSpec = tween(150),
                label = "sliderValue",
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFeatureSettings = "tnum",
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "$label: $valueText" },
        )
    }
}

/** Dark-mode picker: Sistem / Terang / Gelap, backed by the theme pref. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsThemeSegmented(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        "system" to "Sistem",
        "light" to "Terang",
        "dark" to "Gelap",
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size,
                ),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Accent picker: three 48dp icon buttons. Colours are the existing Hermes
 * palette values (coral = primary, teal = HermesTeal, sand = tertiary sand),
 * not a new scheme — HermesTheme maps the stored key to the full scheme.
 */
@Composable
fun SettingsAccentRow(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        Triple("coral", "Aksen coral", MaterialTheme.colorScheme.primary),
        Triple("teal", "Aksen teal", Color(0xFF0E7C86)),
        Triple("sand", "Aksen pasir", Color(0xFF6F5F45)),
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { (value, desc, color) ->
            val isSelected = selected == value
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                2.dp,
                                MaterialTheme.colorScheme.onSurface,
                                CircleShape,
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clickable(
                        onClick = { onSelect(value) },
                        role = Role.Button,
                    )
                    .semantics { contentDescription = desc },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        Text(
            text = when (selected) {
                "teal" -> "Teal"
                "sand" -> "Pasir"
                else -> "Coral"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 8dp status dot. Green when connected, error colour otherwise; pulses
 * (alpha breathing) only while [connecting] is true.
 */
@Composable
fun StatusDot(
    connected: Boolean,
    connecting: Boolean,
    modifier: Modifier = Modifier,
) {
    val base = if (connected) {
        Color(0xFF2E7D32)
    } else {
        MaterialTheme.colorScheme.error
    }
    if (!connecting) {
        Box(
            modifier = modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(base)
                .semantics {
                    contentDescription = if (connected) {
                        "Terhubung"
                    } else {
                        "Tidak terhubung"
                    }
                },
        )
        return
    }
    val transition = rememberInfiniteTransition(label = "statusDotPulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotAlpha",
    )
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(base.copy(alpha = alpha))
            .semantics { contentDescription = "Menghubungkan" },
    )
}

/**
 * Profile header for Settings: no user name or email is invented — the app
 * stores neither, so the header shows the app identity (initial circle with
 * a 2dp gradient border), the active model and the provider base URL, both
 * real values from state.
 */
@Composable
fun SettingsProfileHeader(
    model: String,
    baseUrl: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(
                    2.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary,
                        )
                    ),
                    CircleShape,
                )
                .semantics { contentDescription = "Avatar Hermes" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "H",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Hermes",
                style = MaterialTheme.typography.titleMedium.copy(
                    letterSpacing = 0.15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
            )
            Text(
                text = model.ifBlank { "Belum ada model dipilih" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (baseUrl.isNotBlank()) {
                Text(
                    text = baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Danger zone container: errorContainer fill, 1dp error border, 16dp radius. */
@Composable
fun SettingsDangerBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .border(
                1.dp,
                MaterialTheme.colorScheme.error,
                RoundedCornerShape(16.dp),
            )
            .padding(12.dp),
    ) {
        content()
    }
}

/** Success note: tertiaryContainer chip for saved/synced confirmations. */
@Composable
fun SettingsSuccessNote(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = text },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

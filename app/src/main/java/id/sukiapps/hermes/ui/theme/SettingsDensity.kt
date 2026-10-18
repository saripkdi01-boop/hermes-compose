package id.sukiapps.hermes.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Density untuk layar Pengaturan — compact (padat) vs comfortable (lega).
 * Ganti via prefs "settings_density".
 */
data class SettingsDensity(
    val listItemHeight: Dp, // tinggi baris fitur
    val listIconSize: Dp, // ukuran tile ikon
    val listIconRadius: Dp, // radius tile ikon
    val cardRadius: Dp,
    val cardPadding: Dp,
    val sectionGap: Dp,
    val itemGap: Dp,
    val fieldGap: Dp,
    val heroHeight: Dp,
    val touchTarget: Dp // 48dp — WAJIB dipatuhi semua target sentuh
)

val SettingsDensityCompact = SettingsDensity(
    listItemHeight = 64.dp,
    listIconSize = 40.dp,
    listIconRadius = 12.dp,
    cardRadius = 20.dp,
    cardPadding = 16.dp,
    sectionGap = 24.dp,
    itemGap = 8.dp,
    fieldGap = 12.dp,
    heroHeight = 148.dp,
    touchTarget = 48.dp
)

val SettingsDensityComfortable = SettingsDensity(
    listItemHeight = 72.dp,
    listIconSize = 44.dp,
    listIconRadius = 14.dp,
    cardRadius = 20.dp,
    cardPadding = 20.dp,
    sectionGap = 28.dp,
    itemGap = 10.dp,
    fieldGap = 14.dp,
    heroHeight = 160.dp,
    touchTarget = 48.dp
)

/** Default = compact. HermesTheme mengganti sesuai prefs. */
val LocalSettingsDensity = staticCompositionLocalOf { SettingsDensityCompact }

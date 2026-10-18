package id.sukiapps.hermes.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Palette khusus layar Pengaturan — diturunkan dari ChatPalette
 * (varian PREMIUM) agar tidak duplikasi definisi warna.
 *
 * Field tambahan yang spesifik untuk settings (gradient featured card,
 * border, shadow) didefinisikan di sini.
 */
data class SettingPalette(
    // Diwarisi dari ChatPalette premium
    val screenBackground: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val divider: Color,
    val error: Color,
    val skeletonBase: Color,
    val skeletonShimmer: Color,
    // Spesifik settings
    val screenBackgroundElevated: Color,
    val surfaceElevated: Color,
    val surfaceSunken: Color,
    val featuredGradientStart: Color,
    val featuredGradientEnd: Color,
    val border: Color,
    val borderStrong: Color,
    val dividerSoft: Color,
    val textOnAccent: Color,
    val accentHover: Color,
    val accentPressed: Color,
    val accentSoft: Color,
    val statusReady: Color,
    val statusPending: Color,
    val statusError: Color,
    val statusWorking: Color,
    val shadowKey: Color,
    val scrim: Color,
    val shimmerHighlight: Color
)

fun settingLightPalette(): SettingPalette {
    val base = premiumLightPalette()
    return SettingPalette(
        screenBackground = base.background,
        surface = base.surface,
        textPrimary = base.textPrimary,
        textSecondary = base.textSecondary,
        textTertiary = base.textTertiary,
        accent = base.accent,
        divider = base.divider,
        error = base.errorColor,
        skeletonBase = base.skeletonBase,
        skeletonShimmer = base.skeletonShimmer,
        screenBackgroundElevated = Color(0xFFFFFFFF),
        surfaceElevated = Color(0xFFFFFFFF),
        surfaceSunken = Color(0xFFF0F2F5),
        featuredGradientStart = base.accent,
        featuredGradientEnd = Color(0xFF0B6E5F),
        border = Color(0xFFE9EDEF),
        borderStrong = Color(0xFFD1D7DB),
        dividerSoft = Color(0xFFF0F2F5),
        textOnAccent = Color(0xFFFFFFFF),
        accentHover = Color(0xFF15A085),
        accentPressed = Color(0xFF0E7A64),
        accentSoft = Color(0xFFD1F4CC),
        statusReady = Color(0xFF128C7E),
        statusPending = Color(0xFF8696A0),
        statusError = Color(0xFFE5484D),
        statusWorking = Color(0xFF128C7E),
        shadowKey = Color(0x1A000000),
        scrim = Color(0x52000000),
        shimmerHighlight = Color(0xFFFFFFFF)
    )
}

fun settingDarkPalette(): SettingPalette {
    val base = premiumDarkPalette()
    return SettingPalette(
        screenBackground = base.background,
        surface = base.surface,
        textPrimary = base.textPrimary,
        textSecondary = base.textSecondary,
        textTertiary = base.textTertiary,
        accent = base.accent,
        divider = base.divider,
        error = base.errorColor,
        skeletonBase = base.skeletonBase,
        skeletonShimmer = base.skeletonShimmer,
        screenBackgroundElevated = Color(0xFF1F2C34),
        surfaceElevated = Color(0xFF2A3942),
        surfaceSunken = Color(0xFF0B141A),
        featuredGradientStart = base.accent,
        featuredGradientEnd = Color(0xFF1A7A5A),
        border = Color(0xFF222D34),
        borderStrong = Color(0xFF2A3942),
        dividerSoft = Color(0xFF1A252D),
        textOnAccent = Color(0xFF0B141A),
        accentHover = Color(0xFF2BE07A),
        accentPressed = Color(0xFF1EB85A),
        accentSoft = Color(0xFF1A4D3A),
        statusReady = Color(0xFF25D366),
        statusPending = Color(0xFF8696A0),
        statusError = Color(0xFFE5484D),
        statusWorking = Color(0xFF25D366),
        shadowKey = Color(0x40000000),
        scrim = Color(0xB3000000),
        shimmerHighlight = Color(0xFF3A3A3E)
    )
}

val LocalSettingPalette = staticCompositionLocalOf { settingLightPalette() }

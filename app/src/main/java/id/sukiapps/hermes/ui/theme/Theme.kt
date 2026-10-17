package id.sukiapps.hermes.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import id.sukiapps.hermes.ui.theme.ThemeTokens.Darken
import id.sukiapps.hermes.ui.theme.ThemeTokens.Light

private val HermesLightScheme = lightColorScheme(
    primary = Light.primary,
    onPrimary = Light.onPrimary,
    primaryContainer = Light.bubbleOut,
    onPrimaryContainer = Light.textPrimary,
    secondary = Light.accent,
    onSecondary = Light.onPrimary,
    surface = Light.surface,
    onSurface = Light.textPrimary,
    surfaceVariant = Light.surfaceVariant,
    onSurfaceVariant = Light.textSecondary,
    background = Light.surface,
    onBackground = Light.textPrimary,
    error = Light.destructive,
    onError = Light.onPrimary,
    outline = Light.divider,
    outlineVariant = Light.divider
)

private val HermesDarkScheme = darkColorScheme(
    primary = Dark.primary,
    onPrimary = Dark.onPrimary,
    primaryContainer = Dark.bubbleOut,
    onPrimaryContainer = Dark.textPrimary,
    secondary = Dark.accent,
    onSecondary = Dark.onBadgeUnread,
    surface = Dark.surface,
    onSurface = Dark.textPrimary,
    surfaceVariant = Dark.surfaceVariant,
    onSurfaceVariant = Dark.textSecondary,
    background = Dark.surface,
    onBackground = Dark.textPrimary,
    error = Dark.destructive,
    onError = Dark.onPrimary,
    outline = Dark.divider,
    outlineVariant = Dark.divider
)

/**
 * Tipografi Hermes — SEMUA gaya >= 12sp. Tidak ada 8–11sp.
 * Line height 1.35x sesuai ThemeTokens.Type.
 */
private val HermesTypography = Typography(
    titleMedium = TextStyle(
        fontSize = ThemeTokens.Type.name,
        lineHeight = ThemeTokens.Type.lineHeightFor(ThemeTokens.Type.name),
        fontWeight = FontWeight.Bold
    ),
    bodyLarge = TextStyle(
        fontSize = ThemeTokens.Type.message,
        lineHeight = ThemeTokens.Type.lineHeightFor(ThemeTokens.Type.message),
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = TextStyle(
        fontSize = ThemeTokens.Type.messageSecondary,
        lineHeight = ThemeTokens.Type.lineHeightFor(ThemeTokens.Type.messageSecondary),
        fontWeight = FontWeight.Normal
    ),
    labelSmall = TextStyle(
        fontSize = ThemeTokens.Type.timestamp, // 12sp = minimum
        lineHeight = ThemeTokens.Type.lineHeightFor(ThemeTokens.Type.timestamp),
        fontWeight = FontWeight.Normal
    ),
    labelMedium = TextStyle(
        fontSize = ThemeTokens.Type.caption, // 12sp = minimum
        lineHeight = ThemeTokens.Type.lineHeightFor(ThemeTokens.Type.caption),
        fontWeight = FontWeight.Normal
    )
)

/**
 * HermesTheme — satu-satunya theme aplikasi.
 *
 * @param darkTheme mengikuti pengaturan sistem/aplikasi. Tidak ada layar
 * yang boleh jatuh ke fallback tema terang saat dark aktif.
 * @param dynamicColor SELALU false — palet Hermes dikunci dari ThemeTokens,
 * dynamic color Android 12+ dilarang agar identitas visual konsisten.
 */
@Composable
fun HermesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) HermesDarkScheme else HermesLightScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val appBar = (if (darkTheme) Dark.appBar else Light.appBar).toArgb()
            window.statusBarColor = appBar
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = false // appBar selalu gelap
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HermesTypography,
        content = content
    )
}

/**
 * Akses token warna sesuai tema aktif. Pakai ini di semua screen,
 * JANGAN baca ThemeTokens.Light/Dark langsung dari composable.
 */
object HermesColors {
    @Composable
    fun appBar() = if (isSystemInDarkTheme()) Dark.appBar else Light.appBar
    @Composable
    fun chatBackground() = if (isSystemInDarkTheme()) Dark.chatBackground else Light.chatBackground
    @Composable
    fun bubbleOut() = if (isSystemInDarkTheme()) Dark.bubbleOut else Light.bubbleOut
    @Composable
    fun bubbleIn() = if (isSystemInDarkTheme()) Dark.bubbleIn else Light.bubbleIn
    @Composable
    fun textPrimary() = if (isSystemInDarkTheme()) Dark.textPrimary else Light.textPrimary
    @Composable
    fun textSecondary() = if (isSystemInDarkTheme()) Dark.textSecondary else Light.textSecondary
    @Composable
    fun divider() = if (isSystemInDarkTheme()) Dark.divider else Light.divider
    @Composable
    fun destructive() = if (isSystemInDarkTheme()) Dark.destructive else Light.destructive
    @Composable
    fun badgeUnread() = if (isSystemInDarkTheme()) Dark.badgeUnread else Light.badgeUnread
    @Composable
    fun onBadgeUnread() = if (isSystemInDarkTheme()) Dark.onBadgeUnread else Light.onBadgeUnread
    @Composable
    fun streamingDot() = if (isSystemInDarkTheme()) Dark.streamingDot else Light.streamingDot
    @Composable
    fun quoteBar() = if (isSystemInDarkTheme()) Dark.quoteBar else Light.quoteBar
    @Composable
    fun upinAccent() = if (isSystemInDarkTheme()) Dark.upinAccent else Light.upinAccent
    @Composable
    fun systemPill() = if (isSystemInDarkTheme()) Dark.systemPill else Light.systemPill
    @Composable
    fun onSystemPill() = if (isSystemInDarkTheme()) Dark.onSystemPill else Light.onSystemPill
    @Composable
    fun surfaceVariant() = if (isSystemInDarkTheme()) Dark.surfaceVariant else Light.surfaceVariant
}

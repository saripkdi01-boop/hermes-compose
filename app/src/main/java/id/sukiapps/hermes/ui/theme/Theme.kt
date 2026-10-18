package id.sukiapps.hermes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tema Hermes v14 — satu pintu masuk untuk seluruh UI.
 *
 * @param darkTheme true = gelap, false = terang.
 * @param contentStyle MUSE (chat) atau PREMIUM (settings/soul).
 */
enum class ContentStyle { MUSE, PREMIUM }

private val V14Typography = Typography(
    displayLarge = TextStyle(
        fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp
    ),
    headlineMedium = TextStyle(
        fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontSize = 12.sp, fontWeight = FontWeight.Medium,
        lineHeight = 16.sp, letterSpacing = (0.08f).sp
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp
    )
)

@Composable
fun HermesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    contentStyle: ContentStyle = ContentStyle.MUSE,
    content: @Composable () -> Unit
) {
    val palette = when (contentStyle) {
        ContentStyle.MUSE -> if (darkTheme) museDarkPalette() else museLightPalette()
        ContentStyle.PREMIUM -> if (darkTheme) premiumDarkPalette() else premiumLightPalette()
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.textPrimary,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceVariant,
            onSurfaceVariant = palette.textSecondary,
            error = palette.errorColor,
            onError = palette.textPrimary,
            outline = palette.divider,
            outlineVariant = palette.divider
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.background,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceVariant,
            onSurfaceVariant = palette.textSecondary,
            error = palette.errorColor,
            onError = palette.textPrimary,
            outline = palette.divider,
            outlineVariant = palette.divider
        )
    }

    CompositionLocalProvider(LocalChatPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = V14Typography,
            content = content
        )
    }
}

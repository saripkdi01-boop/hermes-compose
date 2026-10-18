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
 * Menyediakan: MaterialTheme + LocalChatPalette + LocalSettingPalette
 * + LocalSettingsDensity + LocalSettingsShape + LocalSettingsMotion.
 *
 * @param darkTheme true = gelap, false = terang.
 * @param contentStyle MUSE (chat) atau PREMIUM (settings/soul).
 * @param settingsDensity compact atau comfortable (dari prefs).
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
    settingsDensity: SettingsDensity = SettingsDensityCompact,
    content: @Composable () -> Unit
) {
    val chatPalette = when (contentStyle) {
        ContentStyle.MUSE -> if (darkTheme) museDarkPalette() else museLightPalette()
        ContentStyle.PREMIUM -> if (darkTheme) premiumDarkPalette() else premiumLightPalette()
    }
    val settingPalette =
        if (darkTheme) settingDarkPalette() else settingLightPalette()

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = chatPalette.accent,
            onPrimary = chatPalette.textPrimary,
            background = chatPalette.background,
            onBackground = chatPalette.textPrimary,
            surface = chatPalette.surface,
            onSurface = chatPalette.textPrimary,
            surfaceVariant = chatPalette.surfaceVariant,
            onSurfaceVariant = chatPalette.textSecondary,
            error = chatPalette.errorColor,
            onError = chatPalette.textPrimary,
            outline = chatPalette.divider,
            outlineVariant = chatPalette.divider
        )
    } else {
        lightColorScheme(
            primary = chatPalette.accent,
            onPrimary = chatPalette.background,
            background = chatPalette.background,
            onBackground = chatPalette.textPrimary,
            surface = chatPalette.surface,
            onSurface = chatPalette.textPrimary,
            surfaceVariant = chatPalette.surfaceVariant,
            onSurfaceVariant = chatPalette.textSecondary,
            error = chatPalette.errorColor,
            onError = chatPalette.textPrimary,
            outline = chatPalette.divider,
            outlineVariant = chatPalette.divider
        )
    }

    CompositionLocalProvider(
        LocalChatPalette provides chatPalette,
        LocalSettingPalette provides settingPalette,
        LocalSettingsDensity provides settingsDensity,
        LocalSettingsShape provides DefaultSettingsShape,
        LocalSettingsMotion provides DefaultSettingsMotion
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = V14Typography,
            content = content
        )
    }
}

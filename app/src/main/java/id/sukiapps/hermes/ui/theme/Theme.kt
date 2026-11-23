package id.sukiapps.hermes.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
 * @param accent kunci aksen tersimpan: "coral" | "teal" | "sand" |
 *   "dynamic" (Material You dari wallpaper, Android 12+; di bawah itu
 *   jatuh ke coral).
 */
enum class ContentStyle { MUSE, PREMIUM }

private val V14Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 28.sp, fontWeight = FontWeight.Normal, lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 20.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.8.sp
    ),
    // v18: bodyMedium 13sp → 14sp (jarak baris 21sp) — teks isi lebih
    // nyaman dibaca di layar HP tanpa mengubah hierarki tipografi lain.
    bodyMedium = TextStyle(
        fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp
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

/**
 * v14.18 accent mapping: the stored accent key maps to colours that already
 * exist in this package — coral keeps the previous primary, teal is
 * HermesTeal, sand is the existing tertiary sand. Containers for teal/sand
 * are the accent at low alpha over the surface (no new scheme is invented).
 */
private data class AccentColors(
    val primary: Color,
    val onPrimary: Color,
    val container: Color,
    val onContainer: Color,
)

private fun accentColors(
    accent: String,
    darkTheme: Boolean,
    chatPalette: ChatPalette,
    contentStyle: ContentStyle,
): AccentColors {
    // Non-MUSE styles keep their own accent (PREMIUM is green); the stored
    // accent key only restyles the MUSE style used by MainActivity.
    if (contentStyle != ContentStyle.MUSE) {
        return AccentColors(
            chatPalette.accent,
            if (darkTheme) chatPalette.textPrimary else chatPalette.background,
            chatPalette.accentDim,
            chatPalette.textPrimary,
        )
    }
    return when (accent) {
        "teal" -> if (darkTheme) {
            AccentColors(
                HermesTealDark, Color(0xFF062A2E),
                HermesTealDark.copy(alpha = 0.20f), HermesTealDark,
            )
        } else {
            AccentColors(
                HermesTeal, Color.White,
                HermesTeal.copy(alpha = 0.16f), HermesTeal,
            )
        }
        "sand" -> if (darkTheme) {
            AccentColors(
                Color(0xFFD8C9AE), Color(0xFF1F1B16),
                Color(0xFFD8C9AE).copy(alpha = 0.20f), Color(0xFFD8C9AE),
            )
        } else {
            AccentColors(
                Color(0xFF6F5F45), Color.White,
                Color(0xFF6F5F45).copy(alpha = 0.16f), Color(0xFF6F5F45),
            )
        }
        else -> if (darkTheme) {
            AccentColors(
                MuseCoralLight, Color(0xFF2B1208),
                MuseCoralContainerDark, Color(0xFFFFD9C7),
            )
        } else {
            AccentColors(
                MuseCoral, Color.White,
                MuseCoralContainerLight, MuseCoralDark,
            )
        }
    }
}

@Composable
fun HermesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    contentStyle: ContentStyle = ContentStyle.MUSE,
    settingsDensity: SettingsDensity = SettingsDensityCompact,
    accent: String = "coral",
    content: @Composable () -> Unit
) {
    val chatPalette = when (contentStyle) {
        ContentStyle.MUSE -> if (darkTheme) museDarkPalette() else museLightPalette()
        ContentStyle.PREMIUM -> if (darkTheme) premiumDarkPalette() else premiumLightPalette()
    }
    val settingPalette =
        if (darkTheme) settingDarkPalette() else settingLightPalette()
    // v18: Material You — aksen "dynamic" mengambil warna primer dari
    // wallpaper perangkat (dynamic color scheme, API 31+). Permukaan dan
    // teks tetap palet Warm Muse Hermes; hanya aksen yang mengikuti
    // sistem, sama seperti aksen tetap lainnya. Di bawah API 31, gaya
    // PREMIUM, atau kunci aksen lain → pemetaan aksen tetap seperti biasa.
    val context = LocalContext.current
    val dynamicScheme = if (
        accent == "dynamic" && contentStyle == ContentStyle.MUSE &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        if (darkTheme) dynamicDarkColorScheme(context)
        else dynamicLightColorScheme(context)
    } else {
        null
    }
    val accentColors = if (dynamicScheme != null) {
        AccentColors(
            dynamicScheme.primary, dynamicScheme.onPrimary,
            dynamicScheme.primaryContainer, dynamicScheme.onPrimaryContainer,
        )
    } else {
        accentColors(accent, darkTheme, chatPalette, contentStyle)
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = accentColors.primary,
            onPrimary = accentColors.onPrimary,
            primaryContainer = accentColors.container,
            onPrimaryContainer = accentColors.onContainer,
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
            primary = accentColors.primary,
            onPrimary = accentColors.onPrimary,
            primaryContainer = accentColors.container,
            onPrimaryContainer = accentColors.onContainer,
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
            shapes = HermesShapes,
            content = content
        )
    }
}

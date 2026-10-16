package id.sukiapps.hermes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Tema Hermes. dark-default mengikuti preferensi legacy (K_THEME default "dark").
 * @param darkTheme null = ikuti preferensi tersimpan (di-resolve pemanggil).
 */
@Composable
fun HermesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MuseDark.ColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = HermesTypography,
        content = content
    )
}

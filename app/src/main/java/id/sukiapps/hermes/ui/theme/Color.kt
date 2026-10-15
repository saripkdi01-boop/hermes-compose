package id.sukiapps.hermes.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Palet brand Hermes — dipertahankan dari legacy Ui.java (v7.0).
// TEAL = primary, CORAL = error/attention.
val Teal = Color(0xFF0AA6B9)
val TealDark = Color(0xFF078A99)
val TealContainerDark = Color(0xFF0A3B42)
val TealContainerLight = Color(0xFFCDEEF2)
val Coral = Color(0xFFEA5541)
val CoralDark = Color(0xFFFF8A75)
val CoralContainerDark = Color(0xFF4A2119)
val CoralContainerLight = Color(0xFFFFDAD3)
val Sand = Color(0xFFF5EBDD)

val DarkBackground = Color(0xFF0E1513)
val DarkSurface = Color(0xFF17201D)
val DarkSurfaceVariant = Color(0xFF1F2A26)
val DarkOnBackground = Color(0xFFE8EFEc)
val DarkOnSurface = Color(0xFFE8EFEc)
val DarkOutline = Color(0xFF3A4742)

val LightBackground = Color(0xFFF7FAF9)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEAF1EE)
val LightOnBackground = Color(0xFF172321)
val LightOnSurface = Color(0xFF172321)
val LightOutline = Color(0xFFD3DCD8)

val DarkColorScheme = darkColorScheme(
    primary = Teal,
    onPrimary = Color(0xFF062A2E),
    primaryContainer = TealContainerDark,
    onPrimaryContainer = Color(0xFFBDF3F8),
    secondary = Color(0xFF8FD8E0),
    onSecondary = Color(0xFF062A2E),
    error = CoralDark,
    onError = Color(0xFF3D0E06),
    errorContainer = CoralContainerDark,
    onErrorContainer = Color(0xFFFFCFC4),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB9C7C1),
    outline = DarkOutline,
    outlineVariant = Color(0xFF2A3531)
)

val LightColorScheme = lightColorScheme(
    primary = TealDark,
    onPrimary = Color.White,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = Color(0xFF063B42),
    secondary = TealDark,
    onSecondary = Color.White,
    error = Coral,
    onError = Color.White,
    errorContainer = CoralContainerLight,
    onErrorContainer = Color(0xFF4A1410),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF3F4B47),
    outline = LightOutline,
    outlineVariant = Color(0xFFE2E9E6)
)

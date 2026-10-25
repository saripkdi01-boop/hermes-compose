package id.sukiapps.hermes.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// v9.5 — Palet "Warm Muse": 80% nuansa Muse AI (cream hangat + coral),
// identitas Hermes dipertahankan lewat aksen teal sekunder.
// Light: kertas hangat, teks tinta pekat, aksen coral Muse.
// Dark: coklat-abu hangat (bukan hitam kebiruan), aksen coral terang.

// ---- Aksen utama (coral Muse) ----
val MuseCoral = Color(0xFFB4552D)
val MuseCoralDark = Color(0xFF8C3A1B)
val MuseCoralLight = Color(0xFFFFB59B)
val MuseCoralContainerLight = Color(0xFFF6DDD0)
val MuseCoralContainerDark = Color(0xFF4A2317)

// ---- Aksen sekunder (teal Hermes, dipakai hemat: badge, status OK) ----
val HermesTeal = Color(0xFF0E7C86)
val HermesTealDark = Color(0xFF7FD4DC)

// ---- Netral hangat ----
val CreamBg = Color(0xFFF7F3EC)        // kertas hangat (light bg)
val CreamSurface = Color(0xFFFFFDF8)   // kartu di atas kertas
val CreamVariant = Color(0xFFF1E8DA)  // bubble user / surface variant
val InkText = Color(0xFF1F1B16)       // teks utama light
val InkMuted = Color(0xFF6E6659)      // teks sekunder light
val CreamLine = Color(0xFFE7DFD2)     // garis/border light

val CoalBg = Color(0xFF171410)        // dark bg hangat
val CoalSurface = Color(0xFF211C16)   // dark surface
val CoalVariant = Color(0xFF2A241C)   // dark variant
val CoalText = Color(0xFFF0E9DC)      // teks utama dark
val CoalMuted = Color(0xFFA79C8A)     // teks sekunder dark
val CoalLine = Color(0xFF38312A)      // garis/border dark

// Error tetap coral kemerahan agar beda dari aksen utama.
val ErrLight = Color(0xFFB3261E)
val ErrDark = Color(0xFFFFB4A8)

val DarkColorScheme = darkColorScheme(
    primary = MuseCoralLight,
    onPrimary = Color(0xFF2B1208),
    primaryContainer = MuseCoralContainerDark,
    onPrimaryContainer = Color(0xFFFFD9C7),
    secondary = HermesTealDark,
    onSecondary = Color(0xFF062A2E),
    tertiary = Color(0xFFD8C9AE),
    error = ErrDark,
    onError = Color(0xFF3D0E06),
    background = CoalBg,
    onBackground = CoalText,
    surface = CoalSurface,
    onSurface = CoalText,
    surfaceVariant = CoalVariant,
    onSurfaceVariant = CoalMuted,
    outline = CoalLine,
    outlineVariant = Color(0xFF38322B)
)

val LightColorScheme = lightColorScheme(
    primary = MuseCoral,
    onPrimary = Color.White,
    primaryContainer = MuseCoralContainerLight,
    onPrimaryContainer = Color(0xFF4A2317),
    secondary = HermesTeal,
    onSecondary = Color.White,
    tertiary = Color(0xFF6F5F45),
    error = ErrLight,
    onError = Color.White,
    background = CreamBg,
    onBackground = InkText,
    surface = CreamSurface,
    onSurface = InkText,
    surfaceVariant = CreamVariant,
    onSurfaceVariant = InkMuted,
    outline = CreamLine,
    outlineVariant = Color(0xFFEDE4D2)
)

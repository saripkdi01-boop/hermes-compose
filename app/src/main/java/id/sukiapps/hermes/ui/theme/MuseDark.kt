package id.sukiapps.hermes.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * v12.1 — Design token "Muse Dark": bahasa desain ala aplikasi Muse AI.
 *
 * Prinsip:
 * - Dark-first: hitam pekat (#0A0A0A), bukan abu kebiruan.
 * - Kartu rounded besar (24-28dp), border halus, tanpa shadow berat.
 * - Tipografi lega, hierarki jelas.
 * - Aksen hemat: coral Hermes untuk aksi primer, gradien ungu/hijau
 *   untuk kartu unggulan (seperti referensi).
 *
 * Cara pakai: bungkus layar dengan HermesTheme(darkTheme = true)
 * lalu pakai MuseDark.* untuk warna/shape/gradien khusus.
 */
object MuseDark {
    // ---- Latar ----
    val Bg = Color(0xFF0A0A0A)          // hitam pekat
    val BgElevated = Color(0xFF111111)  // sedikit terangkat
    val Surface = Color(0xFF1A1A1A)     // kartu
    val SurfaceHigh = Color(0xFF222222) // kartu di atas kartu
    val Line = Color(0xFF2A2A2A)        // border halus
    val LineStrong = Color(0xFF3A3A3A)

    // ---- Teks ----
    val TextPrimary = Color(0xFFF5F5F5)
    val TextSecondary = Color(0xFFB0B0B0)
    val TextMuted = Color(0xFF6E6E6E)

    // ---- Aksen ----
    val Accent = Color(0xFFE07856)       // coral Hermes (aksi primer)
    val AccentDim = Color(0xFF4A2317)    // coral redup (container)
    val Link = Color(0xFF5B9CF6)         // biru link
    val Success = Color(0xFF4CAF7D)      // hijau sukses
    val Warning = Color(0xFFE0A83C)      // kuning peringatan
    val Error = Color(0xFFFF6B6B)

    // ---- Bubble chat ----
    val BubbleUser = Color(0xFFF5F5F5)   // bubble user: terang
    val BubbleUserText = Color(0xFF111111)
    val BubbleAi = Color(0xFF1A1A1A)     // bubble AI: gelap
    val BubbleAiText = Color(0xFFF5F5F5)

    // ---- Gradien kartu unggulan ----
    val GradientPurple = Brush.linearGradient(
        listOf(Color(0xFF7B5EA7), Color(0xFF4A3670))
    )
    val GradientGreen = Brush.linearGradient(
        listOf(Color(0xFF2E7D5B), Color(0xFF1A4A38))
    )
    val GradientCoral = Brush.linearGradient(
        listOf(Color(0xFFE07856), Color(0xFFA84E2F))
    )

    // ---- Shape ----
    val CardShape = RoundedCornerShape(24.dp)      // kartu besar
    val SheetShape = RoundedCornerShape(28.dp)     // bottom sheet
    val PillShape = RoundedCornerShape(50)         // pill / chip
    val BubbleShapeUser = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    val BubbleShapeAi = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    val InputShape = RoundedCornerShape(28.dp)     // input bar

    // ---- ColorScheme Material3 (gantikan DarkColorScheme saat redesign penuh) ----
    val ColorScheme = darkColorScheme(
        primary = Accent,
        onPrimary = Color(0xFF1A0D06),
        primaryContainer = AccentDim,
        onPrimaryContainer = Color(0xFFFFD9C7),
        secondary = Color(0xFF7FD4DC),
        onSecondary = Color(0xFF062A2E),
        tertiary = TextSecondary,
        error = Error,
        onError = Color(0xFF2B0803),
        background = Bg,
        onBackground = TextPrimary,
        surface = Surface,
        onSurface = TextPrimary,
        surfaceVariant = SurfaceHigh,
        onSurfaceVariant = TextSecondary,
        surfaceContainerLowest = Bg,
        surfaceContainerLow = BgElevated,
        surfaceContainer = Surface,
        surfaceContainerHigh = SurfaceHigh,
        outline = Line,
        outlineVariant = LineStrong
    )
}

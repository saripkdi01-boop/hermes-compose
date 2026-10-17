package id.sukiapps.hermes.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * MUSE-STYLE CHAT THEME
 * Terinspirasi tampilan chat Muse AI: dark-first, bersih, premium.
 * Background near-black, pesan tanpa bubble berat, tipografi lega.
 */
object MuseChatDark {
    val background = Color(0xFF0A0A0A)      // near-black
    val surface = Color(0xFF141414)        // kartu / input
    val surfaceVariant = Color(0xFF1E1E1E) // hover / pressed
    val userBubble = Color(0xFF2A2A2A)     // bubble user, subtle
    val aiBackground = Color(0x00000000)   // AI: tanpa bubble, teks langsung
    val textPrimary = Color(0xFFF5F5F5)
    val textSecondary = Color(0xFFA0A0A0)
    val accent = Color(0xFFE07856)         // coral Hermes
    val accentDim = Color(0x66E07856)      // 40% untuk glow
    val divider = Color(0xFF262626)
    val inputBackground = Color(0xFF1E1E1E)
    val inputBorder = Color(0xFF333333)
    val cursorColor = Color(0xFFE07856)
    val codeBackground = Color(0xFF1A1A1A)
    val codeBorder = Color(0xFF2E2E2E)
}

object MuseChatLight {
    val background = Color(0xFFFFFFFF)
    val surface = Color(0xFFF7F7F7)
    val surfaceVariant = Color(0xFFEFEFEF)
    val userBubble = Color(0xFFF0F0F0)
    val aiBackground = Color(0x00000000)
    val textPrimary = Color(0xFF1A1A1A)
    val textSecondary = Color(0xFF6B6B6B)
    val accent = Color(0xFFD0603A)
    val accentDim = Color(0x66D0603A)
    val divider = Color(0xFFE8E8E8)
    val inputBackground = Color(0xFFF7F7F7)
    val inputBorder = Color(0xFFE0E0E0)
    val cursorColor = Color(0xFFD0603A)
    val codeBackground = Color(0xFFF5F5F5)
    val codeBorder = Color(0xFFE0E0E0)
}

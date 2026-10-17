package id.sukiapps.hermes.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ThemeTokens — SATU-SATUNYA sumber kebenaran desain Hermes.
 *
 * Menggantikan 4 sistem warna paralel yang sebelumnya tersebar
 * (MaterialTheme bawaan, MuseDark, warna hardcoded di screen, warna XML).
 * Semua screen WAJIB membaca dari sini, dilarang hardcode warna/dimensi/font.
 *
 * Palet: disiplin visual WhatsApp (struktur interaksi), identitas warna Hermes.
 * TIDAK meniru aset/brand WhatsApp.
 */
object ThemeTokens {

    // ── Warna Light ──────────────────────────────────────────────
    object Light {
        val appBar = Color(0xFF075E54)
        val primary = Color(0xFF128C7E)
        val accent = Color(0xFF25D366)
        val chatBackground = Color(0xFFECE5DD)
        val bubbleOut = Color(0xFFDCF8C6)
        val bubbleIn = Color(0xFFFFFFFF)
        val textPrimary = Color(0xFF111B21)
        val textSecondary = Color(0xFF667781)
        val divider = Color(0xFFE9EDEF)
        val destructive = Color(0xFFE5484D)
        val surface = Color(0xFFFFFFFF)
        val surfaceVariant = Color(0xFFF0F2F5)
        val onPrimary = Color(0xFFFFFFFF)
        val badgeUnread = Color(0xFF25D366)
        val onBadgeUnread = Color(0xFFFFFFFF)
        val streamingDot = Color(0xFF128C7E)
        val quoteBar = Color(0xFF128C7E)
        val upinAccent = Color(0xFF7C5CFF)
        val systemPill = Color(0xFFE9EDEF)
        val onSystemPill = Color(0xFF667781)
    }

    // ── Warna Dark ───────────────────────────────────────────────
    object Dark {
        val appBar = Color(0xFF1F2C34)
        val primary = Color(0xFF00A884)
        val accent = Color(0xFF25D366)
        val chatBackground = Color(0xFF0B141A)
        val bubbleOut = Color(0xFF005C4B)
        val bubbleIn = Color(0xFF202C33)
        val textPrimary = Color(0xFFE9EDEF)
        val textSecondary = Color(0xFF8696A0)
        val divider = Color(0xFF222D34)
        val destructive = Color(0xFFEF6B6B)
        val surface = Color(0xFF111B21)
        val surfaceVariant = Color(0xFF1F2C34)
        val onPrimary = Color(0xFFFFFFFF)
        val badgeUnread = Color(0xFF25D366)
        val onBadgeUnread = Color(0xFF0B141A)
        val streamingDot = Color(0xFF00A884)
        val quoteBar = Color(0xFF00A884)
        val upinAccent = Color(0xFF9D86FF)
        val systemPill = Color(0xFF1F2C34)
        val onSystemPill = Color(0xFF8696A0)
    }

    // ── Geometri (grid 4dp/8dp) ──────────────────────────────────
    object Dimen {
        val screenPadding: Dp = 16.dp
        val listGutter: Dp = 8.dp
        val bubbleRadius: Dp = 12.dp
        val bubbleTail: Dp = 8.dp
        val bubbleMaxWidthFraction: Float = 0.78f
        val sheetTopRadius: Dp = 28.dp
        val cardRadius: Dp = 16.dp
        val chipPillRadius: Dp = 999.dp
        val avatarList: Dp = 48.dp
        val avatarChat: Dp = 40.dp
        val tapTargetMin: Dp = 48.dp
        val hairline: Dp = 0.5.dp
        val groupedBubbleGap: Dp = 2.dp
        val normalBubbleGap: Dp = 8.dp
        val grid4: Dp = 4.dp
        val grid8: Dp = 8.dp
    }

    // ── Tipografi (MINIMUM 12sp di seluruh app) ──────────────────
    // Dilarang 8–11sp. Line height 1.35x.
    object Type {
        val name: TextUnit = 14.sp          // nama, bold
        val message: TextUnit = 15.sp       // isi pesan
        val messageSecondary: TextUnit = 14.sp
        val timestamp: TextUnit = 12.sp     // batas minimum
        val dateSeparator: TextUnit = 12.sp // batas minimum
        val caption: TextUnit = 12.sp       // batas minimum
        val lineHeightFactor: Float = 1.35f

        /** Line height absolut untuk ukuran font tertentu. */
        fun lineHeightFor(size: TextUnit): TextUnit = (size.value * lineHeightFactor).sp
    }

    // ── Gerak ────────────────────────────────────────────────────
    object Motion {
        const val transitionMs: Int = 200          // rentang 180–220ms
        const val fastMs: Int = 180
        // Easing: pakai FastOutSlowInEasing (standard) dari compose.animation
    }

    // ── Ikon ─────────────────────────────────────────────────────
    object Icon {
        val size: Dp = 24.dp              // stroked 24dp, satu family
        // WAJIB: Material Icons (Outlined untuk aksi, Filled untuk status aktif).
        // DILARANG memakai emoji sebagai ikon.
    }
}

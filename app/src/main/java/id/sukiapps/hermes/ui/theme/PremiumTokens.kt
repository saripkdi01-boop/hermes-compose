package id.sukiapps.hermes.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * PREMIUM DESIGN SYSTEM — Hermes v13
 * Standar: Linear.app-grade polish, dark-first, identitas brutal-kreatif.
 *
 * Hierarki: file ini MELENGKAPI ThemeTokens.kt (Tahap 1), bukan menggantikan.
 * Semua warna hex didefinisikan sekali di sini — dilarang bikin warna baru
 * di luar file ini.
 */

// ─── WARNA ───

object PremiumLight {
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
    val cardBackground = Color(0xFFFFFFFF)
    val cardBorder = Color(0xFFE9EDEF)
    val skeletonBase = Color(0xFFE9EDEF)
    val skeletonShimmer = Color(0xFFF7F8FA)
    val statusOnline = Color(0xFF25D366)
    val statusOffline = Color(0xFF8696A0)
    val statusError = Color(0xFFE5484D)
    val glowAccent = Color(0x4025D366) // 25% alpha untuk glow
}

object PremiumDark {
    val appBar = Color(0xFF1F2C34)
    val primary = Color(0xFF00A884)
    val accent = Color(0xFF25D366)
    val chatBackground = Color(0xFF0B141A)
    val bubbleOut = Color(0xFF005C4B)
    val bubbleIn = Color(0xFF1F2C34)
    val textPrimary = Color(0xFFE9EDEF)
    val textSecondary = Color(0xFF8696A0)
    val divider = Color(0xFF222D34)
    val destructive = Color(0xFFEF6B6B)
    val surface = Color(0xFF1C1B1F)
    val surfaceVariant = Color(0xFF202C33)
    val cardBackground = Color(0xFF1C1B1F)
    val cardBorder = Color(0xFF2A3942) // 1dp outlineVariant, bukan shadow
    val skeletonBase = Color(0xFF202C33)
    val skeletonShimmer = Color(0xFF2A3942)
    val statusOnline = Color(0xFF25D366)
    val statusOffline = Color(0xFF576B75)
    val statusError = Color(0xFFEF6B6B)
    val glowAccent = Color(0x4025D366)
}

// ─── SPACING (grid 4/8dp) ───

object PremiumSpacing {
    val xs = 4.dp   // tight: dalam bubble, antar ikon
    val sm = 8.dp   // compact: padding chip, gap kecil
    val md = 16.dp  // default: padding layar, antar card
    val lg = 24.dp  // section: antar grup settings
    val xl = 32.dp  // spacious: header ke konten
    val xxl = 48.dp // hero: splash, empty state
}

// ─── RADIUS ───

object PremiumRadius {
    val sm = 8.dp   // chip kecil, badge
    val md = 12.dp  // bubble chat (+ tail 8dp terpisah)
    val lg = 16.dp  // card
    val xl = 20.dp  // grouped section settings
    val xxl = 28.dp // bottom sheet top
    val pill = 999.dp // chip pill, tombol kapsul
    val circle = 50 // persen, untuk avatar (dipakai sebagai CircleShape)
}

// ─── ELEVATION (dark mode: glow/garis, bukan black shadow) ───

object PremiumElevation {
    val none = 0.dp
    val sm = 2.dp   // chip terangkat
    val md = 4.dp   // card
    val lg = 8.dp   // bottom sheet, dialog
    // Di dark mode, ganti shadow dengan border 1dp (lihat PremiumDark.cardBorder)
}

// ─── MOTION ───

object PremiumMotion {
    const val fast = 120  // micro: ripple, switch
    const val base = 200  // standar: transisi layar, expand
    const val slow = 320  // hero: splash, stagger entrance
    const val staggerDelay = 30  // delay per item stagger (ms)
    const val staggerMax = 400   // total maksimal stagger (ms)
    // Easing: FastOutSlowIn untuk semua transisi standar
    // Spring (dampingRatio 0.6, stiffness 380) untuk switch & drag
}

// ─── TIPOGRAFI ───
// Minimum 12sp di semua tempat. Line height 1.35x.

object PremiumType {
    val displayLarge = 32.sp
    val displayMedium = 28.sp
    val titleLarge = 22.sp
    val titleMedium = 18.sp
    val titleSmall = 16.sp
    val bodyLarge = 16.sp
    val bodyMedium = 14.sp
    val bodySmall = 12.sp   // MINIMUM — dilarang lebih kecil
    val labelLarge = 14.sp
    val labelMedium = 12.sp // MINIMUM
    val labelSmall = 12.sp  // MINIMUM
    val sectionHeader = 11.sp // KHUSUS settings header uppercase + letterspacing

    const val lineHeightRatio = 1.35f
    const val sectionLetterspacing = 0.08f // em
}

// ─── IKON ───

object PremiumIcon {
    val size = 24.dp       // satu family, stroked
    val sizeSm = 20.dp     // dalam chip
    val sizeLg = 28.dp     // composer send button
    val statusDot = 8.dp   // titik status konektor
    val avatarList = 48.dp // daftar chat
    val avatarChat = 40.dp // dalam chat
    val minTouch = 48.dp   // target sentuh minimum
}

// ─── DURASI KHUSUS ───

object PremiumDuration {
    const val searchDebounce = 200L      // ms, settings search
    const val previewRevert = 3000L      // ms, auto-revert tema preview
    const val skeletonThreshold = 250L   // ms, skeleton muncul jika load > ini
    const val holdToConfirm = 800L       // ms, danger action
    const val valueFlash = 300L          // ms, kedip saat setting berubah
    const val statusPulse = 1600L        // ms, pulsing glow konektor
    const val cursorPulse = 530L         // ms, cursor streaming
}

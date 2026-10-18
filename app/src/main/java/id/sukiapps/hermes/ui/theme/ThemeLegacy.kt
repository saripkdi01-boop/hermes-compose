package id.sukiapps.hermes.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * SHIM KOMPATIBILITAS — v14 Tahap 0 fix CI #39.
 *
 * File UI lama memakai API tema lama (ThemeTokens.Dimen, HermesColors, dll)
 * yang dihapus saat ThemeTokens.kt / Theme.kt diganti. Shim ini
 * mengembalikan API lama sebagai @Deprecated yang mendelegasikan ke
 * sistem baru (PremiumThemeTokens / LocalChatPalette).
 *
 * Jangan pakai di kode baru. Hapus file ini setelah semua UI migrasi.
 */

// ---------- ThemeTokens lama ----------

@Deprecated("Migrasi ke PremiumThemeTokens / LocalChatPalette")
object ThemeTokens {

    @Deprecated("Pakai PremiumThemeTokens.spacing")
    object Dimen {
        val grid4: Dp get() = PremiumThemeTokens.spacing.xs
        val grid8: Dp get() = PremiumThemeTokens.spacing.sm
        val screenPadding: Dp get() = PremiumThemeTokens.spacing.md
        // Nilai dari spec Tahap 1 (ChatShapes, ChatListScreen)
        val bubbleRadius: Dp get() = (12).dp
        val bubbleTail: Dp get() = (8).dp
        val chipPillRadius: Dp get() = (999).dp
        val cardRadius: Dp get() = (16).dp
        val sheetTopRadius: Dp get() = (28).dp
        val avatarList: Dp get() = (48).dp
        val avatarChat: Dp get() = (40).dp
    }

    @Deprecated("Pakai PremiumThemeTokens.type")
    object Type {
        val caption get() = PremiumThemeTokens.type.caption
        val body get() = PremiumThemeTokens.type.body
        val bodySmall get() = PremiumThemeTokens.type.bodySmall
        val title get() = PremiumThemeTokens.type.title
        val headline get() = PremiumThemeTokens.type.headline
    }

    @Deprecated("Pakai PremiumThemeTokens.motion")
    object Motion {
        val base get() = PremiumThemeTokens.motion.base
        val fast get() = PremiumThemeTokens.motion.fast
        val slow get() = PremiumThemeTokens.motion.slow
    }

    @Deprecated("Pakai PremiumThemeTokens.icons")
    object Icon {
        val size get() = PremiumThemeTokens.icons.size
    }

    @Deprecated("Pakai museDarkPalette() / museLightPalette()")
    object Light {
        val background: Color get() = museLightPalette().background
        val surface: Color get() = museLightPalette().surface
    }

    @Deprecated("Pakai museDarkPalette() / museLightPalette()")
    object Dark {
        val background: Color get() = museDarkPalette().background
        val surface: Color get() = museDarkPalette().surface
    }
}

// ---------- HermesColors lama (dari Theme.kt lama) ----------

@Deprecated("Pakai LocalChatPalette.current")
object HermesColors {
    @Composable
    fun appBar(): Color = LocalChatPalette.current.appBar

    @Composable
    fun chatBackground(): Color = LocalChatPalette.current.chatBackground

    @Composable
    fun bubbleOut(): Color = LocalChatPalette.current.bubbleOut

    @Composable
    fun bubbleIn(): Color = LocalChatPalette.current.bubbleIn

    @Composable
    fun textPrimary(): Color = LocalChatPalette.current.textPrimary

    @Composable
    fun textSecondary(): Color = LocalChatPalette.current.textSecondary

    @Composable
    fun divider(): Color = LocalChatPalette.current.divider

    @Composable
    fun destructive(): Color = LocalChatPalette.current.errorColor

    @Composable
    fun surfaceVariant(): Color = LocalChatPalette.current.surfaceVariant

    @Composable
    fun badgeUnread(): Color = LocalChatPalette.current.accent

    @Composable
    fun onBadgeUnread(): Color = LocalChatPalette.current.background

    @Composable
    fun streamingDot(): Color = LocalChatPalette.current.streamCaret
}

// ---------- Alias top-level lama ----------
// (sudah ada di ThemeTokens.kt — jangan duplikasi di sini)

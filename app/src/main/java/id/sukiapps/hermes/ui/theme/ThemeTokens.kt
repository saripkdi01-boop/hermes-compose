package id.sukiapps.hermes.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens v14 — SATU-SATUNYA sumber ukuran, radius, tipografi, motion.
 *
 * Catatan kompatibilitas: file ini MENGGANTIKAN ThemeTokens.kt lama.
 * Kode lama yang memakai ThemeTokens.Dimen/Type/Motion/Icon perlu migrasi
 * ke PremiumThemeTokens (lihat alias di bawah).
 */

// ---------- Spacing: grid 4/8dp ----------

data class Spacing(
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
    val xxl: Dp
)

// ---------- Radii ----------

data class Radii(
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
    val xxl: Dp,
    val pill: Dp,
    val circle: Dp
)

// ---------- Tipografi: MINIMUM 12sp ----------

data class TypeScale(
    val display: TextUnit, // 24sp — judul hero
    val headline: TextUnit, // 20sp — judul layar
    val title: TextUnit, // 17sp — judul section
    val titleSmall: TextUnit, // 15sp — judul item
    val body: TextUnit, // 15sp — isi chat
    val bodySmall: TextUnit, // 13sp — isi sekunder
    val label: TextUnit, // 13sp — label tombol
    val caption: TextUnit, // 12sp — caption (MINIMUM)
    val overline: TextUnit, // 12sp — header section uppercase
    val sectionHeader: TextUnit, // 12sp + spacing — PERBAIKAN: dulu 11sp
    val timestamp: TextUnit, // 12sp — waktu pesan
    val badge: TextUnit // 12sp — angka badge
)

// ---------- Motion ----------

data class Motion(
    val fast: Int, // 120ms — micro interaction
    val base: Int, // 200ms — standar
    val slow: Int, // 320ms — transisi layar
    val staggerDelay: Int, // 30ms — jeda antar item
    val staggerMax: Int, // 400ms — total stagger maksimal
    val holdToConfirm: Long, // 800ms — tahan untuk konfirmasi
    val valueFlash: Long, // 300ms — kilat perubahan nilai
    val statusPulse: Long, // 1600ms — denyut dot status
    val cursorPulse: Long // 530ms — kedip kursor streaming
)

// ---------- Ikon ----------

data class IconSizes(
    val size: Dp, // 24dp — standar
    val sizeSm: Dp, // 20dp — kecil
    val sizeLg: Dp, // 28dp — besar
    val statusDot: Dp, // 8dp — dot status
    val avatarList: Dp, // 48dp — avatar di daftar
    val avatarChat: Dp, // 40dp — avatar di chat
    val minTouch: Dp // 48dp — target sentuh minimum
)

// ---------- Kumpulan token ----------

object PremiumThemeTokens {
    val spacing = Spacing(
        xs = 4.dp,
        sm = 8.dp,
        md = 16.dp,
        lg = 24.dp,
        xl = 32.dp,
        xxl = 48.dp
    )

    val radii = Radii(
        sm = 8.dp,
        md = 12.dp,
        lg = 16.dp,
        xl = 20.dp,
        xxl = 28.dp,
        pill = 999.dp,
        circle = 999.dp // lingkaran penuh via pill
    )

    val type = TypeScale(
        display = 24.sp,
        headline = 20.sp,
        title = 17.sp,
        titleSmall = 15.sp,
        body = 15.sp,
        bodySmall = 13.sp,
        label = 13.sp,
        caption = 12.sp,
        overline = 12.sp,
        sectionHeader = 12.sp, // DIPERBAIKI dari 11sp
        timestamp = 12.sp,
        badge = 12.sp
    )

    val motion = Motion(
        fast = 120,
        base = 200,
        slow = 320,
        staggerDelay = 30,
        staggerMax = 400,
        holdToConfirm = 800L,
        valueFlash = 300L,
        statusPulse = 1600L,
        cursorPulse = 530L
    )

    val icons = IconSizes(
        size = 24.dp,
        sizeSm = 20.dp,
        sizeLg = 28.dp,
        statusDot = 8.dp,
        avatarList = 48.dp,
        avatarChat = 40.dp,
        minTouch = 48.dp
    )
}

// ---------- Alias kompatibilitas ----------
// Kode lama (Tahap 1) memakai ThemeTokens.Dimen/Type/Motion/Icon.
// Alias ini menjaga kode lama tetap compile selama migrasi bertahap.

@Deprecated("Migrasi ke PremiumThemeTokens.spacing", ReplaceWith("PremiumThemeTokens.spacing"))
val ThemeSpacing get() = PremiumThemeTokens.spacing

@Deprecated("Migrasi ke PremiumThemeTokens.type", ReplaceWith("PremiumThemeTokens.type"))
val ThemeType get() = PremiumThemeTokens.type

@Deprecated("Migrasi ke PremiumThemeTokens.motion", ReplaceWith("PremiumThemeTokens.motion"))
val ThemeMotion get() = PremiumThemeTokens.motion

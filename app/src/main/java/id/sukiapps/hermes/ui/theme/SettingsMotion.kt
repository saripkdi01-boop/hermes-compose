package id.sukiapps.hermes.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Durasi animasi untuk layar Pengaturan.
 */
data class SettingsMotion(
    val entranceMs: Int = 260,
    val staggerMs: Int = 40,
    val expandMs: Int = 220,
    val collapseMs: Int = 180,
    val chipChangeMs: Int = 300,
    val holdToConfirmMs: Long = 800,
    val shimmerMs: Int = 1200,
    val testTimeoutMs: Long = 15000
)

val DefaultSettingsMotion = SettingsMotion()

val LocalSettingsMotion = staticCompositionLocalOf { DefaultSettingsMotion }

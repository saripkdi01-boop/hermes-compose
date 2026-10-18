package id.sukiapps.hermes.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Shape untuk layar Pengaturan.
 * SEMUA Dp valid — tidak ada Int yang dikira Dp (bug PremiumRadius.circle).
 * Untuk lingkaran penuh pakai CircleShape, bukan Dp.
 */
data class SettingsShape(
    val card: Shape,
    val cardSmall: Shape,
    val iconTile: Shape,
    val field: Shape,
    val button: Shape,
    val chip: Shape,
    val bottomSheetTop: Shape
)

val DefaultSettingsShape = SettingsShape(
    card = RoundedCornerShape(20.dp),
    cardSmall = RoundedCornerShape(16.dp),
    iconTile = RoundedCornerShape(12.dp),
    field = RoundedCornerShape(12.dp),
    button = RoundedCornerShape(12.dp),
    chip = RoundedCornerShape(999.dp), // pill
    bottomSheetTop = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
)

/** Untuk avatar / nomor langkah lingkaran penuh. */
val CircleShapeToken: Shape = CircleShape

val LocalSettingsShape = staticCompositionLocalOf { DefaultSettingsShape }

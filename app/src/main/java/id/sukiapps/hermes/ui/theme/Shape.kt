package id.sukiapps.hermes.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Hermes Premium — radius: kartu 16, dialog 20, composer pill 24.
val HermesShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

// ---- Bentuk khas Muse (chat) ----
/** Composer: pil lebar, sudut membulat penuh. */
val ComposerShape = RoundedCornerShape(28.dp)

/** Bubble pesan user: sudut kanan-atar "tajam" sebagai penanda arah. */
val UserBubbleShape = RoundedCornerShape(20.dp, 6.dp, 20.dp, 20.dp)

/** Chip saran / pil status / pil model. */
val PillShape = RoundedCornerShape(999.dp)

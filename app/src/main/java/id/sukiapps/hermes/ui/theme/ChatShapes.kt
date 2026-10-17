package id.sukiapps.hermes.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Shape bubble chat dengan ekor, untuk basis Compose.
 *
 * Geometri sesuai spec: radius 12dp, ekor 8dp.
 * Max lebar 78% layar diatur di layout (Modifier.fillMaxWidth(0.78f)),
 * bukan di shape.
 *
 * @param tailRight true = ekor kanan bawah (pesan keluar),
 * false = ekor kiri bawah (pesan masuk).
 * @param grouped true = bubble rapat (pesan berurutan pengirim sama
 * dalam 2 menit): TANPA ekor, rounded rect penuh.
 */
class ChatBubbleShape(
    private val tailRight: Boolean,
    private val grouped: Boolean = false
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val radiusPx = with(density) { ThemeTokens.Dimen.bubbleRadius.toPx() }
        val tailPx = with(density) { ThemeTokens.Dimen.bubbleTail.toPx() }
        val corner = CornerRadius(radiusPx, radiusPx)
        val path = Path()

        if (grouped) {
            path.addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height), corner))
            return Outline.Generic(path)
        }

        // Badan bubble: rounded rect, disisakan ruang tailPx di sisi ekor.
        val bodyLeft = if (tailRight) 0f else tailPx
        val bodyRight = if (tailRight) size.width - tailPx else size.width
        path.addRoundRect(RoundRect(Rect(bodyLeft, 0f, bodyRight, size.height), corner))

        // Ekor: segitiga siku-siku di sudut bawah sisi pengirim.
        // Alas menempel di badan, titik tajam mengarah ke luar-bawah.
        val tailBaseY = size.height - tailPx * 2f
        if (tailRight) {
            path.moveTo(bodyRight - tailPx, tailBaseY)
            path.lineTo(size.width, size.height - tailPx)
            path.lineTo(bodyRight - tailPx, size.height - tailPx)
            path.close()
        } else {
            path.moveTo(bodyLeft + tailPx, tailBaseY)
            path.lineTo(0f, size.height - tailPx)
            path.lineTo(bodyLeft + tailPx, size.height - tailPx)
            path.close()
        }

        return Outline.Generic(path)
    }
}

/** Bubble pesan keluar: ekor kanan bawah. */
fun outgoingBubbleShape(grouped: Boolean = false) =
    ChatBubbleShape(tailRight = true, grouped = grouped)

/** Bubble pesan masuk: ekor kiri bawah. */
fun incomingBubbleShape(grouped: Boolean = false) =
    ChatBubbleShape(tailRight = false, grouped = grouped)

/** Pill pemisah tanggal di tengah chat. */
val dateSeparatorShape = RoundedCornerShape(ThemeTokens.Dimen.chipPillRadius)

/** Chip reaksi di tepi bawah bubble. */
val reactionChipShape = RoundedCornerShape(ThemeTokens.Dimen.chipPillRadius)

/** Kartu lampiran / info: radius 16dp. */
val cardShape = RoundedCornerShape(ThemeTokens.Dimen.cardRadius)

/** Bottom sheet: radius atas 28dp. */
val bottomSheetShape = RoundedCornerShape(
    topStart = ThemeTokens.Dimen.sheetTopRadius,
    topEnd = ThemeTokens.Dimen.sheetTopRadius,
    bottomStart = 0.dp,
    bottomEnd = 0.dp
)

/** Avatar: lingkaran penuh. */
val avatarShape = RoundedCornerShape(percent = 50)

package id.sukiapps.hermes.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import id.sukiapps.hermes.ui.chat.ComposerTool
import id.sukiapps.hermes.ui.chat.composerTiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v20: gaya Muse netral — pilihan gaya, kontras palet, peran permukaan, tile "+". */
class MuseStyleTest {

    private fun contrast(a: Color, b: Color): Double {
        val l1 = a.luminance().toDouble(); val l2 = b.luminance().toDouble()
        val (hi, lo) = if (l1 > l2) l1 to l2 else l2 to l1
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun uiStyle_clean_defaultsToMuse() {
        assertEquals(UiStyle.MUSE, UiStyle.clean(null))
        assertEquals(UiStyle.MUSE, UiStyle.clean("aneh"))
        assertEquals(UiStyle.HANGAT, UiStyle.clean("hangat"))
    }

    @Test
    fun neutralPalettes_meetTextContrast() {
        for (p in listOf(museNeutralDarkPalette(), museNeutralLightPalette())) {
            assertTrue("teks utama ≥ 7:1", contrast(p.textPrimary, p.background) >= 7.0)
            assertTrue("teks sekunder ≥ 4.5:1", contrast(p.textSecondary, p.background) >= 4.5)
            assertTrue("teks di bubble user ≥ 7:1", contrast(p.textPrimary, p.userBubble) >= 7.0)
        }
    }

    @Test
    fun neutralDark_isNearBlack() {
        val bg = museNeutralDarkPalette().background
        assertTrue(bg.luminance() < 0.01f)
    }

    @Test
    fun surfaceRoles_stepAwayFromBackground_monotonic() {
        for (p in listOf(museNeutralDarkPalette(), museNeutralLightPalette(), museDarkPalette(), museLightPalette())) {
            val r = surfaceRoles(p)
            val steps = listOf(r.lowest, r.low, r.container, r.high, r.highest)
                .map { contrast(it, p.background) }
            assertTrue("naik bertahap: $steps", steps.zipWithNext().all { (a, b) -> b >= a })
            assertEquals(p.background, r.lowest)
        }
    }

    @Test
    fun composerTiles_keepOldMenuOrder_plusEmoji() {
        assertEquals(
            listOf(ComposerTool.GALLERY, ComposerTool.CAMERA, ComposerTool.DOCUMENT, ComposerTool.WEB, ComposerTool.UPIN, ComposerTool.EMOJI),
            composerTiles(showEmoji = true)
        )
        assertEquals(5, composerTiles(showEmoji = false).size)
    }
}

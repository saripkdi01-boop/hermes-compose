package id.sukiapps.hermes.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v19.2: shortcode emoji di markdown + daftar emoji terakhir dipakai. */
class EmojiTest {

    @Test
    fun knownShortcodes_areReplaced() {
        assertEquals("Siap 🚀 dan 👍", Emoji.replaceShortcodes("Siap :rocket: dan :thumbsup:"))
        assertEquals("👍 👎", Emoji.replaceShortcodes(":+1: :-1:"))
    }

    @Test
    fun unknownShortcodes_andTimes_areUntouched() {
        assertEquals(":tidakada: jam 10:30:00", Emoji.replaceShortcodes(":tidakada: jam 10:30:00"))
        assertEquals("a:b:c", Emoji.replaceShortcodes("a:b:c"))
    }

    @Test
    fun inlineCode_isUntouched() {
        assertEquals("pakai `:x:` bukan ❌", Emoji.replaceShortcodes("pakai `:x:` bukan :x:"))
    }

    @Test
    fun fencedCode_isUntouched() {
        val md = "Hasil :tada:\n```\nlog :x: gagal\n```\nselesai :white_check_mark:"
        assertEquals("Hasil 🎉\n```\nlog :x: gagal\n```\nselesai ✅", Emoji.replaceShortcodes(md))
    }

    @Test
    fun unbalancedBacktick_stillConvertsTrailingText() {
        assertEquals("harga `5 🔥", Emoji.replaceShortcodes("harga `5 :fire:"))
    }

    @Test
    fun pushRecent_movesToFront_dedupes_andCaps() {
        val r = Emoji.pushRecent(listOf("😀", "👍", "🔥"), "👍")
        assertEquals(listOf("👍", "😀", "🔥"), r)
        val capped = (1..20).fold(emptyList<String>()) { acc, i -> Emoji.pushRecent(acc, "e$i", max = 5) }
        assertEquals(listOf("e20", "e19", "e18", "e17", "e16"), capped)
    }

    @Test
    fun catalog_hasNoDuplicatesWithinCategory_andQuickReactionsPresent() {
        for (c in Emoji.CATEGORIES) {
            assertEquals("duplikat di ${c.label}", c.emojis.size, c.emojis.toSet().size)
        }
        assertEquals(6, Emoji.QUICK_REACTIONS.size)
        assertTrue(Emoji.QUICK_REACTIONS.contains("👍"))
    }
}

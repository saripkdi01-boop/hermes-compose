package id.sukiapps.hermes.ui.chat

import id.sukiapps.hermes.util.Markdown
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleChatTest {

    private fun table(src: String): Markdown.Block.Table? {
        val blocks = Markdown.parseBlocks(src)
        return blocks.filterIsInstance<Markdown.Block.Table>().firstOrNull()
    }

    // ---------- Parser tabel GFM ----------

    @Test
    fun tabelDasarDiparser() {
        val t = table(
            "| Nama | Harga |\n" +
                "| --- | --- |\n" +
                "| Kopi | 20000 |\n" +
                "| Teh | 15000 |"
        )
        assertTrue(t != null)
        assertEquals(listOf("Nama", "Harga"), t!!.headers.map { it.text })
        assertEquals(2, t.rows.size)
        assertEquals("Kopi", t.rows[0][0].text)
        assertEquals("20000", t.rows[0][1].text)
    }

    @Test
    fun tabelTanpaPemisahJadiParagraf() {
        val blocks = Markdown.parseBlocks(
            "| Nama | Harga |\n| Kopi | 20000 |"
        )
        assertTrue(blocks.none { it is Markdown.Block.Table })
        assertTrue(blocks.any { it is Markdown.Block.Paragraph })
    }

    @Test
    fun tabelKolomTakCocokJadiParagraf() {
        val blocks = Markdown.parseBlocks(
            "| Nama | Harga |\n" +
                "| --- | --- |\n" +
                "| Kopi |"
        )
        assertTrue(blocks.none { it is Markdown.Block.Table })
    }

    @Test
    fun tabelMarkerAlignmentDiterima() {
        val t = table(
            "| Nama | Harga |\n" +
                "| :--- | ---: |\n" +
                "| Kopi | 20000 |"
        )
        assertTrue(t != null)
        assertEquals(1, t!!.rows.size)
    }

    @Test
    fun tabelSelMendukungInlineSpan() {
        val t = table(
            "| Nama | Ket |\n" +
                "| --- | --- |\n" +
                "| **Kopi** | `panas` |"
        )
        assertTrue(t != null)
        // Teks polos tetap terbaca walau dibungkus span tebal/kode.
        assertEquals("Kopi", t!!.rows[0][0].text)
        assertEquals("panas", t.rows[0][1].text)
    }

    @Test
    fun tabelStreamingBelumLengkapJadiParagraf() {
        // Pemisah belum tiba (streaming) → fallback teks, bukan tabel
        // setengah jadi; begitu pemisah tiba, parse berikutnya jadi tabel.
        val blocks = Markdown.parseBlocks("| Nama | Harga |\n| ---")
        assertTrue(blocks.none { it is Markdown.Block.Table })
    }

    // ---------- Pemisah tanggal ----------

    private fun ms(
        year: Int,
        month: Int,
        day: Int,
        hour: Int
    ): Long {
        return Calendar.getInstance().apply {
            set(year, month - 1, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun msg(id: Long, ts: Long) = UiMessage(
        id = id,
        timestamp = ts,
        role = if (id % 2L == 0L) "assistant" else "user",
        content = "pesan $id"
    )

    @Test
    fun grupPesanSisipkanPemisahPerHari() {
        val items = buildChatListItems(
            listOf(
                msg(1, ms(2026, 10, 25, 10)),
                msg(2, ms(2026, 10, 25, 15)),
                msg(3, ms(2026, 10, 26, 9))
            ),
            nowMs = ms(2026, 10, 26, 12)
        )
        // 3 pesan + 2 pemisah, urutan dipertahankan.
        assertEquals(5, items.size)
        val sep1 = items[0] as ChatListItem.DateSeparator
        val sep2 = items[3] as ChatListItem.DateSeparator
        assertEquals("Hari ini", sep2.label)
        assertTrue(sep1.label.contains("2026"))
        assertEquals(
            listOf("msg:1", "msg:2", "msg:3"),
            items.filterIsInstance<ChatListItem.Message>()
                .map { it.key }
        )
        // Kunci pemisah stabil per hari.
        assertTrue(sep1.key.startsWith("sep:20261025"))
        assertTrue(sep2.key.startsWith("sep:20261026"))
    }

    @Test
    fun pesanTanpaTimestampTanpaPemisah() {
        val items = buildChatListItems(
            listOf(msg(1, 0L), msg(2, ms(2026, 10, 25, 10)))
        )
        assertEquals(3, items.size)
        assertTrue(items[0] is ChatListItem.Message)
        assertTrue(items[1] is ChatListItem.DateSeparator)
    }

    @Test
    fun labelHariIniBilaHarinyaSama() {
        val ts = ms(2026, 10, 25, 10)
        assertEquals("Hari ini", dayLabelOf(ts, ms(2026, 10, 25, 23)))
        assertEquals("", dayKeyOf(0L))
    }
}

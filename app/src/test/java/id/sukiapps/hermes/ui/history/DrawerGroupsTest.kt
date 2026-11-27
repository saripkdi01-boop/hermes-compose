package id.sukiapps.hermes.ui.history

import id.sukiapps.hermes.data.local.ConversationEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** v20: pengelompokan riwayat drawer per waktu (zona tetap, tanpa Android). */
class DrawerGroupsTest {

    private val zone = ZoneId.of("Asia/Jakarta")
    private val now = LocalDateTime.of(2026, 11, 27, 9, 0).atZone(zone).toInstant().toEpochMilli()

    private fun at(y: Int, m: Int, d: Int, h: Int = 12) =
        LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    private fun conv(id: Long, updated: Long, pinned: Boolean = false) =
        ConversationEntity(id = id, title = "c$id", createdAt = updated, updatedAt = updated, pinned = pinned)

    @Test
    fun bucketsByCalendarDay_inOrder_skippingEmptyGroups() {
        val list = listOf(
            conv(1, at(2026, 11, 27, 8)),           // hari ini
            conv(2, at(2026, 11, 26, 23)),          // kemarin (meski < 24 jam)
            conv(3, at(2026, 11, 21)),              // 6 hari → 7 hari terakhir
            conv(4, at(2026, 11, 20)),              // 7 hari → 30 hari terakhir
            conv(5, at(2026, 10, 1)),               // lebih lama
            conv(6, at(2026, 1, 1), pinned = true)  // disematkan selalu di atas
        )
        val g = DrawerGroups.group(list, now, zone)
        assertEquals(
            listOf("Disematkan", "Hari ini", "Kemarin", "7 hari terakhir", "30 hari terakhir", "Lebih lama"),
            g.map { it.label }
        )
        assertEquals(listOf(listOf(6L), listOf(1L), listOf(2L), listOf(3L), listOf(4L), listOf(5L)), g.map { gr -> gr.items.map { it.id } })
    }

    @Test
    fun keepsInputOrderWithinGroup_andHidesEmptyGroups() {
        val list = listOf(conv(9, at(2026, 11, 27, 7)), conv(8, at(2026, 11, 27, 6)))
        val g = DrawerGroups.group(list, now, zone)
        assertEquals(listOf("Hari ini"), g.map { it.label })
        assertEquals(listOf(9L, 8L), g.single().items.map { it.id })
    }

    @Test
    fun futureTimestamp_clockSkew_isToday() {
        val g = DrawerGroups.group(listOf(conv(1, at(2026, 11, 28))), now, zone)
        assertEquals("Hari ini", g.single().label)
    }

    @Test
    fun empty_givesNoGroups() {
        assertEquals(emptyList<DrawerGroups.Group>(), DrawerGroups.group(emptyList(), now, zone))
    }
}

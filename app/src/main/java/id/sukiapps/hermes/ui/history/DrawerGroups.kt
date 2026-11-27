package id.sukiapps.hermes.ui.history

import id.sukiapps.hermes.data.local.ConversationEntity
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * v20 (gaya Muse): pengelompokan riwayat chat di drawer berdasarkan waktu
 * terakhir diperbarui — "Disematkan" selalu di atas, lalu Hari ini, Kemarin,
 * 7 hari terakhir, 30 hari terakhir, Lebih lama. Urutan di dalam grup
 * mengikuti urutan masukan (DAO sudah mengurutkan updated_at terbaru dulu).
 * Grup kosong tidak ditampilkan. Murni (tanpa Android) → bisa di-unit-test.
 */
object DrawerGroups {

    data class Group(val label: String, val items: List<ConversationEntity>)

    const val PINNED = "Disematkan"
    const val TODAY = "Hari ini"
    const val YESTERDAY = "Kemarin"
    const val WEEK = "7 hari terakhir"
    const val MONTH = "30 hari terakhir"
    const val OLDER = "Lebih lama"

    fun group(
        list: List<ConversationEntity>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<Group> {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val buckets = linkedMapOf<String, MutableList<ConversationEntity>>(
            PINNED to mutableListOf(),
            TODAY to mutableListOf(),
            YESTERDAY to mutableListOf(),
            WEEK to mutableListOf(),
            MONTH to mutableListOf(),
            OLDER to mutableListOf()
        )
        for (c in list) {
            val key = if (c.pinned) {
                PINNED
            } else {
                val day = Instant.ofEpochMilli(c.updatedAt).atZone(zone).toLocalDate()
                when (ChronoUnit.DAYS.between(day, today)) {
                    in Long.MIN_VALUE..0L -> TODAY // termasuk jam HP mundur
                    1L -> YESTERDAY
                    in 2L..6L -> WEEK
                    in 7L..29L -> MONTH
                    else -> OLDER
                }
            }
            buckets.getValue(key).add(c)
        }
        return buckets.filterValues { it.isNotEmpty() }.map { (k, v) -> Group(k, v) }
    }
}

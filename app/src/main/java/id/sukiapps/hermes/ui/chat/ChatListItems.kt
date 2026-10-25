package id.sukiapps.hermes.ui.chat

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Item yang dirender LazyColumn chat — pesan, pemisah tanggal, atau kartu
 * persetujuan agen. Kunci stabil per item dipertahankan (pesan = id,
 * pemisah = kunci hari, kartu = id tugas) supaya daftar panjang tidak
 * dirender ulang saat satu pesan streaming berubah.
 */
sealed interface ChatListItem {
    val key: String
    data class Message(val message: UiMessage) : ChatListItem {
        override val key: String get() = "msg:${message.id}"
    }
    data class DateSeparator(val dayKey: String, val label: String) : ChatListItem {
        override val key: String get() = "sep:$dayKey"
    }
}

/** Kunci hari "yyyyMMdd" zona lokal dari epoch-ms; "" bila tak valid. */
fun dayKeyOf(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    val cal = Calendar.getInstance()
    cal.timeInMillis = timestampMs
    return "%04d%02d%02d".format(
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH)
    )
}

/** Label pemisah: "Hari ini" bila harinya sama dengan [nowMs]. */
fun dayLabelOf(timestampMs: Long, nowMs: Long): String {
    if (dayKeyOf(timestampMs) == dayKeyOf(nowMs)) return "Hari ini"
    return SimpleDateFormat("d MMM yyyy", Locale("id", "ID"))
        .apply { timeZone = TimeZone.getDefault() }
        .format(Date(timestampMs))
}

/**
 * Sisipkan pemisah tanggal per hari di antara pesan. Pesan tanpa stempel
 * waktu (timestamp ≤ 0) tidak memicu pemisah — menempel ke grup sebelumnya.
 * Urutan pesan tidak diubah; hanya pemisah yang disisipkan.
 */
fun buildChatListItems(
    messages: List<UiMessage>,
    nowMs: Long = System.currentTimeMillis()
): List<ChatListItem> {
    val out = mutableListOf<ChatListItem>()
    var lastDayKey = ""
    for (m in messages) {
        val dayKey = dayKeyOf(m.timestamp)
        if (dayKey.isNotEmpty() && dayKey != lastDayKey) {
            out.add(
                ChatListItem.DateSeparator(dayKey, dayLabelOf(m.timestamp, nowMs))
            )
            lastDayKey = dayKey
        }
        out.add(ChatListItem.Message(m))
    }
    return out
}

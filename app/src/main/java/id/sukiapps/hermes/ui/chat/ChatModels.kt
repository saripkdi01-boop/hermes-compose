package id.sukiapps.hermes.ui.chat

/**
 * Model pesan untuk layar chat (Tahap 3).
 *
 * Struktur disengaja datar (flat) — cocok untuk LazyColumn keyed.
 * Metadata UI (reaksi, bintang, draft) disimpan di ChatExtrasStore via JsonStore.
 */
data class ChatMessage(
    val id: String,
    val chatId: String,
    val sender: String, // "user", "ai", "upin", "system"
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val status: MessageStatus = MessageStatus.SENT,
    val replyToId: String? = null,
    val replyToName: String? = null,
    val replyToPreview: String? = null,
    val isCode: Boolean = false,
    val starred: Boolean = false,
    val reactions: Map<String, Int> = emptyMap(), // emoji -> jumlah
    val myReaction: String? = null
)

enum class MessageStatus {
    SENDING, SENT, FAILED, STOPPED
}

/** Pesan sistem (ganti model, ganti persona) — tampil sebagai pill tengah. */
data class SystemEvent(
    val id: String,
    val text: String,
    val timestamp: Long
)

/** Satu baris di LazyColumn: pesan, pemisah tanggal, atau event sistem. */
sealed interface ChatRow {
    data class Msg(val message: ChatMessage) : ChatRow
    data class DateSep(val label: String) : ChatRow
    data class Sys(val event: SystemEvent) : ChatRow
}

val REACTION_EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

/**
 * Bangun daftar baris dari pesan mentah: sisipkan pemisah tanggal,
 * hitung grouping (pengirim sama dalam 2 menit).
 */
fun buildRows(messages: List<ChatMessage>, events: List<SystemEvent> = emptyList()): List<ChatRow> {
    if (messages.isEmpty() && events.isEmpty()) return emptyList()
    val rows = mutableListOf<ChatRow>()
    var lastDay: String? = null
    messages.sortedBy { it.timestamp }.forEach { m ->
        val day = dayLabel(m.timestamp)
        if (day != lastDay) {
            rows.add(ChatRow.DateSep(day))
            lastDay = day
        }
        rows.add(ChatRow.Msg(m))
    }
    return rows
}

private fun dayLabel(ts: Long): String {
    val cal = java.util.Calendar.getInstance()
    val now = java.util.Calendar.getInstance()
    val msg = java.util.Calendar.getInstance().apply { timeInMillis = ts }
    val sameDay = now.get(java.util.Calendar.YEAR) == msg.get(java.util.Calendar.YEAR) &&
            now.get(java.util.Calendar.DAY_OF_YEAR) == msg.get(java.util.Calendar.DAY_OF_YEAR)
    now.add(java.util.Calendar.DAY_OF_YEAR, -1)
    val yesterday = now.get(java.util.Calendar.YEAR) == msg.get(java.util.Calendar.YEAR) &&
            now.get(java.util.Calendar.DAY_OF_YEAR) == msg.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        sameDay -> "Hari ini"
        yesterday -> "Kemarin"
        else -> java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale("id", "ID"))
            .format(java.util.Date(ts))
    }
}

/**
 * Apakah pesan ini grup dengan sebelumnya (pengirim sama, selisih ≤ 2 menit).
 * Dipakai untuk gap rapat + ekor hanya di bubble terakhir grup.
 */
fun isGroupedWithPrev(current: ChatMessage, prev: ChatMessage?): Boolean {
    if (prev == null) return false
    return prev.sender == current.sender &&
            (current.timestamp - prev.timestamp) <= 2 * 60 * 1000
}

package id.sukiapps.hermes.ui.chatv3

/**
 * Model pesan untuk ChatV3 (isolasi penuh dari ui.chat).
 */
data class V3Message(
    val id: String,
    val sender: String, // "user", "ai", "upin", "system"
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val isCode: Boolean = false,
    val starred: Boolean = false,
    val reactions: Map<String, Int> = emptyMap(),
    val myReaction: String? = null,
    val replyToName: String? = null,
    val replyToPreview: String? = null,
    val replyToId: String? = null
)

enum class V3Status { SENDING, SENT, FAILED }

val V3_REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

sealed interface V3Row {
    data class Msg(val m: V3Message, val grouped: Boolean, val lastInGroup: Boolean) : V3Row
    data class Date(val label: String) : V3Row
    data class Sys(val text: String, val id: String) : V3Row
}

fun buildV3Rows(messages: List<V3Message>): List<V3Row> {
    if (messages.isEmpty()) return emptyList()
    val sorted = messages.sortedBy { it.timestamp }
    val rows = mutableListOf<V3Row>()
    var lastDay = ""
    sorted.forEachIndexed { i, m ->
        val day = v3DayLabel(m.timestamp)
        if (day != lastDay) {
            rows.add(V3Row.Date(day))
            lastDay = day
        }
        val prev = sorted.getOrNull(i - 1)
        val next = sorted.getOrNull(i + 1)
        val grouped = prev != null && prev.sender == m.sender &&
                (m.timestamp - prev.timestamp) <= 120_000
        val lastInGroup = next == null || next.sender != m.sender ||
                (next.timestamp - m.timestamp) > 120_000
        rows.add(V3Row.Msg(m, grouped, lastInGroup))
    }
    return rows
}

private fun v3DayLabel(ts: Long): String {
    val now = java.util.Calendar.getInstance()
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ts }
    val sameDay = now.get(java.util.Calendar.DAY_OF_YEAR) == c.get(java.util.Calendar.DAY_OF_YEAR) &&
            now.get(java.util.Calendar.YEAR) == c.get(java.util.Calendar.YEAR)
    if (sameDay) return "Hari ini"
    now.add(java.util.Calendar.DAY_OF_YEAR, -1)
    val yest = now.get(java.util.Calendar.DAY_OF_YEAR) == c.get(java.util.Calendar.DAY_OF_YEAR) &&
            now.get(java.util.Calendar.YEAR) == c.get(java.util.Calendar.YEAR)
    if (yest) return "Kemarin"
    return java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale("id", "ID"))
        .format(java.util.Date(ts))
}

fun v3Time(ts: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(ts))

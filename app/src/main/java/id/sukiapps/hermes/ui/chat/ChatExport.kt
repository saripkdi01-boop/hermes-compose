package id.sukiapps.hermes.ui.chat

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Format ekspor riwayat chat (v16). */
enum class ChatExportFormat {
    MARKDOWN, JSON, TEXT
}

/**
 * v16 — Bangun teks ekspor dari riwayat percakapan aktif. Fungsi murni
 * (tanpa Context) supaya mudah dipatch dan diuji. Dipanggil ViewModel,
 * hasilnya dibagikan lewat share sheet sistem (ACTION_SEND teks) —
 * tanpa FileProvider baru dan tanpa mengubah ApkShareProvider.
 */
fun buildChatExport(
    title: String,
    messages: List<UiMessage>,
    format: ChatExportFormat
): String {
    return when (format) {
        ChatExportFormat.MARKDOWN -> buildMarkdown(title, messages)
        ChatExportFormat.JSON -> buildJson(title, messages)
        ChatExportFormat.TEXT -> buildText(title, messages)
    }
}

private fun roleLabel(role: String): String =
    if (role == "user") "User" else "Hermes"

private fun timeLabel(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return SimpleDateFormat("HH:mm", Locale("id", "ID")).format(Date(timestamp))
}

private fun buildMarkdown(title: String, messages: List<UiMessage>): String {
    val sb = StringBuilder()
    sb.append("# ").append(title).append("\n\n")
    for (m in messages) {
        val time = timeLabel(m.timestamp)
        sb.append("**").append(roleLabel(m.role)).append("**")
        if (time.isNotEmpty()) sb.append(" (").append(time).append(")")
        sb.append(": ").append(m.content).append("\n\n")
    }
    return sb.toString().trimEnd() + "\n"
}

private fun buildText(title: String, messages: List<UiMessage>): String {
    val sb = StringBuilder()
    sb.append(title).append("\n\n")
    for (m in messages) {
        val time = timeLabel(m.timestamp)
        sb.append(roleLabel(m.role))
        if (time.isNotEmpty()) sb.append(" (").append(time).append(")")
        sb.append(": ").append(m.content).append("\n\n")
    }
    return sb.toString().trimEnd() + "\n"
}

private fun buildJson(title: String, messages: List<UiMessage>): String {
    val root = JSONObject()
    root.put("title", title)
    root.put("exportedAt", System.currentTimeMillis())
    val arr = JSONArray()
    for (m in messages) {
        val obj = JSONObject()
        obj.put("role", m.role)
        obj.put("content", m.content)
        obj.put("timestamp", m.timestamp)
        arr.put(obj)
    }
    root.put("messages", arr)
    return root.toString(2)
}

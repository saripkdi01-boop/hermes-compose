package id.sukiapps.hermes.ui.chatv3

import id.sukiapps.hermes.ui.chat.UiMessage

/**
 * Adapter: UiMessage (existing) → V3Message (ChatV3).
 * v2: pakai timestamp asli dari database (UiMessage.timestamp).
 */
fun UiMessage.toV3(): V3Message {
    return V3Message(
        id = id.toString(),
        sender = when (role) {
            "user" -> "user"
            "assistant" -> "ai"
            else -> "ai"
        },
        senderName = if (role == "assistant") "Hermes" else "",
        text = content,
        timestamp = if (timestamp > 0) timestamp else System.currentTimeMillis(),
        isCode = content.trimStart().startsWith("```")
    )
}

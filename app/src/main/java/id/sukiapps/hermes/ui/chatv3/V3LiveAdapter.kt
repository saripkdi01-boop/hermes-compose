package id.sukiapps.hermes.ui.chatv3

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import id.sukiapps.hermes.ui.chat.ChatViewModel
import id.sukiapps.hermes.ui.chat.UiMessage

/**
 * Adapter: UiMessage (existing) → V3Message (ChatV3).
 *
 * Catatan: UiMessage tidak punya timestamp, jadi grouping pakai
 * urutan pesan (selisih 0 = selalu grup jika role sama berurutan).
 * Untuk timestamp akurat, tambahkan field ke UiMessage nanti.
 */
fun UiMessage.toV3(index: Int): V3Message {
    // Timestamp sintetis: index * 60 detik dari base, agar grouping jalan.
    val baseTs = System.currentTimeMillis() - 3600_000L
    return V3Message(
        id = id.toString(),
        sender = when (role) {
            "user" -> "user"
            "assistant" -> "ai"
            else -> "ai"
        },
        senderName = if (role == "assistant") "Hermes" else "",
        text = content,
        timestamp = baseTs + (index * 60_000L),
        isCode = content.trimStart().startsWith("```")
    )
}

/**
 * Layar ChatV3 dengan data LIVE dari ChatViewModel existing.
 *
 * @param viewModel ChatViewModel yang sudah ada (dari ui.chat).
 * @param chatId ID chat untuk draft/reaksi per-chat.
 * @param onBack Kembali.
 */
@Composable
fun V3LiveScreen(
    viewModel: ChatViewModel,
    chatId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val messages = remember(state.messages) {
        state.messages.mapIndexed { i, m -> m.toV3(i) }
    }
    // Merge bintang & reaksi dari store.
    val enriched = remember(messages, chatId) {
        messages.map { m ->
            val starred = V3ExtrasStore.isStarred(chatId, m.id)
            m.copy(starred = starred)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ChatV3Screen(
            chatName = state.title.ifBlank { "Percakapan" },
            messages = enriched,
            onBack = onBack,
            onCopy = {},
            onReply = {},
            onForward = {},
            onToggleStar = { msg ->
                V3ExtrasStore.toggleStar(chatId, msg.id)
            },
            onSpeak = {},
            onDelete = {},
            onReaction = { msg, emoji ->
                V3ExtrasStore.toggleReaction(chatId, msg.id, emoji)
            },
            modifier = Modifier.weight(1f)
        )
        V3Composer(
            chatId = chatId,
            onSend = { text -> viewModel.send(text) }
        )
    }
}

package id.sukiapps.hermes.ui.chatv3

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import id.sukiapps.hermes.ui.chat.ChatViewModel

/**
 * Layar ChatV3 dengan data LIVE dari ChatViewModel existing.
 * Timestamp asli dari database (UiMessage.timestamp).
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
        state.messages.map { m -> m.toV3() }
    }
    val enriched = remember(messages, chatId) {
        messages.map { m ->
            m.copy(starred = V3ExtrasStore.isStarred(chatId, m.id))
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ChatV3Screen(
            chatName = state.title.ifBlank { "Percakapan" },
            messages = enriched,
            onBack = onBack,
            onToggleStar = { msg -> V3ExtrasStore.toggleStar(chatId, msg.id) },
            onReaction = { msg, emoji -> V3ExtrasStore.toggleReaction(chatId, msg.id, emoji) },
            modifier = Modifier.weight(1f)
        )
        V3Composer(
            chatId = chatId,
            onSend = { text -> viewModel.send(text) }
        )
    }
}

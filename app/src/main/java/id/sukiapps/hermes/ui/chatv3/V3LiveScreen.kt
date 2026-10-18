package id.sukiapps.hermes.ui.chatv3

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sukiapps.hermes.ui.theme.LocalChatPalette
import kotlinx.coroutines.launch

/**
 * Layar chat v14: LazyColumn dengan auto-scroll cerdas
 * (hanya scroll bila pengguna sudah di bawah, ambang 120dp),
 * FAB "turun ke pesan terbaru", dan dukungan streaming.
 *
 * @param messages daftar pesan yang sudah diurutkan kronologis
 * @param isStreaming true bila respons AI sedang mengalir
 * @param streamingText teks parsial streaming (ditampilkan di pesan terakhir AI)
 * @param errorIds id pesan yang gagal (tampilkan tombol coba lagi)
 * @param thinkingIds id pesan AI yang sedang "berpikir"
 */
@Composable
fun V3LiveScreen(
    messages: List<V3Message>,
    isStreaming: Boolean = false,
    streamingText: String = "",
    errorIds: Set<String> = emptySet(),
    thinkingIds: Set<String> = emptySet(),
    extrasStore: V3ExtrasStore? = null,
    onSend: (String) -> Unit = {},
    onStop: (() -> Unit)? = null,
    onRetry: ((V3Message) -> Unit)? = null,
    onAttach: ((String) -> Unit)? = null,
    onVoiceInput: (() -> Unit)? = null,
    modelName: String? = null,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChatPalette.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val density = LocalDensity.current
    val bottomThresholdPx = with(density) { 120.dp.toPx() }

    // Apakah pengguna sedang di dekat bawah?
    val isAtBottom by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()
            val total = layout.totalItemsCount
            if (lastVisible == null || total == 0) true
            else {
                val lastIndex = lastVisible.index
                val viewportEnd = layout.viewportEndOffset
                val itemEnd = lastVisible.offset + lastVisible.size
                lastIndex >= total - 1 && (itemEnd - viewportEnd) <= bottomThresholdPx
            }
        }
    }
    var showJumpFab by remember { mutableStateOf(false) }
    LaunchedEffect(isAtBottom) { showJumpFab = !isAtBottom }

    // Auto-scroll cerdas: hanya bila di bawah
    val lastCount = messages.size
    LaunchedEffect(lastCount, streamingText.length) {
        if (isAtBottom && lastCount > 0) {
            listState.animateScrollToItem(lastCount - 1)
        }
    }

    // Estimasi token untuk pesan terakhir pengguna
    val tokenEst = remember(messages) {
        messages.lastOrNull { it.sender == "user" }?.let { v3EstimateTokens(it.text) }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (messages.isEmpty() && !isStreaming) {
                // Empty state
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Halo, saya Hermes",
                            fontSize = 20.sp,
                            color = palette.textPrimary,
                            modifier = Modifier.semantics {
                                contentDescription = "Sapaan awal Hermes"
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tulis pesan untuk memulai percakapan.",
                            fontSize = 14.sp,
                            color = palette.textSecondary,
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    itemsIndexed(
                        items = messages,
                        key = { _, m -> m.id },
                        contentType = { _, m -> if (m.sender == "user") "user" else "ai" },
                    ) { index, msg ->
                        val isLast = index == messages.size - 1
                        val streamingThis = isLast && isStreaming && msg.sender != "user"
                        // Pesan terakhir AI saat streaming: tampilkan teks parsial
                        val effMsg = if (streamingThis && streamingText.isNotEmpty()) {
                            msg.copy(text = streamingText)
                        } else msg
                        MuseBubble(
                            message = effMsg,
                            isStreaming = streamingThis,
                            isThinking = msg.id in thinkingIds,
                            isError = msg.id in errorIds,
                            extrasStore = extrasStore,
                            snackbarHostState = snackbarHostState,
                            onRetry = if (msg.id in errorIds) ({ onRetry?.invoke(msg) }) else null,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            V3Composer(
                onSend = onSend,
                isStreaming = isStreaming,
                onStop = onStop,
                onAttach = onAttach,
                onVoiceInput = onVoiceInput,
                modelName = modelName,
                tokenEstimate = tokenEst,
                extrasStore = extrasStore,
            )
        }

        // FAB turun ke terbaru
        AnimatedVisibility(
            visible = showJumpFab,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 96.dp),
        ) {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        if (messages.isNotEmpty())
                            listState.animateScrollToItem(messages.size - 1)
                    }
                },
                shape = CircleShape,
                containerColor = palette.surfaceVariant,
                contentColor = palette.textPrimary,
                modifier = Modifier.semantics {
                    contentDescription = "Turun ke pesan terbaru"
                },
            ) {
                Icon(Icons.Filled.ArrowDownward, contentDescription = null)
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp),
        )
    }
}

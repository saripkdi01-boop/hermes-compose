package id.sukiapps.hermes.ui.chat

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Forward
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import id.sukiapps.hermes.R
import id.sukiapps.hermes.ui.theme.HermesColors
import kotlinx.coroutines.launch

/**
 * Layar chat (Tahap 3): daftar pesan + bubble + menu aksi.
 *
 * Catatan integrasi:
 * - [messages] dari ViewModel existing (ChatViewModel).
 * - Aksi onCopy/onReply/dst. diteruskan ke handler existing.
 * - Composer & streaming = Tahap 4 (belum di file ini).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HermesChatScreen(
    chatId: String,
    chatName: String,
    chatSubtitle: String,
    messages: List<ChatMessage>,
    onBack: () -> Unit,
    onCopy: (ChatMessage) -> Unit,
    onReply: (ChatMessage) -> Unit,
    onForward: (ChatMessage) -> Unit,
    onToggleStar: (ChatMessage) -> Unit,
    onSpeak: (ChatMessage) -> Unit,
    onEdit: (ChatMessage) -> Unit,
    onDeleteForMe: (ChatMessage) -> Unit,
    onDeleteForAll: (ChatMessage) -> Unit,
    onReaction: (ChatMessage, String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenInfo: () -> Unit,
    onScrollToMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = remember(messages) { buildRows(messages) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var menuOpen by remember { mutableStateOf(false) }
    var menuMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var reactionPickerFor by remember { mutableStateOf<ChatMessage?>(null) }
    val selectedIds = remember { mutableStateListOf<String>() }
    val selectionMode = selectedIds.isNotEmpty()

    // Scroll ke bawah saat buka (tanpa animasi).
    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty()) listState.scrollToItem(rows.size - 1)
    }

    // Pesan untuk lookup grouping & reply target.
    val byId = remember(messages) { messages.associateBy { it.id } }

    Scaffold(
        modifier = modifier,
        containerColor = HermesColors.chatBackground(),
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size}") },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds.clear() }) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.hermes_cd_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            selectedIds.mapNotNull { byId[it] }.forEach { onToggleStar(it) }
                            selectedIds.clear()
                        }) { Icon(Icons.Outlined.Star, stringResource(R.string.hermes_action_star)) }
                        IconButton(onClick = {
                            selectedIds.mapNotNull { byId[it] }.forEach { onDeleteForMe(it) }
                            selectedIds.clear()
                        }) { Icon(Icons.Outlined.Delete, stringResource(R.string.hermes_action_delete)) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = HermesColors.appBar())
                )
            } else {
                TopAppBar(
                    title = {
                        Text(chatName, color = Color.White, maxLines = 1)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, stringResource(R.string.hermes_cd_back), tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenSearch) {
                            Icon(Icons.Outlined.Search, stringResource(R.string.hermes_cd_search), tint = Color.White)
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, stringResource(R.string.hermes_cd_menu), tint = Color.White)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.hermes_action_info)) },
                                leadingIcon = { Icon(Icons.Outlined.Info, null) },
                                onClick = { menuOpen = false; onOpenInfo() }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = HermesColors.appBar())
                )
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (rows.isEmpty()) {
                // Empty state chat baru.
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Text(
                        stringResource(R.string.hermes_chat_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = HermesColors.textPrimary()
                    )
                    androidx.compose.foundation.layout.Spacer(
                        androidx.compose.foundation.layout.Modifier.height(8.dp)
                    )
                    Text(
                        stringResource(R.string.hermes_chat_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = HermesColors.textSecondary()
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(rows, key = { _, r ->
                        when (r) {
                            is ChatRow.Msg -> "msg-${r.message.id}"
                            is ChatRow.DateSep -> "date-${r.label}"
                            is ChatRow.Sys -> "sys-${r.event.id}"
                        }
                    }) { index, row ->
                        when (row) {
                            is ChatRow.DateSep -> DateSeparator(row.label)
                            is ChatRow.Sys -> SystemPill(row.event.text)
                            is ChatRow.Msg -> {
                                val msg = row.message
                                // Grouping: bandingkan dengan pesan sebelumnya.
                                val prevMsg = rows.take(index).lastOrNull()
                                    ?.let { it as? ChatRow.Msg }?.message
                                val grouped = isGroupedWithPrev(msg, prevMsg)
                                // Ekor hanya di terakhir grup: cek pesan berikut.
                                val nextMsg = rows.drop(index + 1).firstOrNull()
                                    ?.let { it as? ChatRow.Msg }?.message
                                val isLastInGroup = nextMsg == null ||
                                        nextMsg.sender != msg.sender ||
                                        (nextMsg.timestamp - msg.timestamp) > 2 * 60 * 1000

                                ChatBubble(
                                    message = msg,
                                    grouped = grouped,
                                    isLastInGroup = isLastInGroup,
                                    selected = msg.id in selectedIds,
                                    onClick = {
                                        if (selectionMode) {
                                            if (msg.id in selectedIds) selectedIds.remove(msg.id)
                                            else selectedIds.add(msg.id)
                                        }
                                    },
                                    onLongPress = {
                                        if (selectionMode) {
                                            if (msg.id !in selectedIds) selectedIds.add(msg.id)
                                        } else {
                                            menuMessage = msg
                                        }
                                    },
                                    onReplyTap = {
                                        msg.replyToId?.let { onScrollToMessage(it) }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Menu long-press ──────────────────────────────────────
    menuMessage?.let { msg ->
        ModalBottomSheet(
            onDismissRequest = { menuMessage = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            val isMine = msg.sender == "user"
            androidx.compose.foundation.layout.Column(
                Modifier.padding(bottom = 24.dp)
            ) {
                // Baris reaksi cepat.
                androidx.compose.foundation.layout.Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
                ) {
                    REACTION_EMOJIS.forEach { emoji ->
                        androidx.compose.material3.TextButton(onClick = {
                            onReaction(msg, emoji)
                            menuMessage = null
                        }) { Text(emoji, style = MaterialTheme.typography.headlineSmall) }
                    }
                }
                androidx.compose.material3.HorizontalDivider()
                MenuRow(R.string.hermes_bubble_copy, Icons.Outlined.ContentCopy) {
                    clipboard.setText(AnnotatedString(msg.text)); onCopy(msg); menuMessage = null
                }
                MenuRow(R.string.hermes_bubble_reply, Icons.Outlined.Reply) {
                    onReply(msg); menuMessage = null
                }
                MenuRow(R.string.hermes_bubble_forward, Icons.Outlined.Forward) {
                    onForward(msg); menuMessage = null
                }
                MenuRow(R.string.hermes_bubble_star, Icons.Outlined.Star) {
                    onToggleStar(msg); menuMessage = null
                }
                MenuRow(R.string.hermes_bubble_tts, Icons.Outlined.VolumeUp) {
                    onSpeak(msg); menuMessage = null
                }
                if (isMine) {
                    MenuRow(R.string.hermes_bubble_edit, Icons.Outlined.MoreVert) {
                        onEdit(msg); menuMessage = null
                    }
                }
                MenuRow(R.string.hermes_bubble_delete_me, Icons.Outlined.Delete) {
                    onDeleteForMe(msg); menuMessage = null
                }
                if (isMine) {
                    MenuRow(R.string.hermes_bubble_delete_all, Icons.Outlined.Delete) {
                        onDeleteForAll(msg); menuMessage = null
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    labelRes: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        leadingIcon = { Icon(icon, null) },
        onClick = onClick
    )
}

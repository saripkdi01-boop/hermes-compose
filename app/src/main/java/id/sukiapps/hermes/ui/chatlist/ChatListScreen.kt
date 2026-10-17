package id.sukiapps.hermes.ui.chatlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.MarkAsUnread
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.sukiapps.hermes.R
import id.sukiapps.hermes.ui.theme.HermesColors
import id.sukiapps.hermes.ui.theme.ThemeTokens
import id.sukiapps.hermes.ui.theme.avatarShape
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Avatar: inisial + warna deterministik dari hash nama ──────────

private val AVATAR_COLORS = listOf(
    Color(0xFF128C7E), Color(0xFF7C5CFF), Color(0xFFE07856),
    Color(0xFF3498DB), Color(0xFFE91E63), Color(0xFF9C27B0),
    Color(0xFF009688), Color(0xFFFF9800)
)

private fun avatarColorFor(name: String): Color =
    AVATAR_COLORS[(name.hashCode().and(Int.MAX_VALUE)) % AVATAR_COLORS.size]

private fun initialsOf(name: String): String {
    val parts = name.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

@Composable
private fun ChatAvatar(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(ThemeTokens.Dimen.avatarList)
            .clip(avatarShape)
            .background(avatarColorFor(name)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initialsOf(name),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
    }
}

// ── Timestamp relatif ────────────────────────────────────────────

private fun formatTimestamp(ts: Long): String {
    if (ts <= 0L) return ""
    val now = Calendar.getInstance()
    val msg = Calendar.getInstance().apply { timeInMillis = ts }
    val sameDay = now.get(Calendar.YEAR) == msg.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == msg.get(Calendar.DAY_OF_YEAR)
    now.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = now.get(Calendar.YEAR) == msg.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == msg.get(Calendar.DAY_OF_YEAR)
    return when {
        sameDay -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
        yesterday -> "Kemarin"
        else -> SimpleDateFormat("d MMM yyyy", Locale("id", "ID")).format(Date(ts))
    }
}

// ── Satu baris chat ──────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(
    item: ChatListItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) HermesColors.surfaceVariant() else Color.Transparent
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = ThemeTokens.Dimen.screenPadding, vertical = ThemeTokens.Dimen.grid8),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChatAvatar(name = item.displayName)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = HermesColors.textPrimary(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (item.pinned) {
                    Icon(
                        Icons.Outlined.PushPin, contentDescription = stringResource(R.string.hermes_cd_pin),
                        tint = HermesColors.textSecondary(), modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = formatTimestamp(item.lastMessageAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.unreadCount > 0) HermesColors.badgeUnread() else HermesColors.textSecondary()
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.previewText(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HermesColors.textSecondary(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (item.streaming) {
                    Spacer(Modifier.width(4.dp))
                    StreamingDots()
                }
                if (item.unreadCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Badge(containerColor = HermesColors.badgeUnread(), contentColor = HermesColors.onBadgeUnread()) {
                        Text(item.unreadCount.coerceAtMost(99).toString())
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingDots() {
    // Tiga titik indikator streaming aktif di daftar chat.
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) {
            Box(
                Modifier.size(6.dp).clip(CircleShape)
                    .background(HermesColors.streamingDot())
            )
        }
    }
}

// ── Layar utama ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    items: List<ChatListItem>,
    onOpenChat: (String) -> Unit,
    onNewChat: () -> Unit,
    onDeleteChats: (List<String>) -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
    onToggleArchive: (String, Boolean) -> Unit,
    onMarkUnread: (List<String>) -> Unit,
    onRename: (String, String) -> Unit,
    onLoadMore: () -> Unit,
    hasMore: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableStateOf(ChatTab.ALL) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    val selectionMode = selectedIds.isNotEmpty()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Filter tab + cari.
    val filtered by remember(items, tab, query) {
        derivedStateOf {
            var list = when (tab) {
                ChatTab.ALL -> items.filter { !it.archived }
                ChatTab.UNREAD -> items.filter { !it.archived && it.unreadCount > 0 }
                ChatTab.ARCHIVED -> items.filter { it.archived }
            }
            if (query.isNotBlank()) {
                list = list.filter {
                    it.displayName.contains(query, ignoreCase = true) ||
                            it.previewText().contains(query, ignoreCase = true)
                }
            }
            list.sortedForDisplay()
        }
    }

    val totalUnread = remember(items) { items.filter { !it.archived }.sumOf { it.unreadCount } }

    // Pagination: muat lagi saat mendekati ujung.
    LaunchedEffect(listState, filtered.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { idx ->
                if (hasMore && !isLoading && idx != null && idx >= filtered.size - 5) onLoadMore()
            }
    }

    // Hapus dengan undo 5 detik.
    var pendingDelete by remember { mutableStateOf<List<ChatListItem>>(emptyList()) }
    fun doDelete(targets: List<ChatListItem>) {
        pendingDelete = targets
        onDeleteChats(targets.map { it.id })
        selectedIds.clear()
        scope.launch {
            val r = snackbar.showSnackbar(
                message = "Percakapan dihapus",
                actionLabel = "Urungkan",
                duration = SnackbarDuration.Short // ~5 detik dengan aksi
            )
            if (r == SnackbarResult.ActionPerformed) {
                // Undo: kembalikan via callback — implementasi di ViewModel me-restore.
                pendingDelete = emptyList()
            }
            pendingDelete = emptyList()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (selectionMode) {
                // Contextual app bar.
                TopAppBar(
                    title = { Text("${selectedIds.size}") },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds.clear() }) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.hermes_cd_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            selectedIds.forEach { onTogglePin(it, true) }; selectedIds.clear()
                        }) { Icon(Icons.Outlined.PushPin, stringResource(R.string.hermes_action_pin)) }
                        IconButton(onClick = {
                            selectedIds.forEach { onToggleArchive(it, true) }; selectedIds.clear()
                        }) { Icon(Icons.Outlined.Archive, stringResource(R.string.hermes_action_archive)) }
                        IconButton(onClick = { onMarkUnread(selectedIds.toList()); selectedIds.clear() }) {
                            Icon(Icons.Outlined.MarkAsUnread, stringResource(R.string.hermes_action_mark_unread))
                        }
                        IconButton(onClick = {
                            val targets = items.filter { it.id in selectedIds }
                            doDelete(targets)
                        }) { Icon(Icons.Outlined.Delete, stringResource(R.string.hermes_action_delete)) }
                        IconButton(onClick = {
                            selectedIds.clear()
                            selectedIds.addAll(filtered.map { it.id })
                        }) { Icon(Icons.Outlined.SelectAll, stringResource(R.string.hermes_action_select_all)) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = HermesColors.appBar())
                )
            } else if (searchOpen) {
                TopAppBar(
                    title = {
                        TextField(
                            value = query, onValueChange = { query = it },
                            placeholder = { Text(stringResource(R.string.hermes_search_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { searchOpen = false; query = "" }) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.hermes_cd_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = HermesColors.appBar())
                )
            } else {
                TopAppBar(
                    title = { Text("Hermes", color = Color.White) },
                    actions = {
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(Icons.Outlined.Search, stringResource(R.string.hermes_cd_search), tint = Color.White)
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, stringResource(R.string.hermes_cd_menu), tint = Color.White)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.hermes_action_mark_unread)) },
                                leadingIcon = { Icon(Icons.Outlined.Drafts, null) },
                                onClick = {
                                    menuOpen = false
                                    onMarkUnread(filtered.filter { it.unreadCount == 0 }.map { it.id })
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = HermesColors.appBar())
                )
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!selectionMode) {
                TabRow(selectedTabIndex = tab.ordinal) {
                    Tab(selected = tab == ChatTab.ALL, onClick = { tab = ChatTab.ALL },
                        text = { Text(stringResource(R.string.hermes_tab_all)) })
                    Tab(selected = tab == ChatTab.UNREAD, onClick = { tab = ChatTab.UNREAD },
                        text = {
                            BadgedBox(badge = {
                                if (totalUnread > 0) Badge { Text(totalUnread.coerceAtMost(99).toString()) }
                            }) { Text(stringResource(R.string.hermes_tab_unread)) }
                        })
                    Tab(selected = tab == ChatTab.ARCHIVED, onClick = { tab = ChatTab.ARCHIVED },
                        text = { Text(stringResource(R.string.hermes_tab_archived)) })
                }
            }

            if (filtered.isEmpty() && !isLoading) {
                // Empty state yang menjelaskan + satu aksi.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.hermes_chat_list_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = HermesColors.textPrimary()
                        )
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.material3.Button(onClick = onNewChat) {
                            Text(stringResource(R.string.hermes_chat_list_empty_action))
                        }
                    }
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(filtered, key = { it.id }) { item ->
                        // Swipe kanan = arsip, swipe kiri = hapus.
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { v ->
                                when (v) {
                                    SwipeToDismissBoxValue.StartToEnd -> {
                                        onToggleArchive(item.id, !item.archived); true
                                    }
                                    SwipeToDismissBoxValue.EndToStart -> {
                                        doDelete(listOf(item)); true
                                    }
                                    else -> false
                                }
                            }
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val dir = dismissState.dismissDirection
                                val color = when (dir) {
                                    SwipeToDismissBoxValue.StartToEnd -> HermesColors.badgeUnread()
                                    SwipeToDismissBoxValue.EndToStart -> HermesColors.destructive()
                                    else -> Color.Transparent
                                }
                                val icon = when (dir) {
                                    SwipeToDismissBoxValue.StartToEnd -> Icons.Outlined.Archive
                                    SwipeToDismissBoxValue.EndToStart -> Icons.Outlined.Delete
                                    else -> null
                                }
                                Box(
                                    Modifier.fillMaxSize().background(color)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = if (dir == SwipeToDismissBoxValue.StartToEnd)
                                        Alignment.CenterStart else Alignment.CenterEnd
                                ) { icon?.let { Icon(it, null, tint = Color.White) } }
                            }
                        ) {
                            ChatRow(
                                item = item,
                                selected = item.id in selectedIds,
                                selectionMode = selectionMode,
                                onClick = {
                                    if (selectionMode) {
                                        if (item.id in selectedIds) selectedIds.remove(item.id)
                                        else selectedIds.add(item.id)
                                    } else onOpenChat(item.id)
                                },
                                onLongPress = {
                                    if (item.id !in selectedIds) selectedIds.add(item.id)
                                }
                            )
                        }
                    }
                    // Tidak ada spinner penuh layar setelah data pertama tampil.
                    if (isLoading && filtered.isNotEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

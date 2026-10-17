package id.sukiapps.hermes.ui.chatv3

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Forward
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import id.sukiapps.hermes.ui.theme.HermesColors

/**
 * ChatV3Screen — layar chat Tahap 3, isolasi penuh.
 *
 * TIDAK menyentuh: MainActivity, ui.chat.ChatScreen, ViewModel existing.
 * Integrasi nanti: panggil ChatV3Screen() dari route baru dengan adapter model.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatV3Screen(
    chatName: String,
    messages: List<V3Message>,
    onBack: () -> Unit,
    onCopy: (V3Message) -> Unit = {},
    onReply: (V3Message) -> Unit = {},
    onForward: (V3Message) -> Unit = {},
    onToggleStar: (V3Message) -> Unit = {},
    onSpeak: (V3Message) -> Unit = {},
    onDelete: (V3Message) -> Unit = {},
    onReaction: (V3Message, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val rows = remember(messages) { buildV3Rows(messages) }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current

    var menuMsg by remember { mutableStateOf<V3Message?>(null) }
    val selected = remember { mutableStateListOf<String>() }
    val inSelection = selected.isNotEmpty()

    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty()) listState.scrollToItem(rows.size - 1)
    }

    Scaffold(
        modifier = modifier,
        containerColor = HermesColors.chatBackground(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (inSelection) "${selected.size} dipilih" else chatName,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (inSelection) selected.clear() else onBack()
                    }) {
                        Icon(
                            imageVector = if (inSelection) Icons.Outlined.Close else Icons.Outlined.ArrowBack,
                            contentDescription = "Kembali",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (inSelection) {
                        IconButton(onClick = { selected.clear() }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Hapus", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HermesColors.appBar())
            )
        }
    ) { pad ->
        Box(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            if (rows.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Belum ada pesan",
                        style = MaterialTheme.typography.titleMedium,
                        color = HermesColors.textPrimary()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Kirim pesan pertama untuk memulai",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HermesColors.textSecondary()
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = rows,
                        key = { r ->
                            when (r) {
                                is V3Row.Msg -> "m-${r.m.id}"
                                is V3Row.Date -> "d-${r.label}"
                                is V3Row.Sys -> "s-${r.id}"
                            }
                        }
                    ) { row ->
                        when (row) {
                            is V3Row.Date -> V3DateSep(row.label)
                            is V3Row.Sys -> V3SysPill(row.text)
                            is V3Row.Msg -> {
                                val dim = if (inSelection && row.m.id !in selected)
                                    0.5f else 1f
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    V3Bubble(
                                        message = row.m,
                                        grouped = row.grouped,
                                        lastInGroup = row.lastInGroup,
                                        modifier = Modifier.combinedClickable(
                                            onClick = {
                                                if (inSelection) {
                                                    if (row.m.id in selected) selected.remove(row.m.id)
                                                    else selected.add(row.m.id)
                                                }
                                            },
                                            onLongClick = {
                                                if (inSelection) {
                                                    if (row.m.id !in selected) selected.add(row.m.id)
                                                } else {
                                                    menuMsg = row.m
                                                }
                                            }
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    menuMsg?.let { msg ->
        ModalBottomSheet(
            onDismissRequest = { menuMsg = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    V3_REACTIONS.forEach { emoji ->
                        TextButton(onClick = {
                            onReaction(msg, emoji)
                            menuMsg = null
                        }) {
                            Text(text = emoji, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
                V3MenuRow("Salin", Icons.Outlined.ContentCopy) {
                    clipboard.setText(AnnotatedString(msg.text))
                    onCopy(msg)
                    menuMsg = null
                }
                V3MenuRow("Balas", Icons.Outlined.Reply) {
                    onReply(msg)
                    menuMsg = null
                }
                V3MenuRow("Teruskan", Icons.Outlined.Forward) {
                    onForward(msg)
                    menuMsg = null
                }
                V3MenuRow("Tandai", Icons.Outlined.Star) {
                    onToggleStar(msg)
                    menuMsg = null
                }
                V3MenuRow("Bacakan", Icons.Outlined.VolumeUp) {
                    onSpeak(msg)
                    menuMsg = null
                }
                V3MenuRow("Hapus", Icons.Outlined.Delete) {
                    onDelete(msg)
                    menuMsg = null
                }
            }
        }
    }
}

@Composable
private fun V3MenuRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(text = label) },
        leadingIcon = { Icon(imageVector = icon, contentDescription = null) },
        onClick = onClick
    )
}

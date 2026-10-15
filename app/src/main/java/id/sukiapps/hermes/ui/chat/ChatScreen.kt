package id.sukiapps.hermes.ui.chat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.sukiapps.hermes.ApkShareProvider
import id.sukiapps.hermes.TtsReader
import id.sukiapps.hermes.ui.history.DrawerContent
import id.sukiapps.hermes.ui.history.HistoryViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    chatVm: ChatViewModel = hiltViewModel(),
    historyVm: HistoryViewModel = hiltViewModel()
) {
    val ctx = LocalContext.current
    val state by chatVm.state.collectAsStateWithLifecycle()
    val attachments by chatVm.attachmentsFlow.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var input by remember { mutableStateOf("") }
    var editingMsg by remember { mutableStateOf<UiMessage?>(null) }
    var editText by remember { mutableStateOf("") }

    // TTS per pesan (TtsReader legacy, lazy init).
    val tts = remember { TtsReader() }
    var speakingId by remember { mutableStateOf<Long?>(null) }
    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }
    fun speak(msg: UiMessage) {
        tts.ensure(ctx) { ok ->
            if (ok) {
                speakingId = msg.id
                tts.speak(msg.content)
            } else {
                Toast.makeText(
                    ctx,
                    "Paket suara Indonesia belum tersedia di HP ini.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Launcher lampiran.
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        uris.forEach { chatVm.addAttachment(it) }
    }
    val pickDocs = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        uris.forEach { chatVm.addAttachment(it) }
    }
    var pendingPhotoFile by remember { mutableStateOf<File?>(null) }
    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val f = pendingPhotoFile
        if (res.resultCode == Activity.RESULT_OK && f != null && f.exists()) {
            chatVm.addAttachment(Uri.fromFile(f))
        }
        pendingPhotoFile = null
    }
    fun launchCamera() {
        try {
            val dir = ApkShareProvider.cameraDir(ctx)
            val name = "cam_" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                .format(Date()) + ".jpg"
            val file = File(dir, name)
            val uri = ApkShareProvider.camUriFor(ctx, file)
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                .putExtra(MediaStore.EXTRA_OUTPUT, uri)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            // Beri izin tulis eksplisit ke semua app kamera kandidat.
            val ris = ctx.packageManager.queryIntentActivities(intent, 0)
            for (ri in ris) {
                ctx.grantUriPermission(
                    ri.activityInfo.packageName,
                    uri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            pendingPhotoFile = file
            takePhoto.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Kamera tidak tersedia.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { snack.showSnackbar(it) }
    }
    // Tandai percakapan aktif di drawer.
    LaunchedEffect(state.convId) {
        historyVm.setActive(state.convId)
    }

    BackHandler(drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                onSelect = { id ->
                    chatVm.openConversation(id)
                    scope.launch { drawerState.close() }
                },
                onNewChat = {
                    chatVm.newConversation()
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = state.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1
                            )
                            val sub = buildString {
                                if (state.model.isNotBlank()) append(state.model)
                                append(" · ~${state.tokenEstimate} token")
                            }
                            Text(
                                text = sub,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Riwayat chat")
                        }
                    },
                    actions = {
                        IconButton(onClick = { chatVm.newConversation() }) {
                            Icon(Icons.Filled.Add, contentDescription = "Chat baru")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Pengaturan")
                        }
                    }
                )
            },
            bottomBar = {
                InputBar(
                    value = input,
                    onValueChange = { input = it },
                    isStreaming = state.isStreaming,
                    onSend = {
                        chatVm.send(input)
                        input = ""
                    },
                    onStop = { chatVm.stopStream() },
                    attachments = attachments,
                    onRemoveAttachment = { chatVm.removeAttachment(it.uri) },
                    onPickImage = { pickImages.launch("image/*") },
                    onTakePhoto = { launchCamera() },
                    onPickDocument = { pickDocs.launch("*/*") },
                    modifier = Modifier.imePadding()
                )
            }
        ) { pad ->
            val listState = rememberLazyListState()
            val all = state.messages
            // Auto-scroll ke bawah saat pesan/stream bertambah.
            LaunchedEffect(all.size, state.streamingText.length) {
                if (all.isNotEmpty() || state.streamingText.isNotEmpty()) {
                    val target = if (state.streamingText.isNotEmpty()) all.size else all.size - 1
                    if (target >= 0) listState.animateScrollToItem(target)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(vertical = 8.dp)
            ) {
                items(all, key = { it.id }) { m ->
                    val isLastAssistant = !state.isStreaming &&
                        m.role == "assistant" &&
                        all.lastOrNull { it.role == "assistant" }?.id == m.id
                    MessageBubble(
                        message = m,
                        isLastAssistant = isLastAssistant,
                        isSpeaking = speakingId == m.id && tts.isSpeaking,
                        onSpeak = { speak(m) },
                        onStopSpeak = {
                            tts.stop()
                            speakingId = null
                        },
                        onEdit = {
                            editingMsg = m
                            editText = m.content
                        },
                        onRegenerate = { chatVm.regenerate() },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                if (state.isStreaming) {
                    item(key = "streaming") {
                        if (state.streamingText.isEmpty()) {
                            Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                TypingIndicator(status = "Menulis…")
                            }
                        } else {
                            MessageBubble(
                                message = UiMessage(
                                    id = -99L,
                                    role = "assistant",
                                    content = state.streamingText
                                ),
                                isLastAssistant = false,
                                isSpeaking = false,
                                onSpeak = {},
                                onStopSpeak = {},
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog edit pesan.
    editingMsg?.let { m ->
        AlertDialog(
            onDismissRequest = { editingMsg = null },
            title = { Text("Edit pesan") },
            text = {
                TextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 8
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    chatVm.editAndResend(m.id, editText)
                    editingMsg = null
                }) { Text("Kirim ulang") }
            },
            dismissButton = {
                TextButton(onClick = { editingMsg = null }) { Text("Batal") }
            }
        )
    }
}

/* akhir ChatScreen */

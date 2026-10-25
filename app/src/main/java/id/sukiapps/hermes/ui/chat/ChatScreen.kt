package id.sukiapps.hermes.ui.chat

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.sukiapps.hermes.ApkShareProvider
import id.sukiapps.hermes.TtsReader
import id.sukiapps.hermes.domain.Prefs
import id.sukiapps.hermes.ui.history.DrawerContent
import id.sukiapps.hermes.ui.history.HistoryViewModel
import id.sukiapps.hermes.ui.live.LiveActivityBar
import id.sukiapps.hermes.ui.live.LiveStatus
import id.sukiapps.hermes.ui.live.LiveStatusBus
import id.sukiapps.hermes.ui.theme.PillShape
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Maksimal karakter hasil web yang ditampilkan/dimasukkan ke input. */
private const val PREVIEW_CHARS = 4000

/**
 * v15 — Chat ala Muse: top bar minimal (judul serif + pil model),
 * layar kosong berisi sapaan + chip saran, status mode agen jadi pil.
 * Seluruh fitur lama dipertahankan: drawer, lampiran, kamera, suara,
 * TTS, web fetch, edit pesan, Upin.
 */
@Composable
fun HermesChatScreen(
    onOpenSettings: () -> Unit,
    onOpenV12Hub: () -> Unit,
    onOpenAktivitas: () -> Unit,
    onOpenMintaFitur: () -> Unit,
    onOpenAudit: () -> Unit,
    chatVm: ChatViewModel = hiltViewModel(),
    historyVm: HistoryViewModel = hiltViewModel(),
    webVm: WebFetchViewModel = hiltViewModel()
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
    var showWebInput by remember { mutableStateOf(false) }
    // TTS per pesan (TtsReader legacy, lazy init).
    val tts = remember { TtsReader() }
    var speakingId by remember { mutableStateOf<Long?>(null) }
    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }
    // Keluar layar chat → batalkan polling Upin.
    DisposableEffect(Unit) {
        onDispose { chatVm.cancelUpin() }
    }
    // ---- status live + input suara ----
    var wasStreaming by remember { mutableStateOf(false) }
    LaunchedEffect(state.isStreaming, state.streamingText.isEmpty()) {
        when {
            state.isStreaming && state.streamingText.isEmpty() -> {
                wasStreaming = true
                LiveStatusBus.post(LiveStatus.Sending)
            }
            state.isStreaming -> {
                wasStreaming = true
                LiveStatusBus.post(LiveStatus.Drawing)
            }
            wasStreaming -> {
                wasStreaming = false
                LiveStatusBus.post(LiveStatus.Done)
            }
        }
    }
    var isListening by remember { mutableStateOf(false) }
    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(ctx)) {
            SpeechRecognizer.createSpeechRecognizer(ctx)
        } else {
            null
        }
    }
    DisposableEffect(Unit) {
        onDispose { recognizer?.destroy() }
    }
    fun startListening() {
        val rec = recognizer
        if (rec == null) {
            Toast.makeText(
                ctx,
                "Pengenalan suara tidak tersedia di HP ini.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }
            override fun onError(error: Int) {
                isListening = false
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH ->
                        "Tidak terdengar jelas, coba lagi."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "Tidak ada suara terdeteksi."
                    SpeechRecognizer.ERROR_AUDIO ->
                        "Mikrofon tidak bisa diakses."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Izin mikrofon belum diberikan."
                    else -> "Input suara gagal (kode $error)."
                }
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isNotBlank()) {
                    input = if (input.isBlank()) {
                        text
                    } else {
                        input.trimEnd() + " " + text
                    }
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Bicara sekarang…")
        }
        runCatching { rec.startListening(intent) }.onFailure {
            isListening = false
            Toast.makeText(
                ctx, "Input suara tidak bisa dimulai.", Toast.LENGTH_SHORT
            ).show()
        }
    }
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening()
        } else {
            Toast.makeText(
                ctx,
                "Izin mikrofon ditolak — input suara mati.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    fun onVoiceInput() {
        if (isListening) {
            recognizer?.stopListening()
            isListening = false
            return
        }
        if (ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    fun speak(msg: UiMessage) {
        tts.ensure(ctx) { ok ->
            if (ok) {
                // v14.18: terapkan kecepatan dari Settings tiap kali bicara,
                // supaya slider di Pengaturan langsung terasa di sesi ini.
                val rate = ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
                    .getFloat(Prefs.KEY_TTS_SPEED, 1.0f)
                tts.setSpeechRate(rate)
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
                },
                onOpenAktivitas = {
                    scope.launch { drawerState.close() }
                    onOpenAktivitas()
                },
                onOpenMintaFitur = {
                    scope.launch { drawerState.close() }
                    onOpenMintaFitur()
                },
                onOpenAudit = {
                    scope.launch { drawerState.close() }
                    onOpenAudit()
                }
            )
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                MuseTopBar(
                    title = state.title,
                    subtitle = buildString {
                        if (state.model.isNotBlank()) append(state.model)
                        append(" · ~${state.tokenEstimate} token")
                    },
                    modelLabel = state.model,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onNewChat = { chatVm.newConversation() },
                    onOpenSettings = onOpenSettings,
                    onOpenV12Hub = onOpenV12Hub
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
                    onWebFetch = { webVm.reset(); showWebInput = true },
                    // Ambil teks input → gate di VM → input dikosongkan
                    // setelah izin diberikan.
                    onAskUpin = {
                        val t = input
                        chatVm.askUpin(t) { input = "" }
                    },
                    onVoiceInput = { onVoiceInput() },
                    isListening = isListening,
                    modifier = Modifier.imePadding()
                )
            }
        ) { pad ->
            val listState = rememberLazyListState()
            val all = state.messages
            // Auto-scroll ke bawah saat pesan/stream bertambah.
            LaunchedEffect(all.size, state.streamingText.length, state.upinBusy) {
                if (all.isNotEmpty() || state.streamingText.isNotEmpty()) {
                    val target = if (state.streamingText.isNotEmpty()) all.size else all.size - 1
                    if (target >= 0) listState.animateScrollToItem(target)
                }
            }
            val showEmpty = all.isEmpty() &&
                state.streamingText.isEmpty() &&
                !state.upinBusy
            Column(
                modifier = Modifier.fillMaxSize().padding(pad)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    LiveActivityBar(modifier = Modifier.padding(top = 6.dp))
                }
                // Chip event tool terakhir (mis. web_fetch).
                state.toolEvent?.let { ev ->
                    Text(
                        text = ev,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp)
                    )
                }
                // Mode Agen — pil kompak.
                AgentModePill(
                    checked = state.agentMode,
                    status = state.agentStatus,
                    onCheckedChange = { chatVm.setAgentMode(it) }
                )
                if (showEmpty) {
                    ChatEmptyState(
                        onSuggestion = { input = it },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
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
                        // Indikator sementara saat menunggu jawaban Upin.
                        if (state.upinBusy) {
                            item(key = "upin_typing") {
                                Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                    TypingIndicator(status = "Upin sedang mengetik…")
                                }
                            }
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
    // ---- "Ambil dari web": dialog input URL → preview → masukkan ke input.
    if (showWebInput) {
        AlertDialog(
            onDismissRequest = { showWebInput = false },
            title = { Text("Ambil dari web") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = webVm.urlText,
                        onValueChange = webVm::setUrl,
                        label = { Text("URL (http/https)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Isi halaman diambil sebagai teks — aksi manual, " +
                            "bukan browsing otomatis.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWebInput = false
                        webVm.fetch()
                    },
                    enabled = !webVm.loading
                ) { Text("Ambil") }
            },
            dismissButton = {
                TextButton(onClick = { showWebInput = false }) { Text("Batal") }
            }
        )
    }
    // Preview hasil / loading / error.
    val webBusy = webVm.loading
    val webText = webVm.result
    val webErr = webVm.error
    if (webBusy || webText != null || webErr != null) {
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { webVm.reset() },
            title = { Text("Hasil ambil web") },
            text = {
                when {
                    webBusy -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("Mengambil halaman…")
                        }
                    }
                    webErr != null -> Text(webErr)
                    else -> {
                        val full = webText.orEmpty()
                        val shown = full.take(PREVIEW_CHARS)
                        Column {
                            Text(
                                shown,
                                modifier = Modifier
                                    .heightIn(max = 400.dp)
                                    .verticalScroll(rememberScrollState()),
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (full.length > PREVIEW_CHARS) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "…(dipotong, total ${full.length} karakter)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (webText != null) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(webText))
                        Toast.makeText(ctx, "Disalin.", Toast.LENGTH_SHORT).show()
                    }) { Text("Salin") }
                }
            },
            dismissButton = {
                Row {
                    if (webText != null) {
                        TextButton(onClick = {
                            val insert = webText.take(PREVIEW_CHARS)
                            input = if (input.isBlank()) insert
                            else "$input\n\n$insert"
                            webVm.reset()
                            Toast.makeText(
                                ctx,
                                "Dimasukkan ke input — review dulu sebelum kirim.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }) { Text("Masukkan ke input chat") }
                    }
                    TextButton(onClick = { webVm.reset() }) { Text("Tutup") }
                }
            }
        )
    }
}

/**
 * Top bar ala Muse: tinggi 56dp, judul serif + subtitle kecil,
 * pil model di kanan, aksi ikon (chat baru, pengaturan, hub v12).
 */
@Composable
private fun MuseTopBar(
    title: String,
    subtitle: String,
    modelLabel: String,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenV12Hub: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.background, tonalElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    Icons.Filled.Menu,
                    contentDescription = "Riwayat chat",
                    tint = scheme.onSurfaceVariant
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onBackground,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (modelLabel.isNotBlank()) {
                Surface(
                    shape = PillShape,
                    color = scheme.surfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(
                        text = modelLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            IconButton(onClick = onNewChat) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Chat baru",
                    tint = scheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Pengaturan",
                    tint = scheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onOpenV12Hub) {
                Icon(
                    Icons.Filled.Apps,
                    contentDescription = "Fitur v12",
                    tint = scheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Pil status Mode Agen: titik aksen + switch + label status. */
@Composable
private fun AgentModePill(
    checked: Boolean,
    status: String?,
    onCheckedChange: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = PillShape,
        color = scheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (checked) scheme.primary.copy(alpha = 0.4f) else scheme.outlineVariant
        ),
        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (checked) scheme.primary
                        else scheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = status ?: "Mode Agen",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
/* akhir ChatScreen */

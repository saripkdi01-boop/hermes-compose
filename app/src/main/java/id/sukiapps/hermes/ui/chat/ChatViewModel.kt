package id.sukiapps.hermes.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import id.sukiapps.hermes.data.notif.ExecNotifier
import id.sukiapps.hermes.data.store.ExecutionLog
import id.sukiapps.hermes.data.agent.MemoryWriter
import id.sukiapps.hermes.data.agent.AgentFiles
import id.sukiapps.hermes.data.agent.SoulPrompt
import id.sukiapps.hermes.data.audit.DangerLevel
import id.sukiapps.hermes.data.audit.PermissionGate
import id.sukiapps.hermes.data.connectors.UpinConnector
import id.sukiapps.hermes.data.local.AppPrefs
import id.sukiapps.hermes.data.local.ChatRepository
import id.sukiapps.hermes.data.local.ReactionStore
import id.sukiapps.hermes.domain.AIProvider
import id.sukiapps.hermes.domain.AIHttpException
import id.sukiapps.hermes.domain.ChatMessage
import id.sukiapps.hermes.domain.ChatRequest
import id.sukiapps.hermes.util.FileUtil
import id.sukiapps.hermes.util.Markdown
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Satu lampiran siap kirim. */
data class AttachmentItem(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mime: String?,
    val kind: Kind,
    val imageBase64: String? = null,
    val textContent: String? = null
) {
    enum class Kind { IMAGE, TEXT, FILE }
}

data class UiMessage(
    val id: Long,
    val timestamp: Long = 0L,
    val role: String, // "user" | "assistant"
    val content: String,
    val attachmentSummary: String? = null,
    // 14.23 — bubble chat: semua opsional, default null supaya pemetaan
    // lama (ChatViewModel baris ~183) tetap kompilasi tanpa perubahan.
    // Produsennya bertahap: kartu persetujuan sudah terhubung penuh di
    // ChatScreen; kutipan, reaksi, opsi, dan metadata berkas dirender
    // bila datanya ada (produsen balasan/reaksi/file-share menyusul).
    val replyToId: Long? = null,
    val replyToSnippet: String? = null,
    val reaction: String? = null,
    val options: List<String>? = null,
    val selectedOption: Int? = null,
    val fileMeta: FileMeta? = null
)

data class ChatUiState(
    val convId: Long = -1L,
    val title: String = "Percakapan baru",
    val model: String = "",
    val availableModels: List<String> = emptyList(),
    val persona: String = "",
    val messages: List<UiMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val streamingText: String = "",
    val error: String? = null,
    val tokenEstimate: Int = 0,
    // v9.4: Upin sedang ditanya (polling jawaban)
    val upinBusy: Boolean = false,
    // Sprint 1 Gate 2: ringkasan event tool terakhir (chip di layar chat)
    val toolEvent: String? = null,
    // v19: percakapan aktif adalah incognito (tidak masuk riwayat &
    // ingatan Jiwa; dihapus permanen saat pindah chat).
    val incognito: Boolean = false,
    // v19.3 (Jiwa): ≤2 tugas aktif dari todo.md → saran proaktif di layar
    // chat kosong ("Bantu aku: …").
    val todoSuggestions: List<String> = emptyList()
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val appCtx: Context,
    private val repo: ChatRepository,
    private val prefs: AppPrefs,
    private val ai: AIProvider,
    private val memoryWriter: MemoryWriter,
    private val upin: UpinConnector,
    private val gate: PermissionGate,
    private val execNotifier: ExecNotifier,
    private val reactionStore: ReactionStore
) : ViewModel() {

    // v19.2: reaksi emoji per pesan + emoji "baru dipakai" (picker composer).
    val reactions: StateFlow<Map<Long, String>> = reactionStore.reactions
    val recentEmojis: StateFlow<List<String>> = reactionStore.recent

    /** Pasang/lepas reaksi. Chat incognito: hanya di memori (tanpa jejak). */
    fun toggleReaction(msgId: Long, emoji: String) =
        reactionStore.toggle(msgId, emoji, persist = !_state.value.incognito)

    fun noteEmojiUsed(emoji: String) = reactionStore.noteUsed(emoji)

    // v14.10: throttle notifikasi eksekusi (timestamp terakhir per kategori).
    private var lastPromptNotifAt = 0L
    private var lastDoneNotifAt = 0L
    private var lastToolNotifAt = 0L
    private var lastErrorNotifAt = 0L

    private fun postPromptNotif(summary: String) {
        if (!prefs.notifEnabled || !prefs.notifExec) return
        val now = System.currentTimeMillis()
        if (now - lastPromptNotifAt < 60_000L) return
        lastPromptNotifAt = now
        execNotifier.notifyExec("Prompt terkirim", summary)
    }

    private fun postDoneNotif(text: String) {
        if (!prefs.notifEnabled || !prefs.notifExec) return
        val now = System.currentTimeMillis()
        if (now - lastDoneNotifAt < 60_000L) return
        lastDoneNotifAt = now
        execNotifier.notifyDone("Respons selesai", text)
    }

    private fun postToolNotif(toolName: String) {
        if (!prefs.notifEnabled || !prefs.notifExec) return
        val now = System.currentTimeMillis()
        if (now - lastToolNotifAt < 30_000L) return
        lastToolNotifAt = now
        execNotifier.notifyExec("Tool selesai", toolName)
    }

    private fun postErrorNotif(message: String) {
        if (!prefs.notifEnabled || !prefs.notifExec) return
        val now = System.currentTimeMillis()
        if (now - lastErrorNotifAt < 30_000L) return
        lastErrorNotifAt = now
        execNotifier.notifyError("Gagal eksekusi", message)
    }

    private fun postApprovalNotif(toolName: String) {
        if (!prefs.notifEnabled || !prefs.notifApproval) return
        execNotifier.notifyApproval("Perlu izin", toolName)
    }

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val attachments = mutableListOf<AttachmentItem>()
    private val _attachmentsFlow = MutableStateFlow<List<AttachmentItem>>(emptyList())
    val attachmentsFlow: StateFlow<List<AttachmentItem>> = _attachmentsFlow.asStateFlow()

    private var streamJob: Job? = null
    // v19.1: penjaga kirim-ganda (lihat send/regenerate).
    private var sendInFlight = false
    private var collectJob: Job? = null
    // v9.4: job polling jawaban Upin (dibatalkan saat kirim lain/keluar layar)
    private var upinJob: Job? = null

    // Jiwa Hermes: cache prompt rakitan per conversation + hitung pesan user
    // untuk pemicu write-back yang murah.
    private var soulCache: Pair<Long, String>? = null
    private var userMsgCount = 0

    init {
        _state.update {
            it.copy(
                model = prefs.model,
                persona = prefs.persona,
                availableModels = prefs.cachedModels() ?: emptyList()
            )
        }
        openConversation(prefs.currentConvId)
        loadTodoSuggestions()
        // v19: bersihkan sisa percakapan incognito dari sesi sebelumnya
        // (mis. aplikasi tertutup saat incognito masih aktif). Bila
        // percakapan yang sedang terbuka ternyata incognito sisa, reset
        // ke chat baru setelah pembersihan selesai.
        viewModelScope.launch(Dispatchers.IO) {
            repo.purgeIncognito()
            if (prefs.currentConvId <= 0 && _state.value.incognito) {
                withContext(Dispatchers.Main) { openConversation(-1L) }
            }
        }
    }

    fun refreshSettings() {
        _state.update {
            it.copy(
                model = prefs.model,
                persona = prefs.persona,
                availableModels = prefs.cachedModels() ?: emptyList()
            )
        }
    }

    /**
     * v19: bila percakapan aktif adalah incognito dan user pindah ke
     * percakapan lain, incognito dihapus permanen (pesan + barisnya) —
     * tanpa write-back ingatan Jiwa sama sekali.
     */
    private fun discardIncognitoIfLeaving(targetId: Long) {
        val s = _state.value
        if (s.incognito && s.convId > 0 && s.convId != targetId) {
            val doomed = s.convId
            viewModelScope.launch(Dispatchers.IO) { repo.deleteConversation(doomed) }
        }
    }

    /** Buka percakapan; -1 = baru (belum disimpan sampai pesan pertama). */
    fun openConversation(convId: Long) {
        discardIncognitoIfLeaving(convId)
        stopStream()
        collectJob?.cancel()
        if (convId <= 0) {
            _state.update {
                ChatUiState(
                    model = prefs.model,
                    persona = prefs.persona,
                    todoSuggestions = it.todoSuggestions
                )
            }
            prefs.currentConvId = -1L
            return
        }
        prefs.currentConvId = convId
        userMsgCount = 0
        collectJob = viewModelScope.launch {
            repo.messages(convId).collect { list ->
                val conv = repo.conversation(convId)
                _state.update {
                    it.copy(
                        convId = convId,
                        title = conv?.title ?: "Percakapan",
                        incognito = conv?.incognito ?: false,
                        messages = list.map { m ->
                            UiMessage(
                                id = m.id,
                        timestamp = m.createdAt,
                                role = m.role,
                                content = m.content,
                                attachmentSummary = m.attachmentPath
                            )
                        },
                        error = null
                    )
                }
                updateTokenEstimate()
            }
        }
    }

    fun newConversation() {
        // Chat lama "ditutup" → coba tulis ingatan dari 10 pesan terakhir.
        // v19: chat incognito TIDAK pernah ditulis ke ingatan Jiwa.
        cancelUpin()
        val oldId = _state.value.convId
        if (oldId > 0 && !_state.value.incognito) {
            viewModelScope.launch(Dispatchers.IO) {
                val snapshot = repo.lastMessages(oldId, 10)
                    .map { ChatMessage(it.role, it.content) }
                memoryWriter.maybeWriteBack(snapshot)
            }
        }
        soulCache = null
        clearAttachments()
        openConversation(-1L)
        loadTodoSuggestions()
    }

    /** v19.3: baca ulang tugas aktif (todo.md) untuk saran proaktif. */
    private fun loadTodoSuggestions() {
        viewModelScope.launch(Dispatchers.IO) {
            val todos = runCatching {
                SoulPrompt.activeTodos(AgentFiles.read(appCtx, AgentFiles.TODO))
                    .take(2)
                    .map { if (it.length > 60) it.take(59) + "…" else it }
            }.getOrDefault(emptyList())
            _state.update { it.copy(todoSuggestions = todos) }
        }
    }

    /** v19.3: preferensi "Bacakan jawaban otomatis" (dibaca layar chat). */
    fun ttsAutoEnabled(): Boolean = prefs.ttsAuto

    /**
     * v19: mulai chat incognito. Baris percakapan dibuat langsung dengan
     * flag incognito supaya seluruh mesin kirim/streaming bekerja seperti
     * biasa; bedanya: tidak tampil di riwayat/pencarian/backup, tidak
     * ditulis ke ingatan Jiwa, dan dihapus permanen saat user pindah
     * chat (atau saat aplikasi dimulai ulang — lihat init).
     */
    fun startIncognito() {
        cancelUpin()
        val oldId = _state.value.convId
        if (oldId > 0 && !_state.value.incognito) {
            viewModelScope.launch(Dispatchers.IO) {
                val snapshot = repo.lastMessages(oldId, 10)
                    .map { ChatMessage(it.role, it.content) }
                memoryWriter.maybeWriteBack(snapshot)
            }
        }
        soulCache = null
        clearAttachments()
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                repo.newConversation("Incognito", prefs.persona, incognito = true)
            }
            openConversation(id)
        }
    }

    // ---------- lampiran ----------

    fun addAttachment(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val info = FileUtil.info(appCtx, uri)
            val lower = info.name.lowercase()
            val kind = when {
                info.mime?.startsWith("image/") == true -> AttachmentItem.Kind.IMAGE
                lower.endsWith(".txt") || lower.endsWith(".md") ||
                    lower.endsWith(".json") || lower.endsWith(".csv") ||
                    info.mime?.startsWith("text/") == true -> AttachmentItem.Kind.TEXT
                else -> AttachmentItem.Kind.FILE
            }
            val item = when (kind) {
                AttachmentItem.Kind.IMAGE ->
                    AttachmentItem(uri, info.name, info.size, info.mime, kind,
                        imageBase64 = FileUtil.imageToBase64(appCtx, uri))
                AttachmentItem.Kind.TEXT ->
                    AttachmentItem(uri, info.name, info.size, info.mime, kind,
                        textContent = FileUtil.readText(appCtx, uri))
                AttachmentItem.Kind.FILE ->
                    AttachmentItem(uri, info.name, info.size, info.mime, kind)
            }
            withContext(Dispatchers.Main) {
                attachments.add(item)
                _attachmentsFlow.value = attachments.toList()
            }
        }
    }

    fun removeAttachment(uri: Uri) {
        attachments.removeAll { it.uri == uri }
        _attachmentsFlow.value = attachments.toList()
    }

    fun clearAttachments() {
        attachments.clear()
        _attachmentsFlow.value = emptyList()
    }

    // ---------- kirim ----------

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() && attachments.isEmpty()) return
        // v19.1 (audit P1): isStreaming baru true setelah pesan user ditulis
        // ke DB, jadi ketukan ganda cepat dulu bisa mengirim dua kali.
        if (_state.value.isStreaming || sendInFlight) return
        sendInFlight = true
        cancelUpin() // kirim pesan lain → hentikan polling Upin
        val atts = attachments.toList()
        clearAttachments()
        viewModelScope.launch {
            try {
                sendInternal(prompt, atts)
            } finally {
                sendInFlight = false
            }
        }
    }

    /**
     * Kirim ulang: jawaban baru untuk pesan user terakhir (tanpa duplikat).
     *
     * v19.1 (audit P1): dulu jawaban lama tetap tersimpan dan jawaban baru
     * ditambahkan di bawahnya (dua jawaban untuk satu pertanyaan), dan
     * riwayat yang dikirim ke model memuat pertanyaan yang sama dua kali
     * (sekali di history, sekali sebagai prompt). Sekarang jawaban setelah
     * pesan user terakhir dihapus dulu — pola yang sama dengan Edit.
     */
    fun regenerate() {
        val s = _state.value
        if (s.isStreaming || sendInFlight || s.convId <= 0) return
        val lastUser = s.messages.lastOrNull { it.role == "user" } ?: return
        sendInFlight = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repo.deleteMessagesAfter(s.convId, lastUser.id)
                }
                streamReply(s.convId, lastUser.content, emptyList())
            } finally {
                sendInFlight = false
            }
        }
    }

    /** Coba lagi setelah error: sama seperti regenerate. */
    fun retry() = regenerate()

    /** Fase 2: tutup kartu error inline (snackbar tetap jalan sendiri). */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /** Edit pesan user lalu kirim ulang (jawaban setelahnya dihapus & dibuat baru). */
    fun editAndResend(msgId: Long, newText: String) {
        val text = newText.trim()
        if (text.isEmpty()) return
        val convId = _state.value.convId
        if (convId <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            repo.editUserMessage(convId, msgId, text)
            streamReply(convId, text, emptyList())
        }
    }

    fun stopStream() {
        streamJob?.cancel()
        streamJob = null
        if (_state.value.isStreaming) {
            _state.update { it.copy(isStreaming = false) }
        }
    }

    // ---------- v9.4: Tanya Upin ----------

    /** Batalkan polling Upin (dipanggil saat kirim pesan lain / keluar layar). */
    fun cancelUpin() {
        upinJob?.cancel()
        upinJob = null
        if (_state.value.upinBusy) {
            _state.update { it.copy(upinBusy = false) }
        }
    }

    /**
     * Tanya Upin: gate MEDIUM (boleh ingat) → kosongkan input (via
     * [onConsumed]) → indikator "mengetik" → kirim + polling tiap 5 detik
     * (maks 3 menit) → jawaban masuk Room sebagai pesan assistant.
     * Error (401/timeout) tampil sebagai pesan di chat, tanpa crash.
     */
    fun askUpin(question: String, onConsumed: () -> Unit = {}) {
        val q = question.trim()
        if (q.isEmpty() || upinJob != null) return
        upinJob = viewModelScope.launch {
            try {
                postApprovalNotif("Upin")
                val granted = gate.request(
                    "Upin",
                    "Kirim ke Upin:\n${q.take(200)}",
                    DangerLevel.MEDIUM
                )
                if (!granted) {
                    _state.update {
                        it.copy(error = "Ditolak — dicatat di Riwayat Izin.")
                    }
                    return@launch
                }
                onConsumed()
                var convId = _state.value.convId
                if (convId <= 0) {
                    convId = withContext(Dispatchers.IO) {
                        repo.newConversation(
                            repo.makeTitle(q),
                            prefs.persona
                        )
                    }
                    prefs.currentConvId = convId
                    openConversation(convId)
                }
                withContext(Dispatchers.IO) {
                    repo.addMessage(convId, "user", q, null)
                }
                _state.update { it.copy(upinBusy = true, error = null) }
                ExecutionLog.log("tool", "Tanya Upin", q.take(200), "start")
                postToolNotif("Tanya Upin")
                val jobId = withContext(Dispatchers.IO) { upin.sendMessage(q) }
                val answer = pollUpinAnswer(jobId)
                withContext(Dispatchers.IO) {
                    repo.addMessage(convId, "assistant", answer, null)
                    ExecutionLog.log("tool", "Upin menjawab", "${answer.length} karakter", "ok")
                    postToolNotif("Upin menjawab")
                }
            } catch (e: CancellationException) {
                throw e // batal wajar (kirim lain / keluar layar) — tanpa pesan error
            } catch (e: Exception) {
                val msg = "⚠️ Upin: ${(e.message ?: "Gagal.").take(200)}"
                withContext(NonCancellable + Dispatchers.IO) {
                    val cid = _state.value.convId
                    if (cid > 0) repo.addMessage(cid, "assistant", msg, null)
                    else _state.update { it.copy(error = msg) }
                }
            } finally {
                withContext(NonCancellable) {
                    _state.update { it.copy(upinBusy = false) }
                }
                upinJob = null
            }
        }
    }

    /** Polling /upin/reply tiap 5 detik sampai "done" atau 3 menit. */
    private suspend fun pollUpinAnswer(jobId: String): String {
        val deadline = System.currentTimeMillis() + 3 * 60 * 1000L
        while (true) {
            val (status, answer) = withContext(Dispatchers.IO) {
                upin.pollReply(jobId)
            }
            if (status == "done") {
                return answer?.trim()?.ifBlank { "(jawaban kosong)" }
                    ?: "(jawaban kosong)"
            }
            if (status == "unknown") {
                throw AIHttpException("Upin: job tidak dikenal server. Coba kirim ulang.")
            }
            if (System.currentTimeMillis() >= deadline) {
                throw AIHttpException("Upin tidak menjawab dalam 3 menit.")
            }
            delay(5000)
        }
    }

    // ---------- internal ----------

    private suspend fun sendInternal(
        prompt: String,
        atts: List<AttachmentItem>
    ) {
        val model = prefs.model.ifBlank {
            _state.update { it.copy(error = "Pilih model dulu di Pengaturan.") }
            return
        }
        var convId = _state.value.convId
        if (convId <= 0) {
            convId = withContext(Dispatchers.IO) {
                repo.newConversation(
                    repo.makeTitle(prompt.ifBlank { "Lampiran" }),
                    prefs.persona,
                    incognito = _state.value.incognito
                )
            }
            prefs.currentConvId = convId
            openConversation(convId)
        }

        // Susun prompt akhir: teks + isi file teks.
        val fullPrompt = buildString {
            append(prompt.ifBlank { "Jelaskan lampiran berikut." })
            for (a in atts.filter { it.kind == AttachmentItem.Kind.TEXT }) {
                append("\n\n[Isi file ${a.name}]:\n")
                append(a.textContent ?: "(gagal membaca file)")
            }
            for (a in atts.filter { it.kind == AttachmentItem.Kind.FILE }) {
                append("\n\n[Lampiran: ${a.name} (${FileUtil.formatSize(a.size)})]")
            }
        }
        val imageB64s = atts
            .filter { it.kind == AttachmentItem.Kind.IMAGE && it.imageBase64 != null }
            .map { it.imageBase64!! }
        val attSummary = if (atts.isEmpty()) null else
            atts.joinToString(", ") { "${it.name} (${FileUtil.formatSize(it.size)})" }

        withContext(Dispatchers.IO) {
            repo.addMessage(convId, "user", fullPrompt, attSummary)
        }
        // Tiap 10 pesan user → coba tulis ingatan (murah, fire-and-forget).
        // v19: pesan incognito tidak pernah ditulis ke ingatan Jiwa.
        userMsgCount++
        if (userMsgCount % 10 == 0 && !_state.value.incognito) {
            viewModelScope.launch(Dispatchers.IO) {
                val snapshot = repo.lastMessages(convId, 10)
                    .map { ChatMessage(it.role, it.content) }
                memoryWriter.maybeWriteBack(snapshot)
            }
        }
        // Chat biasa SELALU lewat streamReply (tugas agen hanya di tab Agent).
        streamReply(convId, fullPrompt, imageB64s)
    }

    /**
     * Rakit system prompt jiwa untuk pesan INI.
     *
     * v19.3: dulu dirakit sekali per percakapan memakai pesan pertama, jadi
     * ingatan yang dipilih tidak pernah mengikuti topik berikutnya dan fakta
     * yang baru ditulis MemoryWriter baru terpakai di percakapan lain.
     * Merakit ulang kini murah (file kecil; features.md tidak lagi disalin
     * ulang), dan bagian dinamis diletakkan di akhir prompt.
     */
    private suspend fun getSoulPrompt(convId: Long, prompt: String): String {
        val built = SoulPrompt.build(appCtx, prompt)
        soulCache = convId to built
        return built
    }

    /** Streaming jawaban asisten untuk prompt yang SUDAH tersimpan di DB. */
    private suspend fun streamReply(
        convId: Long,
        prompt: String,
        imageB64s: List<String>,
        extraSystem: String = ""
    ) {
        val model = prefs.model.ifBlank {
            _state.update { it.copy(error = "Pilih model dulu di Pengaturan.") }
            return
        }
        val history = withContext(Dispatchers.IO) {
            // 20 pesan terakhir SEBELUM prompt saat ini.
            val all = repo.lastMessages(convId, 21)
            val withoutCurrent = if (all.isNotEmpty() && all.last().role == "user" &&
                all.last().content == prompt
            ) {
                all.dropLast(1)
            } else {
                all
            }
            withoutCurrent.takeLast(20).map { ChatMessage(it.role, it.content) }
        }
        // Jiwa Hermes: system prompt dirakit SEKALI per conversation dimulai.
        val soulPrompt = getSoulPrompt(convId, prompt)
        val personaText = prefs.persona.ifBlank { "Kamu adalah asisten AI yang membantu." }
        // v9.5: model wajib tahu fitur aplikasi yang menjalankannya.
        val capsBlock = id.sukiapps.hermes.domain.AppCapabilities.promptBlock()
        val basePrompt = if (soulPrompt.isBlank()) personaText
        else "$soulPrompt\n\n$personaText"
        val req = ChatRequest(
            model = model,
            systemPrompt = "$basePrompt\n\n$capsBlock" +
                if (extraSystem.isBlank()) "" else "\n\n$extraSystem",
            history = history,
            prompt = prompt,
            imageBase64s = imageB64s,
            temperature = prefs.temperature,
            topP = prefs.topP,
            maxTokens = prefs.maxTokens.takeIf { it > 0 }
        )

        _state.update {
            it.copy(isStreaming = true, streamingText = "", error = null, toolEvent = null)
        }
        val acc = StringBuilder()
        // v16: throttle hitung token saat streaming (tiap ~40 karakter).
        var lastTokenLen = 0
        ExecutionLog.log("prompt", "Prompt → $model", prompt.take(500), "start")
        postPromptNotif(prompt.take(40))
        // Sprint 1 Gate 2: tanpa lampiran gambar -> jalur tool-loop
        // (web_fetch); dengan gambar -> jalur vision lama tanpa tool.
        val flow = if (imageB64s.isEmpty()) {
            ai.chatStreamWithTools(req) { name, ok, ms ->
                // 14.28 (penanda sumber): chip tool chat membawa jalur
                // eksplisit — nama tool chat bisa sama dengan tool agen.
                _state.update { s -> s.copy(toolEvent = chatToolEventLabel(name, ok, ms)) }
                ExecutionLog.log("tool", name, "$ms ms", if (ok) "ok" else "fail")
                postToolNotif(name)
            }
        } else {
            ai.chatStream(req)
        }
        streamJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                flow
                    .catch { e ->
                        withContext(Dispatchers.Main) {
                            val msg = (e.message ?: "Gagal terhubung.").take(200)
                            _state.update { it.copy(error = msg) }
                            ExecutionLog.log("error", "LLM gagal", msg, "fail")
                            postErrorNotif(msg)
                        }
                    }
                    // 14.21: stream yang selesai NORMAL tapi tanpa satu token
                    // pun tidak boleh hilang tanpa jejak lagi (gejala Jerouter:
                    // loading lalu hilang, tanpa error). Tampilkan error +
                    // biarkan tombol Coba lagi (regenerate) bekerja. Cause
                    // non-null = dibatalkan user (tombol stop) → jangan
                    // tampilkan error, itu penghentian yang disengaja.
                    .onCompletion { cause ->
                        if (cause == null && acc.isEmpty()) {
                            withContext(Dispatchers.Main) {
                                if (_state.value.error == null) {
                                    val msg = "Server tidak mengirim jawaban. " +
                                        "Coba lagi atau ganti model."
                                    _state.update { it.copy(error = msg) }
                                    ExecutionLog.log("error", "LLM gagal", msg, "fail")
                                }
                            }
                        }
                    }
                    .collect { token ->
                        acc.append(token)
                        val cur = acc.toString()
                        withContext(Dispatchers.Main) {
                            _state.update { it.copy(streamingText = cur) }
                            // v16: token real-time saat streaming — throttle
                            // tiap ~40 karakter (~10 token) supaya tidak
                            // recompute tiap token.
                            if (cur.length - lastTokenLen >= 40) {
                                lastTokenLen = cur.length
                                updateTokenEstimate()
                            }
                        }
                    }
            } finally {
                // Jalan juga saat dibatalkan (tombol stop): simpan teks parsial.
                withContext(NonCancellable) {
                    val finalText = acc.toString()
                    if (finalText.isNotBlank()) {
                        repo.addMessage(convId, "assistant", finalText, null)
                    }
                    ExecutionLog.log("prompt", "Respons selesai", "${finalText.length} karakter", "ok")
                    postDoneNotif("${finalText.length} karakter")
                    withContext(Dispatchers.Main) {
                        _state.update {
                            it.copy(isStreaming = false, streamingText = "")
                        }
                        updateTokenEstimate()
                    }
                }
            }
        }
    }

    /** v16: hitung token real-time saat mengetik — termasuk teks input. */
    fun onInputChanged(input: String) {
        updateTokenEstimate(input)
    }

    private fun updateTokenEstimate(inputText: String = "") {
        val s = _state.value
        val all = s.messages.joinToString("\n") { it.content } + s.streamingText + inputText
        _state.update { it.copy(tokenEstimate = Markdown.estimateTokens(all)) }
    }

    /** v16: bangun teks ekspor riwayat percakapan aktif. */
    fun buildExport(format: ChatExportFormat): String {
        val s = _state.value
        return buildChatExport(s.title, s.messages, format)
    }

    /**
     * v16: ganti model dari chip di layar chat — simpan sebagai preferensi
     * (prefs.model) seperti setModel di Settings, tanpa validasi live
     * (cepat, tanpa panggilan API; bila model tidak valid, error muncul
     * saat kirim seperti biasa).
     */
    fun selectModel(model: String) {
        prefs.model = model
        _state.update { it.copy(model = model) }
    }
}

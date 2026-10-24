package id.sukiapps.hermes.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import id.sukiapps.hermes.data.store.ExecutionLog
import id.sukiapps.hermes.data.agent.MemoryWriter
import id.sukiapps.hermes.data.agent.SoulPrompt
import id.sukiapps.hermes.data.audit.DangerLevel
import id.sukiapps.hermes.data.agent.AgentLimits
import id.sukiapps.hermes.data.audit.PermissionGate
import id.sukiapps.hermes.data.connectors.UpinConnector
import id.sukiapps.hermes.data.local.AppPrefs
import id.sukiapps.hermes.data.local.ChatRepository
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
    val attachmentSummary: String? = null
)

data class ChatUiState(
    val convId: Long = -1L,
    val title: String = "Percakapan baru",
    val model: String = "",
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
    // Sprint 1 Gate 4: Mode Agen (sesi multi-langkah terbatas)
    val agentMode: Boolean = false,
    val agentStatus: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val appCtx: Context,
    private val repo: ChatRepository,
    private val prefs: AppPrefs,
    private val ai: AIProvider,
    private val memoryWriter: MemoryWriter,
    private val upin: UpinConnector,
    private val gate: PermissionGate
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val attachments = mutableListOf<AttachmentItem>()
    private val _attachmentsFlow = MutableStateFlow<List<AttachmentItem>>(emptyList())
    val attachmentsFlow: StateFlow<List<AttachmentItem>> = _attachmentsFlow.asStateFlow()

    private var streamJob: Job? = null
    // Sprint 1 Gate 4: sesi agen
    private var agentJob: Job? = null
    @Volatile private var agentStopRequested = false
    private var collectJob: Job? = null
    // v9.4: job polling jawaban Upin (dibatalkan saat kirim lain/keluar layar)
    private var upinJob: Job? = null

    // Jiwa Hermes: cache prompt rakitan per conversation + hitung pesan user
    // untuk pemicu write-back yang murah.
    private var soulCache: Pair<Long, String>? = null
    private var userMsgCount = 0

    init {
        _state.update {
            it.copy(model = prefs.model, persona = prefs.persona)
        }
        openConversation(prefs.currentConvId)
    }

    fun refreshSettings() {
        _state.update { it.copy(model = prefs.model, persona = prefs.persona) }
    }

    /** Buka percakapan; -1 = baru (belum disimpan sampai pesan pertama). */
    fun openConversation(convId: Long) {
        stopStream()
        collectJob?.cancel()
        if (convId <= 0) {
            _state.update {
                ChatUiState(model = prefs.model, persona = prefs.persona)
            }
            prefs.currentConvId = -1L
            return
        }
        prefs.currentConvId = convId
        collectJob = viewModelScope.launch {
            repo.messages(convId).collect { list ->
                val conv = repo.conversation(convId)
                _state.update {
                    it.copy(
                        convId = convId,
                        title = conv?.title ?: "Percakapan",
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
        cancelUpin()
        val oldId = _state.value.convId
        if (oldId > 0) {
            viewModelScope.launch(Dispatchers.IO) {
                val snapshot = repo.lastMessages(oldId, 10)
                    .map { ChatMessage(it.role, it.content) }
                memoryWriter.maybeWriteBack(snapshot)
            }
        }
        soulCache = null
        clearAttachments()
        openConversation(-1L)
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
        if (_state.value.isStreaming) return
        cancelUpin() // kirim pesan lain → hentikan polling Upin
        val atts = attachments.toList()
        clearAttachments()
        viewModelScope.launch {
            sendInternal(prompt, atts)
        }
    }

    /** Kirim ulang: jawaban baru untuk pesan user terakhir (tanpa duplikat). */
    fun regenerate() {
        val s = _state.value
        if (s.isStreaming || s.convId <= 0) return
        val lastUser = s.messages.lastOrNull { it.role == "user" } ?: return
        viewModelScope.launch { streamReply(s.convId, lastUser.content, emptyList()) }
    }

    /** Coba lagi setelah error: sama seperti regenerate. */
    fun retry() = regenerate()

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
        agentStopRequested = true
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
                val jobId = withContext(Dispatchers.IO) { upin.sendMessage(q) }
                val answer = pollUpinAnswer(jobId)
                withContext(Dispatchers.IO) {
                    repo.addMessage(convId, "assistant", answer, null)
                    ExecutionLog.log("tool", "Upin menjawab", "${answer.length} karakter", "ok")
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
                repo.newConversation(repo.makeTitle(prompt.ifBlank { "Lampiran" }), prefs.persona)
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
        userMsgCount++
        if (userMsgCount % 10 == 0) {
            viewModelScope.launch(Dispatchers.IO) {
                val snapshot = repo.lastMessages(convId, 10)
                    .map { ChatMessage(it.role, it.content) }
                memoryWriter.maybeWriteBack(snapshot)
            }
        }
        // Sprint 1 Gate 4: Mode Agen aktif (tanpa gambar) -> sesi agen.
        if (_state.value.agentMode && imageB64s.isEmpty()) {
            runAgentSession(convId, fullPrompt)
        } else {
            streamReply(convId, fullPrompt, imageB64s)
        }
    }

    // ---------- Sprint 1 Gate 4: Mode Agen ----------

    fun setAgentMode(on: Boolean) {
        _state.update { it.copy(agentMode = on, agentStatus = null) }
    }

    /**
     * Sesi agen: ulangi langkah chat+tool menuju [goal] sampai model
     * menandai SELESAI, atau batas AgentLimits tercapai (iterasi /
     * waktu / budget token ESTIMASI), atau user menekan stop.
     * Langkah lanjutan hanya hidup sebagai pesan user [SISTEM AGEN]
     * di percakapan ini (transparan & tercatat di Room sebagai pesan
     * biasa — BUKAN perubahan skema).
     */
    private fun runAgentSession(convId: Long, goal: String) {
        agentStopRequested = false
        val limits = AgentLimits.get(appCtx)
        val startMs = System.currentTimeMillis()
        var step = 0
        agentJob = viewModelScope.launch {
            var reason = "selesai"
            var currentPrompt = goal
            while (true) {
                step++
                _state.update {
                    it.copy(agentStatus = "🤖 Agen langkah $step/${limits.maxIterations}…")
                }
                val instruction = if (goal.contains("upgrade diri", ignoreCase = true)) {
                    AGENT_INSTRUCTION + SELFUPGRADE_INSTRUCTION
                } else {
                    AGENT_INSTRUCTION
                }
                streamReply(convId, currentPrompt, emptyList(), instruction)
                // streamReply berjalan di streamJob sendiri; tunggu selesai.
                _state.first { s -> !s.isStreaming }
                if (agentStopRequested) { reason = "dihentikan user"; break }
                if (_state.value.error != null) { reason = "error pada langkah $step"; break }
                val last = _state.value.messages
                    .lastOrNull { it.role == "assistant" }?.content.orEmpty()
                val est = _state.value.tokenEstimate
                val elapsed = System.currentTimeMillis() - startMs
                _state.update {
                    it.copy(
                        agentStatus = "🤖 Langkah $step/${limits.maxIterations} · " +
                            "±$est token · ${elapsed / 1000} dtk"
                    )
                }
                when {
                    last.trimStart().startsWith("SELESAI") -> {
                        reason = "tujuan tercapai"; break
                    }
                    step >= limits.maxIterations -> {
                        reason = "batas iterasi (${limits.maxIterations}) tercapai"; break
                    }
                    elapsed >= limits.timeoutMs -> {
                        reason = "batas waktu (${limits.timeoutMinutes} menit) tercapai"; break
                    }
                    est >= limits.tokenBudget -> {
                        reason = "budget token (${limits.tokenBudget}) tercapai"; break
                    }
                }
                currentPrompt = "[SISTEM AGEN] Lanjutkan langkah berikutnya menuju " +
                    "tujuan: $goal — bila tujuan sudah tercapai, awali jawabanmu " +
                    "dengan kata SELESAI diikuti ringkasan hasil akhir."
                withContext(Dispatchers.IO) {
                    repo.addMessage(convId, "user", currentPrompt, null)
                }
            }
            ExecutionLog.log("agent", "Sesi agen", "$reason · $step langkah", "ok")
            _state.update {
                it.copy(agentStatus = "🤖 Sesi agen berhenti: $reason ($step langkah)")
            }
        }
    }

    /** Rakit system prompt jiwa SEKALI per conversation (cache per convId). */
    private suspend fun getSoulPrompt(convId: Long, firstPrompt: String): String {
        soulCache?.let { if (it.first == convId) return it.second }
        val built = SoulPrompt.build(appCtx, firstPrompt)
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
        ExecutionLog.log("prompt", "Prompt → $model", prompt.take(500), "start")
        // Sprint 1 Gate 2: tanpa lampiran gambar -> jalur tool-loop
        // (web_fetch); dengan gambar -> jalur vision lama tanpa tool.
        val flow = if (imageB64s.isEmpty()) {
            ai.chatStreamWithTools(req) { name, ok, ms ->
                val label = "🌐 $name ${if (ok) "✓" else "✗"} " +
                    "${"%.1f".format(ms / 1000.0)} dtk"
                _state.update { s -> s.copy(toolEvent = label) }
                ExecutionLog.log("tool", name, "$ms ms", if (ok) "ok" else "fail")
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
                        }
                    }
                    .collect { token ->
                        acc.append(token)
                        val cur = acc.toString()
                        withContext(Dispatchers.Main) {
                            _state.update { it.copy(streamingText = cur) }
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

    companion object {
        private const val AGENT_INSTRUCTION =
            "MODE AGEN AKTIF: kamu bekerja mandiri menuju tujuan user dengan " +
                "tool yang tersedia. Kerjakan SATU langkah bermakna per giliran, " +
                "jelaskan singkat hasil langkahmu. Bila tujuan SUDAH tercapai, " +
                "awali jawaban akhirmu dengan kata SELESAI diikuti ringkasan " +
                "hasil. Bila belum, akhiri dengan langkah berikutnya yang akan " +
                "kamu ambil."

        /**
         * Resep Upgrade Diri (14.8) — ditempel ke AGENT_INSTRUCTION
         * hanya bila tujuan sesi menyebut "upgrade diri". Urutan
         * wajib: baca -> branch -> tulis -> PR -> CI success ->
         * merge -> draft rilis -> lapor. Signing + lampir APK +
         * publish tetap oleh maintainer (bukan oleh agen).
         */
        private const val SELFUPGRADE_INSTRUCTION =
            " RESEP UPGRADE DIRI: target repo saripkdi01-boop/" +
                "hermes-compose. Langkah: 1) baca file yang diubah via " +
                "github_read_file; 2) buat branch via selfupgrade_branch " +
                "(nama berawalan selfupgrade/); 3) tulis file LENGKAP " +
                "via selfupgrade_write_file — kontrak full-file, JANGAN " +
                "unified diff; bump versionCode +1 dan versionName di " +
                "app/build.gradle.kts; file lama yang diubah WAJIB " +
                "dibackup <nama>.bak; JANGAN ubah skema Room; JANGAN " +
                "hardcode secret; 4) buat PR via selfupgrade_pr; 5) cek " +
                "CI via selfupgrade_ci sampai state success — JANGAN " +
                "merge bila pending atau failure; 6) merge via " +
                "selfupgrade_merge; 7) buat DRAFT rilis via " +
                "selfupgrade_release — tag v<versionName>, body WAJIB " +
                "memuat baris versionCode:NN; 8) laporkan URL draft " +
                "rilis dan perintah berikut: maintainer sign APK dari " +
                "artifact CI, lampirkan ke draft, lalu publish — " +
                "setelah publish, update bisa diunduh dari Cek update. " +
                "Bila tool menolak karena toggle nonaktif, suruh user " +
                "mengaktifkannya di Pengaturan > Agen."
    }

    private fun updateTokenEstimate() {
        val s = _state.value
        val all = s.messages.joinToString("\n") { it.content } + s.streamingText
        _state.update { it.copy(tokenEstimate = Markdown.estimateTokens(all)) }
    }
}

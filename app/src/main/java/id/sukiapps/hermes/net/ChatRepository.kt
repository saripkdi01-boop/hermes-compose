package id.sukiapps.hermes.net
import id.sukiapps.hermes.data.SettingsRepository
import id.sukiapps.hermes.tool.ToolCall
import id.sukiapps.hermes.tool.ToolLoop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
/**
 * Satu-satunya sumber kebenaran untuk request chat.
 * Membaca config dari SettingsRepository, streaming via SseClient.
 */
class ChatRepository(
    private val settings: SettingsRepository,
    private val sse: SseClient,
    private val logger: ActivityLogger? = null
) {
    data class ChatMessage(val role: String, val content: String)
    sealed interface SendResult {
        data object Ok : SendResult
        /** baseUrl kosong — UI harus tampilkan banner ke settings. */
        data object NeedBaseUrl : SendResult
        /** API key kosong. */
        data object NeedApiKey : SendResult
        data class Failed(val message: String, val retryable: Boolean) : SendResult
    }
    /**
     * Kirim pesan dengan streaming.
     * @param systemPrompt digabung sebagai messages[0] role=system
     *   (TIDAK dirender sebagai bubble — filter di layer UI).
     */
    suspend fun sendStreaming(
        messages: List<ChatMessage>,
        systemPrompt: String = "",
        soulExtra: String = "",
        onToken: (String) -> Unit,
        onState: (SseClient.SseState) -> Unit = {}
    ): SendResult = withContext(Dispatchers.IO) {
        val baseUrl = settings.connection.value.baseUrl.trim().trimEnd('/')
        if (baseUrl.isEmpty()) {
            return@withContext SendResult.NeedBaseUrl
        }
        val apiKey = settings.getApiKey()
        if (apiKey.isEmpty()) {
            return@withContext SendResult.NeedApiKey
        }
        val model = settings.model.value.modelId.ifEmpty { "gpt-4o-mini" }
        val params = settings.parameters.value
        val body = buildBody(messages, systemPrompt, soulExtra, model, params)
        val url = "$baseUrl/chat/completions"
        val t0 = System.currentTimeMillis()
        var failed: SendResult.Failed? = null
        var firstToken = true
        sse.stream(
            url = url,
            apiKey = apiKey,
            bodyJson = body.toString(),
            cb = SseClient.StreamCallbacks(
                onState = onState,
                onToken = { tok ->
                    if (firstToken) {
                        firstToken = false
                    }
                    onToken(tok)
                },
                onDone = {},
                onError = { msg, retryable ->
                    failed = SendResult.Failed(msg, retryable)
                }
            )
        )
        val ms = System.currentTimeMillis() - t0
        logger?.log(
            ActivityLogger.ActivityEvent.ChatRequest(
                model = model,
                ms = ms,
                tokens = null, // dihitung di UI bila perlu
                ok = failed == null
            )
        )
        failed ?: SendResult.Ok
    }
    private fun buildBody(
        messages: List<ChatMessage>,
        systemPrompt: String,
        soulExtra: String,
        model: String,
        params: id.sukiapps.hermes.data.Parameters
    ): JSONObject {
        val arr = JSONArray()
        val system = buildString {
            if (systemPrompt.isNotBlank()) append(systemPrompt)
            if (soulExtra.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(soulExtra)
            }
        }
        if (system.isNotBlank()) {
            arr.put(
                JSONObject()
                    .put("role", "system")
                    .put("content", system)
            )
        }
        messages.forEach { m ->
            arr.put(
                JSONObject()
                    .put("role", m.role)
                    .put("content", m.content)
            )
        }
        return JSONObject()
            .put("model", model)
            .put("messages", arr)
            .put("stream", true)
            .put("temperature", params.temperature.toDouble())
            .put("top_p", params.topP.toDouble())
            .put("max_tokens", params.maxTokens)
    }
    /**
     * Kirim dengan tool-loop web_fetch (ADITIF — sendStreaming tidak diubah).
     *
     * Alur: request + tools[] → stream → kumpulkan delta.tool_calls →
     * eksekusi tool → append pesan assistant(tool_calls) + role=tool →
     * ulangi (max [maxIterations], default 6).
     *
     * @param onToolEvent dipanggil tiap tool selesai: (nama, sukses, ms)
     *   — untuk chip UI "🌐 web_fetch ✓ 4.2s".
     */
    suspend fun sendWithTools(
        messages: List<ChatMessage>,
        systemPrompt: String = "",
        soulExtra: String = "",
        onToken: (String) -> Unit,
        onState: (SseClient.SseState) -> Unit = {},
        onToolEvent: (name: String, ok: Boolean, ms: Long) -> Unit = { _, _, _ -> },
        maxIterations: Int = ToolLoop.MAX_ITERATIONS,
    ): SendResult = withContext(Dispatchers.IO) {
        val baseUrl = settings.connection.value.baseUrl.trim().trimEnd('/')
        if (baseUrl.isEmpty()) {
            return@withContext SendResult.NeedBaseUrl
        }
        val apiKey = settings.getApiKey()
        if (apiKey.isEmpty()) {
            return@withContext SendResult.NeedApiKey
        }
        val model = settings.model.value.modelId.ifEmpty { "gpt-4o-mini" }
        val params = settings.parameters.value
        val url = "$baseUrl/chat/completions"
        val t0 = System.currentTimeMillis()
        // Clamp keras: ToolLoop.HARD_MAX_ITERATIONS adalah batas resmi
        // tool-loop. Sebelumnya konstanta itu dead code — pemanggil bisa
        // mengirim maxIterations = 9999 dan loop tidak pernah berhenti
        // sampai server menutup koneksi.
        val iters = maxIterations.coerceIn(1, ToolLoop.HARD_MAX_ITERATIONS)
        // Riwayat yang berkembang tiap iterasi (mulai dari body standar).
        val history = buildBody(messages, systemPrompt, soulExtra, model, params)
            .getJSONArray("messages")
        var iteration = 0
        var failed: SendResult.Failed? = null
        while (iteration < iters) {
            iteration++
            val body = JSONObject()
                .put("model", model)
                .put("messages", history)
                .put("stream", true)
                .put("temperature", params.temperature.toDouble())
                .put("top_p", params.topP.toDouble())
                .put("max_tokens", params.maxTokens)
                .put("tools", ToolLoop.toolsArray())
                .put("tool_choice", "auto")
            val pending = mutableMapOf<Int, ToolCall>()
            sse.stream(
                url = url,
                apiKey = apiKey,
                bodyJson = body.toString(),
                cb = SseClient.StreamCallbacks(
                    onState = onState,
                    onToken = { tok ->
                        onToken(tok)
                    },
                    onToolCall = { index, id, name, argsFragment ->
                        val slot = pending.getOrPut(index) { ToolCall() }
                        if (id.isNotEmpty()) slot.id = id
                        if (name.isNotEmpty()) slot.name = name
                        slot.argsBuf.append(argsFragment)
                    },
                    onDone = {},
                    onError = { msg, retryable ->
                        failed = SendResult.Failed(msg, retryable)
                    },
                ),
            )
            if (failed != null) break
            if (pending.isEmpty()) break
            // Banyak provider OpenAI-compatible mengirim "id" hanya di
            // chunk tool_call PERTAMA; chunk lanjutan hanya index +
            // fragment. Kalau id tidak pernah datang, slot.id tetap ""
            // dan pesan role=tool terkirim dengan "tool_call_id": "" —
            // ditolak HTTP 400 oleh server ketat, tool-loop mati total.
            // Sintesis id deterministik unik per (iterasi, index).
            pending.forEach { (idx, c) ->
                if (c.id.isBlank()) c.id = "call_${iteration}_$idx"
            }
            // 1. Append pesan assistant berisi tool_calls.
            history.put(
                JSONObject()
                    .put("role", "assistant")
                    .put("content", JSONObject.NULL)
                    .put(
                        "tool_calls",
                        JSONArray().apply {
                            pending.entries.sortedBy { it.key }.forEach { (_, c) ->
                                put(
                                    JSONObject()
                                        .put("id", c.id)
                                        .put("type", "function")
                                        .put(
                                            "function",
                                            JSONObject()
                                                .put("name", c.name)
                                                .put("arguments", c.argsBuf.toString()),
                                        ),
                                )
                            }
                        },
                    ),
            )
            // 2. Eksekusi tiap tool, append hasilnya sebagai role=tool.
            for ((_, call) in pending.entries.sortedBy { it.key }) {
                val tTool = System.currentTimeMillis()
                val res = ToolLoop.execute(call.name, call.argsJson())
                val ms = System.currentTimeMillis() - tTool
                onToolEvent(call.name, !res.isError, ms)
                logger?.log(
                    ActivityLogger.ActivityEvent.ConnectorExecuted(
                        id = "web_fetch",
                        action = call.name,
                        decision = if (res.isError) "error" else "allow",
                        ms = ms,
                    ),
                )
                history.put(
                    JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", call.id)
                        .put("content", res.content),
                )
            }
        }
        val ms = System.currentTimeMillis() - t0
        logger?.log(
            ActivityLogger.ActivityEvent.ChatRequest(
                model = model,
                ms = ms,
                tokens = null,
                ok = failed == null,
            ),
        )
        failed ?: SendResult.Ok
    }
}
/* Catatan: SettingsRepository.getApiKey() sudah tersedia sebagai method publik. */

package id.sukiapps.hermes.net

import id.sukiapps.hermes.data.SettingsRepository
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
        if (baseUrl.isEmpty()) return@withContext SendResult.NeedBaseUrl

        val apiKey = settings.getApiKey()
        if (apiKey.isEmpty()) return@withContext SendResult.NeedApiKey

        val model = settings.model.value.modelId.ifEmpty { "gpt-4o-mini" }
        val params = settings.parameters.value

        val body = buildBody(messages, systemPrompt, soulExtra, model, params)
        val url = "$baseUrl/chat/completions"
        val t0 = System.currentTimeMillis()
        var failed: SendResult.Failed? = null

        sse.stream(
            url = url,
            apiKey = apiKey,
            bodyJson = body.toString(),
            cb = SseClient.StreamCallbacks(
                onState = onState,
                onToken = onToken,
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
}

/* Catatan: SettingsRepository.getApiKey() sudah tersedia sebagai method publik. */

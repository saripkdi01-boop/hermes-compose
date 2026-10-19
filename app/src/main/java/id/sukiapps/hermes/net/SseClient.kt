package id.sukiapps.hermes.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Klien SSE (Server-Sent Events) untuk streaming chat.
 * Parsing MANUAL via OkHttp ResponseBody — tanpa dependensi okhttp-sse
 * (M6: jangan tambah dependency tanpa alasan).
 *
 * Fitur:
 * - Header Authorization Bearer + Accept: text/event-stream
 * - Parse event `data:` (multi-baris digabung), `data: [DONE]` = selesai
 * - 401 → pesan "API key invalid"; 429 → info retry; 5xx → retry 3x
 * - Reconnect otomatis max 5x bila putus di tengah
 * - readTimeout 0 (SSE long-lived), connectTimeout 30 dtk
 */
class SseClient(private val baseClient: OkHttpClient) {

    sealed interface SseState {
        data object Idle : SseState
        data object Connecting : SseState
        data class Streaming(val startedAtMs: Long) : SseState
        data object Done : SseState
        data class Error(val message: String, val retryable: Boolean) : SseState
    }

    data class StreamCallbacks(
        val onState: (SseState) -> Unit = {},
        /** Token/potongan teks yang datang. */
        val onToken: (String) -> Unit = {},
        val onDone: () -> Unit = {},
        val onError: (message: String, retryable: Boolean) -> Unit = { _, _ -> },
        /**
         * Delta tool_call (web_fetch): index slot, id (kosong di chunk lanjutan),
         * nama fungsi, fragmen argumen JSON. Aditif untuk tool-loop.
         */
        val onToolCall: (index: Int, id: String, name: String, argsFragment: String) -> Unit =
            { _, _, _, _ -> }
    )

    companion object {
        const val MAX_RECONNECT = 5
        const val MAX_RETRY_5XX = 3
    }

    private fun sseClient(): OkHttpClient = baseClient.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // SSE: tidak ada batas baca
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Streaming POST ke endpoint chat completions.
     * @param url full URL (mis. https://host/v1/chat/completions)
     * @param apiKey Bearer token
     * @param bodyJson body request JSON (sudah termasuk "stream": true)
     */
    suspend fun stream(
        url: String,
        apiKey: String,
        bodyJson: String,
        cb: StreamCallbacks
    ) = withContext(Dispatchers.IO) {
        var attempt = 0
        var retry5xx = 0

        while (true) {
            cb.onState(SseState.Connecting)
            val result = try {
                doStream(url, apiKey, bodyJson, cb)
            } catch (e: Exception) {
                StreamOutcome.Error("Koneksi gagal: ${e.message}", true)
            }

            when (result) {
                is StreamOutcome.Finished -> {
                    cb.onState(SseState.Done)
                    cb.onDone()
                    return@withContext
                }
                is StreamOutcome.Error -> {
                    if (!result.retryable) {
                        cb.onState(SseState.Error(result.message, false))
                        cb.onError(result.message, false)
                        return@withContext
                    }
                    attempt++
                    if (attempt > MAX_RECONNECT) {
                        val msg = "Koneksi putus ${MAX_RECONNECT}x. " + result.message
                        cb.onState(SseState.Error(msg, false))
                        cb.onError(msg, false)
                        return@withContext
                    }
                    // backoff eksponensial: 1s, 2s, 4s, 8s, 16s
                    val waitMs = (1000L shl (attempt - 1)).coerceAtMost(16000L)
                    try {
                        kotlinx.coroutines.delay(waitMs)
                    } catch (_: Exception) {
                        return@withContext
                    }
                    // loop → reconnect
                }
                is StreamOutcome.Retry5xx -> {
                    retry5xx++
                    if (retry5xx > MAX_RETRY_5XX) {
                        val msg = "Server error berulang (${result.code})."
                        cb.onState(SseState.Error(msg, false))
                        cb.onError(msg, false)
                        return@withContext
                    }
                    val waitMs = (1000L * retry5xx) +
                        (0..500).random() // jitter
                    try {
                        kotlinx.coroutines.delay(waitMs)
                    } catch (_: Exception) {
                        return@withContext
                    }
                }
                is StreamOutcome.AuthError -> {
                    val msg = "API key invalid — buka Pengaturan Model."
                    cb.onState(SseState.Error(msg, false))
                    cb.onError(msg, false)
                    return@withContext
                }
                is StreamOutcome.RateLimited -> {
                    val msg = "Rate limit (429) — coba lagi 1 menit lagi."
                    cb.onState(SseState.Error(msg, true))
                    cb.onError(msg, true)
                    return@withContext
                }
            }
        }
    }

    private sealed interface StreamOutcome {
        data object Finished : StreamOutcome
        data class Error(val message: String, val retryable: Boolean) : StreamOutcome
        data class Retry5xx(val code: Int) : StreamOutcome
        data object AuthError : StreamOutcome
        data object RateLimited : StreamOutcome
    }

    private fun doStream(
        url: String,
        apiKey: String,
        bodyJson: String,
        cb: StreamCallbacks
    ): StreamOutcome {
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "text/event-stream")
            .header("Content-Type", "application/json")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            sseClient().newCall(req).execute().use { resp ->
                when (resp.code) {
                    401 -> return StreamOutcome.AuthError
                    429 -> return StreamOutcome.RateLimited
                    in 500..599 -> return StreamOutcome.Retry5xx(resp.code)
                    !in 200..299 -> return StreamOutcome.Error(
                        "Server merespons ${resp.code}.", false
                    )
                }

                val source = resp.body?.source()
                    ?: return StreamOutcome.Error(
                        "Respons kosong dari server.", true
                    )

                cb.onState(SseState.Streaming(System.currentTimeMillis()))
                val dataBuf = StringBuilder()

                while (true) {
                    val line = try {
                        // readUtf8Line() null = stream selesai
                        source.readUtf8Line() ?: break
                    } catch (e: IOException) {
                        return StreamOutcome.Error(
                            "Koneksi terputus di tengah.", true
                        )
                    }

                    if (line.isEmpty()) {
                        // baris kosong = akhir satu event → proses buffer
                        flushData(dataBuf, cb)?.let { return it }
                        continue
                    }
                    if (line.startsWith(":")) continue // komentar SSE
                    if (line.startsWith("data:")) {
                        val payload = line.removePrefix("data:").trimStart()
                        if (dataBuf.isNotEmpty()) dataBuf.append('\n')
                        dataBuf.append(payload)
                    }
                    // field lain (event:, id:, retry:) diabaikan
                }
                // flush sisa buffer di akhir stream
                flushData(dataBuf, cb)?.let { return it }
                return StreamOutcome.Finished
            }
        } catch (e: IOException) {
            return StreamOutcome.Error(
                "Koneksi gagal: ${e.message}", true
            )
        }
    }

    /**
     * Proses buffer data satu event.
     * @return StreamOutcome bila event mengakhiri stream, null bila lanjut.
     */
    private fun flushData(
        buf: StringBuilder,
        cb: StreamCallbacks
    ): StreamOutcome? {
        if (buf.isEmpty()) return null
        val data = buf.toString()
        buf.clear()
        if (data == "[DONE]") return StreamOutcome.Finished
        // Ekstrak delta.content dari JSON OpenAI-compatible
        val token = extractDelta(data)
        if (token.isNotEmpty()) cb.onToken(token)
        // Tool calls (aditif): hanya parse bila chunk mengandung tool_calls
        if (data.contains("\"tool_calls\"")) extractToolCalls(data, cb)
        return null
    }

    /**
     * Ambil choices[0].delta.tool_calls[] via org.json.
     * Dipanggil hanya bila data mengandung "tool_calls" (di flushData).
     * Malformed chunk diabaikan — tidak boleh menggagalkan stream.
     */
    private fun extractToolCalls(json: String, cb: StreamCallbacks) {
        try {
            val root = JSONObject(json)
            val choices = root.optJSONArray("choices") ?: return
            val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: return
            val tcs = delta.optJSONArray("tool_calls") ?: return
            for (i in 0 until tcs.length()) {
                val tc = tcs.optJSONObject(i) ?: continue
                val index = tc.optInt("index", 0)
                val id = tc.optString("id", "")
                val fn = tc.optJSONObject("function")
                val name = fn?.optString("name", "").orEmpty()
                val args = fn?.optString("arguments", "").orEmpty()
                cb.onToolCall(index, id, name, args)
            }
        } catch (_: Exception) {
            // abaikan: chunk tool_call malformed bukan fatal
        }
    }

    /** Ambil choices[0].delta.content tanpa library JSON berat. */
    private fun extractDelta(json: String): String {
        return try {
            // cari "delta":{...} lalu "content":"..."
            val deltaIdx = json.indexOf("\"delta\"")
            if (deltaIdx < 0) return ""
            val contentIdx = json.indexOf("\"content\"", deltaIdx)
            if (contentIdx < 0) return ""
            val colonIdx = json.indexOf(':', contentIdx)
            if (colonIdx < 0) return ""
            var i = colonIdx + 1
            while (i < json.length && json[i].isWhitespace()) i++
            if (i >= json.length) return ""
            if (json.startsWith("null", i)) return ""
            if (json[i] != '"') return ""
            // parse string JSON dengan escape
            val sb = StringBuilder()
            i++
            while (i < json.length) {
                val c = json[i]
                if (c == '"') break
                if (c == '\\' && i + 1 < json.length) {
                    when (json[i + 1]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        'u' -> {
                            if (i + 5 < json.length) {
                                val hex = json.substring(i + 2, i + 6)
                                sb.append(hex.toIntOrNull(16)?.toChar() ?: '?')
                                i += 4
                            }
                        }
                        else -> sb.append(json[i + 1])
                    }
                    i += 2
                } else {
                    sb.append(c)
                    i++
                }
            }
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }
}

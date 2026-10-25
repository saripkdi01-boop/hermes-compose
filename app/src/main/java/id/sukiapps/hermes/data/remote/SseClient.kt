package id.sukiapps.hermes.data.remote

import id.sukiapps.hermes.domain.AIHttpException
import id.sukiapps.hermes.domain.ChatRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import id.sukiapps.hermes.tool.ToolCall
import id.sukiapps.hermes.tool.ToolLoop
import java.io.IOException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streaming SSE /chat/completions via OkHttp.
 * Body identik legacy: {model, messages:[system + 20 terakhir + user],
 * temperature?, top_p?, max_tokens?, stream:true}.
 * Gambar → vision parts image_url (data:image/jpeg;base64,...).
 */
@Singleton
class SseClient @Inject constructor(
    private val client: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    fun stream(baseUrl: String, req: ChatRequest): Flow<String> = callbackFlow {
        val bodyJson = buildBody(req).toString()
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/chat/completions")
            .addHeader("Accept", "text/event-stream")
            .addHeader("Cache-Control", "no-cache")
            .post(bodyJson.toRequestBody(mediaType))
            .build()
        val call = client.newCall(request)
        try {
            val resp = call.execute()
            if (!resp.isSuccessful) {
                val errBody = try {
                    resp.body?.string()?.take(300) ?: ""
                } catch (e: Exception) { "" }
                resp.close()
                close(AIHttpException(mapHttpError(resp.code, errBody)))
                return@callbackFlow
            }
            val body = resp.body
            if (body == null) {
                resp.close()
                close(AIHttpException("Respon server kosong."))
                return@callbackFlow
            }
            val source = body.source()
            while (true) {
                val line = try {
                    source.readUtf8Line()
                } catch (e: IOException) {
                    break // koneksi putus / dibatalkan
                } ?: break // EOF
                val t = line.trim()
                if (t.isEmpty() || t.startsWith(":")) continue
                if (!t.startsWith("data:")) continue
                val data = t.removePrefix("data:").trim()
                if (data == "[DONE]") break
                val delta = SseDecoder.extractDelta(data)
                if (delta != null) trySend(delta)
            }
            resp.close()
            close()
        } catch (e: Exception) {
            // Pembatalan coroutine → call ikut dibatalkan; tutup normal.
            if (e is IOException && call.isCanceled()) {
                close()
            } else {
                close(e)
            }
        }
        awaitClose { call.cancel() }
    }

    /** Susun body OpenAI-style — 1:1 dengan legacy buildBody(). */
    internal fun buildBody(req: ChatRequest): JsonObject {
        val msgs = buildJsonArray {
            add(buildJsonObject {
                put("role", "system")
                put("content", req.systemPrompt)
            })
            val hist = req.history.takeLast(20)
            for (m in hist) {
                add(buildJsonObject {
                    put("role", m.role)
                    put("content", m.content)
                })
            }
            add(buildJsonObject {
                put("role", "user")
                if (req.imageBase64s.isNotEmpty()) {
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put(
                                "text",
                                if (req.prompt.isBlank()) "Jelaskan isi gambar/dokumen ini."
                                else req.prompt
                            )
                        })
                        for (b64 in req.imageBase64s) {
                            add(buildJsonObject {
                                put("type", "image_url")
                                put(
                                    "image_url",
                                    buildJsonObject {
                                        put("url", "data:image/jpeg;base64,$b64")
                                    }
                                )
                            })
                        }
                    })
                } else {
                    put("content", req.prompt)
                }
            })
        }
        return buildJsonObject {
            put("model", req.model)
            put("messages", msgs)
            put("stream", true)
            req.temperature?.let { put("temperature", it) }
            req.topP?.let { put("top_p", it) }
            req.maxTokens?.let { if (it > 0) put("max_tokens", it) }
        }
    }


    /**
     * Sprint 1 Gate 2 — streaming DENGAN tool-loop (web_fetch).
     *
     * Sama seperti [stream], ditambah: body memuat `tools[]` dari
     * [ToolLoop.toolsArray]; delta `tool_calls` diakumulasi per index;
     * tool dieksekusi via [ToolLoop.execute]; hasilnya di-append sebagai
     * pesan role=tool lalu request diulang (maks [maxIterations] iterasi,
     * di-clamp 1..[ToolLoop.HARD_MAX_ITERATIONS]).
     * Hasil tool TIDAK disimpan ke DB — hanya hidup selama request ini;
     * yang dipersistensi UI tetap jawaban akhir asisten.
     */
    fun streamWithTools(
        baseUrl: String,
        req: ChatRequest,
        onToolEvent: (name: String, ok: Boolean, ms: Long) -> Unit,
        maxIterations: Int = ToolLoop.DEFAULT_ITERATIONS
    ): Flow<String> = callbackFlow {
        val toolsJson: JsonArray =
            json.parseToJsonElement(ToolLoop.toolsArray().toString()).jsonArray
        val messages = mutableListOf<kotlinx.serialization.json.JsonElement>()
        messages.add(buildJsonObject {
            put("role", "system")
            put("content", req.systemPrompt)
        })
        for (m in req.history.takeLast(20)) {
            messages.add(buildJsonObject {
                put("role", m.role)
                put("content", m.content)
            })
        }
        messages.add(buildJsonObject {
            put("role", "user")
            put("content", req.prompt)
        })
        val limit = maxIterations.coerceIn(1, ToolLoop.HARD_MAX_ITERATIONS)
        var call: okhttp3.Call? = null
        // Fallback non-streaming hanya SEKALI per pengiriman: Tes Koneksi
        // (non-streaming) terbukti lolos pada kasus Jerouter yang stream-nya
        // kosong, jadi satu percobaan ulang tanpa stream lebih berguna
        // daripada error langsung — tapi tidak boleh berulang tanpa henti.
        var fallbackAttempted = false
        try {
            var iteration = 0
            var finished = false
            while (iteration < limit && !finished) {
                iteration++
                val body = buildJsonObject {
                    put("model", req.model)
                    put("messages", buildJsonArray { addAll(messages) })
                    put("stream", true)
                    put("tools", toolsJson)
                    put("tool_choice", "auto")
                    req.temperature?.let { put("temperature", it) }
                    req.topP?.let { put("top_p", it) }
                    req.maxTokens?.let { if (it > 0) put("max_tokens", it) }
                }
                val request = Request.Builder()
                    .url(baseUrl.trimEnd('/') + "/chat/completions")
                    .addHeader("Accept", "text/event-stream")
                    .addHeader("Cache-Control", "no-cache")
                    .post(body.toString().toRequestBody(mediaType))
                    .build()
                val c = client.newCall(request)
                call = c
                val resp = c.execute()
                if (!resp.isSuccessful) {
                    val errBody = try {
                        resp.body?.string()?.take(300) ?: ""
                    } catch (e: Exception) { "" }
                    val code = resp.code
                    resp.close()
                    close(AIHttpException(mapHttpError(code, errBody)))
                    return@callbackFlow
                }
                val source = resp.body?.source()
                if (source == null) {
                    resp.close()
                    close(AIHttpException("Respon server kosong."))
                    return@callbackFlow
                }
                val pending = sortedMapOf<Int, ToolCall>()
                val iterText = StringBuilder()
                while (true) {
                    val line = try {
                        source.readUtf8Line()
                    } catch (e: IOException) {
                        break
                    } ?: break
                    val t = line.trim()
                    if (t.isEmpty() || t.startsWith(":")) continue
                    if (!t.startsWith("data:")) continue
                    val data = t.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val chunk = runCatching { JSONObject(data) }.getOrNull()
                        ?: continue
                    // 14.21: payload error DI DALAM SSE (HTTP 200) sebelumnya
                    // hanya di-continue diam-diam sampai stream habis tanpa
                    // token dan tanpa error — sumber "loading lalu hilang".
                    // Sekarang error itu diteruskan ke UI sebagai exception.
                    val sseError = chunk.optJSONObject("error")
                    if (sseError != null) {
                        val msg = sseError.optString("message", "")
                            .ifBlank { sseError.toString().take(200) }
                        resp.close()
                        close(AIHttpException("Server menolak: $msg"))
                        return@callbackFlow
                    }
                    val delta = chunk.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("delta")
                        ?: continue
                    val content = delta.optString("content", "")
                    if (content.isNotEmpty()) {
                        iterText.append(content)
                        trySend(content)
                    }
                    val tcs = delta.optJSONArray("tool_calls") ?: continue
                    for (i in 0 until tcs.length()) {
                        val tc = tcs.optJSONObject(i) ?: continue
                        val slot = pending.getOrPut(tc.optInt("index", i)) {
                            ToolCall()
                        }
                        val id = tc.optString("id", "")
                        if (id.isNotEmpty()) slot.id = id
                        val fn = tc.optJSONObject("function") ?: continue
                        val name = fn.optString("name", "")
                        if (name.isNotEmpty()) slot.name = name
                        slot.argsBuf.append(fn.optString("arguments", ""))
                    }
                }
                resp.close()
                if (pending.isEmpty()) {
                    if (iterText.isNotEmpty()) {
                        finished = true
                    } else if (!fallbackAttempted) {
                        // 14.21: stream selesai dengan NOL token dan tanpa
                        // tool_call — jangan diam. Sekali saja, ulangi
                        // request yang sama TANPA stream (jalur yang dipakai
                        // Tes Koneksi dan terbukti lolos) dan tampilkan
                        // hasilnya sebagai token biasa.
                        fallbackAttempted = true
                        val text = fetchNonStreamingText(baseUrl, req, messages)
                        trySend(text)
                        finished = true
                    } else {
                        close(
                            AIHttpException(
                                "Server tidak mengirim jawaban. " +
                                    "Coba lagi atau ganti model."
                            )
                        )
                        return@callbackFlow
                    }
                } else {
                    val assistantMsg = buildJsonObject {
                        put("role", "assistant")
                        if (iterText.isEmpty()) {
                            put("content", JsonNull)
                        } else {
                            put("content", iterText.toString())
                        }
                        put("tool_calls", buildJsonArray {
                            for ((_, tc) in pending) {
                                add(buildJsonObject {
                                    put("id", tc.id)
                                    put("type", "function")
                                    put("function", buildJsonObject {
                                        put("name", tc.name)
                                        put("arguments", tc.argsBuf.toString())
                                    })
                                })
                            }
                        })
                    }
                    messages.add(assistantMsg)
                    for ((_, tc) in pending) {
                        val t0 = System.currentTimeMillis()
                        val res = ToolLoop.execute(tc.name, tc.argsJson())
                        val ms = System.currentTimeMillis() - t0
                        onToolEvent(tc.name, !res.isError, ms)
                        messages.add(buildJsonObject {
                            put("role", "tool")
                            put("tool_call_id", tc.id)
                            put("content", res.content)
                        })
                    }
                }
            }
            close()
        } catch (e: Exception) {
            if (e is IOException && call?.isCanceled() == true) {
                close()
            } else {
                close(e)
            }
        }
        awaitClose { call?.cancel() }
    }

    /**
     * 14.21: satu request non-streaming dengan konteks pesan yang sama
     * (tanpa tools). Dipakai SEKALI sebagai fallback saat stream selesai
     * tanpa token — jalur ini identik dengan yang dipakai Tes Koneksi
     * (validateModel), sehingga bila server memang menolak streaming untuk
     * model tersebut, user tetap mendapat jawaban, bukan layar kosong.
     * Melempar [AIHttpException] bila HTTP gagal atau isi tetap kosong.
     */
    private fun fetchNonStreamingText(
        baseUrl: String,
        req: ChatRequest,
        messages: List<kotlinx.serialization.json.JsonElement>
    ): String {
        val body = buildJsonObject {
            put("model", req.model)
            put("messages", buildJsonArray { addAll(messages) })
            put("stream", false)
            req.temperature?.let { put("temperature", it) }
            req.topP?.let { put("top_p", it) }
            req.maxTokens?.let { if (it > 0) put("max_tokens", it) }
        }
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/chat/completions")
            .post(body.toString().toRequestBody(mediaType))
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errBody = try {
                    resp.body?.string()?.take(300) ?: ""
                } catch (_: Exception) {
                    ""
                }
                throw AIHttpException(mapHttpError(resp.code, errBody))
            }
            val text = try {
                resp.body?.string().orEmpty()
            } catch (_: Exception) {
                ""
            }
            val content = runCatching { JSONObject(text) }
                .getOrNull()
                ?.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "")
                .orEmpty()
            if (content.isBlank()) {
                throw AIHttpException(
                    "Server tidak mengirim jawaban. Coba lagi atau ganti model."
                )
            }
            return content
        }
    }

    /** Mapping error HTTP → pesan Indonesia (dipertahankan dari legacy). */
    internal fun mapHttpError(code: Int, errBody: String): String = when (code) {
        401 -> "API key salah atau kedaluwarsa (401). Perbarui di Pengaturan."
        429 -> "Batas request tercapai (429). Coba lagi sebentar."
        404 -> "Model tidak tersedia (404). Pilih model lain."
        504 -> "Model timeout (504). Coba model lain yang lebih cepat."
        else -> "Server menolak (HTTP $code)" +
            (if (errBody.isBlank()) "." else ": $errBody")
    }
}

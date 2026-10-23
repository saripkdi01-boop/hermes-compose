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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
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

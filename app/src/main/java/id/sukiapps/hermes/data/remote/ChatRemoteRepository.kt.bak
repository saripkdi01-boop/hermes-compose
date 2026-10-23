package id.sukiapps.hermes.data.remote

import id.sukiapps.hermes.data.local.AppPrefs
import id.sukiapps.hermes.domain.AIHttpException
import id.sukiapps.hermes.domain.AIProvider
import id.sukiapps.hermes.domain.ChatRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementasi AIProvider untuk Jerouter (OpenAI-compatible).
 * models(): GET {base}/models dengan cache 24 jam (perbaikan v7.1);
 * force=true melewati cache (tombol "Muat ulang" harus live).
 */
@Singleton
class ChatRemoteRepository @Inject constructor(
    private val api: JerouterApi,
    private val sse: SseClient,
    private val prefs: AppPrefs,
    private val client: OkHttpClient // AuthInterceptor sudah pasang Bearer
) : AIProvider {
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun models(force: Boolean): List<String> {
        if (!force) prefs.cachedModels()?.let { return it }
        val base = prefs.baseUrl.trimEnd('/')
        val resp = try {
            api.models("$base/models")
        } catch (e: Exception) {
            throw AIHttpException(mapFetchError(e))
        }
        val ids = resp.data.map { it.id }.filter { it.isNotBlank() }.distinct()
        if (ids.isNotEmpty()) prefs.saveModelsCache(ids)
        return ids
    }

    /**
     * Ping minimal non-streaming untuk memastikan API key user boleh
     * memakai [model]. Sukses = 2xx + body JSON valid.
     */
    override suspend fun validateModel(model: String) {
        val base = prefs.baseUrl.trimEnd('/')
        val body = buildJsonObject {
            put("model", model)
            put("stream", false)
            put("max_tokens", 1)
            put(
                "messages", buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", "ping")
                        }
                    )
                }
            )
        }.toString()
        val req = Request.Builder()
            .url("$base/chat/completions")
            .post(body.toRequestBody(mediaType))
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw AIHttpException(mapValidateError(resp.code, model))
                val text = resp.body?.string().orEmpty()
                runCatching { json.decodeFromString<ChatCompletionResponse>(text) }
                    .getOrElse { throw AIHttpException("Respons validasi tidak dikenali.") }
            }
        } catch (e: AIHttpException) {
            throw e
        } catch (e: Exception) {
            throw AIHttpException(mapFetchError(e))
        }
    }

    private fun mapValidateError(code: Int, model: String): String = when (code) {
        401 -> "API key salah atau kedaluwarsa (401)."
        403 -> "Akses ditolak (403) — key ini tidak boleh memakai model \"$model\"."
        404 -> "Model \"$model\" tidak ditemukan di server (404)."
        429 -> "Batas request tercapai (429). Coba lagi sebentar."
        else -> "Validasi gagal (HTTP $code) untuk model \"$model\"."
    }

    override fun chatStream(request: ChatRequest): Flow<String> =
        sse.stream(prefs.baseUrl, request).flowOn(Dispatchers.IO)

    private fun mapFetchError(e: Exception): String {
        val m = e.message ?: ""
        return when {
            m.contains("401") -> "API key salah atau kedaluwarsa (401). Perbarui di Pengaturan."
            m.contains("429") -> "Batas request tercapai (429). Coba lagi sebentar."
            m.contains("Unable to resolve host") || m.contains("UnknownHostException") ->
                "Tidak ada koneksi internet. Periksa jaringan lalu coba lagi."
            m.contains("timeout", ignoreCase = true) || m.contains("timed out", ignoreCase = true) ->
                "Koneksi timeout. Coba lagi."
            else -> "Gagal mengambil daftar model: ${m.take(160)}"
        }
    }
}

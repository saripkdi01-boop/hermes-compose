package id.sukiapps.hermes.data.remote

import id.sukiapps.hermes.domain.AIHttpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * v20.1 — satu panggilan `POST {base}/chat/completions` NON-streaming untuk
 * tool agen (`delegate_task`, `cronjob`). Kontrak REST sama dengan chat:
 * Bearer dipasang AuthInterceptor HANYA ke host base URL ([AuthScope]).
 * Bisa dibatalkan: pembatalan coroutine → `call.cancel()`.
 */
object SimpleCompletion {

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    fun body(model: String, system: String, user: String, maxTokens: Int): String =
        JSONObject()
            .put("model", model)
            .put("stream", false)
            .put("max_tokens", maxTokens)
            .put(
                "messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user))
            )
            .toString()

    /** Ambil isi jawaban; null bila kosong / bukan bentuk OpenAI. */
    fun parse(responseBody: String): String? = runCatching {
        JSONObject(responseBody).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").optString("content", "")
    }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    suspend fun complete(
        client: OkHttpClient,
        baseUrl: String,
        model: String,
        system: String,
        user: String,
        maxTokens: Int = 800
    ): String = withContext(Dispatchers.IO) {
        if (model.isBlank()) throw AIHttpException("Model belum dipilih di Pengaturan.")
        val req = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/chat/completions")
            .post(body(model, system, user, maxTokens).toRequestBody(JSON_TYPE))
            .build()
        val call = client.newCall(req)
        val text = suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(AIHttpException("Koneksi gagal: ${e.message?.take(120)}", true))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { r ->
                        val raw = runCatching { r.body?.string().orEmpty() }.getOrDefault("")
                        if (!r.isSuccessful) {
                            if (cont.isActive) cont.resumeWithException(
                                AIHttpException("Server menolak (HTTP ${r.code}).", r.code == 429 || r.code >= 500)
                            )
                        } else if (cont.isActive) {
                            cont.resume(raw)
                        }
                    }
                }
            })
        }
        parse(text) ?: throw AIHttpException("Server tidak mengirim jawaban.")
    }
}

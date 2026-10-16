package id.sukiapps.hermes.data.connectors

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import id.sukiapps.hermes.KeyVault
import id.sukiapps.hermes.data.local.AppPrefs
import id.sukiapps.hermes.domain.AIHttpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Konektor Upin (v9.4) — tanya-jawab dengan agent Upin di VM via SSH forward.
 * Arsitektur: HP menjalankan `ssh -L 8766:127.0.0.1:8766 muse-vm -N` di
 * Termux → localhost HP:8766 diteruskan ke VM. Latensi jawaban ~1-2 menit
 * (worker polling di sisi VM).
 *
 * Auth: secret milik user (terenkripsi via KeyVault "upin_secret"), dikirim
 * sebagai header Authorization manual per request. Client OkHttp KHUSUS
 * tanpa auth interceptor global (interceptor lain untuk Jerouter).
 * 0 dependensi baru.
 */
@Singleton
class UpinConnector @Inject constructor(
    private val prefs: AppPrefs,
    @ApplicationContext private val ctx: Context
) {
    companion object {
        const val DEFAULT_URL = "http://127.0.0.1:8766"
        const val SECRET_KEY = "upin_secret"
        const val SSH_HINT = "ssh -L 8766:127.0.0.1:8766 muse-vm -N"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // v9.4.2: konektor Upin SELALU ke loopback HP (127.0.0.1) via SSH
        // forward Termux — JANGAN lewat proxy/VPN sistem. OkHttp default ikut
        // ProxySelector sistem; di HP dengan VPN/proxy itu membuat 127.0.0.1
        // tidak terjangkau (curl Termux tidak pakai proxy → selalu tembus,
        // app selalu "Tidak terhubung"). NO_PROXY = direct selalu benar
        // untuk loopback.
        .proxy(Proxy.NO_PROXY)
        .build()
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private fun base(): String =
        prefs.upinUrl.trim().trimEnd('/').ifBlank { DEFAULT_URL }

    private fun secret(): String =
        KeyVault.getSecret(ctx, SECRET_KEY).orEmpty().trim()

    private fun authed(url: String, method: String, body: String? = null): Request {
        val s = secret()
        if (s.isEmpty()) {
            throw AIHttpException(
                "Secret Upin belum diisi — buka Pengaturan → Konektor Upin."
            )
        }
        val b = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $s")
        when (method) {
            "POST" -> b.post((body ?: "").toRequestBody(mediaType))
            else -> b.get()
        }
        return b.build()
    }

    /**
     * Tes koneksi: POST /upin/chat {"message":"ping"} → 200 + ada job_id.
     * Melempar AIHttpException berbahasa Indonesia bila gagal.
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        sendMessage("ping")
        true
    }

    /**
     * Kirim pesan → kembalikan job_id. Melempar AIHttpException
     * (401 / IO-error / respons tak dikenali).
     */
    suspend fun sendMessage(message: String): String =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject { put("message", message) }
                .toString()
            val req = authed("${base()}/upin/chat", "POST", body)
            try {
                client.newCall(req).execute().use { resp ->
                    when {
                        resp.code == 401 ->
                            throw AIHttpException("Secret Upin salah (401).")
                        !resp.isSuccessful ->
                            throw AIHttpException("Upin menolak (HTTP ${resp.code}).")
                        else -> {
                            // job_id wajib ada (dilempar bila kosong/tak dikenali).
                            UpinProtocol.jobIdOf(resp.body?.string().orEmpty())
                        }
                    }
                }
            } catch (e: AIHttpException) {
                throw e
            } catch (e: IOException) {
                throw AIHttpException(
                    "Tidak terhubung ke Upin. Pastikan di Termux: $SSH_HINT"
                )
            }
        }

    /** GET /upin/reply/<jobId> → (status, answer). */
    suspend fun pollReply(jobId: String): Pair<String, String?> =
        withContext(Dispatchers.IO) {
            val req = authed("${base()}/upin/reply/$jobId", "GET")
            try {
                client.newCall(req).execute().use { resp ->
                    when {
                        resp.code == 401 ->
                            throw AIHttpException("Secret Upin salah (401).")
                        !resp.isSuccessful ->
                            throw AIHttpException("Upin menolak (HTTP ${resp.code}).")
                        else -> UpinProtocol.replyOf(resp.body?.string().orEmpty())
                    }
                }
            } catch (e: AIHttpException) {
                throw e
            } catch (e: IOException) {
                throw AIHttpException("Koneksi ke Upin putus saat menunggu jawaban.")
            }
        }

    @Serializable
    internal data class ChatResp(val job_id: String = "")

    @Serializable
    internal data class ReplyResp(
        val status: String = "",
        val answer: String? = null
    )
}

/**
 * Parsing murni kontrak API Upin — internal agar bisa di-unit-test di JVM
 * (tanpa Android): POST /upin/chat → {"job_id"}, GET /upin/reply → status.
 */
internal object UpinProtocol {
    private val json = Json { ignoreUnknownKeys = true }

    /** Ekstrak job_id; lempar AIHttpException bila kosong/tak dikenali. */
    fun jobIdOf(payload: String): String {
        val id = runCatching { json.decodeFromString<UpinConnector.ChatResp>(payload).job_id }
            .getOrDefault("").trim()
        if (id.isBlank()) throw AIHttpException("Respons Upin tidak dikenali.")
        return id
    }

    /** (status ternormalisasi lowercase, answer nullable). */
    fun replyOf(payload: String): Pair<String, String?> {
        val r = runCatching { json.decodeFromString<UpinConnector.ReplyResp>(payload) }
            .getOrElse { throw AIHttpException("Respons Upin tidak dikenali.") }
        return r.status.trim().lowercase() to r.answer
    }
}

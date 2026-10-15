package id.sukiapps.hermes.data.connectors

import id.sukiapps.hermes.data.audit.DangerLevel
import id.sukiapps.hermes.data.audit.PermissionGate
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Konektor Telegram Bot API — AKSI MANUAL via tombol di Pengaturan.
 * BUKAN dipakai agen otomatis: setiap pemanggilan berasal dari ketukan user.
 * Auth: bot token milik user (terenkripsi via KeyVault.putSecret).
 * 0 dependensi baru.
 */
@Singleton
class TelegramConnector @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    /** Kirim satu pesan teks. Mentah — hanya dipanggil via [sendGated]. */
    private suspend fun sendMessage(
        botToken: String,
        chatId: String,
        text: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Bot API memakai token di path URL — ini pola resmi Telegram.
            val url = "https://api.telegram.org/bot$botToken/sendMessage"
            val body = buildJsonObject {
                put("chat_id", chatId)
                put("text", text)
            }.toString().toRequestBody(mediaType)
            val req = Request.Builder().url(url).post(body).build()
            client.newCall(req).execute().use { resp ->
                val payload = resp.body?.string().orEmpty()
                val ok = runCatching {
                    json.decodeFromString<TgResponse>(payload).ok
                }.getOrDefault(false)
                if (!resp.isSuccessful || !ok) {
                    throw Exception(
                        if (resp.code == 401) "Bot token salah (401)."
                        else "Telegram menolak (HTTP ${resp.code})."
                    )
                }
            }
        }
    }

    @Serializable
    private data class TgResponse(val ok: Boolean = false)

    /**
     * Chokepoint publik: kirim pesan — selalu lewat PermissionGate
     * (MEDIUM, boleh diingat).
     */
    suspend fun sendGated(
        gate: PermissionGate,
        botToken: String,
        chatId: String,
        text: String
    ): Result<Unit> {
        val ok = gate.request(
            "Telegram",
            "Kirim pesan ke chat $chatId:\n${text.take(200)}",
            DangerLevel.MEDIUM
        )
        if (!ok) return Result.failure(Exception("Ditolak — dicatat di Riwayat Izin."))
        return sendMessage(botToken, chatId, text)
    }
}

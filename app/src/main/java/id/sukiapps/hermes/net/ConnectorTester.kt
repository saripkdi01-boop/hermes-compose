package id.sukiapps.hermes.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** Alasan kegagalan yang spesifik — untuk pesan user yang tepat. */
enum class FailureReason {
    NETWORK,
    TIMEOUT,
    AUTH,
    NOT_FOUND,
    RATE_LIMITED,
    SERVER,
    NOT_CONFIGURED,
    PERMISSION
}

sealed interface TestResult {
    data class Ok(val ms: Long, val detail: String) : TestResult
    data class Fail(
        val ms: Long,
        val reason: FailureReason,
        val message: String,
        val hint: String?
    ) : TestResult
}

/**
 * Penguji koneksi untuk semua fitur settings.
 * SEMUA request hanya dipicu ketukan user (aturan AGENTS.md).
 * Timeout 15 detik per request. Body respons TIDAK pernah
 * ditampilkan mentah ke user (bisa berisi secret).
 */
class ConnectorTester(
    private val client: OkHttpClient
) {

    private fun clientWithTimeout(): OkHttpClient = client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    // ---------- base URL ----------

    suspend fun testConnection(baseUrl: String): TestResult =
        withContext(Dispatchers.IO) {
            val clean = baseUrl.trim().trimEnd('/')
            if (clean.isEmpty()) {
                return@withContext TestResult.Fail(
                    0, FailureReason.NOT_CONFIGURED,
                    "Base URL belum diisi.",
                    "Isi dulu di Pengaturan → Koneksi."
                )
            }
            if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
                return@withContext TestResult.Fail(
                    0, FailureReason.NOT_CONFIGURED,
                    "Base URL harus diawali http:// atau https://.",
                    "Contoh: https://je.jerouter.web.id/v1"
                )
            }
            val t0 = System.currentTimeMillis()
            try {
                // GET /models tanpa auth: 401 = endpoint benar, key yang salah
                val req = Request.Builder()
                    .url("$clean/models")
                    .get()
                    .build()
                clientWithTimeout().newCall(req).execute().use { resp ->
                    val ms = System.currentTimeMillis() - t0
                    return@withContext when (resp.code) {
                        200 -> TestResult.Ok(ms, "Endpoint merespons.")
                        401 -> TestResult.Ok(ms, "Endpoint benar (perlu API key).")
                        404 -> TestResult.Fail(
                            ms, FailureReason.NOT_FOUND,
                            "Endpoint tidak ada (404).",
                            "Cek path /v1 di akhir URL."
                        )
                        429 -> TestResult.Fail(
                            ms, FailureReason.RATE_LIMITED,
                            "Rate limit (429).",
                            "Coba lagi 1 menit lagi."
                        )
                        in 500..599 -> TestResult.Fail(
                            ms, FailureReason.SERVER,
                            "Server error (${resp.code}).",
                            "Coba lagi nanti."
                        )
                        else -> TestResult.Fail(
                            ms, FailureReason.SERVER,
                            "Respons tak terduga (${resp.code}).",
                            "Cek kembali base URL."
                        )
                    }
                }
            } catch (e: Exception) {
                TestResult.Fail(
                    System.currentTimeMillis() - t0,
                    mapException(e),
                    userMessage(e),
                    userHint(e)
                )
            }
        }

    // ---------- API key ----------

    suspend fun testApiKey(baseUrl: String, apiKey: String): TestResult =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext TestResult.Fail(
                    0, FailureReason.NOT_CONFIGURED,
                    "API key belum diisi.",
                    "Tempel key lalu tekan Simpan."
                )
            }
            val t0 = System.currentTimeMillis()
            try {
                val req = Request.Builder()
                    .url("${baseUrl.trim().trimEnd('/')}/models")
                    .header("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                clientWithTimeout().newCall(req).execute().use { resp ->
                    val ms = System.currentTimeMillis() - t0
                    return@withContext when (resp.code) {
                        200 -> {
                            val count = try {
                                JSONObject(resp.body!!.string())
                                    .optJSONArray("data")?.length() ?: 0
                            } catch (_: Exception) {
                                0
                            }
                            TestResult.Ok(ms, "API key valid ($count model).")
                        }
                        401 -> TestResult.Fail(
                            ms, FailureReason.AUTH,
                            "API key ditolak (401).",
                            "Cek key di pengaturan server."
                        )
                        429 -> TestResult.Fail(
                            ms, FailureReason.RATE_LIMITED,
                            "Rate limit (429).",
                            "Coba lagi 1 menit lagi."
                        )
                        else -> TestResult.Fail(
                            ms, FailureReason.SERVER,
                            "Respons tak terduga (${resp.code}).",
                            null
                        )
                    }
                }
            } catch (e: Exception) {
                TestResult.Fail(
                    System.currentTimeMillis() - t0,
                    mapException(e),
                    userMessage(e),
                    userHint(e)
                )
            }
        }

    // ---------- model (1 pesan pendek) ----------

    suspend fun testModel(
        baseUrl: String,
        apiKey: String,
        model: String
    ): TestResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        try {
            val body = JSONObject()
                .put("model", model)
                .put("max_tokens", 8)
                .put(
                    "messages", org.json.JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", "Balas dengan kata: ok")
                    )
                )
            val req = Request.Builder()
                .url("${baseUrl.trim().trimEnd('/')}/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .post(
                    body.toString().toRequestBody("application/json".toMediaType())
                )
                .build()
            clientWithTimeout().newCall(req).execute().use { resp ->
                val ms = System.currentTimeMillis() - t0
                return@withContext when (resp.code) {
                    200 -> TestResult.Ok(ms, "Model merespons dalam ${ms}ms.")
                    401 -> TestResult.Fail(
                        ms, FailureReason.AUTH,
                        "API key ditolak (401).", null
                    )
                    404 -> TestResult.Fail(
                        ms, FailureReason.NOT_FOUND,
                        "Model tidak ditemukan (404).",
                        "Pilih model dari daftar."
                    )
                    429 -> TestResult.Fail(
                        ms, FailureReason.RATE_LIMITED,
                        "Rate limit (429).",
                        "Coba lagi 1 menit lagi."
                    )
                    in 500..599 -> TestResult.Fail(
                        ms, FailureReason.SERVER,
                        "Server error (${resp.code}).", null
                    )
                    else -> TestResult.Fail(
                        ms, FailureReason.SERVER,
                        "Respons tak terduga (${resp.code}).", null
                    )
                }
            }
        } catch (e: Exception) {
            TestResult.Fail(
                System.currentTimeMillis() - t0,
                mapException(e),
                userMessage(e),
                userHint(e)
            )
        }
    }

    // ---------- GitHub ----------

    suspend fun testGithub(pat: String): TestResult =
        withContext(Dispatchers.IO) {
            if (pat.isBlank()) {
                return@withContext TestResult.Fail(
                    0, FailureReason.NOT_CONFIGURED,
                    "Token GitHub belum diisi.", null
                )
            }
            val t0 = System.currentTimeMillis()
            try {
                val req = Request.Builder()
                    .url("https://api.github.com/user")
                    .header("Authorization", "Bearer $pat")
                    .header("Accept", "application/vnd.github+json")
                    .get()
                    .build()
                clientWithTimeout().newCall(req).execute().use { resp ->
                    val ms = System.currentTimeMillis() - t0
                    return@withContext when (resp.code) {
                        200 -> {
                            val login = try {
                                JSONObject(resp.body!!.string()).optString("login", "?")
                            } catch (_: Exception) {
                                "?"
                            }
                            TestResult.Ok(ms, "OK, username=$login")
                        }
                        401 -> TestResult.Fail(
                            ms, FailureReason.AUTH,
                            "Token ditolak (401).",
                            "Cek scope repo & workflow."
                        )
                        403 -> TestResult.Fail(
                            ms, FailureReason.RATE_LIMITED,
                            "Rate limit GitHub (403).",
                            "Coba lagi nanti."
                        )
                        else -> TestResult.Fail(
                            ms, FailureReason.SERVER,
                            "Respons tak terduga (${resp.code}).", null
                        )
                    }
                }
            } catch (e: Exception) {
                TestResult.Fail(
                    System.currentTimeMillis() - t0,
                    mapException(e),
                    userMessage(e),
                    userHint(e)
                )
            }
        }

    // ---------- Telegram ----------

    suspend fun testTelegram(botToken: String, chatId: String): TestResult =
        withContext(Dispatchers.IO) {
            if (botToken.isBlank() || chatId.isBlank()) {
                return@withContext TestResult.Fail(
                    0, FailureReason.NOT_CONFIGURED,
                    "Token bot / chat ID belum lengkap.", null
                )
            }
            val t0 = System.currentTimeMillis()
            try {
                // getMe dulu (tidak kirim pesan)
                val meReq = Request.Builder()
                    .url("https://api.telegram.org/bot$botToken/getMe")
                    .get()
                    .build()
                clientWithTimeout().newCall(meReq).execute().use { resp ->
                    if (resp.code == 401) {
                        return@withContext TestResult.Fail(
                            System.currentTimeMillis() - t0,
                            FailureReason.AUTH,
                            "Token bot salah (401).",
                            "Salin ulang dari @BotFather."
                        )
                    }
                    if (resp.code != 200) {
                        return@withContext TestResult.Fail(
                            System.currentTimeMillis() - t0,
                            FailureReason.SERVER,
                            "Telegram error (${resp.code}).", null
                        )
                    }
                }
                TestResult.Ok(
                    System.currentTimeMillis() - t0,
                    "Bot valid. Tekan Tes Kirim untuk uji pesan."
                )
            } catch (e: Exception) {
                TestResult.Fail(
                    System.currentTimeMillis() - t0,
                    mapException(e),
                    userMessage(e),
                    userHint(e)
                )
            }
        }

    /** Kirim pesan uji BENAR — panggil hanya setelah dialog konfirmasi. */
    suspend fun sendTelegramTest(
        botToken: String,
        chatId: String
    ): TestResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        try {
            val body = JSONObject()
                .put("chat_id", chatId)
                .put("text", "Tes koneksi Hermes — abaikan pesan ini.")
            val req = Request.Builder()
                .url("https://api.telegram.org/bot$botToken/sendMessage")
                .post(
                    body.toString().toRequestBody("application/json".toMediaType())
                )
                .build()
            clientWithTimeout().newCall(req).execute().use { resp ->
                val ms = System.currentTimeMillis() - t0
                return@withContext when (resp.code) {
                    200 -> TestResult.Ok(ms, "Terkirim.")
                    400 -> TestResult.Fail(
                        ms, FailureReason.NOT_FOUND,
                        "Chat ID salah (400).",
                        "Ambil ID dari @userinfobot."
                    )
                    403 -> TestResult.Fail(
                        ms, FailureReason.PERMISSION,
                        "Bot diblokir / belum /start (403).",
                        "Buka bot lalu tekan /start."
                    )
                    else -> TestResult.Fail(
                        ms, FailureReason.SERVER,
                        "Telegram error (${resp.code}).", null
                    )
                }
            }
        } catch (e: Exception) {
            TestResult.Fail(
                System.currentTimeMillis() - t0,
                mapException(e),
                userMessage(e),
                userHint(e)
            )
        }
    }

    // ---------- Upin ----------

    suspend fun testUpin(url: String, secret: String): TestResult =
        withContext(Dispatchers.IO) {
            val clean = url.trim().trimEnd('/')
            if (clean.isEmpty()) {
                return@withContext TestResult.Fail(
                    0, FailureReason.NOT_CONFIGURED,
                    "URL Upin belum diisi.", null
                )
            }
            val t0 = System.currentTimeMillis()
            try {
                val req = Request.Builder()
                    .url("$clean/health")
                    .header("X-Upin-Secret", secret)
                    .get()
                    .build()
                clientWithTimeout().newCall(req).execute().use { resp ->
                    val ms = System.currentTimeMillis() - t0
                    return@withContext if (resp.code == 200) {
                        TestResult.Ok(ms, "Upin merespons.")
                    } else {
                        TestResult.Fail(
                            ms, FailureReason.SERVER,
                            "Upin error (${resp.code}).", null
                        )
                    }
                }
            } catch (e: java.net.ConnectException) {
                TestResult.Fail(
                    System.currentTimeMillis() - t0,
                    FailureReason.NETWORK,
                    "SSH forward belum aktif.",
                    "Jalankan perintah di langkah 1 panduan."
                )
            } catch (e: Exception) {
                TestResult.Fail(
                    System.currentTimeMillis() - t0,
                    mapException(e),
                    userMessage(e),
                    userHint(e)
                )
            }
        }

    // ---------- Termux ----------

    suspend fun checkTermux(binPath: String): TestResult =
        withContext(Dispatchers.IO) {
            val f = java.io.File(binPath.ifEmpty {
                "/data/data/com.termux/files/usr/bin/bash"
            })
            return@withContext if (f.exists() && f.canExecute()) {
                TestResult.Ok(0, "Termux terpasang.")
            } else {
                TestResult.Fail(
                    0, FailureReason.NOT_FOUND,
                    "Termux tidak terpasang / path salah.",
                    "Pasang dari Play Store / F-Droid."
                )
            }
        }

    // ---------- mapping ----------

    private fun mapException(e: Exception): FailureReason = when (e) {
        is UnknownHostException -> FailureReason.NETWORK
        is SocketTimeoutException -> FailureReason.TIMEOUT
        is java.net.ConnectException -> FailureReason.NETWORK
        is java.io.InterruptedIOException -> FailureReason.TIMEOUT
        is SecurityException -> FailureReason.PERMISSION
        else -> FailureReason.NETWORK
    }

    private fun userMessage(e: Exception): String = when (mapException(e)) {
        FailureReason.NETWORK -> "Tidak ada koneksi. Cek data/VPN."
        FailureReason.TIMEOUT -> "Server tidak merespons dalam 15 detik."
        FailureReason.PERMISSION -> "Izin ditolak sistem."
        else -> "Koneksi gagal: ${e.message?.take(80)}"
    }

    private fun userHint(e: Exception): String? = when (mapException(e)) {
        FailureReason.NETWORK -> "Pastikan internet aktif."
        FailureReason.TIMEOUT -> "Coba lagi atau cek server."
        else -> null
    }
}

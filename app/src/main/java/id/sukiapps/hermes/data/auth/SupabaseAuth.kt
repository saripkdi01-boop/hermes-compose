package id.sukiapps.hermes.data.auth

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * D3 (14.31) — Login Google via Supabase Auth (PKCE).
 *
 * v1 scope: login (Custom Tab + PKCE) / profil / logout / refresh sesi.
 * BUKAN pengganti API key provider, BUKAN sinkronisasi — tidak ada data
 * yang naik/turun otomatis karena login.
 *
 * Nilai proyek (URL + anon key) TIDAK ada di sini: dibaca dari KeyVault
 * (slot `supabase_url`/`supabase_key` yang sama dengan konektor Supabase
 * read-only) dan diisi Suki di Pengaturan. Field kosong = login ditolak
 * di ViewModel dengan pesan jelas, tanpa network call.
 *
 * File ini murni Java/Kotlin + OkHttp + kotlinx.serialization (tanpa API
 * Android) agar seluruh logika murni bisa diuji JVM.
 */
object SupabaseAuth {

    /** Deep link callback — HARUS terdaftar di dashboard Supabase
     * (Authentication → URL Configuration) + provider Google aktif,
     * dan sebagai intent-filter di AndroidManifest. */
    const val REDIRECT_URI = "id.sukiapps.hermes://auth/callback"

    // Slot KeyVault (nama harus [a-z0-9_]{1,32}).
    const val SECRET_URL = "supabase_url"
    const val SECRET_KEY = "supabase_key"
    const val SECRET_ACCESS_TOKEN = "supabase_access_token"
    const val SECRET_REFRESH_TOKEN = "supabase_refresh_token"
    const val SECRET_USER_ID = "supabase_user_id"
    const val SECRET_USER_EMAIL = "supabase_user_email"
    const val SECRET_USER_NAME = "supabase_user_name"
    const val SECRET_PKCE_VERIFIER = "supabase_pkce_verifier"
    const val SECRET_EXPIRES_AT = "supabase_expires_at"

    /** Batas sesi: token dianggap kedaluwarsa 60 detik sebelum expires_in. */
    const val EXPIRY_SKEW_SEC = 60L

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Verifier PKCE: 32 byte acak → base64url tanpa padding (43 karakter). */
    fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** Challenge S256: base64url(SHA-256(verifier)) tanpa padding. */
    fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /**
     * URL authorize Supabase. Null bila [projectUrl] kosong / bukan https
     * — pemanggil TIDAK BOLEH membuka Custom Tab dengan URL null.
     */
    fun buildAuthorizeUrl(projectUrl: String, challenge: String): String? {
        val base = projectUrl.trim().trimEnd('/')
        if (base.isBlank() || !base.startsWith("https://")) return null
        val redirect = URLEncoder.encode(REDIRECT_URI, "UTF-8")
        return "$base/auth/v1/authorize?provider=google" +
            "&redirect_to=$redirect" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256"
    }

    /** Tukar `code` dari callback menjadi sesi (grant PKCE GoTrue).
     *
     * Body mengirim `code` SEKALIGUS `auth_code` dengan nilai yang sama:
     * dokumentasi GoTrue memakai keduanya di versi berbeda, dan server
     * mengabaikan key yang tidak dikenal — jadi kirim keduanya agar
     * peluang lolos di uji tap pertama maksimal. Bentuk final grant ini
     * WAJIB dikonfirmasi di uji tap fisik (lihat badan PR).
     */
    suspend fun exchangeCode(
        projectUrl: String,
        anonKey: String,
        code: String,
        verifier: String
    ): Result<AuthSession> = withContext(Dispatchers.IO) {
        runCatching {
            val base = requireBase(projectUrl)
            val body = buildJsonObject {
                put("code", code)
                put("auth_code", code)
                put("code_verifier", verifier)
            }.toString().toRequestBody(jsonMediaType)
            val req = Request.Builder()
                .url("$base/auth/v1/token?grant_type=pkce")
                .addHeader("apikey", anonKey)
                .addHeader("Accept", "application/json")
                .post(body)
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw Exception(errorMessage(resp.code, text))
                parseTokenResponse(text)
            }
        }
    }

    /** Refresh sesi (grant refresh_token). */
    suspend fun refreshSession(
        projectUrl: String,
        anonKey: String,
        refreshToken: String
    ): Result<AuthSession> = withContext(Dispatchers.IO) {
        runCatching {
            val base = requireBase(projectUrl)
            val body = buildJsonObject {
                put("refresh_token", refreshToken)
            }.toString().toRequestBody(jsonMediaType)
            val req = Request.Builder()
                .url("$base/auth/v1/token?grant_type=refresh_token")
                .addHeader("apikey", anonKey)
                .addHeader("Accept", "application/json")
                .post(body)
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw Exception(errorMessage(resp.code, text))
                parseTokenResponse(text)
            }
        }
    }

    /** Profil dari GET /auth/v1/user (Bearer access token). */
    suspend fun fetchUser(
        projectUrl: String,
        anonKey: String,
        accessToken: String
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val base = requireBase(projectUrl)
            val req = Request.Builder()
                .url("$base/auth/v1/user")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Accept", "application/json")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw Exception(errorMessage(resp.code, text))
                parseUserResponse(text)
            }
        }
    }

    /**
     * Logout server (best-effort). Pemanggil TETAP menghapus secret sesi
     * lokal apa pun hasilnya — logout lokal tidak boleh gagal hanya karena
     * server tidak terjangkau (offline).
     */
    suspend fun logout(
        projectUrl: String,
        anonKey: String,
        accessToken: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val base = requireBase(projectUrl)
            val req = Request.Builder()
                .url("$base/auth/v1/logout")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $accessToken")
                .post("".toRequestBody(jsonMediaType))
                .build()
            client.newCall(req).execute().use { resp ->
                resp.body?.string()
                if (!resp.isSuccessful) throw Exception(errorMessage(resp.code, ""))
            }
        }
    }

    /** Parse respons token → sesi. `expiresAtEpochSec` dihitung saat parse
     * (now + expires_in − skew). Token TIDAK PERNAH di-log di sini. */
    fun parseTokenResponse(text: String): AuthSession {
        val dto = json.decodeFromString<TokenResponseDto>(text)
        val now = System.currentTimeMillis() / 1000
        val user = dto.user
        return AuthSession(
            accessToken = dto.access_token,
            refreshToken = dto.refresh_token,
            expiresInSec = dto.expires_in,
            expiresAtEpochSec = now + dto.expires_in - EXPIRY_SKEW_SEC,
            userId = user?.id.orEmpty(),
            email = user?.email.orEmpty(),
            name = userNameFromMetadata(user?.user_metadata)
        )
    }

    /** Parse respons GET /auth/v1/user → profil. */
    fun parseUserResponse(text: String): UserProfile {
        val dto = json.decodeFromString<UserResponseDto>(text)
        return UserProfile(
            id = dto.id,
            email = dto.email,
            name = userNameFromMetadata(dto.user_metadata)
        )
    }

    private fun userNameFromMetadata(metadata: JsonObject?): String {
        if (metadata == null) return ""
        val v = metadata["full_name"] ?: metadata["name"] ?: return ""
        return try {
            (v as? JsonPrimitive)?.contentOrNull.orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun requireBase(projectUrl: String): String {
        val base = projectUrl.trim().trimEnd('/')
        require(base.startsWith("https://")) { "URL Supabase harus https." }
        return base
    }

    /** Pesan error untuk UI. Tidak pernah memuat token. */
    fun errorMessage(code: Int, body: String): String = when (code) {
        400 -> "Permintaan login ditolak (400) — cek URL proyek, anon key, " +
            "dan redirect deep link di dashboard Supabase."
        401, 403 -> "Key Supabase ditolak ($code) — cek anon key."
        422 -> "Kode login tidak valid / kedaluwarsa — coba login ulang."
        else -> "Supabase menolak (HTTP $code): ${body.take(160)}"
    }

    @Serializable
    private data class TokenResponseDto(
        val access_token: String = "",
        val refresh_token: String = "",
        val expires_in: Long = 0,
        val token_type: String = "",
        val user: UserResponseDto? = null
    )

    @Serializable
    private data class UserResponseDto(
        val id: String = "",
        val email: String = "",
        val user_metadata: JsonObject? = null
    )
}

/** Sesi login yang sudah ditukar (token mentah hanya dipegang ViewModel
 * seperlunya untuk disimpan ke KeyVault — jangan dimasukkan ke UI state). */
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSec: Long,
    val expiresAtEpochSec: Long,
    val userId: String,
    val email: String,
    val name: String
)

/** Profil tampil (tanpa token). */
data class UserProfile(
    val id: String,
    val email: String,
    val name: String
)

/**
 * Penampung callback deep link (satu proses). MainActivity mengisi dari
 * intent deep link; SettingsViewModel mengambil + mengosongkan.
 *
 * Anti-ular: `consumedCode` mencegah kode yang sama ditukar dua kali bila
 * MainActivity ter-recreate dengan intent yang sama (mis. rotasi) — GoTrue
 * menolak pemakaian ulang code, jadi tanpa penjaga ini user akan melihat
 * error membingungkan setelah login berhasil.
 */
object SupabaseAuthCallback {
    var code: String? = null
        private set
    var error: String? = null
        private set
    private var consumedCode: String? = null

    fun hasPending(): Boolean = code != null || error != null

    /** @return true bila URI ini callback auth (code/error ditangkap). */
    fun capture(rawUri: String?): Boolean {
        if (rawUri.isNullOrBlank()) return false
        val uri = try {
            URI(rawUri)
        } catch (_: Exception) {
            return false
        }
        if (uri.scheme != "id.sukiapps.hermes" || uri.host != "auth" ||
            uri.path != "/callback"
        ) return false
        val params = parseQuery(uri.rawQuery)
        val newCode = params["code"]
        val newError = params["error"] ?: params["error_description"]
        if (newCode != null && newCode != consumedCode) code = newCode
        if (newError != null) error = newError
        return code != null || error != null
    }

    /** Ambil + kosongkan (sekali pakai). */
    fun take(): Pair<String?, String?> {
        val c = code
        val e = error
        if (c != null) consumedCode = c
        code = null
        error = null
        return c to e
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val out = mutableMapOf<String, String>()
        for (part in rawQuery.split("&")) {
            val eq = part.indexOf('=')
            if (eq <= 0) continue
            val k = URLDecoder.decode(part.substring(0, eq), "UTF-8")
            val v = URLDecoder.decode(part.substring(eq + 1), "UTF-8")
            out[k] = v
        }
        return out
    }
}

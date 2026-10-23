package id.sukiapps.hermes.data.connectors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Konektor Vercel — READ-ONLY (Sprint 1 Gate 3 opsi A).
 * Hanya GET ke API Vercel dengan token milik user (KeyVault: vercel_token).
 * Tidak ada operasi tulis/deploy di kelas ini.
 */
@Singleton
class VercelConnector @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun authed(url: String, token: String): Request =
        Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

    private suspend fun getJson(url: String, token: String): JSONObject =
        withContext(Dispatchers.IO) {
            client.newCall(authed(url, token)).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw Exception(
                        when (resp.code) {
                            401, 403 -> "Token Vercel ditolak (${resp.code})."
                            else -> "Vercel menolak (HTTP ${resp.code})."
                        }
                    )
                }
                JSONObject(body)
            }
        }

    /** Daftar project (nama + id), maks 50. */
    suspend fun listProjects(token: String): Result<List<String>> =
        runCatching {
            val root = getJson("https://api.vercel.com/v9/projects?limit=50", token)
            val arr = root.optJSONArray("projects")
                ?: throw Exception("Respons Vercel tidak dikenali.")
            val out = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add("${o.optString("name")} — id: ${o.optString("id")}")
            }
            out
        }

    /** 5 deployment terakhir sebuah project. */
    suspend fun listDeployments(
        token: String,
        projectId: String
    ): Result<List<String>> = runCatching {
        val root = getJson(
            "https://api.vercel.com/v6/deployments?projectId=$projectId&limit=5",
            token
        )
        val arr = root.optJSONArray("deployments")
            ?: throw Exception("Respons Vercel tidak dikenali.")
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val state = o.optString("readyState", o.optString("state", "?"))
            out.add(
                "${o.optString("name")} — $state — " +
                    "https://${o.optString("url")} — dibuat ${o.optLong("createdAt")}"
            )
        }
        out
    }
}

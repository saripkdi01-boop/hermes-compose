package id.sukiapps.hermes.data.connectors

import android.util.Base64
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

/** Info satu workflow run GitHub Actions. */
data class GhRun(
    val name: String,
    val status: String,
    val conclusion: String?,
    val url: String
)

/**
 * Konektor GitHub — AKSI MANUAL via tombol di Pengaturan.
 * BUKAN dipakai agen otomatis: setiap pemanggilan berasal dari ketukan user.
 * Auth: Personal Access Token milik user (terenkripsi via KeyVault.putSecret).
 * 0 dependensi baru.
 */
@Singleton
class GitHubConnector @Inject constructor() {
    // Client sendiri TANPA AuthInterceptor (interceptor itu untuk Jerouter).
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private fun authed(url: String, pat: String): Request.Builder =
        Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $pat")
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("X-GitHub-Api-Version", "2022-11-28")

    /** Tes koneksi: list repo milik user (butuh scope `repo`). */
    suspend fun listRepos(pat: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = authed(
                    "https://api.github.com/user/repos?per_page=100&sort=updated",
                    pat
                ).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw Exception(mapError(resp.code))
                    val body = resp.body?.string() ?: "[]"
                    json.decodeFromString<List<GhRepo>>(body).map { it.full_name }
                }
            }
        }

    /**
     * Trigger workflow_dispatch (butuh scope `workflow`). Mentah — hanya
     * dipanggil via [triggerDispatchGated].
     * @param workflowFile nama file workflow, mis. "build.yml".
     */
    private suspend fun triggerDispatch(
        pat: String,
        owner: String,
        repo: String,
        workflowFile: String,
        ref: String = "main"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url =
                "https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowFile/dispatches"
            val body = "{\"ref\":\"$ref\"}".toRequestBody(mediaType)
            val req = authed(url, pat).post(body).build()
            client.newCall(req).execute().use { resp ->
                // Sukses = 204 No Content.
                if (resp.code != 204) throw Exception(mapError(resp.code))
            }
        }
    }

    /** Chokepoint publik: trigger workflow — selalu lewat PermissionGate (MEDIUM). */
    suspend fun triggerDispatchGated(
        gate: PermissionGate,
        pat: String,
        owner: String,
        repo: String,
        workflowFile: String,
        ref: String = "main"
    ): Result<Unit> {
        val ok = gate.request(
            "GitHub",
            "Jalankan workflow $workflowFile ($ref) di $owner/$repo",
            DangerLevel.MEDIUM
        )
        if (!ok) return Result.failure(Exception("Ditolak — dicatat di Riwayat Izin."))
        return triggerDispatch(pat, owner, repo, workflowFile, ref)
    }

    /**
     * SHA file di repo via Contents API. null bila file belum ada (404) —
     * artinya operasi berikutnya = create, bukan update.
     */
    suspend fun getFileSha(
        pat: String,
        owner: String,
        repo: String,
        path: String
    ): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.github.com/repos/$owner/$repo/contents/$path"
            val req = authed(url, pat).build()
            client.newCall(req).execute().use { resp ->
                when {
                    resp.code == 404 -> null
                    !resp.isSuccessful -> throw Exception(mapError(resp.code))
                    else -> json.decodeFromString<GhContent>(
                        resp.body?.string() ?: "{}"
                    ).sha.ifBlank { null }
                }
            }
        }
    }

    /** Baca isi file teks via Contents API (read-only). Null bila 404. */
    suspend fun getFileContent(
        pat: String,
        owner: String,
        repo: String,
        path: String
    ): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.github.com/repos/$owner/$repo/contents/$path"
            val req = authed(url, pat).build()
            client.newCall(req).execute().use { resp ->
                when {
                    resp.code == 404 -> null
                    !resp.isSuccessful -> throw Exception(mapError(resp.code))
                    else -> {
                        val dto = json.decodeFromString<GhFileContent>(
                            resp.body?.string() ?: "{}"
                        )
                        val raw = Base64.decode(
                            dto.content.replace("\n", ""),
                            Base64.DEFAULT
                        )
                        String(raw, Charsets.UTF_8)
                    }
                }
            }
        }
    }

    /**
     * Buat/update file via Contents API. Mentah — hanya dipanggil via
     * [putContentsGated]/[pushFilesGated]. Butuh scope `repo`.
     */
    private suspend fun putContents(
        pat: String,
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String,
        branch: String = "main"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val sha = getFileSha(pat, owner, repo, path).getOrThrow()
            val url = "https://api.github.com/repos/$owner/$repo/contents/$path"
            val body = buildJsonObject {
                put("message", message)
                put(
                    "content",
                    Base64.encodeToString(
                        content.toByteArray(Charsets.UTF_8),
                        Base64.NO_WRAP
                    )
                )
                if (sha != null) put("sha", sha)
                put("branch", branch)
            }.toString().toRequestBody(mediaType)
            val req = authed(url, pat).put(body).build()
            client.newCall(req).execute().use { resp ->
                // Sukses = 200 (update) / 201 (create).
                if (resp.code != 200 && resp.code != 201) {
                    throw Exception(mapError(resp.code))
                }
            }
        }
    }

    /** Chokepoint publik: tulis 1 file — selalu lewat PermissionGate (MEDIUM). */
    suspend fun putContentsGated(
        gate: PermissionGate,
        pat: String,
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String
    ): Result<Unit> {
        val ok = gate.request(
            "GitHub",
            "Tulis file:\n$path\nke $owner/$repo@main\n\n$message",
            DangerLevel.MEDIUM
        )
        if (!ok) return Result.failure(Exception("Ditolak — dicatat di Riwayat Izin."))
        return putContents(pat, owner, repo, path, content, message)
    }

    /**
     * Chokepoint publik: push N file sekaligus — SATU popup untuk semuanya
     * (bukan N popup). Dipakai layar "Minta Fitur".
     */
    suspend fun pushFilesGated(
        gate: PermissionGate,
        pat: String,
        owner: String,
        repo: String,
        files: List<Pair<String, String>>,
        message: String
    ): Result<Int> {
        val listing = files.joinToString("\n") { (p, _) -> "• $p" }
        val ok = gate.request(
            "GitHub",
            "Push ${files.size} file ke $owner/$repo@main:\n$listing\n\n$message",
            DangerLevel.MEDIUM
        )
        if (!ok) return Result.failure(Exception("Ditolak — dicatat di Riwayat Izin."))
        var n = 0
        for ((path, content) in files) {
            putContents(pat, owner, repo, path, content, message).getOrThrow()
            n++
        }
        return Result.success(n)
    }

    /** 5 workflow run terakhir sebuah repo. */
    suspend fun latestRuns(
        pat: String,
        owner: String,
        repo: String
    ): Result<List<GhRun>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.github.com/repos/$owner/$repo/actions/runs?per_page=5"
            val req = authed(url, pat).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception(mapError(resp.code))
                val body = resp.body?.string() ?: "{}"
                json.decodeFromString<GhRunsResponse>(body).workflow_runs
                    .map { GhRun(it.name, it.status, it.conclusion, it.html_url) }
            }
        }
    }

    private fun mapError(code: Int): String = when (code) {
        401 -> "PAT salah/kedaluwarsa (401)."
        403 -> "Scope PAT kurang (403). Butuh repo + workflow."
        404 -> "Repo/workflow tidak ditemukan (404)."
        422 -> "Validasi GitHub gagal (422) — cek path file."
        else -> "GitHub menolak (HTTP $code)."
    }

    @Serializable
    private data class GhContent(val sha: String = "")

    @Serializable
    private data class GhFileContent(val content: String = "", val encoding: String = "")

    @Serializable
    private data class GhRepo(val full_name: String = "")

    @Serializable
    private data class GhRunsResponse(val workflow_runs: List<GhRunDto> = emptyList())

    @Serializable
    private data class GhRunDto(
        val name: String = "",
        val status: String = "",
        val conclusion: String? = null,
        val html_url: String = ""
    )
}

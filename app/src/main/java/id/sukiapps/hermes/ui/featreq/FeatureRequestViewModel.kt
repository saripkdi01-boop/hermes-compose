package id.sukiapps.hermes.ui.featreq

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import id.sukiapps.hermes.KeyVault
import id.sukiapps.hermes.data.agent.AgentFiles
import id.sukiapps.hermes.data.audit.PermissionGate
import id.sukiapps.hermes.data.connectors.GitHubConnector
import id.sukiapps.hermes.data.local.AppPrefs
import id.sukiapps.hermes.data.remote.ChatCompletionResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

/** Satu file hasil generate: path + isi lengkap. */
data class ReqFile(val path: String, val content: String)

data class FeatureRequestUiState(
    val request: String = "",
    val owner: String = "saripkdi01-boop",
    val repo: String = "hermes-compose",
    val generating: Boolean = false,
    val files: List<ReqFile> = emptyList(),
    val pushing: Boolean = false,
    val snack: String? = null
)

/**
 * "Minta Fitur" — loop upgrade via prompt: user tulis permintaan (natural) →
 * AI buatkan draf ISI FILE LENGKAP → user review → push via GitHub Contents
 * API → Actions build otomatis.
 *
 * Batas jujur: AI hanya punya features.md sebagai konteks (bukan full source),
 * jadi hasilnya = DRAF untuk perubahan kecil yang wajib di-review. Push selalu
 * lewat PermissionGate (MEDIUM, satu popup untuk semua file).
 */
@HiltViewModel
class FeatureRequestViewModel @Inject constructor(
    @ApplicationContext private val appCtx: Context,
    private val client: OkHttpClient, // AuthInterceptor sudah pasang Bearer Jerouter
    private val prefs: AppPrefs,
    private val github: GitHubConnector,
    private val gate: PermissionGate
) : ViewModel() {

    private val _state = MutableStateFlow(FeatureRequestUiState())
    val state: StateFlow<FeatureRequestUiState> = _state.asStateFlow()
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    fun setRequest(v: String) = _state.update { it.copy(request = v) }
    fun setOwner(v: String) = _state.update { it.copy(owner = v) }
    fun setRepo(v: String) = _state.update { it.copy(repo = v) }
    fun consumeSnack() = _state.update { it.copy(snack = null) }

    /** Minta AI buatkan draf file. */
    fun generate() {
        val req = _state.value.request.trim()
        if (req.isEmpty()) {
            _state.update { it.copy(snack = "Tulis dulu permintaan fiturnya.") }
            return
        }
        val model = prefs.model
        if (model.isBlank()) {
            _state.update { it.copy(snack = "Pilih model AI dulu di Pengaturan.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(generating = true, files = emptyList(), snack = null) }
            try {
                val features = withContext(Dispatchers.IO) {
                    AgentFiles.read(appCtx, AgentFiles.FEATURES)
                }
                val output = withContext(Dispatchers.IO) {
                    callAi(model, features, req)
                }
                val files = parseFiles(output)
                _state.update {
                    it.copy(
                        generating = false,
                        files = files,
                        snack = if (files.isEmpty())
                            "AI tidak menghasilkan file yang valid — coba rumuskan ulang."
                        else "Draf ${files.size} file siap. Review dulu sebelum push."
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        generating = false,
                        snack = (e.message ?: "Gagal.").take(200)
                    )
                }
            }
        }
    }

    /** Push semua file draf ke GitHub (satu popup izin untuk semuanya). */
    fun push() {
        val s = _state.value
        if (s.files.isEmpty()) {
            _state.update { it.copy(snack = "Belum ada draf file.") }
            return
        }
        val pat = KeyVault.getSecret(appCtx, "github_pat")
        if (pat.isBlank()) {
            _state.update { it.copy(snack = "Isi & simpan PAT GitHub di Pengaturan → Konektor.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(pushing = true, snack = null) }
            val message = "Minta Fitur: " + s.request.trim().take(80)
            val r = withContext(Dispatchers.IO) {
                github.pushFilesGated(
                    gate, pat, s.owner.trim(), s.repo.trim(),
                    s.files.map { it.path to it.content }, message
                )
            }
            _state.update {
                it.copy(
                    pushing = false,
                    snack = r.fold(
                        onSuccess = { n ->
                            "Berhasil push $n file. Build berjalan otomatis " +
                                "di tab Actions GitHub."
                        },
                        onFailure = { (it.message ?: "Gagal push.").take(200) }
                    )
                )
            }
        }
    }

    private fun callAi(model: String, features: String, req: String): String {
        val body = buildJsonObject {
            put("model", model)
            put("stream", false)
            put("max_tokens", 4000)
            put(
                "messages", buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put("content", SYSTEM_PROMPT + "\n\n" + features)
                        }
                    )
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", req)
                        }
                    )
                }
            )
        }.toString()
        val httpReq = Request.Builder()
            .url(prefs.baseUrl.trimEnd('/') + "/chat/completions")
            .post(body.toRequestBody(mediaType))
            .build()
        client.newCall(httpReq).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("AI menolak (HTTP ${resp.code}).")
            val text = resp.body?.string() ?: throw Exception("Respons AI kosong.")
            return runCatching {
                json.decodeFromString<ChatCompletionResponse>(text)
                    .choices.firstOrNull()?.message?.content.orEmpty()
            }.getOrDefault("")
        }
    }

    /**
     * Parse format kontrak:
     *   --- FILE: app/src/.../X.kt ---
     *   ```kotlin
     *   <isi file lengkap>
     *   ```
     * Path divalidasi: harus diawali "app/src/", tanpa "..".
     */
    internal fun parseFiles(output: String): List<ReqFile> {
        val files = mutableListOf<ReqFile>()
        var idx = output.indexOf(MARKER)
        while (idx >= 0) {
            val lineEnd = output.indexOf('\n', idx)
            if (lineEnd < 0) break
            val path = output.substring(idx + MARKER.length, lineEnd)
                .trim().removeSuffix("---").trim()
            val fenceStart = output.indexOf("```", lineEnd)
            if (fenceStart < 0) break
            val nlAfterFence = output.indexOf('\n', fenceStart)
            if (nlAfterFence < 0) break
            val contentStart = nlAfterFence + 1
            val fenceEnd = output.indexOf("\n```", contentStart)
            if (fenceEnd < 0) break
            val content = output.substring(contentStart, fenceEnd)
            if (path.startsWith("app/src/") && !path.contains("..") && content.isNotBlank()) {
                files.add(ReqFile(path, content.trimEnd() + "\n"))
            }
            idx = output.indexOf(MARKER, fenceEnd)
        }
        return files.take(5) // batas aman: maksimal 5 file per permintaan
    }

    companion object {
        private const val MARKER = "--- FILE:"
        private const val SYSTEM_PROMPT =
            "Kamu generator draf file untuk aplikasi Android Hermes " +
                "(Kotlin + Jetpack Compose + Material3 1.3.x, Hilt). " +
                "Konteks fitur aplikasi ada di bawah.\n\n" +
                "ATURAN KELUARAN (wajib):\n" +
                "1. Keluarkan HANYA blok file — tanpa prosa di luar blok.\n" +
                "2. Setiap file diawali baris persis: --- FILE: <path> ---\n" +
                "   <path> = path relatif dari project root, HARUS diawali " +
                "\"app/src/\", tanpa \"..\".\n" +
                "3. Setelah baris FILE, tulis ISI FILE LENGKAP dalam satu " +
                "fenced code block (```kotlin / ```java).\n" +
                "4. Hanya sertakan file yang berubah/dibutuhkan. Maksimal 3 file.\n" +
                "5. Jangan sertakan file yang tidak berubah.\n" +
                "6. Jangan pakai API yang sudah dihapus di Material3 1.3.x " +
                "(ExposedDropdownMenu* tidak ada)."
    }
}

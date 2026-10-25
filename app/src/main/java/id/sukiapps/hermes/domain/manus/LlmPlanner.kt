package id.sukiapps.hermes.domain.manus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Hasil perencanaan LLM: langkah mentah, atau alasan gagal yang jujur. */
sealed interface LlmPlanResult {
    /**
     * @param providerUsed nama provider yang akhirnya berhasil.
     * @param fallback true bila provider yang berhasil BUKAN provider
     *   terpilih user (rantai cadangan dipakai).
     */
    data class Success(
        val steps: List<RawStep>,
        val providerUsed: String = "",
        val fallback: Boolean = false
    ) : LlmPlanResult

    data class Failure(val reason: String) : LlmPlanResult
}

/**
 * LlmPlanner — otak agen tunggal (14.22, rantai fallback 14.26).
 *
 * Satu panggilan /chat/completions NON-STREAMING memakai model + base URL
 * yang sudah dipilih user di Pengaturan. LLM diminta mengembalikan HANYA
 * array JSON langkah; hasilnya TIDAK langsung dieksekusi — [PlanValidator]
 * memvalidasi dulu, langkah tak valid dibuang.
 *
 * 14.26 (B3): bila provider terpilih gagal (DNS, HTTP error, respons
 * kosong, format tak dikenali), planner mencoba provider cadangan
 * BERURUTAN (Jerouter → OpenRouter → DCompute, provider terpilih selalu
 * pertama) — BUKAN langsung jatuh ke planner kata kunci. Aturan kunci:
 * - Provider terpilih memakai [client] apa adanya (AuthInterceptor
 *   memasang Bearer kunci utama — key tidak pernah dibaca di sini).
 * - Provider cadangan HANYA dicoba bila ada kunci tersimpan untuknya di
 *   [providerKeys] (baseUrl → key, dari KeyVault). Tanpa kunci, provider
 *   itu dilewati dengan alasan "tanpa key tersimpan" — tidak pernah
 *   dicoba tanpa kunci, dan kunci provider lain tidak pernah dipakai
 *   silang (itu kebocoran kunci + 401 sia-sia).
 * - Provider cadangan memakai klien TANPA interceptor auth (supaya
 *   header Authorization eksplisitnya tidak dobel dengan kunci utama)
 *   dan header Authorization eksplisit dari kunci cadangannya.
 * - HTTP 5xx (termasuk 504) mendapat 1x retry dengan jeda sebelum
 *   pindah provider; kegagalan jaringan (DNS dsb.) langsung pindah.
 * - Semua gagal → [LlmPlanResult.Failure] dengan alasan GABUNGAN per
 *   provider ("semua provider gagal (Jerouter: …; OpenRouter: …; …)").
 *   Pemanggil (ManusViewModel) lalu memakai fallback planner kata kunci
 *   BERLABEL dengan alasan gabungan itu, bukan gagal diam-diam.
 *
 * Batas jujur: model yang sama dipakai di semua provider. Bila model
 * terpilih tidak ada di provider cadangan, provider itu gagal (mis.
 * HTTP 404) dan dicatat — model per provider adalah pekerjaan lanjutan,
 * bukan bagian PR ini.
 *
 * Tidak ada dependensi Android di sini selain OkHttp (sudah dipakai app);
 * pemanggilan jaringan selalu di Dispatchers.IO.
 */
object LlmPlanner {

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    /** Jeda retry untuk HTTP 5xx sebelum pindah provider. */
    private const val RETRY_DELAY_MS = 800L

    suspend fun plan(
        goal: String,
        model: String,
        baseUrl: String,
        client: OkHttpClient,
        /** Kunci cadangan per baseUrl (dari KeyVault). Kosong = tanpa cadangan. */
        providerKeys: Map<String, String> = emptyMap()
    ): LlmPlanResult = withContext(Dispatchers.IO) {
        if (model.isBlank()) return@withContext LlmPlanResult.Failure(
            "model belum dipilih di Pengaturan"
        )
        if (baseUrl.isBlank()) return@withContext LlmPlanResult.Failure(
            "base URL belum diatur di Pengaturan"
        )
        val body = JSONObject()
            .put("model", model)
            .put("stream", false)
            .put("max_tokens", 1000)
            .put(
                "messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    .put(JSONObject().put("role", "user").put("content", goal))
            )
            .toString()
        val chain = ProviderChain.ordered(baseUrl)
        val failures = ArrayList<String>()
        for ((index, provider) in chain.withIndex()) {
            val isSelected = index == 0
            val key: String?
            val callClient: OkHttpClient
            if (isSelected) {
                // Provider terpilih: kunci utama lewat AuthInterceptor.
                key = null
                callClient = client
            } else {
                key = providerKeys[provider.baseUrl].orEmpty()
                if (key.isBlank()) {
                    failures += "${provider.name}: tanpa key tersimpan"
                    continue
                }
                callClient = withoutAuth(client)
            }
            val attempt = attemptProvider(provider, key, body, callClient)
            when (attempt) {
                is ProviderAttempt.Ok -> {
                    val parsed = parse(attempt.body)
                    if (parsed is LlmPlanResult.Success) {
                        return@withContext parsed.copy(
                            providerUsed = provider.name,
                            fallback = !isSelected
                        )
                    }
                    // Isi respons tidak bisa diparse sebagai rencana:
                    // provider ini gagal, lanjut ke cadangan.
                    failures += "${provider.name}: ${(parsed as LlmPlanResult.Failure).reason}"
                }
                is ProviderAttempt.Failed -> failures += "${provider.name}: ${attempt.reason}"
            }
        }
        LlmPlanResult.Failure("semua provider gagal (${failures.joinToString("; ")})")
    }

    /** Hasil satu percobaan provider: body mentah, atau alasan gagal. */
    private sealed interface ProviderAttempt {
        data class Ok(val body: String) : ProviderAttempt
        data class Failed(val reason: String) : ProviderAttempt
    }

    /**
     * Satu provider, dengan 1x retry untuk HTTP 5xx. Mengembalikan body
     * mentah bila HTTP sukses (parse dilakukan pemanggil), atau alasan.
     */
    private suspend fun attemptProvider(
        provider: LlmProvider,
        key: String?,
        body: String,
        callClient: OkHttpClient
    ): ProviderAttempt {
        var lastError = ""
        // Percobaan 0 = utama, 1 = retry khusus 5xx.
        for (tryIndex in 0..1) {
            if (tryIndex == 1) kotlinx.coroutines.delay(RETRY_DELAY_MS)
            val reqBuilder = Request.Builder()
                .url(provider.baseUrl.trimEnd('/') + "/chat/completions")
                .post(body.toRequestBody(mediaType))
            if (key != null) reqBuilder.addHeader("Authorization", "Bearer $key")
            // Penanda retry: continue tidak boleh duduk di dalam lambda
            // inline .use { } (fitur eksperimental) — jadi keputusan retry
            // dicatat di sini, continue-nya dieksekusi di badan loop.
            var shouldRetry = false
            try {
                callClient.newCall(reqBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        lastError = "LLM HTTP ${resp.code}"
                        // Hanya 5xx yang di-retry; 4xx (key/model salah)
                        // tidak akan membaik dengan mengulang.
                        if (resp.code in 500..599 && tryIndex == 0) {
                            shouldRetry = true
                        } else {
                            return ProviderAttempt.Failed(lastError)
                        }
                    } else {
                        val text = resp.body?.string().orEmpty()
                        if (text.isBlank()) {
                            return ProviderAttempt.Failed("respons LLM kosong")
                        }
                        return ProviderAttempt.Ok(text)
                    }
                }
            } catch (e: Exception) {
                return ProviderAttempt.Failed(
                    "LLM gagal: ${(e.message ?: e.javaClass.simpleName).take(40)}"
                )
            }
            if (shouldRetry) continue
        }
        return ProviderAttempt.Failed(lastError)
    }

    /**
     * Klien turunan tanpa interceptor auth apa pun, supaya request
     * cadangan tidak membawa DUA header Authorization (kunci utama dari
     * interceptor + kunci cadangan eksplisit). Dideteksi dari nama kelas
     * supaya layer domain tidak bergantung ke modul DI.
     */
    private fun withoutAuth(client: OkHttpClient): OkHttpClient {
        val builder = client.newBuilder()
        builder.interceptors().removeAll {
            it.javaClass.simpleName == "AuthInterceptor"
        }
        return builder.build()
    }

    /** Parse konten message LLM menjadi daftar langkah mentah. */
    internal fun parse(responseBody: String): LlmPlanResult {
        val content = runCatching {
            JSONObject(responseBody)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }.getOrNull().orEmpty()
        if (content.isBlank()) {
            return LlmPlanResult.Failure("respons LLM kosong")
        }
        val cleaned = content.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val array = runCatching {
            if (cleaned.startsWith("[")) JSONArray(cleaned)
            else JSONObject(cleaned).getJSONArray("steps")
        }.getOrNull() ?: return LlmPlanResult.Failure(
            "format rencana LLM tidak dikenali"
        )
        val steps = ArrayList<RawStep>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val argsObj = obj.optJSONObject("args") ?: JSONObject()
            val args = LinkedHashMap<String, String>()
            val keys = argsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                args[k] = argsObj.opt(k)?.toString() ?: ""
            }
            steps += RawStep(
                title = obj.optString("title"),
                toolName = obj.optString("tool"),
                args = args,
                detail = obj.optString("detail")
            )
        }
        return LlmPlanResult.Success(steps)
    }

    private const val SYSTEM_PROMPT =
        "Kamu perencana tugas untuk agen di HP. Pecah goal user menjadi " +
            "MAKSIMAL 8 langkah yang bisa dieksekusi tool. Jawab HANYA " +
            "sebagai array JSON, tanpa teks lain dan tanpa markdown, " +
            "dengan tiap elemen berbentuk " +
            "{\"title\": \"judul singkat Indonesia\", \"tool\": \"nama_tool\", " +
            "\"args\": {\"kunci\": \"nilai\"}, \"detail\": \"alasan singkat " +
            "langkah ini\"}. " +
            "HANYA pakai tool berikut (argumen wajib dalam kurung): " +
            "web_fetch(url), shell_exec(command), code_run(code), " +
            "file_read(path), file_write(path, content), net_scan(host), " +
            "conn_health(url), github_repos(), github_runs(owner, repo), " +
            "github_read_file(owner, repo, path), " +
            "github_write_file(owner, repo, path, content), " +
            "github_dispatch(owner, repo), telegram_send(text), " +
            "upin_ask(question), supabase_query(table), " +
            "vercel_projects(), vercel_deployments(project_id), " +
            "system_info(), memory_list(), memory_save(text), " +
            "selfupgrade_branch(), selfupgrade_write_file(branch, path, content), " +
            "selfupgrade_pr(branch), selfupgrade_ci(pr), " +
            "selfupgrade_merge(pr), selfupgrade_release(tag). " +
            "Jangan mengarang nilai argumen yang tidak kamu ketahui dari goal; " +
            "bila argumen tidak diketahui, buat langkah baca/riset dulu untuk " +
            "mendapatkannya. Argumen harus singkat."
}

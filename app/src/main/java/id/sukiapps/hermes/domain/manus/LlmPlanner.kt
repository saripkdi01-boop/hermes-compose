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
        val fallback: Boolean = false,
        /**
         * 14.32: model yang akhirnya berhasil. Kosong = tidak tercatat
         * (hasil dari parse() langsung). Bila berbeda dari model pilihan
         * user, pemanggil WAJIB menampilkannya — bukan turun diam-diam.
         */
        val modelUsed: String = "",
        /**
         * 14.27 (B8): usage dari field `usage` respons provider
         * (prompt/completion/total). null = provider tidak mengirim
         * usage — saat itu angka token adalah estimasi kasar.
         */
        val usageTotal: Int? = null,
        val usagePrompt: Int? = null,
        val usageCompletion: Int? = null
    ) : LlmPlanResult

    data class Failure(val reason: String) : LlmPlanResult
}

/**
 * LlmPlanner — otak agen tunggal (14.22, rantai fallback 14.26, rantai model 14.32).
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
 * Batas jujur 14.32: rantai model — tiap provider dicoba dengan model
 * pilihan user dulu; khusus provider Meta, bila model pilihan ditolak,
 * model cadangan muse-spark-1.3 (model awal preset Muse by Meta) dicoba
 * pada provider yang sama sebelum pindah provider. Model/provider yang
 * berhasil dilaporkan di Success (modelUsed/providerUsed) supaya
 * penurunan tidak diam-diam. Tidak ada ID model karangan untuk provider
 * lain: tidak ada katalog model terverifikasi untuk mereka di app ini.
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
        providerKeys: Map<String, String> = emptyMap(),
        /** Anggaran token perencanaan (dari LaneModelChain per lane). */
        maxTokens: Int = 1000
    ): LlmPlanResult = withContext(Dispatchers.IO) {
        if (model.isBlank()) return@withContext LlmPlanResult.Failure(
            "model belum dipilih di Pengaturan"
        )
        if (baseUrl.isBlank()) return@withContext LlmPlanResult.Failure(
            "base URL belum diatur di Pengaturan"
        )
        // 14.32: rantai percobaan provider-major dari LaneModelChain:
        // model pilihan user dulu pada tiap provider, lalu (khusus Meta)
        // model cadangan muse-spark-1.3 pada provider yang sama, lalu
        // provider berikutnya. "Provider menolak model" jatuh ke model
        // berikutnya — bukan diam-diam turun kualitas, karena model dan
        // provider yang berhasil dilaporkan di Success.
        val selected = baseUrl.trimEnd('/')
        val attempts = LaneModelChain.attempts(baseUrl, model)
        val failures = ArrayList<String>()
        for (laneAttempt in attempts) {
            val provider = laneAttempt.provider
            val isSelectedProvider = provider.baseUrl.trimEnd('/') == selected
            val key: String?
            val callClient: OkHttpClient
            if (isSelectedProvider) {
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
            val body = requestBody(goal, laneAttempt.model, maxTokens)
            val attempt = attemptProvider(provider, key, body, callClient)
            when (attempt) {
                is ProviderAttempt.Ok -> {
                    val parsed = parse(attempt.body)
                    if (parsed is LlmPlanResult.Success) {
                        return@withContext parsed.copy(
                            providerUsed = provider.name,
                            fallback = !isSelectedProvider,
                            modelUsed = laneAttempt.model
                        )
                    }
                    // Isi respons tidak bisa diparse sebagai rencana:
                    // percobaan ini gagal, lanjut ke percobaan berikutnya.
                    failures += "${provider.name}: ${laneAttempt.model}: ${(parsed as LlmPlanResult.Failure).reason}"
                }
                is ProviderAttempt.Failed -> failures += "${provider.name}: ${laneAttempt.model}: ${attempt.reason}"
            }
        }
        LlmPlanResult.Failure("semua provider gagal (${failures.joinToString("; ")})")
    }

    /** Body /chat/completions untuk satu percobaan (provider, model). */
    private fun requestBody(goal: String, model: String, maxTokens: Int): String =
        JSONObject()
            .put("model", model)
            .put("stream", false)
            .put("max_tokens", maxTokens)
            .put(
                "messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    .put(JSONObject().put("role", "user").put("content", goal))
            )
            .toString()

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
        val root = runCatching { JSONObject(responseBody) }.getOrNull()
            ?: return LlmPlanResult.Failure("respons LLM kosong")
        val content = runCatching {
            root
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }.getOrNull().orEmpty()
        if (content.isBlank()) {
            return LlmPlanResult.Failure("respons LLM kosong")
        }
        // 14.27 (B8): baca usage ASLI bila provider mengirimnya.
        // total_tokens diutamakan; bila hanya prompt+completion yang
        // ada, total = jumlah keduanya. Tanpa field usage → null
        // (pemanggil memakai estimasi kasar, bukan mengarang angka).
        val usageObj = root.optJSONObject("usage")
        val usagePrompt = usageObj?.optInt("prompt_tokens", -1)
            ?.takeIf { it >= 0 }
        val usageCompletion = usageObj?.optInt("completion_tokens", -1)
            ?.takeIf { it >= 0 }
        val usageTotal = usageObj?.optInt("total_tokens", -1)
            ?.takeIf { it >= 0 }
            ?: if (usagePrompt != null && usageCompletion != null) {
                usagePrompt + usageCompletion
            } else {
                null
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
        return LlmPlanResult.Success(
            steps,
            usageTotal = usageTotal,
            usagePrompt = usagePrompt,
            usageCompletion = usageCompletion
        )
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

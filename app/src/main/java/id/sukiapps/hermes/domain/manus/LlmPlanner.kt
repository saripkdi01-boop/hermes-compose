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
    data class Success(val steps: List<RawStep>) : LlmPlanResult
    data class Failure(val reason: String) : LlmPlanResult
}

/**
 * LlmPlanner — otak agen tunggal (14.22).
 *
 * Satu panggilan /chat/completions NON-STREAMING memakai model + base URL
 * yang sudah dipilih user di Pengaturan (AuthInterceptor sudah memasang
 * Bearer dari KeyVault — key tidak pernah dibaca di sini). LLM diminta
 * mengembalikan HANYA array JSON langkah; hasilnya TIDAK langsung
 * dieksekusi — [PlanValidator] memvalidasi dulu, langkah tak valid dibuang.
 *
 * Gagal apa pun (model kosong, base URL kosong, HTTP error, respons kosong,
 * format tak dikenali) → [LlmPlanResult.Failure] dengan alasan. Pemanggil
 * (ManusViewModel) lalu memakai fallback planner kata kunci BERLABEL,
 * bukan gagal diam-diam dan bukan mengeksekusi rencana setengah jadi.
 *
 * Tidak ada dependensi Android di sini selain OkHttp (sudah dipakai app);
 * pemanggilan jaringan selalu di Dispatchers.IO.
 */
object LlmPlanner {

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun plan(
        goal: String,
        model: String,
        baseUrl: String,
        client: OkHttpClient
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
        val req = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/chat/completions")
            .post(body.toRequestBody(mediaType))
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext LlmPlanResult.Failure("LLM HTTP ${resp.code}")
                }
                val text = resp.body?.string().orEmpty()
                if (text.isBlank()) {
                    return@withContext LlmPlanResult.Failure("respons LLM kosong")
                }
                return@withContext parse(text)
            }
        } catch (e: Exception) {
            LlmPlanResult.Failure("LLM gagal: ${(e.message ?: e.javaClass.simpleName).take(80)}")
        }
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

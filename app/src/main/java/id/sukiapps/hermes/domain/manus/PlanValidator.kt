package id.sukiapps.hermes.domain.manus

import id.sukiapps.hermes.domain.manus.ManusToolPlanner.PlannedStep
import org.json.JSONObject

/**
 * Bentuk mentah satu langkah rencana dari LLM, sebelum validasi.
 * Dipakai [LlmPlanner] (parse) dan [PlanValidator] (validasi) supaya
 * keduanya tidak bergantung pada org.json di luar lapisan ini.
 */
data class RawStep(
    val title: String,
    val toolName: String,
    val args: Map<String, String> = emptyMap(),
    val detail: String = ""
)

/** Hasil validasi: langkah yang lolos + alasan tiap langkah yang dibuang. */
data class ValidatedPlan(
    val steps: List<PlannedStep>,
    val dropped: List<String> = emptyList()
)

/**
 * PlanValidator — gerbang keamanan rencana LLM (agen tunggal 14.22).
 *
 * Murni Kotlin, tanpa Android, supaya bisa di-unit-test di JVM.
 * Aturan:
 * - toolName HARUS ada di daftar tool yang diizinkan (diambil runtime dari
 *   ToolLoop.toolsArray, supaya tidak drift dari implementasi tool).
 * - argumen wajib per tool HARUS ada dan tidak kosong.
 * - judul kosong, tool tak dikenal, atau argumen wajib hilang → langkah
 *   DIBUANG dengan alasan (tidak pernah dieksekusi).
 * - maks 8 langkah; sisanya dibuang dengan alasan.
 * - nilai argumen dipotong 2000 karakter, judul 120, detail 300 — supaya
 *   satu rencana nakal tidak membanjiri memori / UI.
 *
 * Validator TIDAK menilai benar-tidaknya rencana (itu tugas verifikasi
 * bukti di akhir tugas). Ia hanya memastikan rencana itu *aman dieksekusi*.
 */
object PlanValidator {

    const val MAX_STEPS = 8

    /**
     * Argumen wajib per tool. Tool yang tidak ada di peta dianggap tidak
     * punya argumen wajib (tetap harus lolos cek nama di [allowedTools]).
     * Peta ini harus selaras dengan ToolLoop.toolsArray().
     */
    val REQUIRED_ARGS: Map<String, List<String>> = mapOf(
        "web_fetch" to listOf("url"),
        "shell_exec" to listOf("command"),
        "code_run" to listOf("code"),
        "file_read" to listOf("path"),
        "file_write" to listOf("path", "content"),
        "net_scan" to listOf("host"),
        "conn_health" to listOf("url"),
        "github_runs" to listOf("owner", "repo"),
        "github_read_file" to listOf("owner", "repo", "path"),
        "github_write_file" to listOf("owner", "repo", "path", "content"),
        "github_dispatch" to listOf("owner", "repo"),
        "telegram_send" to listOf("text"),
        "upin_ask" to listOf("question"),
        "supabase_query" to listOf("table"),
        "vercel_deployments" to listOf("project_id"),
        "memory_save" to listOf("text"),
        "selfupgrade_write_file" to listOf("branch", "path", "content"),
        "selfupgrade_pr" to listOf("branch"),
        "selfupgrade_ci" to listOf("pr"),
        "selfupgrade_merge" to listOf("pr"),
        "selfupgrade_release" to listOf("tag")
        // Tanpa argumen wajib: github_repos, vercel_projects, system_info,
        // memory_list, selfupgrade_branch.
    )

    /** Daftar tool yang dikenal validator bila pemanggil tidak memberi daftar. */
    fun knownTools(): Set<String> = REQUIRED_ARGS.keys +
        setOf(
            "github_repos", "vercel_projects", "system_info",
            "memory_list", "selfupgrade_branch"
        )

    fun validate(raw: List<RawStep>, allowedTools: Set<String>): ValidatedPlan {
        val allowed = if (allowedTools.isEmpty()) knownTools() else allowedTools
        val kept = ArrayList<PlannedStep>(MAX_STEPS)
        val dropped = ArrayList<String>()
        for (step in raw) {
            if (kept.size >= MAX_STEPS) {
                dropped += "langkah '${step.title.take(40)}' dibuang: melebihi batas $MAX_STEPS langkah"
                continue
            }
            val title = step.title.trim()
            if (title.isEmpty()) {
                dropped += "langkah tanpa judul dibuang"
                continue
            }
            if (step.toolName.isBlank() || step.toolName !in allowed) {
                dropped += "langkah '$title' dibuang: tool '${step.toolName}' tidak dikenal"
                continue
            }
            val missing = (REQUIRED_ARGS[step.toolName] ?: emptyList())
                .filter { (step.args[it] ?: "").isBlank() }
            if (missing.isNotEmpty()) {
                dropped += "langkah '$title' dibuang: argumen wajib hilang (${missing.joinToString(", ")})"
                continue
            }
            val argsJson = JSONObject()
            step.args.forEach { (k, v) -> argsJson.put(k, v.take(2000)) }
            kept += PlannedStep(
                title = title.take(120),
                toolName = step.toolName,
                args = argsJson,
                detail = step.detail.take(300)
            )
        }
        return ValidatedPlan(kept, dropped)
    }
}

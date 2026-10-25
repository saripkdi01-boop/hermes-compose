package id.sukiapps.hermes.ui.manus

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import id.sukiapps.hermes.data.store.ExecutionLog
import id.sukiapps.hermes.domain.manus.AgentStep
import id.sukiapps.hermes.domain.manus.AgentTask
import id.sukiapps.hermes.domain.manus.LogLevel
import id.sukiapps.hermes.domain.manus.ManusEvent
import id.sukiapps.hermes.domain.manus.ManusUiState
import id.sukiapps.hermes.domain.manus.StepStatus
import id.sukiapps.hermes.domain.manus.ToolCall
import id.sukiapps.hermes.domain.manus.risk
import id.sukiapps.hermes.domain.manus.RiskLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AgentRunner — pelaksana NYATA untuk layar Manus (v14.16).
 *
 * Bedanya dari simulasi lama (tombol "Selesai Langkah" di ViewModel):
 * setiap langkah yang punya tool dijalankan lewat [id.sukiapps.hermes.tool.ToolLoop.execute]
 * — tool yang sama dengan yang dipakai chat (web_fetch, shell_exec, file_read,
 * file_write, net_scan, conn_health, github_*, telegram_send, supabase_query,
 * vercel_*, selfupgrade_*, memory_*, system_info, upin_ask, code_run).
 *
 * Kontrak yang DIPERTAHANKAN dari state machine:
 * - tool berisiko (risk != LOW) TIDAK PERNAH dijalankan otomatis: runner
 *   berhenti di kartu izin (PermissionRequest) seperti sebelumnya. Izin
 *   diberikan -> runner melanjutkan langkah yang sama; ditolak -> langkah
 *   dilewati (SKIPPED), task lanjut ke langkah berikut.
 * - satu task satu coroutine ([jobs]); task baru menggantikan job lama per taskId.
 * - gagal di tengah -> status FAILED + error dicatat, tidak menggantung UI.
 * - semua hasil juga ditulis ke ExecutionLog (live review) selain timeline Manus.
 *
 * Runner TIDAK mengubah ManusUiState langsung: ia hanya mengirim ManusEvent
 * ke ViewModel, reducer tetap satu-satunya yang boleh mengubah state.
 */
@Singleton
class AgentRunner @Inject constructor(
    @ApplicationContext private val appCtx: Context
) {
    /** Job aktif per taskId — dipakai pause/resume/cancel tanpa keburu job lain. */
    private val jobs = mutableMapOf<String, Job>()
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Jalankan satu task sampai selesai. Idempoten per taskId: memanggil lagi
     * dengan taskId yang sama tidak membuat eksekusi ganda.
     *
     * @param task task yang mau dijalankan (status IDLE atau PAUSED).
     * @param snapshot state terkini untuk pembacaan ulang setelah event.
     * @param dispatch kirim event ke ViewModel (reducer + persist).
     */
    fun run(
        task: AgentTask,
        snapshot: () -> ManusUiState,
        dispatch: (ManusEvent) -> Unit
    ) {
        if (jobs[task.id]?.isActive == true) return
        val job = scope.launch {
            var current = task
            while (true) {
                // Baca ulang task dari snapshot supaya event yang dikirim runner
                // (dan event user seperti pause/cancel) sudah tercermin.
                val live = snapshot().tasks.firstOrNull { it.id == task.id } ?: break
                if (!live.status.isActive) break
                current = live
                // Cari langkah berikutnya yang PENDING. Langkah SKIPPED/DONE/FAILED dilewati.
                val step = current.steps.firstOrNull { it.status == StepStatus.PENDING } ?: break
                if (!executeStep(current, step, snapshot, dispatch)) break
            }
            // Selesaikan task bila tidak ada sisa langkah PENDING.
            val done = snapshot().tasks.firstOrNull { it.id == task.id }
            if (done != null && done.status.isActive && done.nextStep == null) {
                dispatch(ManusEvent.ToolFinished(task.id, done.steps.last().id, "(semua langkah lewat)"))
            }
            jobs.remove(task.id)
        }
        jobs[task.id] = job
    }

    /**
     * Eksekusi satu langkah. Return true = lanjut ke langkah berikutnya.
     *
     * Alur:
     * 1. langkah tanpa tool -> tandai DONE (langkah rencana biasa, output judul).
     * 2. langkah dengan tool berisiko -> kirim RequestPermission dan berhenti
     *    (false). Setelah izin diberikan, runner dipanggil ulang (lihat ViewModel)
     *    dan langkah ini menjadi RUNNING -> dieksekusi di cabang 3.
     * 3. langkah dengan tool LOW -> jalankan ToolLoop.execute langsung, lalu
     *    laporkan ToolFinished (output nyata) atau ToolFailed.
     */
    private suspend fun executeStep(
        task: AgentTask,
        step: AgentStep,
        snapshot: () -> ManusUiState,
        dispatch: (ManusEvent) -> Unit
    ): Boolean {
        val tool = step.tool
            ?: run {
                dispatch(ManusEvent.ToolFinished(task.id, step.id, "Langkah rencana selesai."))
                return true
            }
        val risk = tool.risk()
        if (risk.requiresApproval && step.status == StepStatus.PENDING) {
            dispatch(
                ManusEvent.RequestPermission(
                    taskId = task.id,
                    stepId = step.id,
                    tool = tool,
                    reason = "langkah \"${step.title}\" butuh izin",
                    risk = risk
                )
            )
            return false
        }
        return runTool(task, step, tool, snapshot, dispatch)
    }

    private suspend fun runTool(
        task: AgentTask,
        step: AgentStep,
        tool: ToolCall,
        snapshot: () -> ManusUiState,
        dispatch: (ManusEvent) -> Unit
    ): Boolean {
        val args = JSONObject()
        tool.args.forEach { (k, v) -> args.put(k, v) }
        val t0 = System.currentTimeMillis()
        ExecutionLog.log("manus", "tool:${tool.name}", tool.summary().take(160), "start")
        val result = runCatching {
            withContext(Dispatchers.IO) {
                id.sukiapps.hermes.tool.ToolLoop.execute(tool.name, args)
            }
        }.getOrElse { id.sukiapps.hermes.tool.ToolResult("Error: ${it.javaClass.simpleName}: ${it.message}", true) }
        val ms = System.currentTimeMillis() - t0
        ExecutionLog.log(
            "manus",
            "tool:${tool.name}",
            tool.summary().take(160),
            if (result.isError) "fail" else "ok"
        )
        return if (result.isError) {
            dispatch(ManusEvent.ToolFailed(task.id, step.id, result.content.take(2000)))
            false
        } else {
            dispatch(ManusEvent.ToolFinished(task.id, step.id, result.content.take(4000)))
            true
        }
    }

    /** Hentikan job satu task (dipakai cancelTask). */
    fun cancel(taskId: String) {
        jobs.remove(taskId)?.cancel()
    }

    /** Hentikan semua job (dipakai clearHistory / ViewModel onCleared). */
    fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }

    /**
     * Petakan goal bebas jadi daftar langkah awal (plan) + tool call per langkah.
     *
     * Dipakai ketika user hanya menulis goal tanpa menulis rencana manual:
     * "cari harga emas di website X" -> langkah web_fetch(url=X);
     * "scan localhost" -> net_scan; "baca file notes.txt" -> file_read.
     *
     * Aturan: nama tool asli dari ToolLoop.toolsArray() — TIDAK mengarang tool.
     * Kalau tidak ada yang cocok -> satu langkah riset web_fetch(search) supaya
     * agen tetap menghasilkan sesuatu, bukan gagal.
     */
    fun planFromGoal(goal: String): List<Pair<String, ToolCall?>> {
        val g = goal.lowercase()
        val steps = mutableListOf<Pair<String, ToolCall?>>()
        val urlMatch = Regex("""https?://\S+""").find(goal)
        when {
            g.contains("scan") && (g.contains("port") || g.contains("localhost") || g.contains("host")) -> {
                val host = Regex("""(localhost|127\.0\.0\.1|\d+\.\d+\.\d+\.\d+|[a-z0-9.-]+\.[a-z]{2,})""")
                    .find(g)?.value ?: "localhost"
                steps += "Scan port $host" to ToolCall("net_scan", mapOf("host" to host))
            }
            g.contains("baca file") || g.contains("read file") -> {
                val path = Regex("""(?:baca file|read file)\s+([\w./-]+)""").find(g)?.groupValues?.getOrNull(1)
                    ?: "notes.txt"
                steps += "Baca file $path" to ToolCall("file_read", mapOf("path" to path))
            }
            g.contains("tulis file") || g.contains("write file") -> {
                val path = Regex("""(?:tulis file|write file)\s+([\w./-]+)""").find(g)?.groupValues?.getOrNull(1)
                    ?: "catatan.txt"
                steps += "Tulis file $path" to ToolCall("file_write", mapOf("path" to path, "content" to "Ditulis agen Hermes: $goal"))
            }
            g.contains("health") || g.contains("status") -> {
                val url = urlMatch?.value ?: "https://sukiapps.web.id"
                steps += "Health check $url" to ToolCall("conn_health", mapOf("url" to url))
            }
            urlMatch != null -> {
                steps += "Ambil isi ${urlMatch.value}" to ToolCall("web_fetch", mapOf("url" to urlMatch.value))
            }
            g.contains("repo") || g.contains("github") -> {
                steps += "Daftar repo GitHub" to ToolCall("github_repos", emptyMap())
            }
            g.contains("sistem") || g.contains("perangkat") || g.contains("device") -> {
                steps += "Info perangkat & sandbox" to ToolCall("system_info", emptyMap())
            }
            else -> {
                steps += "Riset web: $goal" to ToolCall(
                    "web_fetch",
                    mapOf("url" to "https://duckduckgo.com/html/?q=${java.net.URLEncoder.encode(goal, "UTF-8")}")
                )
            }
        }
        return steps
    }

    /**
     * Bangun langkah lengkap (AgentStep) dari hasil planFromGoal — dipakai
     * ViewModel supaya reducer tetap satu-satunya yang membuat AgentTask.
     */
    companion object {
        fun level(risk: RiskLevel): LogLevel = when (risk) {
            RiskLevel.LOW -> LogLevel.DEBUG
            RiskLevel.MEDIUM -> LogLevel.INFO
            RiskLevel.HIGH -> LogLevel.WARN
            RiskLevel.CRITICAL -> LogLevel.ERROR
        }
    }
}

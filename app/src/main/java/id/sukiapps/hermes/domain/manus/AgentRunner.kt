package id.sukiapps.hermes.domain.manus

import android.util.Log
import id.sukiapps.hermes.tool.ToolLoop
import id.sukiapps.hermes.tool.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * AgentRunner — pelaksana NYATA untuk mesin Manus.
 *
 * Bagian dari upgrade Manus Live Console (14.16). Sebelumnya tombol
 * "Selesai Langkah" hanya simulasi; runner ini memanggil tool asli
 * ([ToolLoop.execute]) untuk setiap langkah yang punya tool call, lalu
 * melaporkan hasilnya ke reducer melalui ManusEvent.ToolFinished/ToolFailed.
 *
 * Kontrak penting:
 * - HANYA dieksekusi di [Dispatchers.IO]; UI tetap responsif.
 * - Gating risiko TIDAK diulang di sini: reducer yang menandai WAITING_PERMISSION.
 *   Runner mulai bekerja saat task RUNNING (artinya izin sudah diberi atau
 *   langkah LOW). Runner hanya mengeksekusi langkah RUNNING pertama.
 * - Stop bersyarat: `stopRequested` dicek setiap iterasi supaya tombol Batal
 *   benar-benar menghentikan pelaksana, bukan cuma mengganti status di UI.
 * - Tidak ada tool call? Runner menandai langkah selesai tanpa memanggil
 *   tool (langkah rencana biasa / ringkasan manusia).
 */
class AgentRunner(
    private val toolLoop: ToolLoop,
    private val onEvent: (ManusEvent) -> Unit
) {
    @Volatile
    var stopRequested: Boolean = false
        private set

    private var running = false

    val isRunning: Boolean get() = running

    /** Minta pelaksana berhenti setelah langkah/iterasi berjalan saat ini. */
    fun requestStop() {
        stopRequested = true
    }

    /**
     * Jalankan satu langkah nyata. Dipanggil dari [runTask] dan bisa juga
     * dipanggil langsung untuk menjalankan langkah RUNNING tertentu.
     * Mengembalikan true bila langkah selesai (DONE), false bila gagal.
     */
    suspend fun executeStep(taskId: String, stepId: String, toolCall: ToolCall?): Boolean =
        withContext(Dispatchers.IO) {
            if (toolCall == null) {
                // Langkah tanpa tool: tandai selesai dengan catatan jujur.
                onEvent(
                    ManusEvent.ToolFinished(
                        taskId = taskId,
                        stepId = stepId,
                        output = "Langkah selesai (tanpa tool — langkah rencana/ringkasan)."
                    )
                )
                return@withContext true
            }
            val args = JSONObject()
            toolCall.args.forEach { (k, v) -> args.put(k, v) }
            val start = System.currentTimeMillis()
            val result: ToolResult = runCatching {
                toolLoop.execute(toolCall.name, args)
            }.getOrElse { e ->
                ToolResult(content = "Eksekusi gagal: ${e.message ?: e.javaClass.simpleName}", isError = true)
            }
            val tookMs = System.currentTimeMillis() - start
            val withTiming = if (result.content.isBlank()) result
            else result.copy(content = result.content + "\n[${tookMs} ms]")
            if (withTiming.isError) {
                onEvent(ManusEvent.ToolFailed(taskId, stepId, withTiming.content))
                false
            } else {
                onEvent(ManusEvent.ToolFinished(taskId, stepId, withTiming.content))
                true
            }
        }

    /**
     * Loop utama: eksekusi langkah RUNNING sampai task tidak aktif lagi
     * (COMPLETED / FAILED / CANCELLED / WAITING_PERMISSION / PAUSED), atau
     * [requestStop] dipanggil. State dibaca ulang setiap iterasi melalui
     * [stateProvider] supaya keputusan (pause, batal, langkah baru) selalu
     * berbasis snapshot terbaru, bukan state basi saat loop dimulai.
     *
     * 14.27 (B6): batas sesi [limits] DITEGAKKAN di sini — bukan hanya
     * ditampilkan di Pengaturan. Sebelum tiap langkah: iterasi sesi
     * (langkah yang sudah settled saat loop dimulai + yang dieksekusi
     * loop ini), timeout sejak loop dimulai, dan budget token
     * (task.tokensUsed berjalan). Batas apa pun yang terlampaui →
     * [ManusEvent.LimitReached] (STOPPED + alasan eksplisit) dan loop
     * berhenti. Pemeriksaan timeout/budget juga jalan di jalur tunggu
     * (tidak ada langkah RUNNING) supaya tugas yang menggantung tetap
     * berhenti di batasnya, bukan jalan selamanya.
     * Setelah tiap langkah selesai, estimasi outputnya ditambahkan ke
     * tokensUsed lewat [ManusEvent.UsageUpdated] (estimasi kasar —
     * angka asli hanya datang dari usage planner, lihat B8).
     */
    suspend fun runTask(
        taskId: String,
        stateProvider: () -> ManusUiState?,
        limits: SessionLimits = SessionLimits()
    ) {
        if (running) {
            Log.w(TAG, "runTask ditolak: runner sudah menjalankan tugas lain.")
            return
        }
        running = true
        stopRequested = false
        val runStartMs = System.currentTimeMillis()
        var executedThisRun = 0
        // Langkah yang sudah settled sebelum loop ini = eksekusi sesi
        // sebelumnya (relevan saat resume: batas iterasi bersifat
        // per sesi, bukan per pemanggilan runTask).
        val settledAtStart = stateProvider()?.tasks
            ?.firstOrNull { it.id == taskId }
            ?.steps?.count { it.isSettled } ?: 0
        try {
            while (true) {
                if (stopRequested) {
                    Log.i(TAG, "runTask dihentikan atas permintaan user.")
                    break
                }
                val snapshot = stateProvider() ?: break
                val task = snapshot.tasks.firstOrNull { it.id == taskId } ?: break

                // 14.27: penegakan batas sesi (berlaku untuk semua
                // jalur tunggu/eksekusi di bawah, kecuali VERIFYING —
                // tugas yang langkahnya sudah habis tetap boleh
                // diverifikasi meski batas baru saja terlampaui).
                if (task.status != TaskStatus.VERIFYING) {
                    val now = System.currentTimeMillis()
                    val limitReason = when {
                        now - runStartMs > limits.timeoutMs ->
                            "Timeout sesi tercapai (${limits.timeoutMs / 60_000} menit)"
                        task.tokensUsed >= limits.tokenBudget ->
                            "Budget token tercapai (${task.tokensUsed}/${limits.tokenBudget})"
                        else -> null
                    }
                    if (limitReason != null) {
                        onEvent(ManusEvent.LimitReached(taskId, limitReason))
                        break
                    }
                }

                // 14.22: gerbang bukti. Semua langkah habis (VERIFYING):
                // runner memverifikasi, lalu dispatch hasil; loop berhenti
                // (tugas kini COMPLETED, PARTIAL, atau STOPPED).
                if (task.status == TaskStatus.VERIFYING) {
                    val result = verifyTask(task)
                    if (result.isPartial) {
                        onEvent(
                            ManusEvent.VerificationPartial(
                                taskId,
                                result.reason ?: "hasil sebagian",
                                result.missing
                            )
                        )
                    } else if (result.passed) {
                        onEvent(ManusEvent.VerificationPassed(taskId, result.evidence))
                    } else {
                        onEvent(ManusEvent.VerificationFailed(taskId, result.reason ?: "verifikasi gagal"))
                    }
                    break
                }

                // Hanya proses task yang memang aktif dan sedang RUNNING.
                if (task.status != TaskStatus.RUNNING) break

                val runningStep = task.steps.firstOrNull { it.status == StepStatus.RUNNING }
                if (runningStep == null) {
                    // Tidak ada langkah RUNNING: beri kesempatan reducer memulai
                    // langkah PENDING berikutnya (advance() dipicu oleh
                    // ToolFinished sebelumnya). Bila tetap tidak ada, stop.
                    delay(POLL_MS)
                    val after = stateProvider()?.tasks?.firstOrNull { it.id == taskId } ?: break
                    val next = after.steps.firstOrNull { it.status == StepStatus.RUNNING }
                        ?: after.steps.firstOrNull { it.status == StepStatus.PENDING }
                    if (next == null || !after.status.isActive) break
                    if (after.status == TaskStatus.RUNNING &&
                        next.status != StepStatus.RUNNING
                    ) {
                        // Reducer sudah siap; langkah akan ditandai RUNNING oleh
                        // event berikutnya. Tunggu sedikit dan ulangi.
                        continue
                    }
                    continue
                }

                // 14.27: batas iterasi dicek tepat sebelum eksekusi.
                if (settledAtStart + executedThisRun >= limits.maxIterations) {
                    onEvent(
                        ManusEvent.LimitReached(
                            taskId,
                            "Batas iterasi tercapai (${settledAtStart + executedThisRun}/${limits.maxIterations})"
                        )
                    )
                    break
                }
                val ok = executeStep(taskId, runningStep.id, runningStep.tool)
                if (!ok) break // gagal: reducer sudah menandai task FAILED
                executedThisRun++
                // 14.27: tambah estimasi output langkah ke tokensUsed.
                val fresh = stateProvider()?.tasks?.firstOrNull { it.id == taskId }
                val out = fresh?.steps?.firstOrNull { it.id == runningStep.id }?.output
                if (!out.isNullOrBlank() && fresh != null) {
                    onEvent(
                        ManusEvent.UsageUpdated(
                            taskId,
                            fresh.tokensUsed + estimateSessionTokens(out)
                        )
                    )
                }

                // Jeda kecil supaya timeline terasa hidup dan tidak membanjiri
                // device entry-level.
                delay(STEP_DELAY_MS)
            }
        } finally {
            running = false
        }
    }

    /**
     * 14.22: verifikasi bukti tugas. Baca-balik file_write dilakukan lewat
     * ToolLoop file_read yang sama (nyata, bukan rekaman) — bila konektor
     * belum diinisialisasi, baca-balik gagal dan verifikasi gagal dengan
     * jujur, bukan lolos diam-diam.
     */
    private suspend fun verifyTask(task: AgentTask): VerificationResult {
        return TaskVerifier.verify(task) { path ->
            runCatching {
                val result = toolLoop.execute(
                    "file_read",
                    JSONObject().put("path", path)
                )
                if (result.isError) null else result.content
            }.getOrNull()
        }
    }

    companion object {
        private const val TAG = "AgentRunner"
        private const val POLL_MS = 120L
        private const val STEP_DELAY_MS = 250L
    }
}

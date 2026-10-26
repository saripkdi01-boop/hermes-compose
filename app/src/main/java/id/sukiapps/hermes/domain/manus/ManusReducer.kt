package id.sukiapps.hermes.domain.manus
/**
 * Semua input ke state machine lewat event terketat. Tidak ada jalur lain yang
 * boleh mengubah ManusUiState — ini yang bikin perilaku agent bisa diprediksi
 * dan bisa dites tanpa Android.
 *
 * BACKUP: versi sebelumnya disimpan sebagai ManusReducer.kt.bak di repo
 * sebelum file ini ditulis (kebijakan upgrade: file lama yang diubah WAJIB
 * punya backup). Perubahan utama pada versi ini:
 * - `risk()` kini mengenal tool NYATA Hermes (web_fetch, shell_exec,
 *   github_read_file, ...) selain nama tool lama.
 * - `SubmitGoal.plan` tetap List<String> (kontrak UI), tapi setiap langkah
 *   tetap bisa menerima ToolCall terpisah melalui ManusEvent.AttachStepTool
 *   sehingga panel Komputer, kartu izin, dan executor bekerja dengan data
 *   nyata, bukan cuma judul teks.
 */
sealed interface ManusEvent {
    /** User menyerahkan goal baru. Membuat task IDLE + langkah rencana awal. */
    data class SubmitGoal(val goal: String, val plan: List<String>) : ManusEvent
    /**
     * Sematkan tool NYATA ke satu langkah. Dipakai oleh AgentRunner saat
     * memetakan goal (ManusToolPlanner) ke langkah rencana, supaya reducer
     * tahu tool apa yang akan dijalankan — bukan hanya judul teks.
     */
    data class AttachStepTool(
        val taskId: String,
        val stepId: String,
        val tool: ToolCall,
        val detail: String? = null,
        val sources: List<String> = emptyList(),
        val artifacts: List<String> = emptyList(),
        /** 14.24: true bila langkah ini hanya persiapan (bukan hasil). */
        val isPreparation: Boolean = false
    ) : ManusEvent
    /** Mulai eksekusi task aktif dari langkah pertama. */
    data class StartTask(val taskId: String) : ManusEvent
    /** Pelaksana tool melaporkan langkah selesai. */
    data class ToolFinished(
        val taskId: String,
        val stepId: String,
        val output: String? = null
    ) : ManusEvent
    /** Pelaksana tool melaporkan langkah gagal. */
    data class ToolFailed(
        val taskId: String,
        val stepId: String,
        val error: String
    ) : ManusEvent
    /** Agent minta izin menjalankan tool berisiko. */
    data class RequestPermission(
        val taskId: String,
        val stepId: String,
        val tool: ToolCall,
        val reason: String,
        val risk: RiskLevel
    ) : ManusEvent
    /** Izin diberikan: lanjutkan ke langkah berikutnya. */
    data class PermissionGranted(val taskId: String) : ManusEvent
    /** Izin ditolak: langkah DILEWATI (SKIPPED), task LANJUT ke langkah berikut. */
    data class PermissionDenied(val taskId: String, val reason: String = "ditolak user") : ManusEvent
    /** User membuka kembali tugas lama (riwayat) untuk dilihat ulang / replay. */
    data class SelectTask(val taskId: String) : ManusEvent
    /** Arahan user di tengah jalan: dicatat di timeline, tidak mengubah rencana diam-diam. */
    data class SteerTask(val taskId: String, val message: String) : ManusEvent
    data class PauseTask(val taskId: String) : ManusEvent
    data class ResumeTask(val taskId: String) : ManusEvent
    data class CancelTask(val taskId: String, val reason: String = "dibatalkan user") : ManusEvent
    data class RetryStep(val taskId: String, val stepId: String) : ManusEvent
    /**
     * 14.22: ganti daftar langkah tugas yang masih IDLE (dipakai jalur
     * agen tunggal: task dibuat dulu dengan langkah "Merencanakan…",
     * lalu diganti rencana LLM / fallback begitu rencana siap).
     * Tidak boleh mengubah langkah yang sudah settled.
     */
    data class ReplacePlan(
        val taskId: String,
        val plan: List<String>,
        val planLabel: String = "",
        /** 14.27 (B8): usage planner dari respons provider (null = tidak dikirim). */
        val usageTotal: Int? = null,
        val usagePrompt: Int? = null,
        val usageCompletion: Int? = null,
        /** 14.27 (B6): token awal sesi — usage asli bila ada, estimasi bila tidak. */
        val initialTokens: Int = 0
    ) : ManusEvent
    /**
     * 14.27 (B6): pelaksana melaporkan pemakaian token berjalan.
     * Hanya memperbarui angka di task; tidak mengubah status.
     */
    data class UsageUpdated(val taskId: String, val tokensUsed: Int) : ManusEvent
    /**
     * 14.27 (B6): batas sesi tercapai (iterasi/timeout/budget) — hanya
     * dari jalur RUNNING → STOPPED dengan alasan eksplisit. Bukan
     * FAILED (tugas tidak rusak) dan bukan COMPLETED (tugas tidak
     * tuntas); user bisa menekan Lanjutkan setelah menaikkan batas
     * di Pengaturan, atau memulai tugas baru.
     */
    data class LimitReached(val taskId: String, val reason: String) : ManusEvent
    /** 14.22: gerbang verifikasi lolos — hanya dari VERIFYING. */
    data class VerificationPassed(val taskId: String, val evidence: String) : ManusEvent
    /** 14.22: gerbang verifikasi gagal — hanya dari VERIFYING → STOPPED. */
    data class VerificationFailed(val taskId: String, val reason: String) : ManusEvent
    /**
     * 14.24: gerbang verifikasi sebagian — hanya dari VERIFYING → PARTIAL.
     * Tugas berjalan sampai habis tapi hasilnya tidak cukup membuktikan
     * goal tercapai. BUKAN COMPLETED dan BUKAN FAILED: capaian tercatat,
     * `missing` menjelaskan apa yang kurang.
     */
    data class VerificationPartial(
        val taskId: String,
        val reason: String,
        val missing: List<String> = emptyList()
    ) : ManusEvent
    /** 14.22: lanjutkan tugas STOPPED (verifikasi ulang / langkah tersisa). */
    data class ResumeStopped(val taskId: String) : ManusEvent
    /** Bersihkan riwayat task yang sudah selesai. */
    data object ClearHistory : ManusEvent
}
/**
 * Pemetaan nama tool ke tingkat risiko. Sengaja TOP-LEVEL (bukan member
 * `object ManusReducer`) supaya bisa dipanggil dari reducer, dari UI, dan dari
 * unit test tanpa perlu scope object.
 *
 * Kategori (diperbarui untuk tool NYATA Hermes + nama tool lama):
 * - LOW: baca saja, aman dijalankan otomatis
 * - MEDIUM: menulis/mengirim, sebaiknya diawasi
 * - HIGH: menjalankan perintah / hapus file / tulis file
 * - CRITICAL: deploy, merge PR, release, shell, aksi keluar — wajib izin
 */
fun ToolCall.risk(): RiskLevel {
    val low = setOf(
        "read_file", "list_files", "web_search", "read_logs", "summarize",
        "web_fetch", "file_read", "github_read_file", "github_repos",
        "supabase_query", "vercel_projects", "memory_list", "system_info",
        "net_scan", "conn_health", "github_runs"
    )
    val medium = setOf(
        "write_file", "send_message", "create_task", "edit_file",
        "file_write", "github_write_file", "telegram_send", "memory_save",
        "github_dispatch"
    )
    val high = setOf(
        "run_command", "delete_file", "shell_exec", "code_run"
    )
    val critical = setOf(
        "deploy", "selfupgrade_merge", "selfupgrade_release",
        "selfupgrade_pr", "selfupgrade_branch", "upin_ask"
    )
    return when (name) {
        in critical -> RiskLevel.CRITICAL
        in high -> RiskLevel.HIGH
        in medium -> RiskLevel.MEDIUM
        in low -> RiskLevel.LOW
        else -> RiskLevel.MEDIUM
    }
}
/**
 * Reducer tunggal untuk seluruh state Manus. Fungsi murni: (state, event) -> state.
 * Clock disuntikkan supaya test bisa memakai waktu palsu yang deterministik.
 */
object ManusReducer {
    fun reduce(
        state: ManusUiState,
        event: ManusEvent,
        clock: () -> Long = System::currentTimeMillis
    ): ManusUiState = when (event) {
        is ManusEvent.SubmitGoal -> submitGoal(state, event, clock)
        is ManusEvent.AttachStepTool -> attachStepTool(state, event, clock)
        is ManusEvent.StartTask -> startTask(state, event, clock)
        is ManusEvent.ToolFinished -> toolFinished(state, event, clock)
        is ManusEvent.ToolFailed -> toolFailed(state, event, clock)
        is ManusEvent.RequestPermission -> requestPermission(state, event, clock)
        is ManusEvent.PermissionGranted -> permissionGranted(state, event, clock)
        is ManusEvent.PermissionDenied -> permissionDenied(state, event, clock)
        is ManusEvent.SelectTask -> selectTask(state, event, clock)
        is ManusEvent.SteerTask -> steerTask(state, event, clock)
        is ManusEvent.PauseTask -> pauseTask(state, event, clock)
        is ManusEvent.ResumeTask -> resumeTask(state, event, clock)
        is ManusEvent.CancelTask -> cancelTask(state, event, clock)
        is ManusEvent.RetryStep -> retryStep(state, event, clock)
        is ManusEvent.ReplacePlan -> replacePlan(state, event, clock)
        is ManusEvent.UsageUpdated -> usageUpdated(state, event, clock)
        is ManusEvent.LimitReached -> limitReached(state, event, clock)
        is ManusEvent.VerificationPassed -> verificationPassed(state, event, clock)
        is ManusEvent.VerificationFailed -> verificationFailed(state, event, clock)
        is ManusEvent.VerificationPartial -> verificationPartial(state, event, clock)
        is ManusEvent.ResumeStopped -> resumeStopped(state, event, clock)
        ManusEvent.ClearHistory -> clearHistory(state)
    }
    // --- transisi per event -------------------------------------------------
    private fun submitGoal(
        state: ManusUiState,
        event: ManusEvent.SubmitGoal,
        clock: () -> Long
    ): ManusUiState {
        val now = clock()
        val taskId = "task-${state.nextStepSeq}"
        val steps = event.plan.mapIndexed { index, title ->
            AgentStep(id = "$taskId-s${index + 1}", title = title)
        }
        val task = AgentTask(
            id = taskId,
            goal = event.goal,
            steps = steps,
            status = TaskStatus.IDLE,
            createdAt = now,
            updatedAt = now
        )
        return state
            .copy(
                tasks = state.tasks + task,
                activeTaskId = taskId,
                nextStepSeq = state.nextStepSeq + 1,
                running = true
            )
            .log(now, LogLevel.INFO, "manus", "goal diterima: ${event.goal} (${steps.size} langkah)")
    }
    /**
     * Sematkan tool nyata ke langkah. Tidak mengubah status task; hanya
     * melengkapi data langkah supaya panel Komputer, kartu izin, dan
     * AgentRunner punya ToolCall untuk dieksekusi.
     */
    private fun attachStepTool(
        state: ManusUiState,
        event: ManusEvent.AttachStepTool,
        clock: () -> Long
    ): ManusUiState {
        val now = clock()
        val task = state.tasks.firstOrNull { it.id == event.taskId } ?: return state
        val target = task.steps.firstOrNull { it.id == event.stepId } ?: return state
        // Jangan timpa langkah yang sudah selesai/gagal (append-only status).
        if (target.isSettled) return state
        val updatedTask = task.copy(
            steps = task.steps.map {
                if (it.id == event.stepId) {
                    it.copy(
                        tool = event.tool,
                        detail = event.detail ?: it.detail,
                        sources = event.sources.ifEmpty { it.sources },
                        artifacts = event.artifacts.ifEmpty { it.artifacts },
                        isPreparation = event.isPreparation
                    )
                } else it
            },
            updatedAt = now
        )
        return replaceTask(state, updatedTask)
            .log(now, LogLevel.INFO, "manus", "tool disematkan: ${event.tool.summary()} → ${event.stepId}")
    }
    /**
     * StartTask harus langsung masuk [advance] supaya langkah pertama benar-benar
     * menjadi RUNNING. Tanpa ini task nyangkut di PLANNING: tidak ada langkah
     * RUNNING, tombol "Selesai Langkah" di UI jadi no-op, task tak pernah maju.
     */
    private fun startTask(
        state: ManusUiState,
        event: ManusEvent.StartTask,
        clock: () -> Long
    ): ManusUiState {
        val now = clock()
        val task = state.tasks.firstOrNull { it.id == event.taskId } ?: return state
        if (task.status != TaskStatus.IDLE && task.status != TaskStatus.PAUSED) return state
        val updated = task.copy(status = TaskStatus.PLANNING, updatedAt = now)
        return advance(replaceTask(state, updated), updated, clock)
            .log(now, LogLevel.INFO, "manus", "task ${task.id} mulai direncanakan")
    }
    /**
     * Setelah sebuah langkah selesai, tentukan langkah berikutnya. Bila langkah
     * berikutnya memakai tool berisiko -> catat pendingPermission dan berhenti.
     * Bila tidak -> tandai langkah itu RUNNING dan biarkan pelaksana melapor.
     * Bila tidak ada langkah tersisa -> task VERIFYING (14.22): COMPLETED
     * HANYA boleh keluar dari gerbang verifikasi (VerificationPassed),
     * bukan langsung dari sini. Ini yang membedakan "tool tidak error"
     * dengan "tujuan tercapai dengan bukti".
     */
    private fun advance(
        state: ManusUiState,
        task: AgentTask,
        clock: () -> Long
    ): ManusUiState {
        val now = clock()
        val next = task.nextStep
        return if (next == null) {
            val waiting = task.copy(
                status = TaskStatus.VERIFYING,
                updatedAt = now
            )
            replaceTask(state, waiting)
                .copy(pendingPermission = null)
                .log(now, LogLevel.INFO, "manus", "task ${task.id} menunggu verifikasi bukti: ${task.doneCount}/${task.steps.size} langkah")
        } else {
            val risk = next.tool?.risk() ?: RiskLevel.LOW
            if (risk.requiresApproval) {
                val gated = task.copy(status = TaskStatus.WAITING_PERMISSION, updatedAt = now)
                replaceTask(state, gated)
                    .copy(
                        pendingPermission = permissionRequestFor(
                            taskId = task.id,
                            stepId = next.id,
                            tool = next.tool!!,
                            reason = "langkah ${next.title} butuh izin",
                            risk = risk
                        )
                    )
                    .log(now, LogLevel.WARN, "manus", "izin diperlukan: ${next.tool!!.summary()} (risk=$risk)")
            } else {
                val started = task.copy(
                    steps = task.steps.map {
                        if (it.id == next.id) it.copy(status = StepStatus.RUNNING, startedAt = now) else it
                    },
                    status = TaskStatus.RUNNING,
                    updatedAt = now
                )
                replaceTask(state, started)
                    .copy(pendingPermission = null)
                    .log(now, LogLevel.DEBUG, "manus", "langkah ${next.id} berjalan")
            }
        }
    }
    private fun toolFinished(
        state: ManusUiState,
        event: ManusEvent.ToolFinished,
        clock: () -> Long
    ): ManusUiState {
        val now = clock()
        val task = state.tasks.firstOrNull { it.id == event.taskId } ?: return state
        val target = task.steps.firstOrNull { it.id == event.stepId } ?: return state
        val updatedTask = task.copy(
            steps = task.steps.map {
                if (it.id == event.stepId) {
                    it.copy(status = StepStatus.DONE, finishedAt = now, output = event.output)
                } else it
            },
            updatedAt = now
        )
        return advance(replaceTask(state, updatedTask), updatedTask, clock)
            .log(now, LogLevel.INFO, "manus", "langkah ${target.id} selesai: ${target.title}")
    }
    private fun toolFailed(
        state: ManusUiState,
        event: ManusEvent.ToolFailed,
        clock: () -> Long
    ): ManusUiState {
        val now = clock()
        val task = state.tasks.firstOrNull { it.id == event.taskId } ?: return state
        val target = task.steps.firstOrNull { it.id == event.stepId } ?: return state
        val updatedTask = task.copy(
            steps = task.steps.map {
                if (it.id == event.stepId) {
                    it.copy(status = StepStatus.FAILED, finishedAt = now, error = event.error)
                } else it
            },
            status = TaskStatus.FAILED,
            updatedAt = now
        )
        return replaceTask(state, updatedTask)
            .copy(pendingPermission = null)
            .log(now, LogLevel.ERROR, "manus", "langkah ${target.id} gagal: ${event.error}")
    }
    private fun requestPermission(
        state: ManusUiState,
        event: ManusEvent.RequestPermission,
        clock: () -> Long
    ): ManusUiState {
        val now = clock()
        val task = state.tasks.firstOrNull { it.id == event.taskId } ?: return state
        val gated = task.copy(status = TaskStatus.WAITING_PERMISSION, updatedAt = now)
        return replaceTask(state, gated)
            .copy(
                pendingPermission = permissionRequestFor(
                    taskId = event.taskId,
                    stepId = event.stepId,
                    tool = event.tool,
                    reason = event.reason,
                    risk = event.risk
                )
            )
            .log(now, LogLevel.WARN, "manus", "izin diminta: ${event.tool.summary()} (risk=${event.risk})")
    }
    private fun permissionGranted(
        state: ManusUiState,
        event: ManusEvent.PermissionGranted,
        clock: () -> Long
    ): ManusUiState {
        val now = clock()
        val req = state.pendingPermission ?: return state
        if (req.taskId != event.taskId) return state
        val task = state.tasks.firstOrNull { it.id == event.taskId } ?: return state
        val started = task.copy(
            steps = task.steps.map {
                if (it.id == req.stepId) it.copy(status = StepStatus.RUNNING, startedAt = now) else it
            },
            status = TaskStatus.RUNNING,
            updatedAt = now
        )
        return replaceTask(state, started)
            .copy(pendingPermission = null)
            .log(now, LogLevel.INFO, "manus", "izin diberikan untuk ${req.tool.summary()}")
    }
    /**
     * Penolakan izin = langkah dilewati, BUKAN task gagal. Fail-closed tetap
     * terjaga: tool berisiko itu TIDAK PERNAH dijalankan.
     */
    private fun permissionDenied(
        state: ManusUiState,
        event: ManusEvent.PermissionDenied,
        clock: () -> Long
    ): ManusUiState {
        val now = clock()
        val req = state.pendingPermission ?: return state
        if (req.taskId != event.taskId) return state
        val task = state.tasks.firstOrNull { it.id == event.taskId } ?: return state
        val skippedTask = task.copy(
            steps = task.steps.map {
                if (it.id == req.stepId) {
                    it.copy(status = StepStatus.SKIPPED, finishedAt = now, error = event.reason)
                } else it
            },
            updatedAt = now
        )
        return advance(replaceTask(state, skippedTask), skippedTask, clock)
            .log(now, LogLevel.WARN, "manus", "izin ditolak: ${event.reason} — langkah ${req.stepId} dilewati")
    }
    private fun selectTask(
        state: ManusUiState,
        event: ManusEvent.SelectTask,
        clock: () -> Long
    ): ManusUiState {
        val task = state.tasks.firstOrNull { it.id == event.taskId } ?: return state
        return state.copy(activeTaskId = task.id)
            .log(clock(), LogLevel.INFO, "manus", "membuka ulang tugas: ${task.goal}")
    }
    private fun steerTask(
        state: ManusUiState,
        event: ManusEvent.SteerTask,
        clock: () -> Long
    ): ManusUiState {
        val task = state.tasks.firstOrNull { it.id == event.taskId } ?: return state
        if (event.message.isBlank()) return state
        return state.log(clock(), LogLevel.INFO, "manus", "arahan user untuk ${task.id}: ${event.message}")
    }
    /**
     * Detail kartu izin dari satu tool call. Scope SELALU "once": satu izin
     * hanya berlaku untuk satu langkah ini, tidak pernah melebar ke langkah
     * lain (tidak ada opsi "ingat selamanya" di jalur Manus ini).
     */
    private fun permissionRequestFor(
        taskId: String,
        stepId: String,
        tool: ToolCall,
        reason: String,
        risk: RiskLevel
    ): PermissionRequest {
        val targets = if (tool.args.isEmpty()) listOf(tool.name)
        else tool.args.entries.map { "${it.key}=${it.value}" }
        val reversible = tool.name in setOf("write_file", "edit_file", "create_task", "file_write")
        val rollback = if (reversible) {
            "Bisa dibatalkan: file/tugas hasil langkah ini bisa dihapus manual setelah dibuat."
        } else {
            "TIDAK bisa dibatalkan otomatis — putuskan sebelum menekan Izinkan."
        }
        return PermissionRequest(
            taskId = taskId,
            stepId = stepId,
            tool = tool,
            reason = reason,
            risk = risk,
            targets = targets,
            scope = "once",
            reversible = reversible,
            rollback = rollback
        )
    }
    private fun pauseTask(
        state: ManusUiState,
        event: ManusEvent.PauseTask,
        clock: () -> Long
    ): ManusUiState {
        val now = clock()
        val task = state.tasks.firstOrNull { it.id == event.taskId } ?: return state
        if (!task.status.isActive || task.status == TaskStatus.WAITING_PERMISSION) return state
        val paused = task.copy(status = TaskStatus.PAUSED, updatedAt = now)
        return replaceTask(state, paused)
            .copy(pendingPermission = null)
            .log(now, LogLevel.INFO, "manus", "task ${task.id} dijeda")
    }
    private fun resumeTask(
        state: ManusUiState,
        event: ManusEvent.ResumeTask,
        clock: () -> Long
    ): ManusUiState {
        val now = clock()
        val task = state.tasks.firstOrNull { it.id == event.taskId } ?: return state
        if (task.status != TaskStatus.PAUSED) return state
        if (task.steps.any { it.status == StepStatus.RUNNING }) {
            val resumed = task.copy(status = TaskStatus.RUNNING, updatedAt = now)
            return replaceTask(state, resumed)
                .log(now, LogLevel.INFO, "manus", "task ${task.id} dilanjutkan")
        }
        val updated = task.copy(status = TaskStatus.RUNNING, updatedAt = now)
        return advance(replaceTask(state, updated), updated, clock)
    }
    private fun cancelTask(
        state: ManusUiState,
        event: ManusEvent.CancelTask,
        clock: () -> Long
    ): ManusUiState {
        val now = clock()
        val task = state.tasks.firstOrNull { it.id == event.taskId } ?: return state
        val cancelled = task.copy(status = TaskStatus.CANCELLED, updatedAt = now)
        return replaceTask(state, cancelled)
            .copy(pendingPermission = null)
            .log(now, LogLevel.WARN, "manus", "task ${task.id} dibatalkan: ${event.reason}")
    }
    private fun retryStep(
        state: ManusUiState,
        event: ManusEvent.RetryStep,
        clock: () -> Long
    ): ManusUiState {
        val now = clock()
        val task = state.tasks.firstOrNull { it.id == event.taskId } ?: return state
        val updated = task.copy(
            steps = task.steps.map {
                if (it.id == event.stepId) {
                    it.copy(status = StepStatus.PENDING, startedAt = 0L, finishedAt = 0L, error = null, output = null)
                } else it
            },
            status = TaskStatus.RUNNING,
            updatedAt = now
        )
        return advance(replaceTask(state, updated), updated, clock)
    }
    /**
     * 14.22: ganti rencana tugas IDLE. Hanya bila belum ada langkah yang
     * settled (belum ada eksekusi) — rencana yang sudah berjalan tidak
     * boleh ditimpa diam-diam. ID langkah dihitung ulang dari urutan baru.
     */
    private fun replacePlan(
        state: ManusUiState,
        event: ManusEvent.ReplacePlan,
        clock: () -> Long
    ): ManusUiState {
        val now = clock()
        val task = state.tasks.firstOrNull { it.id == event.taskId } ?: return state
        if (task.status != TaskStatus.IDLE) return state
        if (task.steps.any { it.isSettled }) return state
        val steps = event.plan.mapIndexed { index, title ->
            AgentStep(id = "${task.id}-s${index + 1}", title = title)
        }
        val updated = task.copy(
            steps = steps,
            planLabel = event.planLabel,
            plannerUsageTotal = event.usageTotal,
            plannerUsagePrompt = event.usagePrompt,
            plannerUsageCompletion = event.usageCompletion,
            tokensUsed = event.initialTokens,
            tokensReal = event.usageTotal != null,
            updatedAt = now
        )
        return replaceTask(state, updated)
            .log(now, LogLevel.INFO, "manus", "rencana ${task.id} diganti: ${steps.size} langkah (${event.planLabel})")
    }

    /** 14.27: perbarui angka token berjalan, tanpa mengubah status. */
    private fun usageUpdated(
        state: ManusUiState,
        event: ManusEvent.UsageUpdated,
        clock: () -> Long
    ): ManusUiState {
        val task = state.tasks.firstOrNull { it.id == event.taskId } ?: return state
        return replaceTask(state, task.copy(tokensUsed = event.tokensUsed, updatedAt = clock()))
    }

    /** 14.27: batas sesi → STOPPED + alasan eksplisit (lihat LimitReached). */
    private fun limitReached(
        state: ManusUiState,
        event: ManusEvent.LimitReached,
        clock: () -> Long
    ): ManusUiState {
        val now = clock()
        val task = state.tasks.firstOrNull { it.id == event.taskId } ?: return state
        if (!task.status.isActive) return state
        val stopped = task.copy(
            status = TaskStatus.STOPPED,
            stopReason = event.reason,
            updatedAt = now
        )
        return replaceTask(state, stopped)
            .copy(pendingPermission = null)
            .log(now, LogLevel.WARN, "manus", "task ${task.id} TERHENTI (batas sesi): ${event.reason}")
    }

    /** 14.22: hanya dari VERIFYING — bukti lolos, tugas sah COMPLETED. */
    private fun verificationPassed(
        state: ManusUiState,
        event: ManusEvent.VerificationPassed,
        clock: () -> Long
    ): ManusUiState {
        val now = clock()
        val task = state.tasks.firstOrNull { it.id == event.taskId } ?: return state
        if (task.status != TaskStatus.VERIFYING) return state
        val done = task.copy(
            status = TaskStatus.COMPLETED,
            verificationEvidence = event.evidence,
            updatedAt = now
        )
        return replaceTask(state, done)
            .log(now, LogLevel.INFO, "manus", "task ${task.id} selesai TERVERIFIKASI: ${event.evidence}")
    }

    /** 14.22: hanya dari VERIFYING — bukti gagal, tugas TERHENTI (STOPPED). */
    private fun verificationFailed(
        state: ManusUiState,
        event: ManusEvent.VerificationFailed,
        clock: () -> Long
    ): ManusUiState {
        val now = clock()
        val task = state.tasks.firstOrNull { it.id == event.taskId } ?: return state
        if (task.status != TaskStatus.VERIFYING) return state
        val stopped = task.copy(
            status = TaskStatus.STOPPED,
            stopReason = event.reason,
            updatedAt = now
        )
        return replaceTask(state, stopped)
            .log(now, LogLevel.WARN, "manus", "task ${task.id} TERHENTI: ${event.reason}")
    }

    /** 14.24: hanya dari VERIFYING — hasil sebagian, tugas SELESAI SEBAGIAN. */
    private fun verificationPartial(
        state: ManusUiState,
        event: ManusEvent.VerificationPartial,
        clock: () -> Long
    ): ManusUiState {
        val now = clock()
        val task = state.tasks.firstOrNull { it.id == event.taskId } ?: return state
        if (task.status != TaskStatus.VERIFYING) return state
        val partial = task.copy(
            status = TaskStatus.PARTIAL,
            partialReason = event.reason,
            missingItems = event.missing,
            updatedAt = now
        )
        return replaceTask(state, partial)
            .log(now, LogLevel.WARN, "manus", "task ${task.id} SELESAI SEBAGIAN: ${event.reason}")
    }

    /**
     * 14.22: lanjutkan tugas STOPPED. Tugas STOPPED selalu tanpa langkah
     * PENDING (VERIFYING hanya tercapai bila tidak ada langkah tersisa),
     * jadi advance() kembali ke VERIFYING — verifikasi ulang. Ini berguna
     * bila user sudah memperbaiki bukti secara manual (mis. menulis file
     * yang tadi gagal dibaca balik), lalu menekan "Lanjutkan".
     * Alasan berhenti + bukti lama dibersihkan untuk percobaan baru.
     * (Bila di masa depan STOPPED bisa punya langkah PENDING, advance()
     * yang sama akan memulai langkah itu / meminta izin bila berisiko.)
     */
    private fun resumeStopped(
        state: ManusUiState,
        event: ManusEvent.ResumeStopped,
        clock: () -> Long
    ): ManusUiState {
        val now = clock()
        val task = state.tasks.firstOrNull { it.id == event.taskId } ?: return state
        if (task.status != TaskStatus.STOPPED) return state
        val updated = task.copy(
            status = TaskStatus.RUNNING,
            stopReason = null,
            verificationEvidence = null,
            updatedAt = now
        )
        return advance(replaceTask(state, updated), updated, clock)
            .log(now, LogLevel.INFO, "manus", "task ${task.id} dilanjutkan dari TERHENTI")
    }

    private fun clearHistory(state: ManusUiState): ManusUiState =
        state.copy(tasks = state.tasks.filter { !it.isFinished })
    // --- util ----------------------------------------------------------------
    /** Ganti task berdasar id; kalau tidak ketemu, state tidak berubah. */
    private fun replaceTask(state: ManusUiState, task: AgentTask): ManusUiState =
        state.copy(tasks = state.tasks.map { if (it.id == task.id) task else it })
    /** Tambah satu baris log di kepala daftar (paling baru di depan). */
    private fun ManusUiState.log(
        at: Long,
        level: LogLevel,
        tag: String,
        message: String
    ): ManusUiState {
        val entry = LogEntry(id = nextLogId, timestamp = at, level = level, tag = tag, message = message)
        return copy(logs = (listOf(entry) + logs).take(200), nextLogId = nextLogId + 1)
    }
}

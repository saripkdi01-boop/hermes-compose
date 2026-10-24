package id.sukiapps.hermes.domain.manus
/**
 * Semua input ke state machine lewat event terketat. Tidak ada jalur lain yang
 * boleh mengubah ManusUiState — ini yang bikin perilaku agent bisa diprediksi
 * dan bisa dites tanpa Android.
 */
sealed interface ManusEvent {
    /** User menyerahkan goal baru. Membuat task IDLE + langkah rencana awal. */
    data class SubmitGoal(val goal: String, val plan: List<String>) : ManusEvent
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
    /** Izin ditolak: langkah diblokir, task ditandai gagal. */
    data class PermissionDenied(val taskId: String, val reason: String = "ditolak user") : ManusEvent
    data class PauseTask(val taskId: String) : ManusEvent
    data class ResumeTask(val taskId: String) : ManusEvent
    data class CancelTask(val taskId: String, val reason: String = "dibatalkan user") : ManusEvent
    data class RetryStep(val taskId: String, val stepId: String) : ManusEvent
    /** Bersihkan riwayat task yang sudah selesai. */
    data object ClearHistory : ManusEvent
}
/**
 * Pemetaan nama tool ke tingkat risiko. Sengaja TOP-LEVEL (bukan member
 * `object ManusReducer`) supaya bisa dipanggil dari reducer, dari UI, dan dari
 * unit test tanpa perlu scope object — member extension hanya terlihat di
 * dalam tubuh object-nya sendiri.
 *
 * Kategori:
 * - LOW: baca saja, aman dijalankan otomatis
 * - MEDIUM: menulis/mengirim, sebaiknya diawasi
 * - HIGH: menjalankan perintah / hapus file
 * - CRITICAL: deploy, merge PR, release, shell — wajib izin manusia
 */
fun ToolCall.risk(): RiskLevel {
    val low = setOf("read_file", "list_files", "web_search", "read_logs", "summarize")
    val medium = setOf("write_file", "send_message", "create_task", "edit_file")
    val high = setOf("run_command", "github_dispatch", "delete_file")
    val critical = setOf("deploy", "selfupgrade_merge", "selfupgrade_release", "shell_exec")
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
        is ManusEvent.StartTask -> startTask(state, event, clock)
        is ManusEvent.ToolFinished -> toolFinished(state, event, clock)
        is ManusEvent.ToolFailed -> toolFailed(state, event, clock)
        is ManusEvent.RequestPermission -> requestPermission(state, event, clock)
        is ManusEvent.PermissionGranted -> permissionGranted(state, event, clock)
        is ManusEvent.PermissionDenied -> permissionDenied(state, event, clock)
        is ManusEvent.PauseTask -> pauseTask(state, event, clock)
        is ManusEvent.ResumeTask -> resumeTask(state, event, clock)
        is ManusEvent.CancelTask -> cancelTask(state, event, clock)
        is ManusEvent.RetryStep -> retryStep(state, event, clock)
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
     * Bila tidak ada langkah tersisa -> task COMPLETED.
     */
    private fun advance(
        state: ManusUiState,
        task: AgentTask,
        clock: () -> Long
    ): ManusUiState {
        val now = clock()
        val next = task.nextStep
        return if (next == null) {
            val done = task.copy(
                status = TaskStatus.COMPLETED,
                updatedAt = now
            )
            replaceTask(state, done)
                .copy(pendingPermission = null)
                .log(now, LogLevel.INFO, "manus", "task ${task.id} selesai: ${task.doneCount}/${task.steps.size} langkah")
        } else {
            val risk = next.tool?.risk() ?: RiskLevel.LOW
            if (risk.requiresApproval) {
                val gated = task.copy(status = TaskStatus.WAITING_PERMISSION, updatedAt = now)
                replaceTask(state, gated)
                    .copy(
                        pendingPermission = PermissionRequest(
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
                pendingPermission = PermissionRequest(
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
    private fun permissionDenied(
        state: ManusUiState,
        event: ManusEvent.PermissionDenied,
        clock: () -> Long
    ): ManusUiState {
        val now = clock()
        val req = state.pendingPermission
        val task = state.tasks.firstOrNull { it.id == event.taskId } ?: return state
        val blockedTask = task.copy(
            steps = task.steps.map {
                if (it.id == req?.stepId) it.copy(status = StepStatus.BLOCKED, error = event.reason) else it
            },
            status = TaskStatus.FAILED,
            updatedAt = now
        )
        return replaceTask(state, blockedTask)
            .copy(pendingPermission = null)
            .log(now, LogLevel.ERROR, "manus", "izin ditolak: ${event.reason}")
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
        // FIX run #116: jangan panggil advance() membabi-buta. Bila langkah yang
        // di-pause masih RUNNING, advance() melihat nextStep==null (nextStep hanya
        // mencari PENDING) lalu salah menandai task COMPLETED padahal langkah itu
        // belum selesai. Lanjut = kembali ke RUNNING dan biarkan pelaksana melapor;
        // hanya bila tidak ada langkah RUNNING barulah advance() memulai PENDING.
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

package id.sukiapps.hermes.manus

/**
 * State keseluruhan layar Manus. Immutable; semua transaksi lewat [ManusReducer].
 */
data class ManusUiState(
    val tasks: List<AgentTask> = emptyList(),
    val activeTaskId: String? = null,
    val events: List<ManusEvent> = emptyList(),
    val pendingPermissions: List<PermissionRequest> = emptyList(),
    val composerText: String = "",
    val autoApprove: Boolean = false,
    val lastError: String? = null
) {
    val activeTask: AgentTask?
        get() = tasks.firstOrNull { it.id == activeTaskId }

    val awaitingCount: Int
        get() = pendingPermissions.count { it.isPending }
}

/**
 * Aksi yang bisa dikirim UI ke engine.
 */
sealed class ManusAction {
    data class ComposerChanged(val text: String) : ManusAction()
    data class SubmitGoal(val goal: String) : ManusAction()
    data class ToggleAutoApprove(val enabled: Boolean) : ManusAction()
    data class SelectTask(val taskId: String) : ManusAction()
    data class ResolvePermission(
        val requestId: String,
        val granted: Boolean
    ) : ManusAction()
    data object AdvanceActiveTask : ManusAction()
    data object CancelActiveTask : ManusAction()
    data class ToolFinished(
        val stepId: String,
        val toolRunId: String,
        val success: Boolean,
        val output: String
    ) : ManusAction()
    data object DismissError : ManusAction()
}

/**
 * Reducer murni. Tidak ada IO. Waktu datang dari [clock], id dari [ids],
 * supaya unit test JVM bisa menentukan keduanya.
 *
 * State machine:
 *   DRAFT --SubmitGoal--> PLANNING --Advance--> RUNNING (step punya rencana)
 *   RUNNING --Advance (step perlu izin)--> AWAITING_PERMISSION
 *   AWAITING_PERMISSION --ResolvePermission(true)--> RUNNING (run APPROVED)
 *   AWAITING_PERMISSION --ResolvePermission(false)--> FAILED
 *   RUNNING --Advance (step ada run APPROVED)--> step DONE, step berikutnya
 *   RUNNING --Advance (semua step terminal)--> DONE
 */
object ManusReducer {

    fun reduce(
        state: ManusUiState,
        action: ManusAction,
        clock: () -> Long = { 0L },
        ids: (String) -> String = { prefix -> "$prefix-${state.events.size}" }
    ): ManusUiState = when (action) {

        is ManusAction.ComposerChanged ->
            state.copy(composerText = action.text)

        is ManusAction.ToggleAutoApprove ->
            state.copy(autoApprove = action.enabled)

        is ManusAction.SelectTask ->
            state.copy(activeTaskId = action.taskId, lastError = null)

        is ManusAction.DismissError ->
            state.copy(lastError = null)

        is ManusAction.SubmitGoal -> {
            val goal = action.goal.trim()
            if (goal.isEmpty()) {
                state.copy(lastError = "Goal kosong.")
            } else {
                val now = clock()
                val task = AgentTask(
                    id = ids("task"),
                    title = goal.lineSequence().first().take(60),
                    goal = goal,
                    status = AgentTaskStatus.PLANNING,
                    steps = emptyList(),
                    createdAt = now,
                    updatedAt = now
                )
                val event = ManusEvent.TaskCreated(
                    id = ids("evt"),
                    at = now,
                    taskId = task.id,
                    title = task.title
                )
                state.copy(
                    tasks = listOf(task) + state.tasks,
                    activeTaskId = task.id,
                    composerText = "",
                    events = state.events + event,
                    lastError = null
                )
            }
        }

        is ManusAction.AdvanceActiveTask -> {
            val task = state.activeTask
            if (task == null) {
                state.copy(lastError = "Tidak ada task aktif.")
            } else {
                advance(state, task, clock, ids)
            }
        }

        is ManusAction.ResolvePermission -> {
            val req = state.pendingPermissions.firstOrNull { it.id == action.requestId }
            if (req == null) {
                state.copy(lastError = "Permintaan izin tidak ditemukan.")
            } else {
                val now = clock()
                val resolved = req.copy(granted = action.granted, decidedAt = now)
                val remaining = state.pendingPermissions - req
                val event = ManusEvent.PermissionResolved(
                    id = ids("evt"),
                    at = now,
                    requestId = req.id,
                    granted = action.granted
                )
                val tasks = if (action.granted) {
                    // Run yang menunggu jadi APPROVED; task balik RUNNING.
                    state.tasks.map { t ->
                        if (t.id != req.taskId) {
                            t
                        } else {
                            t.copy(
                                status = AgentTaskStatus.RUNNING,
                                updatedAt = now,
                                steps = t.steps.map { s ->
                                    if (s.id != req.stepId) {
                                        s
                                    } else {
                                        s.copy(
                                            status = StepStatus.RUNNING,
                                            toolRuns = s.toolRuns.map { r ->
                                                if (r.id == req.toolRunId) {
                                                    r.copy(
                                                        status = ToolRunStatus.APPROVED,
                                                        startedAt = now
                                                    )
                                                } else r
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    }
                } else {
                    // Ditolak: run DENIED, step FAILED, task FAILED.
                    state.tasks.map { t ->
                        if (t.id != req.taskId) {
                            t
                        } else {
                            t.copy(
                                status = AgentTaskStatus.FAILED,
                                finishedAt = now,
                                updatedAt = now,
                                steps = t.steps.map { s ->
                                    if (s.id != req.stepId) {
                                        s
                                    } else {
                                        s.copy(
                                            status = StepStatus.FAILED,
                                            error = "Izin ditolak user.",
                                            finishedAt = now,
                                            toolRuns = s.toolRuns.map { r ->
                                                if (r.id == req.toolRunId) {
                                                    r.copy(
                                                        status = ToolRunStatus.DENIED,
                                                        finishedAt = now
                                                    )
                                                } else r
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
                state.copy(
                    pendingPermissions = remaining,
                    tasks = tasks,
                    events = state.events + event,
                    lastError = null
                )
            }
        }

        is ManusAction.ToolFinished -> finishTool(state, action, clock, ids)

        is ManusAction.CancelActiveTask -> {
            val task = state.activeTask
            if (task == null) {
                state.copy(lastError = "Tidak ada task aktif.")
            } else {
                val now = clock()
                val terminalStepIds = task.steps.map { it.id }.toSet()
                val cancelled = task.copy(
                    status = AgentTaskStatus.CANCELLED,
                    finishedAt = now,
                    updatedAt = now,
                    steps = task.steps.map {
                        if (it.isTerminal) it
                        else it.copy(status = StepStatus.SKIPPED, finishedAt = now)
                    }
                )
                val event = ManusEvent.Note(
                    id = ids("evt"),
                    at = now,
                    text = "Task dibatalkan: ${task.title}"
                )
                state.copy(
                    tasks = state.tasks.map { if (it.id == task.id) cancelled else it },
                    events = state.events + event,
                    pendingPermissions = state.pendingPermissions.filterNot {
                        it.taskId == task.id || it.stepId in terminalStepIds
                    },
                    lastError = null
                )
            }
        }
    }

    /**
     * Tool dirapor selesai (baik dari eksekusi nyata maupun input user).
     * Step yang run-nya sukses jadi DONE; task lanjut ke step berikutnya.
     */
    private fun finishTool(
        state: ManusUiState,
        action: ManusAction.ToolFinished,
        clock: () -> Long,
        ids: (String) -> String
    ): ManusUiState {
        val now = clock()
        val owner = state.tasks.firstOrNull { t ->
            t.steps.any { it.id == action.stepId }
        } ?: return state.copy(lastError = "Step ${action.stepId} tidak ditemukan.")

        val step = owner.steps.first { it.id == action.stepId }
        val runName = step.toolRuns.firstOrNull { it.id == action.toolRunId }?.toolName
            ?: "unknown"

        val updatedStep = step.copy(
            status = if (action.success) StepStatus.DONE else StepStatus.FAILED,
            finishedAt = now,
            error = if (action.success) null else action.output.take(300),
            toolRuns = step.toolRuns.map { r ->
                if (r.id != action.toolRunId) {
                    r
                } else {
                    r.copy(
                        status = if (action.success) ToolRunStatus.SUCCESS
                        else ToolRunStatus.FAILED,
                        output = action.output,
                        startedAt = r.startedAt ?: now,
                        finishedAt = now
                    )
                }
            }
        )

        val allDone = owner.steps.all { it.id == action.stepId || it.isTerminal }
        val updatedTask = owner.copy(
            status = when {
                action.success && allDone -> AgentTaskStatus.DONE
                action.success -> AgentTaskStatus.RUNNING
                else -> AgentTaskStatus.FAILED
            },
            finishedAt = if (action.success && allDone) now else owner.finishedAt,
            updatedAt = now,
            steps = owner.steps.map { if (it.id == action.stepId) updatedStep else it }
        )

        val evTool = ManusEvent.ToolFinished(
            id = ids("evt"),
            at = now,
            toolRunId = action.toolRunId,
            toolName = runName,
            success = action.success,
            output = action.output.take(500)
        )
        val evStep = ManusEvent.StepFinished(
            id = ids("evt"),
            at = now,
            stepId = action.stepId,
            success = action.success
        )
        return state.copy(
            tasks = state.tasks.map { if (it.id == owner.id) updatedTask else it },
            events = state.events + listOf(evTool, evStep),
            lastError = null
        )
    }

    private fun advance(
        state: ManusUiState,
        task: AgentTask,
        clock: () -> Long,
        ids: (String) -> String
    ): ManusUiState {
        val now = clock()
        return when (task.status) {
            AgentTaskStatus.PLANNING -> {
                val planned = task.copy(
                    status = AgentTaskStatus.RUNNING,
                    steps = buildPlan(task.goal),
                    updatedAt = now
                )
                val event = ManusEvent.PlanReady(
                    id = ids("evt"),
                    at = now,
                    taskId = task.id,
                    stepCount = planned.steps.size
                )
                state.copy(
                    tasks = state.tasks.map { if (it.id == task.id) planned else it },
                    events = state.events + event
                )
            }

            AgentTaskStatus.AWAITING_PERMISSION ->
                state.copy(lastError = "Menunggu keputusan izin user.")

            AgentTaskStatus.RUNNING -> {
                val current = task.currentStep
                if (current == null) {
                    val done = task.copy(
                        status = AgentTaskStatus.DONE,
                        finishedAt = now,
                        updatedAt = now
                    )
                    val event = ManusEvent.Note(
                        id = ids("evt"),
                        at = now,
                        text = "Task selesai: ${task.title}"
                    )
                    state.copy(
                        tasks = state.tasks.map { if (it.id == task.id) done else it },
                        events = state.events + event
                    )
                } else {
                    val approvedRun = current.toolRuns.firstOrNull {
                        it.status == ToolRunStatus.APPROVED
                    }
                    if (approvedRun != null) {
                        // Sudah diizinkan user: eksekusi sekarang.
                        finishTool(
                            state,
                            ManusAction.ToolFinished(
                                stepId = current.id,
                                toolRunId = approvedRun.id,
                                success = true,
                                output = "OK: ${approvedRun.toolName} ${approvedRun.args}"
                            ),
                            clock,
                            ids
                        )
                    } else {
                        val alreadyProposed = current.toolRuns.isNotEmpty()
                        if (alreadyProposed) {
                            state.copy(
                                lastError = "Step \"${current.title}\" sudah punya run tertunda."
                            )
                        } else {
                            proposeTool(state, task, current, now, clock, ids)
                        }
                    }
                }
            }

            else -> state.copy(
                lastError = "Task tidak bisa dilanjutkan (status ${task.status})."
            )
        }
    }

    private fun proposeTool(
        state: ManusUiState,
        task: AgentTask,
        step: AgentStep,
        now: Long,
        clock: () -> Long,
        ids: (String) -> String
    ): ManusUiState {
        val toolName = toolForStep(step.title)
        val args = argsForStep(step.title)
        val scope = scopeForStep(step.title)
        val run = ToolRun(
            id = ids("run"),
            stepId = step.id,
            toolName = toolName,
            args = args,
            status = ToolRunStatus.PROPOSED
        )
        val started = task.copy(
            status = AgentTaskStatus.RUNNING,
            updatedAt = now,
            steps = task.steps.map {
                if (it.id != step.id) {
                    it
                } else {
                    it.copy(
                        status = StepStatus.RUNNING,
                        startedAt = now,
                        toolRuns = it.toolRuns + run
                    )
                }
            }
        )
        val evStart = ManusEvent.StepStarted(
            id = ids("evt"),
            at = now,
            stepId = step.id,
            title = step.title
        )
        val evTool = ManusEvent.ToolProposed(
            id = ids("evt"),
            at = now,
            stepId = step.id,
            toolName = toolName,
            args = args
        )
        // autoApprove tidak boleh melewati SECRETS.
        val autoOk = state.autoApprove && scope != PermissionScope.SECRETS
        return if (scope != null && !autoOk) {
            val req = PermissionRequest(
                id = ids("perm"),
                taskId = task.id,
                stepId = step.id,
                scope = scope,
                reason = "Step \"${step.title}\" butuh akses ${scope.name}.",
                toolRunId = run.id
            )
            val waiting = started.copy(status = AgentTaskStatus.AWAITING_PERMISSION)
            val evPerm = ManusEvent.PermissionRequested(
                id = ids("evt"),
                at = now,
                requestId = req.id,
                scope = scope,
                reason = req.reason
            )
            state.copy(
                tasks = state.tasks.map { if (it.id == task.id) waiting else it },
                pendingPermissions = state.pendingPermissions + req,
                events = state.events + listOf(evStart, evTool, evPerm)
            )
        } else {
            val approved = if (scope != null) {
                started.copy(
                    steps = started.steps.map {
                        if (it.id != step.id) {
                            it
                        } else {
                            it.copy(
                                toolRuns = it.toolRuns.map { r ->
                                    if (r.id != run.id) r
                                    else r.copy(
                                        status = ToolRunStatus.APPROVED,
                                        startedAt = now
                                    )
                                }
                            )
                        }
                    }
                )
            } else {
                started
            }
            state.copy(
                tasks = state.tasks.map { if (it.id == task.id) approved else it },
                events = state.events + listOf(evStart, evTool)
            )
        }
    }

    /**
     * Rencana deterministik dari goal teks. Satu baris = satu step.
     * Baris kosong dan baris komentar ('#') diabaikan.
     */
    fun buildPlan(goal: String): List<AgentStep> {
        val lines = goal.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
        val base = lines.ifEmpty { listOf(goal.trim()) }
        return base.mapIndexed { index, line ->
            AgentStep(
                id = "step-$index",
                title = line.take(60),
                detail = line
            )
        }
    }

    private fun toolForStep(title: String): String {
        val t = title.lowercase()
        return when {
            t.contains("baca") || t.contains("read") || t.contains("audit") -> "file_read"
            t.contains("tulis") || t.contains("write") || t.contains("buat") -> "file_write"
            t.contains("shell") || t.contains("jalankan") || t.contains("run") -> "shell_exec"
            t.contains("scan") -> "net_scan"
            t.contains("web") || t.contains("url") || t.contains("fetch") -> "web_fetch"
            t.contains("github") || t.contains("repo") || t.contains("pr") -> "github_write_file"
            else -> "note"
        }
    }

    private fun argsForStep(title: String): Map<String, String> {
        val t = title.lowercase()
        return when {
            t.contains("baca") || t.contains("read") || t.contains("audit") ->
                mapOf("path" to "docs/STATUS.md")
            t.contains("shell") || t.contains("jalankan") || t.contains("run") ->
                mapOf("command" to "ls -la")
            t.contains("scan") ->
                mapOf("host" to "127.0.0.1", "ports" to "80,443,8080")
            t.contains("web") || t.contains("url") ->
                mapOf("url" to "https://sukiapps.web.id")
            else -> mapOf("note" to title.take(120))
        }
    }

    private fun scopeForStep(title: String): PermissionScope? {
        val t = title.lowercase()
        return when {
            t.contains("shell") || t.contains("jalankan") || t.contains("run") -> PermissionScope.SHELL
            t.contains("tulis") || t.contains("write") || t.contains("buat") -> PermissionScope.FS_WRITE
            t.contains("baca") || t.contains("read") || t.contains("audit") -> PermissionScope.FS_READ
            t.contains("scan") -> PermissionScope.NETWORK
            t.contains("web") || t.contains("url") -> PermissionScope.NETWORK
            t.contains("github") || t.contains("repo") || t.contains("pr") -> PermissionScope.GITHUB
            t.contains("secret") || t.contains("token") || t.contains("key") -> PermissionScope.SECRETS
            else -> null
        }
    }
}

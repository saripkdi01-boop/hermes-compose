package id.sukiapps.hermes.manus

/**
 * State keseluruhan layar Manus. Immutable; semua transaksi lewat [reduce].
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
 * Reducer murni. Tidak ada IO, tidak ada waktu nyata kecuali dikirim
 * via [clock]. Dipakai VM maupun unit test.
 */
object ManusReducer {

    fun reduce(
        state: ManusUiState,
        action: ManusAction,
        clock: () -> Long = { 0L },
        ids: (String) -> String = { prefix -> "$prefix-${clock()}" }
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
                val tasks = state.tasks.map { t ->
                    if (t.id != req.stepId?.let { _ -> t.id } && t.id != req.stepId) {
                        t
                    } else if (action.granted) {
                        t
                    } else {
                        t.copy(
                            status = AgentTaskStatus.FAILED,
                            finishedAt = now,
                            steps = t.steps.map { s ->
                                if (s.id == req.stepId) {
                                    s.copy(
                                        status = StepStatus.FAILED,
                                        error = "Izin ditolak user.",
                                        finishedAt = now
                                    )
                                } else s
                            }
                        )
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

        is ManusAction.ToolFinished -> {
            val now = clock()
            val tasks = state.tasks.map { t ->
                val step = t.steps.firstOrNull { it.id == action.stepId } ?: return@map t
                val runs = step.toolRuns.map { r ->
                    if (r.id == action.toolRunId) {
                        r.copy(
                            status = if (action.success) ToolRunStatus.SUCCESS
                            else ToolRunStatus.FAILED,
                            output = action.output,
                            finishedAt = now
                        )
                    } else r
                }
                t.copy(
                    steps = t.steps.map {
                        if (it.id == action.stepId) it.copy(toolRuns = runs) else it
                    }
                )
            }
            val event = ManusEvent.ToolFinished(
                id = ids("evt"),
                at = now,
                toolRunId = action.toolRunId,
                toolName = tasks.firstNotNullOfOrNull { t ->
                    t.steps.firstOrNull { it.id == action.stepId }
                        ?.toolRuns?.firstOrNull { it.id == action.toolRunId }?.toolName
                } ?: "unknown",
                success = action.success,
                output = action.output
            )
            state.copy(
                tasks = tasks,
                events = state.events + event,
                lastError = null
            )
        }

        is ManusAction.CancelActiveTask -> {
            val task = state.activeTask
            if (task == null) {
                state.copy(lastError = "Tidak ada task aktif.")
            } else {
                val now = clock()
                val cancelled = task.copy(
                    status = AgentTaskStatus.CANCELLED,
                    finishedAt = now,
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
                    pendingPermissions = state.pendingPermissions.filter {
                        it.stepId !in cancelled.steps.map { s -> s.id }
                    },
                    lastError = null
                )
            }
        }
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
                val planned = planFromGoal(task, now)
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

            AgentTaskStatus.RUNNING, AgentTaskStatus.AWAITING_PERMISSION -> {
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
                    val run = ToolRun(
                        id = ids("run"),
                        stepId = current.id,
                        toolName = toolForStep(current.title),
                        args = argsForStep(current.title),
                        status = ToolRunStatus.PROPOSED
                    )
                    val needed = scopeForStep(current.title)
                    val withRun = task.copy(
                        status = AgentTaskStatus.RUNNING,
                        updatedAt = now,
                        steps = task.steps.map {
                            if (it.id == current.id) {
                                it.copy(
                                    status = StepStatus.RUNNING,
                                    startedAt = now,
                                    toolRuns = it.toolRuns + run
                                )
                            } else it
                        }
                    )
                    if (needed != null) {
                        val req = PermissionRequest(
                            id = ids("perm"),
                            scope = needed,
                            reason = "Step \"${current.title}\" butuh akses ${needed.name}.",
                            toolRunId = run.id,
                            stepId = current.id
                        )
                        val awaiting = withRun.copy(status = AgentTaskStatus.AWAITING_PERMISSION)
                        val evProposed = ManusEvent.ToolProposed(
                            id = ids("evt"),
                            at = now,
                            stepId = current.id,
                            toolName = run.toolName,
                            args = run.args
                        )
                        val evPerm = ManusEvent.PermissionRequested(
                            id = ids("evt"),
                            at = now,
                            requestId = req.id,
                            scope = needed,
                            reason = req.reason
                        )
                        state.copy(
                            tasks = state.tasks.map { if (it.id == task.id) awaiting else it },
                            pendingPermissions = state.pendingPermissions + req,
                            events = state.events + listOf(evProposed, evPerm)
                        )
                    } else {
                        val evStart = ManusEvent.StepStarted(
                            id = ids("evt"),
                            at = now,
                            stepId = current.id,
                            title = current.title
                        )
                        val evProposed = ManusEvent.ToolProposed(
                            id = ids("evt"),
                            at = now,
                            stepId = current.id,
                            toolName = run.toolName,
                            args = run.args
                        )
                        state.copy(
                            tasks = state.tasks.map {
                                if (it.id == task.id) withRun else it
                            },
                            events = state.events + listOf(evStart, evProposed)
                        )
                    }
                }
            }

            else -> state.copy(lastError = "Task tidak bisa dilanjutkan (status ${task.status}).")
        }
    }

    private fun planFromGoal(task: AgentTask, now: Long): AgentTask {
        val plan = buildPlan(task.goal)
        return task.copy(
            status = AgentTaskStatus.RUNNING,
            steps = plan,
            updatedAt = now
        )
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
            t.contains("scan") || t.contains("port") -> "net_scan"
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
            else -> null
        }
    }
}

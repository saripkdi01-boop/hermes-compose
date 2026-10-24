package id.sukiapps.hermes.domain.manus

/**
 * State engine Manus — reducer deterministik untuk seluruh workspace agent.
 *
 * Kontrak:
 * - [reduce] murni: state lama + event -> state baru. Tanpa side effect,
 *   tanpa IO, tanpa waktu — bisa di-unit-test penuh di JVM.
 * - Timeline [AgentEvent] append-only: jejak audit lengkap, urut waktu.
 * - Step tidak bisa lompat status: hanya transisi yang wajar yang diizinkan,
 *   sisanya mengembalikan state tanpa perubahan (guard diam).
 */
data class ManusState(
    val tasks: List<ManusTask> = emptyList(),
    val events: List<AgentEvent> = emptyList(),
    val pendingPermissions: List<PermissionRequest> = emptyList()
) {
    val activeTask: ManusTask?
        get() = tasks.firstOrNull {
            it.status == TaskStatus.RUNNING || it.status == TaskStatus.BLOCKED
        }
}

/** Satu baris jejak waktu. Append-only, tidak pernah diedit/dihapus. */
data class AgentEvent(
    val id: Long,
    val taskId: String,
    val text: String,
    val at: Long
)

/** Event yang masuk ke reducer. */
sealed interface ManusEvent {
    data class TaskCreated(val task: ManusTask) : ManusEvent
    data class StepStarted(val taskId: String, val stepId: String, val at: Long) : ManusEvent
    data class StepLogged(val taskId: String, val stepId: String, val line: String, val at: Long) : ManusEvent
    data class StepFinished(val taskId: String, val stepId: String, val ok: Boolean, val at: Long) : ManusEvent
    data class PermissionRaised(val request: PermissionRequest) : ManusEvent
    data class PermissionResolved(val id: String, val approved: Boolean, val at: Long) : ManusEvent
}

object ManusStateEngine {

    fun reduce(prev: ManusState, event: ManusEvent): ManusState = when (event) {
        is ManusEvent.TaskCreated ->
            prev.copy(
                tasks = prev.tasks + event.task,
                events = prev.events + AgentEvent(
                    id = nextId(prev),
                    taskId = event.task.id,
                    text = "task dibuat: ${event.task.title}",
                    at = event.task.createdAt
                )
            )

        is ManusEvent.StepStarted -> prev.mutateTask(event.taskId) { task ->
            val step = task.steps.firstOrNull { it.id == event.stepId } ?: return@mutateTask task
            if (step.status != StepStatus.PENDING && step.status != StepStatus.BLOCKED) task
            else task.mapStep(event.stepId) { it.copy(status = StepStatus.RUNNING) }
        }.logged(prev, event.taskId, "step mulai: ${labelOf(prev, event.taskId, event.stepId)}", event.at)

        is ManusEvent.StepLogged -> prev.mutateTask(event.taskId) { task ->
            task.mapStep(event.stepId) { it.copy(log = it.log + event.line) }
        }

        is ManusEvent.StepFinished -> prev.mutateTask(event.taskId) { task ->
            val target = task.steps.firstOrNull { it.id == event.stepId } ?: return@mutateTask task
            if (target.isTerminal) task
            else {
                val mapped = task.mapStep(event.stepId) {
                    it.copy(status = if (event.ok) StepStatus.DONE else StepStatus.FAILED)
                }
                val allDone = mapped.steps.isNotEmpty() && mapped.steps.all { it.isTerminal }
                if (allDone) {
                    mapped.copy(
                        status = if (mapped.steps.any { it.status == StepStatus.FAILED })
                            TaskStatus.BLOCKED else TaskStatus.DONE
                    )
                } else mapped
            }
        }.logged(prev, event.taskId, "step selesai (${if (event.ok) "ok" else "gagal"})", event.at)

        is ManusEvent.PermissionRaised -> prev.copy(
            pendingPermissions = prev.pendingPermissions + event.request,
            events = prev.events + AgentEvent(
                id = nextId(prev),
                taskId = "-",
                text = "izin diminta: ${event.request.action} [${event.request.scope}]",
                at = event.request.requestedAt
            )
        )

        is ManusEvent.PermissionResolved -> {
            val target = prev.pendingPermissions.firstOrNull { it.id == event.id }
                ?: return prev
            val resolved = target.copy(
                status = if (event.approved) PermissionStatus.APPROVED
                         else PermissionStatus.DENIED
            )
            prev.copy(
                pendingPermissions = prev.pendingPermissions - target,
                events = prev.events + AgentEvent(
                    id = nextId(prev),
                    taskId = "-",
                    text = "izin ${if (event.approved) "disetujui" else "ditolak"}: ${resolved.action}",
                    at = event.at
                )
            )
        }
    }

    /** Snapshot awal untuk demo — task contoh dengan 4 step siap dieksekusi. */
    fun seed(): ManusState = reduce(
        ManusState(),
        ManusEvent.TaskCreated(
            ManusTask(
                id = "task-demo-1",
                title = "Audit UI Hermes",
                detail = "Petakan layar ada, temukan celah, usulkan upgrade maksimal.",
                steps = listOf(
                    ManusStep("s1", "Petakan layar & navigasi"),
                    ManusStep("s2", "Temukan celah UI/UX"),
                    ManusStep("s3", "Susun rencana upgrade"),
                    ManusStep("s4", "Eksekusi PR")
                )
            )
        )
    )

    private fun nextId(prev: ManusState): Long =
        (prev.events.lastOrNull()?.id ?: 0L) + 1L

    private fun labelOf(state: ManusState, taskId: String, stepId: String): String =
        state.tasks.firstOrNull { it.id == taskId }
            ?.steps?.firstOrNull { it.id == stepId }?.label ?: stepId

    private inline fun ManusState.mutateTask(
        taskId: String,
        crossinline transform: (ManusTask) -> ManusTask
    ): ManusState = copy(
        tasks = tasks.map { if (it.id == taskId) transform(it) else it }
    )

    private inline fun ManusTask.mapStep(
        stepId: String,
        crossinline transform: (ManusStep) -> ManusStep
    ): ManusTask = copy(steps = steps.map { if (it.id == stepId) transform(it) else it })

    private inline fun ManusState.logged(
        prev: ManusState,
        taskId: String,
        text: String,
        at: Long
    ): ManusState = copy(
        events = events + AgentEvent(nextId(prev), taskId, text, at)
    )
}

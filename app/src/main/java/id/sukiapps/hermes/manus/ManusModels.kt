package id.sukiapps.hermes.manus

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Status agregat sebuah task agent.
 */
@Serializable
enum class AgentTaskStatus {
    @SerialName("draft") DRAFT,
    @SerialName("planning") PLANNING,
    @SerialName("running") RUNNING,
    @SerialName("awaiting") AWAITING_PERMISSION,
    @SerialName("done") DONE,
    @SerialName("failed") FAILED,
    @SerialName("cancelled") CANCELLED
}

/**
 * Status satu step di dalam rencana task.
 */
@Serializable
enum class StepStatus {
    @SerialName("pending") PENDING,
    @SerialName("running") RUNNING,
    @SerialName("awaiting") AWAITING_PERMISSION,
    @SerialName("done") DONE,
    @SerialName("failed") FAILED,
    @SerialName("skipped") SKIPPED
}

/**
 * Scope izin yang diminta agent sebelum melakukan aksi berdampak.
 * Setiap scope ditampilkan mentah kepada user; tidak ada "allow all" tersembunyi.
 */
@Serializable
enum class PermissionScope {
    @SerialName("fs_read") FS_READ,
    @SerialName("fs_write") FS_WRITE,
    @SerialName("network") NETWORK,
    @SerialName("shell") SHELL,
    @SerialName("secrets") SECRETS,
    @SerialName("github") GITHUB
}

/**
 * Status eksekusi sebuah tool run.
 */
@Serializable
enum class ToolRunStatus {
    @SerialName("proposed") PROPOSED,
    @SerialName("approved") APPROVED,
    @SerialName("running") RUNNING,
    @SerialName("success") SUCCESS,
    @SerialName("failed") FAILED,
    @SerialName("denied") DENIED,
    @SerialName("cancelled") CANCELLED
}

/**
 * Satu step dalam rencana task.
 */
@Serializable
data class AgentStep(
    val id: String,
    val title: String,
    val detail: String,
    val status: StepStatus = StepStatus.PENDING,
    val toolRuns: List<ToolRun> = emptyList(),
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val error: String? = null
) {
    val isTerminal: Boolean
        get() = status == StepStatus.DONE ||
            status == StepStatus.FAILED ||
            status == StepStatus.SKIPPED
}

/**
 * Satu kali pemanggilan tool oleh agent.
 */
@Serializable
data class ToolRun(
    val id: String,
    val stepId: String,
    val toolName: String,
    val args: Map<String, String> = emptyMap(),
    val status: ToolRunStatus = ToolRunStatus.PROPOSED,
    val output: String = "",
    val exitCode: Int? = null,
    val startedAt: Long? = null,
    val finishedAt: Long? = null
)

/**
 * Permintaan izin yang menunggu keputusan user.
 * [taskId] + [stepId] eksplisit supaya reducer tidak perlu menebak
 * task pemilik step.
 */
@Serializable
data class PermissionRequest(
    val id: String,
    val taskId: String,
    val stepId: String,
    val scope: PermissionScope,
    val reason: String,
    val toolRunId: String? = null,
    val granted: Boolean? = null,
    val decidedAt: Long? = null
) {
    val isPending: Boolean get() = granted == null
}

/**
 * Task agent lengkap dengan rencana step.
 */
@Serializable
data class AgentTask(
    val id: String,
    val title: String,
    val goal: String,
    val status: AgentTaskStatus = AgentTaskStatus.DRAFT,
    val steps: List<AgentStep> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val finishedAt: Long? = null
) {
    val progress: Float
        get() = if (steps.isEmpty()) 0f
        else steps.count { it.isTerminal }.toFloat() / steps.size

    val currentStep: AgentStep?
        get() = steps.firstOrNull { !it.isTerminal }
}

/**
 * Event pada timeline task. Append-only, untuk render kronologi
 * yang bisa diaudit user.
 */
@Serializable
sealed class ManusEvent {
    abstract val id: String
    abstract val at: Long

    @Serializable
    @SerialName("task_created")
    data class TaskCreated(
        override val id: String,
        override val at: Long,
        val taskId: String,
        val title: String
    ) : ManusEvent()

    @Serializable
    @SerialName("plan_ready")
    data class PlanReady(
        override val id: String,
        override val at: Long,
        val taskId: String,
        val stepCount: Int
    ) : ManusEvent()

    @Serializable
    @SerialName("step_started")
    data class StepStarted(
        override val id: String,
        override val at: Long,
        val stepId: String,
        val title: String
    ) : ManusEvent()

    @Serializable
    @SerialName("tool_proposed")
    data class ToolProposed(
        override val id: String,
        override val at: Long,
        val stepId: String,
        val toolName: String,
        val args: Map<String, String>
    ) : ManusEvent()

    @Serializable
    @SerialName("permission_requested")
    data class PermissionRequested(
        override val id: String,
        override val at: Long,
        val requestId: String,
        val scope: PermissionScope,
        val reason: String
    ) : ManusEvent()

    @Serializable
    @SerialName("permission_resolved")
    data class PermissionResolved(
        override val id: String,
        override val at: Long,
        val requestId: String,
        val granted: Boolean
    ) : ManusEvent()

    @Serializable
    @SerialName("tool_finished")
    data class ToolFinished(
        override val id: String,
        override val at: Long,
        val toolRunId: String,
        val toolName: String,
        val success: Boolean,
        val output: String
    ) : ManusEvent()

    @Serializable
    @SerialName("step_finished")
    data class StepFinished(
        override val id: String,
        override val at: Long,
        val stepId: String,
        val success: Boolean
    ) : ManusEvent()

    @Serializable
    @SerialName("note")
    data class Note(
        override val id: String,
        override val at: Long,
        val text: String
    ) : ManusEvent()
}

package id.sukiapps.hermes.domain.manus

import kotlinx.serialization.Serializable

/**
 * Siklus hidup satu tugas agent. Append-only: status hanya maju, tidak pernah
 * berjalan mundar ke status yang sudah ditinggalkan.
 */
@Serializable
enum class TaskStatus {
    IDLE,
    PLANNING,
    RUNNING,
    WAITING_PERMISSION,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED;

    /** True bila agent masih boleh menerima event baru. */
    val isActive: Boolean
        get() = this == PLANNING || this == RUNNING || this == WAITING_PERMISSION || this == PAUSED
}

@Serializable
enum class StepStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED,
    BLOCKED
}

/**
 * Tingkat risiko sebuah aksi. Menentukan apakah agent boleh jalan sendiri atau
 * wajib minta izin manusia lebih dulu.
 */
@Serializable
enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    /** LOW aman dijalankan otomatis. Sisanya menunggu persetujuan. */
    val requiresApproval: Boolean
        get() = this != LOW
}

/** Satu pemanggilan tool beserta argumennya. Argumen sengaja flat string. */
@Serializable
data class ToolCall(
    val name: String,
    val args: Map<String, String> = emptyMap()
) {
    /** Ringkasan pendek untuk log dan kartu UI. */
    fun summary(): String =
        if (args.isEmpty()) name
        else "$name(${args.entries.joinToString(", ") { "${it.key}=${it.value}" }})"
}

/**
 * Satu langkah dalam rencana tugas. Immutable — perubahan status dibuat lewat
 * copy() oleh reducer, bukan mutasi di tempat.
 */
@Serializable
data class AgentStep(
    val id: String,
    val title: String,
    val tool: ToolCall? = null,
    val status: StepStatus = StepStatus.PENDING,
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
    val output: String? = null,
    val error: String? = null
) {
    val isSettled: Boolean
        get() = status == StepStatus.DONE || status == StepStatus.FAILED
}

/**
 * Unit kerja utama: satu tujuan, satu daftar langkah, satu status agregat.
 */
@Serializable
data class AgentTask(
    val id: String,
    val goal: String,
    val steps: List<AgentStep> = emptyList(),
    val status: TaskStatus = TaskStatus.IDLE,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    /** Progress 0f..1f dari langkah yang sudah selesai. */
    val progress: Float
        get() = if (steps.isEmpty()) 0f
        else steps.count { it.isSettled }.toFloat() / steps.size.toFloat()

    val doneCount: Int
        get() = steps.count { it.status == StepStatus.DONE }

    /** Langkah pertama yang masih PENDING, atau null kalau semua sudah lewat. */
    val nextStep: AgentStep?
        get() = steps.firstOrNull { it.status == StepStatus.PENDING }

    val isFinished: Boolean
        get() = status == TaskStatus.COMPLETED ||
            status == TaskStatus.FAILED ||
            status == TaskStatus.CANCELLED
}

/**
 * Permintaan izin dari agent ke manusia. Ditampilkan sebagai dialog blocking;
 * agent tidak lanjut sebelum dijawui.
 */
@Serializable
data class PermissionRequest(
    val taskId: String,
    val stepId: String,
    val tool: ToolCall,
    val reason: String,
    val risk: RiskLevel
)

/** Level keparahan untuk baris log. */
@Serializable
enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

/**
 * Satu baris di timeline aktivitas. Append-only: daftar log hanya ditambah di
 * kepala, tidak pernah diedit setelah masuk.
 */
@Serializable
data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
)

/**
 * Snapshot tunggal yang dirender UI. Selalu dibuat lewat reducer supaya tidak
 * ada state tersembunyi di luar siklus event.
 */
@Serializable
data class ManusUiState(
    val tasks: List<AgentTask> = emptyList(),
    val activeTaskId: String? = null,
    val pendingPermission: PermissionRequest? = null,
    val logs: List<LogEntry> = emptyList(),
    val running: Boolean = false,
    val nextLogId: Long = 1L,
    val nextStepSeq: Long = 1L
) {
    val activeTask: AgentTask?
        get() = tasks.firstOrNull { it.id == activeTaskId }

    val history: List<AgentTask>
        get() = tasks.filter { it.isFinished }

    val queued: List<AgentTask>
        get() = tasks.filter { it.status.isActive }

    companion object {
        fun empty(): ManusUiState = ManusUiState()
    }
}

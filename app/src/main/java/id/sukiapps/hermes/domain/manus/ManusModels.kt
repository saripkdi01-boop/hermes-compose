package id.sukiapps.hermes.domain.manus

import kotlinx.serialization.Serializable

/**
 * Domain Manus — model inti untuk workspace agent (task, step, permission).
 * Murni Kotlin + kotlinx.serialization; tanpa dependency Android agar bisa
 * di-unit-test di JVM dan dipakai bersama oleh ViewModel maupun state engine.
 */

/** Tugas agent: satu unit kerja yang bisa dipecah jadi beberapa [ManusStep]. */
@Serializable
data class ManusTask(
    val id: String,
    val title: String,
    val detail: String = "",
    val status: TaskStatus = TaskStatus.PLANNING,
    val steps: List<ManusStep> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Progress 0f..1f dari step yang sudah selesai. Nol kalau belum ada step. */
    val progress: Float
        get() = if (steps.isEmpty()) 0f
                else steps.count { it.status == StepStatus.DONE }.toFloat() / steps.size
}

@Serializable
enum class TaskStatus { PLANNING, RUNNING, BLOCKED, DONE, CANCELLED }

/**
 * Satu langkah konkret di dalam [ManusTask].
 * [log] append-only supaya jejak eksekusi bisa diaudit penuh.
 */
@Serializable
data class ManusStep(
    val id: String,
    val label: String,
    val status: StepStatus = StepStatus.PENDING,
    val toolId: String? = null,
    val log: List<String> = emptyList()
) {
    val isTerminal: Boolean
        get() = status == StepStatus.DONE || status == StepStatus.FAILED
}

@Serializable
enum class StepStatus { PENDING, RUNNING, BLOCKED, DONE, FAILED }

/**
 * Permintaan izin untuk aksi sensitif. Satu pintu: semua aksi berisiko
 * harus punya [PermissionRequest] dan di-approve lewat UI sebelum jalan.
 */
@Serializable
data class PermissionRequest(
    val id: String,
    val action: String,
    val scope: PermissionScope,
    val reason: String = "",
    val status: PermissionStatus = PermissionStatus.PENDING,
    val requestedAt: Long = System.currentTimeMillis()
)

@Serializable
enum class PermissionScope { READ_ONLY, WRITE_LOCAL, NETWORK, SHELL, PAYMENT, SYSTEM }

@Serializable
enum class PermissionStatus { PENDING, APPROVED, DENIED, EXPIRED }

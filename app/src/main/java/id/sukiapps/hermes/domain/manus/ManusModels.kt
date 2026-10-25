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
    /** Semua langkah habis, menunggu gerbang verifikasi bukti (14.22). */
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED,
    /**
     * 14.24: verifikasi jujur. Terminal, tapi BUKAN COMPLETED: tugas
     * berjalan sampai habis, namun hasilnya tidak cukup membuktikan goal
     * tercapai (mis. audit tanpa temuan, atau hanya langkah persiapan
     * yang selesai). `partialReason` + `missingItems` menjelaskan apa
     * yang kurang — kartu TIDAK boleh menampilkan TERVERIFIKASI untuk
     * status ini. User memulai tugas baru untuk melengkapi, bukan
     * menekan Lanjutkan (verifikasi ulang tidak mengubah bukti yang ada).
     */
    PARTIAL,
    /**
     * 14.22: verifikasi bukti GAGAL. Terminal, tapi bukan FAILED biasa:
     * capaian tetap tercatat dan user bisa menekan "Lanjutkan" untuk
     * mencoba lagi (verifikasi ulang / langkah tersisa).
     */
    STOPPED;

    /** True bila agent masih boleh menerima event baru. */
    val isActive: Boolean
        get() = this == PLANNING || this == RUNNING || this == WAITING_PERMISSION ||
            this == PAUSED || this == VERIFYING
}

@Serializable
enum class StepStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED,
    BLOCKED,
    /** Ditolak user di kartu izin: langkah dilewati, tugas LANJUT (bukan gagal). */
    SKIPPED
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
    val error: String? = null,
    /** Bukti langkah untuk panel Komputer: penjelasan, sumber, dan file hasil. */
    val detail: String? = null,
    val sources: List<String> = emptyList(),
    val artifacts: List<String> = emptyList(),
    /**
     * 14.24: true bila langkah ini HANYA persiapan (snapshot lingkungan,
     * riset web generik) — bukan hasil yang menjawab goal. Langkah
     * persiapan tidak dihitung "selesai" oleh kartu/ringkasan dan tidak
     * dihitung sebagai bukti hasil oleh TaskVerifier. Default false
     * supaya simpanan lama (tanpa field ini) tetap terbaca.
     */
    val isPreparation: Boolean = false
) {
    val isSettled: Boolean
        get() = status == StepStatus.DONE || status == StepStatus.FAILED ||
            status == StepStatus.SKIPPED
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
    val updatedAt: Long = 0L,
    /** True hanya untuk tugas contoh bawaan (seed) — UI menandainya CONTOH. */
    val isDemo: Boolean = false,
    /**
     * 14.22: asal rencana, ditampilkan di UI supaya user tahu apakah
     * rencana dibuat LLM atau fallback kata kunci. Contoh: "LLM",
     * "rencana fallback — tanpa LLM (alasan)". Kosong = rencana lama /
     * manual (kompatibel dengan simpanan sebelum 14.22).
     */
    val planLabel: String = "",
    /** 14.22: bukti verifikasi, hanya diisi bila COMPLETED lewat gerbang. */
    val verificationEvidence: String? = null,
    /** 14.22: alasan verifikasi gagal, hanya diisi bila STOPPED. */
    val stopReason: String? = null,
    /** 14.24: alasan status PARTIAL (verifikasi jujur). */
    val partialReason: String? = null,
    /** 14.24: daftar yang kurang agar tugas PARTIAL bisa disebut selesai. */
    val missingItems: List<String> = emptyList()
) {
    /** Progress 0f..1f dari langkah yang sudah selesai. */
    val progress: Float
        get() = if (steps.isEmpty()) 0f
        else steps.count { it.isSettled }.toFloat() / steps.size.toFloat()

    val doneCount: Int
        get() = steps.count { it.status == StepStatus.DONE }

    /**
     * 14.24: hitungan JUJUR untuk kartu/ringkasan/verifikasi. Langkah
     * persiapan yang DONE tidak dihitung sebagai hasil; langkah manual
     * (tanpa tool) tetap dihitung karena itu kerja manusia, bukan filler.
     */
    val resultDoneCount: Int
        get() = steps.count {
            it.status == StepStatus.DONE && !it.isPreparation
        }

    /** Jumlah langkah hasil (semua langkah minus persiapan). */
    val resultStepCount: Int
        get() = steps.count { !it.isPreparation }

    /** Jumlah langkah persiapan (DONE maupun tidak). */
    val preparationCount: Int
        get() = steps.count { it.isPreparation }

    /** True bila rencana berasal dari fallback kata kunci (tanpa LLM). */
    val isFallbackPlan: Boolean
        get() = planLabel.startsWith("rencana fallback")

    /** Langkah pertama yang masih PENDING, atau null kalau semua sudah lewat. */
    val nextStep: AgentStep?
        get() = steps.firstOrNull { it.status == StepStatus.PENDING }

    val isFinished: Boolean
        get() = status == TaskStatus.COMPLETED ||
            status == TaskStatus.FAILED ||
            status == TaskStatus.CANCELLED ||
            status == TaskStatus.STOPPED ||
            status == TaskStatus.PARTIAL
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
    val risk: RiskLevel,
    /** Target persis yang terdampak (dari argumen tool); kosong = nama tool. */
    val targets: List<String> = emptyList(),
    /** Scope izin. Satu-satunya nilai yang didukung: "once" (sekali saja). */
    val scope: String = "once",
    /** Apakah aksi bisa dibatalkan setelah dijalankan. */
    val reversible: Boolean = false,
    /** Penjelasan rollback dalam bahasa manusia. */
    val rollback: String? = null
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

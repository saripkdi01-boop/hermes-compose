package id.sukiapps.hermes.ui.chatv3

/**
 * Status streaming AI per pesan.
 */
enum class V3StreamState {
    IDLE,       // tidak streaming
    TYPING,     // titik-titik "mengetik..."
    STREAMING,  // token mengalir
    STOPPED,    // dihentikan user — teks parsial dipertahankan
    FAILED,     // gagal — bubble merah + tombol coba lagi
    DONE        // selesai
}

data class V3StreamInfo(
    val messageId: String,
    val state: V3StreamState = V3StreamState.IDLE,
    val partialText: String = "",
    val elapsedMs: Long = 0L,
    val error: String? = null
)

/**
 * Status progresif Tanya Upin.
 */
enum class V3UpinStage {
    QUEUED,      // Mengantre
    PROCESSING,  // Upin memproses
    DONE,        // Selesai
    CANCELLED,
    FAILED
}

data class V3UpinJob(
    val jobId: String,
    val stage: V3UpinStage = V3UpinStage.QUEUED,
    val elapsedMs: Long = 0L,
    val error: String? = null
)

fun v3UpinStageLabel(stage: V3UpinStage): String = when (stage) {
    V3UpinStage.QUEUED -> "Mengantre"
    V3UpinStage.PROCESSING -> "Upin memproses"
    V3UpinStage.DONE -> "Selesai"
    V3UpinStage.CANCELLED -> "Dibatalkan"
    V3UpinStage.FAILED -> "Tanya Upin gagal — coba lagi"
}

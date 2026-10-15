package id.sukiapps.hermes.domain

import kotlinx.coroutines.flow.Flow

/**
 * Kontrak provider AI — versi suspend/Flow dari legacy AIProvider (callback).
 * Implementasi: ChatRemoteRepository (Jerouter, OpenAI-compatible).
 */
interface AIProvider {
    /** Daftar id model dari GET {base}/models. */
    suspend fun models(): List<String>

    /**
     * Chat streaming. Mengalirkan token incremental; selesai saat [DONE].
     * Pembatalan: batalkan coroutine pengoleksi (call HTTP ikut dibatalkan).
     */
    fun chatStream(request: ChatRequest): Flow<String>
}

/** Parameter satu request chat. */
data class ChatRequest(
    val model: String,
    val systemPrompt: String,
    val history: List<ChatMessage>,
    val prompt: String,
    val imageBase64s: List<String> = emptyList(),
    val temperature: Float? = null,
    val topP: Float? = null,
    val maxTokens: Int? = null
)

/** Satu pesan riwayat untuk request. */
data class ChatMessage(
    val role: String, // "user" | "assistant" | "system"
    val content: String
)

/** Error HTTP yang sudah dipetakan ke pesan Indonesia. */
class AIHttpException(message: String) : Exception(message)

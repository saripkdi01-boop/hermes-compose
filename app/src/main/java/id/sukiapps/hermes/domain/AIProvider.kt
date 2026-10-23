package id.sukiapps.hermes.domain

import kotlinx.coroutines.flow.Flow

/**
 * Kontrak provider AI — versi suspend/Flow dari legacy AIProvider (callback).
 * Implementasi: ChatRemoteRepository (Jerouter, OpenAI-compatible).
 */
interface AIProvider {
    /**
     * Daftar id model dari GET {base}/models.
     * @param force true = lewati cache 24 jam, selalu hit network.
     */
    suspend fun models(force: Boolean = false): List<String>

    /**
     * Validasi live: model bisa dipakai API key saat ini.
     * Implementasi: POST /chat/completions minimal (ping, max_tokens=1).
     * @throws AIHttpException dengan pesan Indonesia bila gagal.
     */
    suspend fun validateModel(model: String)

    /**
     * Chat streaming. Mengalirkan token incremental; selesai saat [DONE].
     * Pembatalan: batalkan coroutine pengoleksi (call HTTP ikut dibatalkan).
     */
    fun chatStream(request: ChatRequest): Flow<String>

    /**
     * Chat streaming DENGAN tool-loop (Sprint 1 Gate 2).
     * Model boleh memanggil tool (web_fetch); eksekusi + iterasi terjadi
     * di implementasi. [onToolEvent]: (nama tool, sukses, durasi ms).
     * Implementasi default: tanpa tool (sama seperti [chatStream]).
     */
    fun chatStreamWithTools(
        request: ChatRequest,
        onToolEvent: (name: String, ok: Boolean, ms: Long) -> Unit = { _, _, _ -> }
    ): Flow<String> = chatStream(request)
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

package id.sukiapps.hermes.data.remote

import kotlinx.serialization.Serializable

/** GET {base}/models → {"data":[{"id": "..."}]} */
@Serializable
data class ModelsResponse(
    val data: List<ModelInfo> = emptyList()
) {
    @Serializable
    data class ModelInfo(val id: String = "")
}

/** Satu chunk SSE /chat/completions: choices[0].delta.content */
@Serializable
data class ChatChunk(
    val choices: List<ChunkChoice> = emptyList()
) {
    @Serializable
    data class ChunkChoice(
        val delta: Delta = Delta()
    ) {
        @Serializable
        data class Delta(
            val role: String? = null,
            val content: String? = null
        )
    }
}

/** POST /chat/completions non-streaming (dipakai MemoryWriter write-back). */
@Serializable
data class ChatCompletionResponse(
    val choices: List<CompletionChoice> = emptyList()
) {
    @Serializable
    data class CompletionChoice(
        val message: CompletionMessage = CompletionMessage()
    ) {
        @Serializable
        data class CompletionMessage(
            val content: String = ""
        )
    }
}

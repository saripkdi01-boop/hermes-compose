package id.sukiapps.hermes.data.remote

import kotlinx.serialization.json.Json

/**
 * Dekoder satu baris "data:" SSE menjadi teks delta.
 * Murni Kotlin (tanpa Android) → bisa di-unit-test di JVM.
 */
object SseDecoder {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param data isi setelah prefix "data:" (sudah di-trim).
     * @return teks delta, atau null bila baris bukan konten
     *         (mis. delta berisi role saja / [DONE] / JSON rusak).
     */
    fun extractDelta(data: String): String? {
        if (data == "[DONE]") return null
        return try {
            val chunk = json.decodeFromString<ChatChunk>(data)
            chunk.choices.firstOrNull()?.delta?.content
                ?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }
}

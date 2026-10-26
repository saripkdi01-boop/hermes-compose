package id.sukiapps.hermes.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

    /**
     * Hasil parse satu baris "data:" SSE yang MEMBEDAKAN tiap kasus
     * (14.29, B6): jangan samakan null "bukan konten" dengan null
     * "gagal parse" atau "error provider" — ketiganya punya arti beda
     * bagi pemanggil.
     */
    sealed interface SseEvent {
        /** Delta teks yang bisa langsung dikirim ke UI. */
        data class Delta(val text: String) : SseEvent

        /**
         * Payload error provider, mis. {"error": {"message": "...",
         * "type": "rate_limit_error", "code": "rate_limit_exceeded"}}.
         * @param retryable true bila error kemungkinan hilang bila
         * diulang (rate limit / overload server / timeout), false untuk
         * error permanen (key salah, model tak ada, request tak valid).
         */
        data class ProviderError(val message: String, val retryable: Boolean) : SseEvent

        /** Baris valid tapi bukan konten (role saja, [DONE], usage, dsb). */
        data object Ignored : SseEvent

        /** Baris bukan JSON valid — dibedakan dari Ignored. */
        data object ParseFailed : SseEvent
    }

    fun parseEvent(data: String): SseEvent {
        if (data == "[DONE]") return SseEvent.Ignored
        // 1. Delta konten (jalur normal). ChatChunk.choices punya default
        // emptyList, jadi payload error pun lolos decode ini dengan
        // choices kosong — karena itu cek error dilakukan setelahnya.
        val delta = try {
            json.decodeFromString<ChatChunk>(data)
                .choices.firstOrNull()?.delta?.content
                ?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
        if (delta != null) return SseEvent.Delta(delta)
        // 2. Payload error provider (tanpa choices).
        val obj = try {
            json.parseToJsonElement(data).jsonObject
        } catch (e: Exception) {
            return SseEvent.ParseFailed
        }
        val err = obj["error"] as? JsonObject ?: return SseEvent.Ignored
        val message = err.stringField("message")?.takeIf { it.isNotBlank() }
            ?: err.stringField("type")?.takeIf { it.isNotBlank() }
            ?: err.toString().take(200)
        val signal = ("${err.stringField("type").orEmpty()} " +
            err.stringField("code").orEmpty()).lowercase()
        val retryable = signal.contains("rate_limit") ||
            signal.contains("rate-limit") || signal.contains("429") ||
            signal.contains("overload") || signal.contains("server_error") ||
            signal.contains("timeout") || signal.contains("temporarily") ||
            signal.contains("unavailable") || signal.contains("500") ||
            signal.contains("502") || signal.contains("503") ||
            signal.contains("504")
        return SseEvent.ProviderError(message, retryable)
    }

    private fun JsonObject.stringField(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull
}

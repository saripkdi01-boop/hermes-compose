package id.sukiapps.hermes.data.agent

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import id.sukiapps.hermes.data.audit.AuditLog
import id.sukiapps.hermes.data.local.AppPrefs
import id.sukiapps.hermes.data.remote.ChatCompletionResponse
import id.sukiapps.hermes.domain.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Write-back ingatan: ekstrak ≤3 fakta durable dari riwayat chat via satu
 * panggilan /chat/completions non-streaming, lalu append ke memory.md
 * (dengan prefix tanggal + dedup keyword).
 *
 * Dipicu murah saja: tiap 10 pesan user & saat chat ditutup (lihat
 * ChatViewModel). Gagal diam-diam — write-back tidak boleh merusak chat.
 */
@Singleton
class MemoryWriter @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val client: OkHttpClient, // AuthInterceptor sudah pasang Bearer
    private val prefs: AppPrefs,
    private val audit: AuditLog // LOW: tulis ingatan dicatat diam-diam
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun maybeWriteBack(messages: List<ChatMessage>) =
        withContext(Dispatchers.IO) {
            try {
                val model = prefs.model.ifBlank { return@withContext }
                if (messages.isEmpty()) return@withContext
                val facts = extractFacts(model, messages) ?: return@withContext
                if (facts.isEmpty()) return@withContext
                val existing = AgentFiles.read(ctx, AgentFiles.MEMORY)
                val fresh = facts.filter { isFresh(it, existing) }.take(3)
                if (fresh.isEmpty()) return@withContext
                val date = LocalDate.now().toString() // minSdk 30 ≥ API 26 OK
                AgentFiles.appendMemory(
                    ctx,
                    fresh.joinToString("\n") { "- $date: $it" } + "\n"
                )
                // Catat diam-diam (LOW, tanpa popup): bukti tulis ingatan.
                audit.log(
                    "Ingatan",
                    "Write-back: ${fresh.size} fakta baru ke memory.md",
                    granted = true,
                    via = "auto"
                )
            } catch (e: Exception) {
                // Gagal diam-diam: ingatan tidak sepenting chat yang sedang jalan.
            }
        }

    /** Satu panggilan LLM murah; null bila tidak ada fakta / gagal. */
    private fun extractFacts(
        model: String,
        messages: List<ChatMessage>
    ): List<String>? {
        val transcript = messages.takeLast(10)
            .joinToString("\n") { "${it.role}: ${it.content.take(500)}" }
        val body = buildJsonObject {
            put("model", model)
            put("stream", false)
            put("max_tokens", 200)
            put(
                "messages", buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put("content", EXTRACT_PROMPT)
                        }
                    )
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", transcript)
                        }
                    )
                }
            )
        }.toString()
        val req = Request.Builder()
            .url(prefs.baseUrl.trimEnd('/') + "/chat/completions")
            .post(body.toRequestBody(mediaType))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val text = resp.body?.string() ?: return null
            val content = runCatching {
                json.decodeFromString<ChatCompletionResponse>(text)
                    .choices.firstOrNull()?.message?.content.orEmpty()
            }.getOrDefault("")
            if (content.contains("TIDAK ADA", ignoreCase = true)) return emptyList()
            return content.lineSequence()
                .map { it.trim().removePrefix("-").removePrefix("*").trim() }
                .map { it.removePrefix(Regex("^\\d+[.)]\\s*")) }
                .filter { it.length >= 8 }
                .take(3)
                .toList()
        }
    }

    /**
     * Fakta dianggap duplikat bila 3 kata kuncinya (≥4 huruf) semuanya
     * sudah muncul di memory.md — jangan menumpuk fakta yang sama.
     */
    internal fun isFresh(fact: String, existing: String): Boolean {
        val keys = fact.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 4 }
            .distinct()
            .take(3)
        if (keys.size < 3) return true // terlalu pendek untuk dinilai → anggap baru
        val low = existing.lowercase()
        return keys.any { !low.contains(it) }
    }

    companion object {
        private const val EXTRACT_PROMPT =
            "Dari percakapan berikut, ekstrak MAKSIMAL 3 fakta durable tentang " +
                "user (preferensi, keputusan, konteks penting). Abaikan basa-basi dan " +
                "hal sekali pakai. Jawab hanya sebagai daftar bullet, satu fakta per " +
                "baris, tanpa nomor. Jika tidak ada fakta durable, jawab persis: TIDAK ADA"
    }
}

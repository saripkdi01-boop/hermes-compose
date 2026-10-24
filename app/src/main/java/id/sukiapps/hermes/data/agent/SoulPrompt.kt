package id.sukiapps.hermes.data.agent

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Merakit system prompt "jiwa" dengan urutan: soul → skill (bawaan) →
 * agents (≤10 baris) →
 * features (≤40 baris) → memory relevan (keyword match, fallback 15 baris
 * pertama) → todo aktif (≤5). Total dibatasi ±1500 kata.
 *
 * shortcut: memory >8KB belum diringkas via LLM (playbook menyarankan),
 * untuk sekarang dipotong oleh batas kata + keyword match. Upgrade bila
 * ingatan terasa basi/penuh.
 */
object SoulPrompt {
    private const val MAX_WORDS = 1500

    suspend fun build(ctx: Context, userMessage: String): String =
        withContext(Dispatchers.IO) {
            val soul = AgentFiles.read(ctx, AgentFiles.SOUL)
            val skill = AgentFiles.read(ctx, AgentFiles.SKILL)
            val agents = AgentFiles.read(ctx, AgentFiles.AGENTS)
                .lineSequence()
                .filter { it.isNotBlank() }
                .take(10)
                .joinToString("\n")
            val memory = AgentFiles.read(ctx, AgentFiles.MEMORY)
            val todo = AgentFiles.read(ctx, AgentFiles.TODO)
            val features = AgentFiles.read(ctx, AgentFiles.FEATURES)
                .lineSequence()
                .filter { it.isNotBlank() }
                .take(40)
                .joinToString("\n")
            val activeTodos = todo.lineSequence()
                .filter { it.trimStart().startsWith("- [ ]") }
                .take(5)
                .joinToString("\n")

            val assembled = buildString {
                if (soul.isNotBlank()) appendLine(soul.trim())
                if (skill.isNotBlank()) {
                    appendLine()
                    appendLine("[Skill agen — cara kerja]")
                    appendLine(skill.trim())
                }
                if (agents.isNotBlank()) {
                    appendLine()
                    appendLine("[Aturan operasi]")
                    appendLine(agents.trim())
                }
                if (features.isNotBlank()) {
                    appendLine()
                    appendLine("[Fitur aplikasi Hermes — kenali agar bisa memandu user]")
                    appendLine(features.trim())
                }
                val relevant = relevantMemory(memory, userMessage)
                if (relevant.isNotBlank()) {
                    appendLine()
                    appendLine("[Ingatan tentang user]")
                    appendLine(relevant.trim())
                }
                if (activeTodos.isNotBlank()) {
                    appendLine()
                    appendLine("[Tugas aktif user]")
                    appendLine(activeTodos.trim())
                }
            }.trim()
            capWords(assembled, MAX_WORDS)
        }

    /** Skor baris memory dari kata kunci pesan user (≥4 huruf, maks 5 kata). */
    internal fun relevantMemory(memory: String, userMessage: String): String {
        val keywords = userMessage.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 4 }
            .distinct()
            .take(5)
        val lines = memory.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("<!--") && !it.startsWith("#") }
            .toList()
        if (lines.isEmpty()) return ""
        if (keywords.isEmpty()) return lines.take(15).joinToString("\n")
        return lines
            .map { line ->
                val low = line.lowercase()
                line to keywords.count { low.contains(it) }
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(8)
            .joinToString("\n") { it.first }
            .ifBlank { lines.take(15).joinToString("\n") }
    }

    private fun capWords(text: String, maxWords: Int): String {
        val words = text.split(Regex("\\s+"))
        return if (words.size <= maxWords) text
        else words.take(maxWords).joinToString(" ")
    }
}

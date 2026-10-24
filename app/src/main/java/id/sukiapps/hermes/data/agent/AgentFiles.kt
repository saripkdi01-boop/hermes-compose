package id.sukiapps.hermes.data.agent

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * File jiwa Hermes di filesDir/agent/: soul.md, memory.md, todo.md, agents.md,
 * plus features.md (manual fitur aplikasi, read-only, tidak diedit user).
 * Seed default disalin dari assets/agent/ saat pertama dibuka.
 * Semua fungsi suspend + I/O di Dispatchers.IO — aman dipanggil dari ViewModel.
 */
object AgentFiles {
    const val SOUL = "soul.md"
    const val MEMORY = "memory.md"
    const val TODO = "todo.md"
    const val AGENTS = "agents.md"
    const val FEATURES = "features.md"
    const val SKILL = "skill.md"

    val ALL = listOf(SOUL, MEMORY, TODO, AGENTS, FEATURES, SKILL)

    fun dir(ctx: Context): File = File(ctx.filesDir, "agent").apply { mkdirs() }

    suspend fun seedIfNeeded(ctx: Context) = withContext(Dispatchers.IO) {
        val d = dir(ctx)
        for (name in ALL) {
            val f = File(d, name)
            if (!f.exists()) {
                try {
                    ctx.assets.open("agent/$name").use { ins ->
                        f.outputStream().use { out -> ins.copyTo(out) }
                    }
                } catch (e: Exception) {
                    // Asset hilang (mis. build parsial) — buat file kosong, jangan crash.
                    runCatching { f.writeText("") }
                }
            }
        }
    }

    suspend fun read(ctx: Context, name: String): String = withContext(Dispatchers.IO) {
        require(name in ALL) { "unknown agent file: $name" }
        seedIfNeeded(ctx)
        runCatching { File(dir(ctx), name).readText() }.getOrDefault("")
    }

    suspend fun write(ctx: Context, name: String, text: String) =
        withContext(Dispatchers.IO) {
            require(name in ALL) { "unknown agent file: $name" }
            seedIfNeeded(ctx)
            File(dir(ctx), name).writeText(text)
        }

    /** Append satu blok ke memory.md (dipakai MemoryWriter). */
    suspend fun appendMemory(ctx: Context, block: String) =
        withContext(Dispatchers.IO) {
            seedIfNeeded(ctx)
            File(dir(ctx), MEMORY).appendText(block)
        }
}

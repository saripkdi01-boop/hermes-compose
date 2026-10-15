package id.sukiapps.hermes.data.audit

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AuditEntry(
    val ts: Long,
    val type: String,    // "Termux" | "Telegram" | "GitHub" | "Ingatan" | "Web"
    val detail: String,  // cuplikan perintah/aksi
    val decision: String, // "granted" | "denied"
    val via: String       // "popup" | "remembered" | "auto"
)

/**
 * Audit trail append-only: filesDir/audit.jsonl, cap 500 entri (buang terlama).
 * Satu-satunya bukti tiap aksi sensitif — dibaca layar "Riwayat Izin".
 * Semua tulis lewat Mutex agar tidak ada entri yang hilang tertimpa.
 */
@Singleton
class AuditLog @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private fun file(): File = File(ctx.filesDir, "audit.jsonl")

    suspend fun log(type: String, detail: String, granted: Boolean, via: String) =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val e = AuditEntry(
                    ts = System.currentTimeMillis(),
                    type = type,
                    detail = detail.take(500),
                    decision = if (granted) "granted" else "denied",
                    via = via
                )
                val f = file()
                f.appendText(json.encodeToString(AuditEntry.serializer(), e) + "\n")
                val lines = f.readLines()
                if (lines.size > MAX_ENTRIES) {
                    f.writeText(lines.takeLast(MAX_ENTRIES).joinToString("\n") + "\n")
                }
            }
        }

    suspend fun readAll(): List<AuditEntry> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val f = file()
            if (!f.exists()) return@withContext emptyList()
            f.readLines()
                .mapNotNull { runCatching { json.decodeFromString<AuditEntry>(it) }.getOrNull() }
                .takeLast(MAX_ENTRIES)
                .reversed() // terbaru dulu
        }
    }

    companion object {
        const val MAX_ENTRIES = 500
    }
}

package id.sukiapps.hermes.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import id.sukiapps.hermes.data.store.ApprovalStore
import id.sukiapps.hermes.data.store.JsonStore
import id.sukiapps.hermes.data.store.ToolStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v12 F4 — Morning Brief worker (tanpa Room).
 * Dijadwalkan harian via WorkManager pada jam brief_hour (default 7).
 * Output: filesDir/brief/brief-YYYY-MM-DD.md + notifikasi ringkas.
 */
class BriefWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = applicationContext.getSharedPreferences("hermes_flags", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("brief_enabled", true)) return Result.success()

            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val briefDir = File(applicationContext.filesDir, "brief").apply { mkdirs() }
            val briefFile = File(briefDir, "brief-$dateStr.md")

            // Kumpulkan data (via JsonStore, thread io sudah dijamin CoroutineWorker).
            val pending = ApprovalStore.getPendingSync()
            val tools = ToolStore.getToolsSync()
            val drafts = ToolStore.getDraftsSync()

            val sb = StringBuilder()
            sb.appendLine("# Morning Brief — $dateStr")
            sb.appendLine()
            sb.appendLine("## Menunggu persetujuan (${pending.size})")
            if (pending.isEmpty()) {
                sb.appendLine("- Tidak ada.")
            } else {
                pending.take(10).forEach {
                    sb.appendLine("- [${it.type}] ${it.title} — ${it.detail}")
                }
                if (pending.size > 10) sb.appendLine("- ... dan ${pending.size - 10} lainnya.")
            }
            sb.appendLine()
            sb.appendLine("## Tools (${tools.size} published, ${drafts.size} draft)")
            tools.take(10).forEach {
                val tag = if (it.sellable) "Rp${it.priceIdr}" else "internal"
                sb.appendLine("- ${it.name} ($tag)")
            }
            if (drafts.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("### Draft menunggu publish")
                drafts.take(5).forEach { sb.appendLine("- ${it.name}") }
            }
            sb.appendLine()
            sb.appendLine("_Dibuat otomatis oleh Hermes v12._")

            briefFile.writeText(sb.toString())

            // Simpan pointer brief terakhir di JsonStore.
            JsonStore.mutate("brief_meta", { json ->
                json.put("last_brief", dateStr)
                json.put("last_brief_at", System.currentTimeMillis())
            })

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

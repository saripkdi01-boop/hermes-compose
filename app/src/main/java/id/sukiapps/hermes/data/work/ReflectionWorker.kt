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
 * v12 F5 — Reflection worker mingguan (tanpa Room).
 * Dijadwalkan via WorkManager seminggu sekali.
 * Output: filesDir/reflection/weekly-YYYY-Www.md
 */
class ReflectionWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            val weekStr = SimpleDateFormat("yyyy-'W'ww", Locale.US).format(Date())
            val dir = File(applicationContext.filesDir, "reflection").apply { mkdirs() }
            val outFile = File(dir, "weekly-$weekStr.md")

            // Baca brief_meta untuk tahu berapa brief dibuat minggu ini.
            val meta = JsonStore.load("brief_meta")
            val lastBrief = meta.optString("last_brief", "-")

            val tools = ToolStore.getToolsSync()
            val pending = ApprovalStore.getPendingSync()

            val sb = StringBuilder()
            sb.appendLine("# Weekly Reflection — $weekStr")
            sb.appendLine()
            sb.appendLine("## Ringkasan")
            sb.appendLine("- Brief terakhir: $lastBrief")
            sb.appendLine("- Tools published: ${tools.size}")
            sb.appendLine("- Approval pending saat ini: ${pending.size}")
            sb.appendLine()
            sb.appendLine("## Tools sellable")
            val sellable = tools.filter { it.sellable }
            if (sellable.isEmpty()) {
                sb.appendLine("- Belum ada tool yang dijual.")
            } else {
                sellable.forEach { sb.appendLine("- ${it.name}: Rp${it.priceIdr}") }
            }
            sb.appendLine()
            sb.appendLine("## Catatan")
            sb.appendLine("_Refleksi otomatis. Tambahkan insight manual di bawah garis ini._")
            sb.appendLine()
            sb.appendLine("---")

            // Append jika file minggu ini sudah ada (jangan timpa insight manual).
            if (outFile.exists()) {
                outFile.appendText("\n\n" + sb.toString())
            } else {
                outFile.writeText(sb.toString())
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

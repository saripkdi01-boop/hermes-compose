package id.sukiapps.hermes.diagnostics

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CrashReporter — tangkap exception tak tertangani, simpan sebagai JSON.
 *
 * Pasang sekali di Application.onCreate():
 *     CrashReporter.install(this)
 *
 * File tersimpan di: filesDir/crashes/crash-<timestamp>.json
 * Kirim via tombol "Kirim laporan" di HealthScreen, lalu hapus yang terkirim.
 */
object CrashReporter {

    fun install(context: Context) {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrash(context, thread, throwable)
            } catch (_: Exception) {
                // Jangan pernah crash di dalam crash handler.
            }
            default?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrash(context: Context, thread: Thread, t: Throwable) {
        val dir = File(context.filesDir, "crashes").apply { mkdirs() }
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) { "?" }

        // Rantai cause, maksimal 3 level.
        val causes = mutableListOf<String>()
        var c: Throwable? = t.cause
        repeat(3) {
            if (c == null) return@repeat
            causes.add("${c!!::class.java.name}: ${c!!.message}")
            c = c!!.cause
        }

        val json = JSONObject().apply {
            put("time", ts)
            put("thread", thread.name)
            put("exception", t::class.java.name)
            put("message", t.message ?: "")
            put("causes", causes.joinToString(" <- "))
            put("stacktrace", t.stackTraceToString().take(4000))
            put("model", Build.MODEL)
            put("manufacturer", Build.MANUFACTURER)
            put("android", Build.VERSION.RELEASE)
            put("sdk", Build.VERSION.SDK_INT)
            put("appVersion", versionName)
        }
        File(dir, "crash-$ts.json").writeText(json.toString(2))
    }

    fun pendingCrashes(context: Context): List<File> =
        File(context.filesDir, "crashes")
            .listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedByDescending { it.name }
            ?: emptyList()

    fun clearSent(files: List<File>) {
        files.forEach { runCatching { it.delete() } }
    }
}

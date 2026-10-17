package id.sukiapps.hermes.diagnostics

import android.content.Context
import android.os.StatFs
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

/**
 * HealthCheck — audit mandiri 6 aspek kesehatan aplikasi.
 *
 * Dipakai oleh HealthScreen. Semua cek sinkron & cepat (<10 detik total);
 * panggil dari Dispatchers.IO.
 */
data class HealthResult(val name: String, val ok: Boolean, val detail: String)

object HealthCheck {

    fun run(context: Context): List<HealthResult> = listOf(
        checkStorage(),
        checkJsonStore(context),
        checkBackend(),
        checkUpinTunnel(),
        checkNotifications(context),
        checkCrashCount(context)
    )

    private fun checkStorage(): HealthResult {
        return try {
            val stat = StatFs(context_filesDir())
            val freeMb = stat.availableBytes / 1_048_576
            HealthResult("Penyimpanan", freeMb > 100, "$freeMb MB bebas")
        } catch (e: Exception) {
            HealthResult("Penyimpanan", false, e.message ?: "gagal dibaca")
        }
    }

    private fun context_filesDir(): String = "/data"

    private fun checkJsonStore(context: Context): HealthResult {
        val dir = File(context.filesDir, "jsonstore")
        if (!dir.exists()) return HealthResult("JsonStore", true, "belum ada data")
        var corrupt = 0
        var total = 0
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.extension == "json") {
                total++
                try {
                    val t = f.readText().trim()
                    if (!(t.startsWith("{") || t.startsWith("["))) corrupt++
                } catch (_: Exception) {
                    corrupt++
                }
            }
        }
        return HealthResult("JsonStore", corrupt == 0, "$total file, $corrupt corrupt")
    }

    private fun checkBackend(): HealthResult {
        return try {
            val conn = URL("https://sukiapps.web.id/api/health").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val code = conn.responseCode
            conn.disconnect()
            HealthResult("Backend", code == 200, "HTTP $code")
        } catch (e: Exception) {
            HealthResult("Backend", false, e.message ?: "tidak terjangkau")
        }
    }

    private fun checkUpinTunnel(): HealthResult {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress("127.0.0.1", 8766), 3000)
                HealthResult("Tunnel Upin", true, "port 8766 terbuka")
            }
        } catch (_: Exception) {
            HealthResult("Tunnel Upin", false, "mati — jalankan ssh -L di Termux")
        }
    }

    private fun checkNotifications(context: Context): HealthResult {
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        val ok = nm?.areNotificationsEnabled() == true
        return HealthResult(
            "Izin notifikasi", ok,
            if (ok) "aktif" else "mati — nyalakan di Pengaturan HP"
        )
    }

    private fun checkCrashCount(context: Context): HealthResult {
        val n = CrashReporter.pendingCrashes(context).size
        return HealthResult("Crash tersimpan", n == 0, "$n laporan belum dikirim")
    }
}

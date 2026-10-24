package id.sukiapps.hermes.data.notif

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import id.sukiapps.hermes.MainActivity
import id.sukiapps.hermes.R
import id.sukiapps.hermes.domain.Prefs

/**
 * ExecNotifier — notifikasi lokal untuk event eksekusi & izin (v15.0).
 *
 * Titik pakai:
 *  - HermesApp.onCreate() -> ensureChannels()
 *  - ChatViewModel -> hasil tool/sesi agen, error jaringan, keputusan izin
 *
 * Id notifikasi stabil per kategori supaya notif baru menimpa yang lama
 * (tidak spam). Semua posting dicek izin POST_NOTIFICATIONS + toggle
 * notif_enabled / notif_exec / notif_approval di SharedPreferences hermes_chat.
 */
object ExecNotifier {

    const val CHANNEL_EXEC = "hermes_exec"
    const val CHANNEL_APPROVAL = "hermes_approval"

    private const val ID_EXEC = 4201
    private const val ID_APPROVAL = 4202

    // Notif eksekusi di-throttle 30 detik supaya tidak bor tiap pesan.
    private const val EXEC_THROTTLE_MS = 30_000L
    @Volatile private var lastExecAt = 0L

    /** Daftarkan channel — dipanggil sekali saat app start (minSdk 30, tanpa guard versi). */
    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_EXEC,
                    "Eksekusi & aktivitas",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Hasil eksekusi tool, sesi agen, dan status respons"
                    setShowBadge(true)
                },
                NotificationChannel(
                    CHANNEL_APPROVAL,
                    "Izin & persetujuan",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Permintaan izin aksi sensitif (tool, konektor, Termux)"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 200, 250)
                }
            )
        )
    }

    /** Hasil eksekusi: tool selesai, sesi agen berhenti, respons selesai. */
    fun notifyExec(ctx: Context, title: String, text: String, force: Boolean = false) {
        if (!execEnabled(ctx)) return
        val now = System.currentTimeMillis()
        if (!force && now - lastExecAt < EXEC_THROTTLE_MS) return
        lastExecAt = now
        post(ctx, CHANNEL_EXEC, ID_EXEC, title, text)
    }

    /** Keputusan izin — prioritas tinggi, tidak di-throttle. */
    fun notifyApproval(ctx: Context, title: String, text: String) {
        if (!approvalEnabled(ctx)) return
        post(ctx, CHANNEL_APPROVAL, ID_APPROVAL, title, text)
    }

    private fun post(ctx: Context, channel: String, id: Int, title: String, text: String) {
        if (!canPost(ctx)) return
        val tap = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx,
            id,
            tap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_hermes)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .build()
        // Permission POST_NOTIFICATIONS bisa dicabut user kapan saja —
        // gagal posting harus senyap, bukan crash.
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }

    private fun canPost(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Prefs.KEY_NOTIF_ENABLED, true)) return false
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return true
    }

    private fun execEnabled(ctx: Context) =
        ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
            .getBoolean(Prefs.KEY_NOTIF_EXEC, true)

    private fun approvalEnabled(ctx: Context) =
        ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
            .getBoolean(Prefs.KEY_NOTIF_APPROVAL, true)
}

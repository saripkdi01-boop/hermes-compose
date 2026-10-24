package id.sukiapps.hermes.data.store
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

/**
 * v14.10 — Notifikasi hasil eksekusi & status konektor.
 *
 * Channel "hermes_exec" (IMPORTANCE_DEFAULT): prompt terkirim, respons
 * selesai, tool selesai, error jaringan. Notifikasi non-intrusif — tidak
 * berbunyi, tidak tampil sebagai heads-up, bisa dilihat di notification
 * shade setelah eksekusi.
 *
 * Desain:
 * - Notif id STABIL per-event ("exec_prompt", "exec_tool", "exec_error")
 *   → notif baru menimpa yang lama, tidak menumpuk di shade.
 * - Semua posting dicek izin POST_NOTIFICATIONS (API 33+) + prefs toggle
 *   `notif_enabled` + `notif_exec`. Gagal = senyap, tidak crash.
 * - Tidak ada data sensitif (isi prompt) di notifikasi — hanya judul
 *   ringkas + jumlah karakter/ms.
 *
 * Channel persetujuan tetap di [ApprovalNotifier] (CHANNEL_ID "persetujuan").
 */
object ExecNotifier {
    private const val CHANNEL_ID = "hermes_exec"
    private const val CHANNEL_NAME = "Eksekusi Hermes"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description =
                            "Hasil eksekusi: prompt terkirim, respons selesai, " +
                                "tool selesai, error jaringan"
                        setShowBadge(true)
                    }
                )
            }
        }
    }

    /** Cek izin notifikasi runtime (API 33+) — aman dipanggil dari mana saja. */
    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Kirim notifikasi eksekusi. [eventKey] menentukan id notifikasi stabil;
     * [title]/[text] sudah dipangkas pemanggil (maks 40/120 karakter).
     */
    fun notifyExec(context: Context, eventKey: String, title: String, text: String) {
        if (!hasPermission(context)) return
        val sp = context.getSharedPreferences("hermes_chat", Context.MODE_PRIVATE)
        if (!sp.getBoolean("notif_enabled", true)) return
        if (!sp.getBoolean("notif_exec", true)) return
        ensureChannel(context)
        val intent = Intent().apply {
            setClassName(context.packageName, "id.sukiapps.hermes.MainActivity")
            putExtra("nav_route", "chat")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            eventKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(context)
                .notify(eventKey.hashCode(), notif)
        } catch (_: SecurityException) {
            // Izin dicabut setelah app start — senyap.
        }
    }
}

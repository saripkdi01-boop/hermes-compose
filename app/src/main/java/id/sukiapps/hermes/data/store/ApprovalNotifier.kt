package id.sukiapps.hermes.data.store

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * v12 F2 — Notification channel "Persetujuan" (HIGH priority).
 * Dipanggil saat approval baru masuk.
 *
 * Activity tujuan: id.sukiapps.hermes.ui.approval.ApprovalActivity
 * (dibuat di patch berikutnya — notifikasi tetap tampil tanpa activity).
 */
object ApprovalNotifier {
    const val CHANNEL_ID = "persetujuan"
    private const val NOTIF_ID_BASE = 9000

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Persetujuan",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Permintaan persetujuan aksi sensitif Hermes"
                    }
                )
            }
        }
    }

    fun notifyApproval(context: Context, id: String, title: String, detail: String) {
        ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent dibuka saat notifikasi diketuk — aman bila activity belum ada.
        val intent = Intent().apply {
            setClassName(context.packageName, "id.sukiapps.hermes.MainActivity")
            putExtra("nav_route", "approval")
            putExtra("approval_id", id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Butuh persetujuan: $title")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        nm.notify(NOTIF_ID_BASE + (id.hashCode() and 0xFFFF), notif)
    }
}

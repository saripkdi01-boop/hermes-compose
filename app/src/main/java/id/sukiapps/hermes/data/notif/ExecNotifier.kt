package id.sukiapps.hermes.data.notif

import android.Manifest
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
import dagger.hilt.android.qualifiers.ApplicationContext
import id.sukiapps.hermes.MainActivity
import id.sukiapps.hermes.data.store.ApprovalNotifier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v14.10 — Notifikasi lokal untuk event eksekusi, hasil prompt, error,
 * dan keputusan izin.
 *
 * Channel eksekusi: "hermes_exec" (IMPORTANCE_DEFAULT).
 * Channel izin: REUSE [ApprovalNotifier.CHANNEL_ID] ("persetujuan") —
 * tidak ada channel approval baru yang dibuat di sini.
 *
 * Semua posting dicek [NotificationManagerCompat.areNotificationsEnabled]
 * dan izin POST_NOTIFICATIONS; bila tidak lolos, silent return, tanpa crash.
 * Id notifikasi stabil per kategori supaya notifikasi baru menimpa yang
 * lama, bukan menumpuk spam.
 */
@Singleton
class ExecNotifier @Inject constructor(
    @ApplicationContext private val appCtx: Context
) {
    fun notifyExec(title: String, text: String) {
        post(CHANNEL_EXEC, ID_EXEC_TOOL, title, text, NotificationCompat.PRIORITY_DEFAULT)
    }

    fun notifyDone(title: String, text: String) {
        post(CHANNEL_EXEC, ID_EXEC_DONE, title, text, NotificationCompat.PRIORITY_DEFAULT)
    }

    fun notifyError(title: String, text: String) {
        post(CHANNEL_EXEC, ID_EXEC_ERROR, title, text, NotificationCompat.PRIORITY_HIGH)
    }

    fun notifyApproval(title: String, text: String) {
        post(
            ApprovalNotifier.CHANNEL_ID,
            ID_EXEC_APPROVAL,
            title,
            text,
            NotificationCompat.PRIORITY_HIGH
        )
    }

    private fun post(channelId: String, notifId: Int, title: String, text: String, priority: Int) {
        if (!NotificationManagerCompat.from(appCtx).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                appCtx, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(appCtx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appCtx, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val icon = if (channelId == ApprovalNotifier.CHANNEL_ID || priority == NotificationCompat.PRIORITY_HIGH) {
            android.R.drawable.ic_dialog_alert
        } else {
            android.R.drawable.ic_dialog_info
        }
        val notif = NotificationCompat.Builder(appCtx, channelId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        runCatching {
            NotificationManagerCompat.from(appCtx).notify(notifId, notif)
        }
    }

    companion object {
        const val CHANNEL_EXEC = "hermes_exec"

        // Id stabil per kategori: exec_tool, exec_done, exec_error, exec_approval.
        const val ID_EXEC_TOOL = 4101
        const val ID_EXEC_DONE = 4102
        const val ID_EXEC_ERROR = 4103
        const val ID_EXEC_APPROVAL = 4104

        /**
         * Daftarkan channel eksekusi saja. Channel izin tidak dibuat di sini;
         * panggil [ApprovalNotifier.ensureChannel] untuk channel "persetujuan".
         */
        fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel(CHANNEL_EXEC) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(
                            CHANNEL_EXEC,
                            "Eksekusi & aktivitas",
                            NotificationManager.IMPORTANCE_DEFAULT
                        ).apply {
                            description = "Hasil eksekusi tool, prompt, dan respons model"
                        }
                    )
                }
            }
        }
    }
}

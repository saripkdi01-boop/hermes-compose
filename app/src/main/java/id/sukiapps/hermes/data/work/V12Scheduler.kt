package id.sukiapps.hermes.data.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * v12 F4/F5 — Penjadwal WorkManager (tanpa Room, tanpa Hilt).
 * Panggil dari HermesApp.onCreate() setelah JsonStore.init().
 */
object V12Scheduler {

    private const val BRIEF_WORK = "v12-morning-brief"
    private const val REFLECTION_WORK = "v12-weekly-reflection"

    fun scheduleAll(context: Context) {
        scheduleBrief(context)
        scheduleReflection(context)
    }

    fun scheduleBrief(context: Context) {
        val prefs = context.getSharedPreferences("hermes_flags", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("brief_enabled", true)) return

        val hour = prefs.getInt("brief_hour", 7)
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val delayMin = (target.timeInMillis - now.timeInMillis) / 60000

        val req = PeriodicWorkRequestBuilder<BriefWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMin, TimeUnit.MINUTES)
            .addTag(BRIEF_WORK)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BRIEF_WORK, ExistingPeriodicWorkPolicy.UPDATE, req
        )
    }

    fun scheduleReflection(context: Context) {
        val req = PeriodicWorkRequestBuilder<ReflectionWorker>(7, TimeUnit.DAYS)
            .addTag(REFLECTION_WORK)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            REFLECTION_WORK, ExistingPeriodicWorkPolicy.KEEP, req
        )
    }
}

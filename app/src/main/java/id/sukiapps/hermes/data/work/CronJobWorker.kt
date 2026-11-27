package id.sukiapps.hermes.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import id.sukiapps.hermes.data.remote.SimpleCompletion
import id.sukiapps.hermes.data.store.ExecutionLog
import id.sukiapps.hermes.di.agentDeps
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * v20.1 — penjadwal tugas `cronjob` di atas WorkManager (dependensi sudah ada).
 * Satu unique work per tugas: "hermes-cron-<id>". Semua butuh jaringan.
 */
object CronScheduler {
    private fun workName(id: String) = "hermes-cron-$id"

    private val NET = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(context: Context, job: CronJob, now: ZonedDateTime = ZonedDateTime.now()) {
        val spec = CronSpec.parse(job.schedule)
        val wm = WorkManager.getInstance(context)
        val delay = spec.initialDelayMillis(now)
        val data = workDataOf(CronJobWorker.KEY_ID to job.id)
        when (spec) {
            is CronSpec.Once -> wm.enqueueUniqueWork(
                workName(job.id), ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<CronJobWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setConstraints(NET).setInputData(data).build()
            )
            is CronSpec.Every -> wm.enqueueUniquePeriodicWork(
                workName(job.id), ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<CronJobWorker>(spec.minutes, TimeUnit.MINUTES)
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setConstraints(NET).setInputData(data).build()
            )
            is CronSpec.Daily -> wm.enqueueUniquePeriodicWork(
                workName(job.id), ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<CronJobWorker>(24, TimeUnit.HOURS)
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setConstraints(NET).setInputData(data).build()
            )
        }
    }

    fun runNow(context: Context, id: String) {
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<CronJobWorker>()
                .setConstraints(NET)
                .setInputData(workDataOf(CronJobWorker.KEY_ID to id))
                .build()
        )
    }

    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }
}

/**
 * Menjalankan satu tugas terjadwal: prompt → model (non-streaming, pakai
 * model & base URL Pengaturan) → simpan ke percakapan "⏰ <nama>" →
 * notifikasi. Tugas dijeda/hilang = selesai tanpa aksi. Gagal = dicatat,
 * tidak di-retry tanpa batas (WorkManager periodic tetap jalan di periode
 * berikutnya).
 */
class CronJobWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        const val KEY_ID = "cron_id"
        private const val SYSTEM =
            "Kamu Hermes, asisten pribadi berbahasa Indonesia. Kamu sedang " +
                "menjalankan TUGAS TERJADWAL milik user tanpa user hadir di layar. " +
                "Kerjakan instruksi berikut dan laporkan hasilnya ringkas (maks ±200 kata). " +
                "Kamu tidak punya akses tool atau internet di tugas ini; jangan mengarang fakta terkini."
    }

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.success()
        val store = CronJobStore.forApp(applicationContext.filesDir)
        val job = store.get(id) ?: return Result.success()
        if (job.paused) return Result.success()
        val deps = agentDeps(applicationContext)
        val prefs = deps.appPrefs()
        val repo = deps.chatRepository()
        val now = System.currentTimeMillis()
        return try {
            val answer = SimpleCompletion.complete(
                client = deps.okHttpClient(),
                baseUrl = prefs.baseUrl,
                model = prefs.model,
                system = SYSTEM + if (prefs.persona.isNotBlank()) "\n\nPersona: ${prefs.persona}" else "",
                user = job.prompt,
                maxTokens = 600
            )
            val convId = job.convId.takeIf { it > 0 && repo.conversation(it) != null }
                ?: repo.newConversation("⏰ ${job.name}", prefs.persona)
            repo.addMessage(convId, "user", "⏰ Tugas terjadwal (${CronSpec.parse(job.schedule).describe()}): ${job.prompt}", null)
            repo.addMessage(convId, "assistant", answer, null)
            store.update(id) { it.copy(convId = convId, lastRunAt = now, lastStatus = "ok") }
            ExecutionLog.log("tool", "cronjob ${job.name}", "${answer.length} karakter", "ok")
            if (prefs.notifEnabled) deps.execNotifier().notifyDone("⏰ ${job.name}", answer.take(300))
            if (CronSpec.parse(job.schedule) is CronSpec.Once) store.update(id) { it.copy(paused = true) }
            Result.success()
        } catch (e: Exception) {
            val msg = (e.message ?: e.javaClass.simpleName).take(160)
            runCatching { store.update(id) { it.copy(lastRunAt = now, lastStatus = "gagal: $msg") } }
            ExecutionLog.log("error", "cronjob ${job.name}", msg, "fail")
            if (prefs.notifEnabled) deps.execNotifier().notifyError("⏰ ${job.name} gagal", msg)
            Result.success()
        }
    }
}

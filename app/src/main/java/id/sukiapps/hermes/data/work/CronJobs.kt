package id.sukiapps.hermes.data.work

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * v20.1 — jadwal tugas agen (setara tool `cronjob` Hermes Agent desktop).
 * Murni JVM (java.time + org.json + File) → bisa di-unit-test.
 *
 * Format jadwal yang diterima (Indonesia/Inggris):
 * - "setiap 30 menit" / "every 30m" / "setiap 2 jam" / "every 2h"  (min 15 menit — batas WorkManager)
 * - "setiap hari 07:30" / "harian 07:30" / "daily 07:30"
 * - "sekali 2026-11-28 09:00" / "once 2026-11-28 09:00"
 */
sealed interface CronSpec {
    data class Every(val minutes: Long) : CronSpec
    data class Daily(val hour: Int, val minute: Int) : CronSpec
    data class Once(val at: LocalDateTime) : CronSpec

    companion object {
        const val MIN_MINUTES = 15L
        private val EVERY = Regex("""^(?:setiap|tiap|every)\s+(\d{1,4})\s*(m|menit|min|mnt|h|j|jam|hour|hours)$""")
        private val DAILY = Regex("""^(?:setiap hari|tiap hari|harian|daily)\s+(\d{1,2})[:.](\d{2})$""")
        private val ONCE = Regex("""^(?:sekali|once)\s+(\d{4}-\d{2}-\d{2})[ T](\d{1,2})[:.](\d{2})$""")

        fun parse(text: String): CronSpec {
            val t = text.trim().lowercase().replace(Regex("\\s+"), " ")
            EVERY.matchEntire(t)?.let { m ->
                val n = m.groupValues[1].toLong()
                val unit = m.groupValues[2]
                val minutes = if (unit in setOf("h", "j", "jam", "hour", "hours")) n * 60 else n
                require(minutes >= MIN_MINUTES) { "interval minimal $MIN_MINUTES menit (batas Android WorkManager)" }
                require(minutes <= 7 * 24 * 60) { "interval maksimal 7 hari" }
                return Every(minutes)
            }
            DAILY.matchEntire(t)?.let { m ->
                val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
                require(h in 0..23 && mi in 0..59) { "jam tidak valid: $h:$mi" }
                return Daily(h, mi)
            }
            ONCE.matchEntire(t)?.let { m ->
                val h = m.groupValues[2].toInt(); val mi = m.groupValues[3].toInt()
                require(h in 0..23 && mi in 0..59) { "jam tidak valid: $h:$mi" }
                val at = LocalDateTime.parse(
                    "${m.groupValues[1]}T${"%02d".format(h)}:${"%02d".format(mi)}"
                )
                return Once(at)
            }
            throw IllegalArgumentException(
                "format jadwal tidak dikenal: '$text'. Contoh: 'setiap 30 menit', " +
                    "'setiap hari 07:30', 'sekali 2026-11-28 09:00'"
            )
        }
    }

    fun describe(): String = when (this) {
        is Every -> if (minutes % 60 == 0L) "setiap ${minutes / 60} jam" else "setiap $minutes menit"
        is Daily -> "setiap hari %02d:%02d".format(hour, minute)
        is Once -> "sekali " + at.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }

    /** Jeda sampai eksekusi pertama dari [now] (≥ 0). */
    fun initialDelayMillis(now: ZonedDateTime): Long = when (this) {
        is Every -> minutes * 60_000L
        is Daily -> {
            var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            ChronoUnit.MILLIS.between(now, next)
        }
        is Once -> ChronoUnit.MILLIS.between(now, at.atZone(now.zone)).coerceAtLeast(0L)
    }

    /** Untuk "sekali": waktu sudah lewat → ditolak saat dibuat. */
    fun isInPast(now: ZonedDateTime): Boolean = this is Once && !at.atZone(now.zone).isAfter(now)
}

/** Satu tugas terjadwal. */
data class CronJob(
    val id: String,
    val name: String,
    val prompt: String,
    val schedule: String,
    val paused: Boolean = false,
    val createdAt: Long = 0L,
    val convId: Long = -1L,
    val lastRunAt: Long = 0L,
    val lastStatus: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("prompt", prompt).put("schedule", schedule)
        .put("paused", paused).put("createdAt", createdAt).put("convId", convId)
        .put("lastRunAt", lastRunAt).put("lastStatus", lastStatus)

    companion object {
        fun fromJson(o: JSONObject) = CronJob(
            id = o.getString("id"),
            name = o.optString("name"),
            prompt = o.optString("prompt"),
            schedule = o.optString("schedule"),
            paused = o.optBoolean("paused", false),
            createdAt = o.optLong("createdAt", 0L),
            convId = o.optLong("convId", -1L),
            lastRunAt = o.optLong("lastRunAt", 0L),
            lastStatus = o.optString("lastStatus", "")
        )
    }
}

/**
 * Penyimpanan tugas terjadwal di `filesDir/agent/cron_jobs.json` (bukan Room —
 * skema kontrak tidak berubah). Tulis atomik; maks [MAX_JOBS] tugas.
 */
class CronJobStore(private val file: File) {
    companion object {
        const val MAX_JOBS = 20
        const val MAX_PROMPT = 2_000
        fun forApp(filesDir: File) = CronJobStore(File(filesDir, "agent/cron_jobs.json"))
    }

    @Synchronized
    fun all(): List<CronJob> = runCatching {
        if (!file.isFile) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { CronJob.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun get(id: String): CronJob? = all().firstOrNull { it.id == id }

    @Synchronized
    fun add(name: String, prompt: String, schedule: String, now: Long): CronJob {
        val jobs = all()
        require(jobs.size < MAX_JOBS) { "batas $MAX_JOBS tugas terjadwal tercapai" }
        require(prompt.isNotBlank()) { "prompt tugas kosong" }
        val job = CronJob(
            id = UUID.randomUUID().toString().take(8),
            name = name.trim().ifBlank { prompt.trim().take(40) }.take(60),
            prompt = prompt.trim().take(MAX_PROMPT),
            schedule = schedule,
            createdAt = now
        )
        save(jobs + job)
        return job
    }

    @Synchronized
    fun update(id: String, f: (CronJob) -> CronJob): CronJob {
        val jobs = all()
        val cur = jobs.firstOrNull { it.id == id } ?: throw IllegalArgumentException("tugas '$id' tidak ada")
        val next = f(cur)
        save(jobs.map { if (it.id == id) next else it })
        return next
    }

    @Synchronized
    fun remove(id: String) {
        val jobs = all()
        require(jobs.any { it.id == id }) { "tugas '$id' tidak ada" }
        save(jobs.filterNot { it.id == id })
    }

    private fun save(jobs: List<CronJob>) {
        file.parentFile?.mkdirs()
        val arr = JSONArray().apply { jobs.forEach { put(it.toJson()) } }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(arr.toString())
            tmp.delete()
        }
    }
}

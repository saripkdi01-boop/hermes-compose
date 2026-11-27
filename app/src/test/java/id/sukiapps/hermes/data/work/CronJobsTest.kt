package id.sukiapps.hermes.data.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class CronJobsTest {
    @get:Rule val tmp = TemporaryFolder()
    private val now = ZonedDateTime.of(2026, 11, 27, 13, 0, 0, 0, ZoneId.of("Asia/Jakarta"))

    @Test fun parse_indonesianAndEnglish() {
        assertEquals(CronSpec.Every(30), CronSpec.parse("setiap 30 menit"))
        assertEquals(CronSpec.Every(120), CronSpec.parse("Every 2h"))
        assertEquals(CronSpec.Every(60), CronSpec.parse("tiap 1 jam"))
        assertEquals(CronSpec.Daily(7, 30), CronSpec.parse("setiap hari 07:30"))
        assertEquals(CronSpec.Daily(21, 5), CronSpec.parse("daily 21.05"))
        assertEquals(CronSpec.Once(LocalDateTime.of(2026, 11, 28, 9, 0)), CronSpec.parse("sekali 2026-11-28 09:00"))
    }

    @Test fun describe_roundTripsThroughParse() {
        for (s in listOf("setiap 45 menit", "setiap 3 jam", "setiap hari 06:15", "sekali 2026-12-01 18:30")) {
            val spec = CronSpec.parse(s)
            assertEquals(spec, CronSpec.parse(spec.describe()))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun belowWorkManagerMinimum_rejected() { CronSpec.parse("setiap 5 menit") }

    @Test(expected = IllegalArgumentException::class)
    fun garbage_rejected() { CronSpec.parse("kapan-kapan") }

    @Test fun initialDelay() {
        assertEquals(30 * 60_000L, CronSpec.Every(30).initialDelayMillis(now))
        assertEquals(18L * 3_600_000 + 30 * 60_000, CronSpec.Daily(7, 30).initialDelayMillis(now)) // besok 07:30
        assertEquals(2L * 3_600_000, CronSpec.Daily(15, 0).initialDelayMillis(now))              // hari ini 15:00
        assertEquals(20L * 3_600_000, CronSpec.Once(LocalDateTime.of(2026, 11, 28, 9, 0)).initialDelayMillis(now))
        assertTrue(CronSpec.Once(LocalDateTime.of(2026, 11, 27, 12, 0)).isInPast(now))
    }

    @Test fun store_add_update_remove_persists() {
        val f = File(tmp.root, "agent/cron_jobs.json")
        val store = CronJobStore(f)
        val j = store.add("Brief pagi", "Ringkas rencana hari ini", "setiap hari 07:00", 1L)
        assertEquals(1, CronJobStore(f).all().size)
        store.update(j.id) { it.copy(paused = true, lastStatus = "ok") }
        val reloaded = CronJobStore(f).get(j.id)!!
        assertTrue(reloaded.paused); assertEquals("ok", reloaded.lastStatus)
        store.remove(j.id)
        assertTrue(CronJobStore(f).all().isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun store_emptyPrompt_rejected() { CronJobStore(File(tmp.root, "c.json")).add("x", "  ", "setiap 1 jam", 0) }
}

package id.sukiapps.hermes.domain.manus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifikasi siklus penuh state machine Manus di JVM, tanpa Android.
 * Clock dipalsukan supaya hasil deterministik.
 */
class ManusReducerTest {

    private var fakeNow = 1_000L

    private val clock = { fakeNow }

    private fun lowRisk(title: String) =
        AgentStep(id = "", title = title, tool = ToolCall("read_file"))

    private fun submit(plan: List<String>): ManusEvent =
        ManusEvent.SubmitGoal(goal = "uji", plan = plan)

    private fun ManusUiState.task(id: String): AgentTask =
        tasks.first { it.id == id }

    @Test
    fun submitGoal_membuatTaskIdle_denganLangkahRencana() {
        val state = ManusReducer.reduce(ManusUiState.empty(), submit(listOf("baca", "tulis")), clock)

        val task = state.tasks.single()
        assertEquals(TaskStatus.IDLE, task.status)
        assertEquals(2, task.steps.size)
        assertEquals("baca", task.steps[0].title)
        assertEquals("tulis", task.steps[1].title)
        assertTrue(state.running)
    }

    @Test
    fun taskLowRisk_selesaiPenuhTanpaIzin() {
        var state = ManusUiState.empty()
        state = ManusReducer.reduce(state, submit(listOf("baca file", "ringkas")), clock)
        val taskId = state.tasks.single().id
        state = ManusReducer.reduce(state, ManusEvent.StartTask(taskId), clock)
        state = ManusReducer.reduce(
            state,
            ManusEvent.ToolFinished(taskId, "$taskId-s1", "isi file"),
            clock
        )
        state = ManusReducer.reduce(
            state,
            ManusEvent.ToolFinished(taskId, "$taskId-s2", "ringkasan"),
            clock
        )

        val done = state.task(taskId)
        assertEquals(TaskStatus.COMPLETED, done.status)
        assertEquals(1f, done.progress, 0.001f)
        assertNull(state.pendingPermission)
        assertTrue(state.logs.any { it.message.contains("selesai") })
    }

    @Test
    fun toolHighRisk_mintaIzin_danBerhentiSampaiDijawab() {
        var state = ManusUiState.empty()
        state = ManusReducer.reduce(state, ManusEvent.SubmitGoal("uji", listOf("hapus file")), clock)
        // injeksikan tool berisiko secara manual lewat request eksplisit
        val taskId = state.tasks.single().id
        state = ManusReducer.reduce(
            state,
            ManusEvent.RequestPermission(
                taskId = taskId,
                stepId = "$taskId-s1",
                tool = ToolCall("delete_file", mapOf("path" to "/tmp/x")),
                reason = "uji gate",
                risk = RiskLevel.HIGH
            ),
            clock
        )

        val gated = state.task(taskId)
        assertEquals(TaskStatus.WAITING_PERMISSION, gated.status)
        assertNotNull(state.pendingPermission)
        assertEquals(RiskLevel.HIGH, state.pendingPermission!!.risk)

        state = ManusReducer.reduce(state, ManusEvent.PermissionDenied(taskId, "tidak boleh"), clock)
        val denied = state.task(taskId)
        assertEquals(TaskStatus.FAILED, denied.status)
        assertNull(state.pendingPermission)
        assertEquals(StepStatus.BLOCKED, denied.steps[0].status)
    }

    @Test
    fun izinDiberikan_lanjutKeStatusRunning() {
        var state = ManusUiState.empty()
        state = ManusReducer.reduce(state, submit(listOf("jalankan perintah")), clock)
        val taskId = state.tasks.single().id
        state = ManusReducer.reduce(
            state,
            ManusEvent.RequestPermission(
                taskId = taskId,
                stepId = "$taskId-s1",
                tool = ToolCall("shell_exec"),
                reason = "uji",
                risk = RiskLevel.CRITICAL
            ),
            clock
        )
        state = ManusReducer.reduce(state, ManusEvent.PermissionGranted(taskId), clock)

        val running = state.task(taskId)
        assertEquals(TaskStatus.RUNNING, running.status)
        assertEquals(StepStatus.RUNNING, running.steps[0].status)
        assertNull(state.pendingPermission)
    }

    @Test
    fun toolGagal_menandaiTaskFailed() {
        var state = ManusUiState.empty()
        state = ManusReducer.reduce(state, submit(listOf("kirim pesan")), clock)
        val taskId = state.tasks.single().id
        state = ManusReducer.reduce(state, ManusEvent.StartTask(taskId), clock)
        state = ManusReducer.reduce(
            state,
            ManusEvent.ToolFailed(taskId, "$taskId-s1", "jaringan mati"),
            clock
        )

        val failed = state.task(taskId)
        assertEquals(TaskStatus.FAILED, failed.status)
        assertEquals("jaringan mati", failed.steps[0].error)
        assertTrue(state.logs.any { it.level == LogLevel.ERROR })
    }

    @Test
    fun pause_danResume_pertahankanKemajuan() {
        var state = ManusUiState.empty()
        state = ManusReducer.reduce(state, submit(listOf("a", "b")), clock)
        val taskId = state.tasks.single().id
        state = ManusReducer.reduce(state, ManusEvent.StartTask(taskId), clock)
        state = ManusReducer.reduce(
            state,
            ManusEvent.ToolFinished(taskId, "$taskId-s1", "ok"),
            clock
        )

        state = ManusReducer.reduce(state, ManusEvent.PauseTask(taskId), clock)
        assertEquals(TaskStatus.PAUSED, state.task(taskId).status)

        state = ManusReducer.reduce(state, ManusEvent.ResumeTask(taskId), clock)
        val resumed = state.task(taskId)
        assertEquals(TaskStatus.RUNNING, resumed.status)
        assertEquals(1, resumed.doneCount)
    }

    @Test
    fun eventUntukTaskTakAda_diabaikanTanpaCrash() {
        val state = ManusUiState.empty()
        val after = ManusReducer.reduce(state, ManusEvent.CancelTask("task-999"), clock)
        assertEquals(state.tasks, after.tasks)
    }

    @Test
    fun clearHistory_hanyaBuangTaskSelesai() {
        var state = ManusUiState.empty()
        state = ManusReducer.reduce(state, submit(listOf("a")), clock)
        val doneId = state.tasks.single().id
        state = ManusReducer.reduce(state, ManusEvent.StartTask(doneId), clock)
        state = ManusReducer.reduce(
            state,
            ManusEvent.ToolFinished(doneId, "$doneId-s1", "ok"),
            clock
        )
        state = ManusReducer.reduce(state, submit(listOf("b")), clock)
        assertEquals(2, state.tasks.size)

        state = ManusReducer.reduce(state, ManusEvent.ClearHistory, clock)
        assertEquals(1, state.tasks.size)
        assertFalse(state.tasks.single().isFinished)
    }

    @Test
    fun pemetaanRisikoTool_konsisten() {
        val reducer = ManusReducer
        assertEquals(RiskLevel.LOW, ToolCall("read_file").risk())
        assertEquals(RiskLevel.MEDIUM, ToolCall("write_file").risk())
        assertEquals(RiskLevel.HIGH, ToolCall("run_command").risk())
        assertEquals(RiskLevel.CRITICAL, ToolCall("selfupgrade_merge").risk())
        assertFalse(RiskLevel.LOW.requiresApproval)
        assertTrue(RiskLevel.MEDIUM.requiresApproval)
    }
}

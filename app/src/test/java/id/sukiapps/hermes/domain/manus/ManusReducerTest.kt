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
        // 14.22: langkah habis -> VERIFYING dulu, BUKAN langsung COMPLETED.
        // COMPLETED hanya keluar dari gerbang verifikasi.
        assertEquals(TaskStatus.VERIFYING, state.task(taskId).status)
        state = ManusReducer.reduce(
            state,
            ManusEvent.VerificationPassed(taskId, "Terverifikasi: 2/2 langkah tool lolos"),
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
        // Kartu izin lengkap: target persis, scope sekali-saja, rollback eksplisit.
        assertTrue(state.pendingPermission!!.targets.contains("path=/tmp/x"))
        assertEquals("once", state.pendingPermission!!.scope)
        assertFalse(state.pendingPermission!!.reversible)
        assertNotNull(state.pendingPermission!!.rollback)
        state = ManusReducer.reduce(state, ManusEvent.PermissionDenied(taskId, "tidak boleh"), clock)
        val denied = state.task(taskId)
        // Penolakan = langkah dilewati, BUKAN task gagal (kontrak masterprompt).
        // Satu-satunya langkah dilewati -> tidak ada sisa langkah -> VERIFYING
        // (14.22), lalu gerbang meloloskan (tidak ada langkah tool yang gagal).
        assertEquals(TaskStatus.VERIFYING, denied.status)
        state = ManusReducer.reduce(
            state,
            ManusEvent.VerificationPassed(taskId, "Terverifikasi (tanpa langkah tool)"),
            clock
        )
        val verified = state.task(taskId)
        assertEquals(TaskStatus.COMPLETED, verified.status)
        assertNull(state.pendingPermission)
        assertEquals(StepStatus.SKIPPED, denied.steps[0].status)
        assertEquals(1f, verified.progress, 0.001f)
    }

    @Test
    fun izinDitolak_tugasLanjutKeLangkahBerikutnya() {
        var state = ManusUiState.empty()
        state = ManusReducer.reduce(state, submit(listOf("hapus file", "ringkas")), clock)
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
        state = ManusReducer.reduce(state, ManusEvent.PermissionDenied(taskId, "tidak boleh"), clock)
        val lanjut = state.task(taskId)
        assertEquals(StepStatus.SKIPPED, lanjut.steps[0].status)
        // Langkah kedua tanpa tool = LOW -> langsung RUNNING, bukan FAILED.
        assertEquals(TaskStatus.RUNNING, lanjut.status)
        assertEquals(StepStatus.RUNNING, lanjut.steps[1].status)
        assertNull(state.pendingPermission)
    }

    @Test
    fun selectTask_danSteerTask_dicatatTanpaMengubahRencana() {
        var state = ManusUiState.empty()
        state = ManusReducer.reduce(state, submit(listOf("a")), clock)
        val taskId = state.tasks.single().id
        state = ManusReducer.reduce(state, ManusEvent.StartTask(taskId), clock)
        state = ManusReducer.reduce(state, ManusEvent.ToolFinished(taskId, "$taskId-s1", "ok"), clock)
        // 14.22: langkah habis -> VERIFYING, lalu VerificationPassed -> COMPLETED.
        assertEquals(TaskStatus.VERIFYING, state.task(taskId).status)
        state = ManusReducer.reduce(
            state,
            ManusEvent.VerificationPassed(taskId, "Terverifikasi"),
            clock
        )
        assertEquals(TaskStatus.COMPLETED, state.task(taskId).status)
        state = ManusReducer.reduce(state, ManusEvent.SubmitGoal("kedua", listOf("b")), clock)
        val keduaId = state.tasks.last().id
        state = ManusReducer.reduce(state, ManusEvent.SelectTask(taskId), clock)
        assertEquals(taskId, state.activeTaskId)
        state = ManusReducer.reduce(state, ManusEvent.SteerTask(keduaId, "fokus ke harga saja"), clock)
        assertTrue(state.logs.any { it.message.contains("fokus ke harga saja") })
        // Rencana tugas kedua tidak berubah diam-diam oleh arahan.
        assertEquals(listOf("b"), state.task(keduaId).steps.map { it.title })
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
        // risk() sekarang top-level di package — bisa dipanggil langsung.
        assertEquals(RiskLevel.LOW, ToolCall("read_file").risk())
        assertEquals(RiskLevel.MEDIUM, ToolCall("write_file").risk())
        assertEquals(RiskLevel.HIGH, ToolCall("run_command").risk())
        assertEquals(RiskLevel.CRITICAL, ToolCall("selfupgrade_merge").risk())
        // Nama tool tak dikenal harus default MEDIUM, bukan LOW — fail-safe:
        // tool asing tidak boleh lolos gate izin tanpa review.
        assertEquals(RiskLevel.MEDIUM, ToolCall("tool_asing_belum_dipetakan").risk())
        assertFalse(RiskLevel.LOW.requiresApproval)
        assertTrue(RiskLevel.MEDIUM.requiresApproval)
        assertTrue(RiskLevel.CRITICAL.requiresApproval)
    }
}

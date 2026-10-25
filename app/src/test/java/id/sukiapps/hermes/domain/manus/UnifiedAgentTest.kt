package id.sukiapps.hermes.domain.manus

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test logika baru agen tunggal (14.22): validasi rencana LLM,
 * aturan verifikasi bukti, dan transisi status baru (VERIFYING/STOPPED).
 * Murni JVM, tanpa Android. Clock dipalsukan seperti ManusReducerTest.
 */
class UnifiedAgentTest {
    private var fakeNow = 1_000L
    private val clock = { fakeNow }

    private fun submit(plan: List<String>): ManusEvent =
        ManusEvent.SubmitGoal(goal = "uji", plan = plan)

    private fun ManusUiState.task(id: String): AgentTask =
        tasks.first { it.id == id }

    private fun taskWithSteps(vararg steps: AgentStep): AgentTask =
        AgentTask(
            id = "t1",
            goal = "uji",
            steps = steps.toList(),
            status = TaskStatus.VERIFYING
        )

    // ---------------- PlanValidator ----------------

    @Test
    fun rencanaValid_diterimaUtuh() {
        val raw = listOf(
            RawStep("Ambil halaman", "web_fetch", mapOf("url" to "https://sukiapps.web.id")),
            RawStep("Info sistem", "system_info")
        )
        val out = PlanValidator.validate(raw, PlanValidator.knownTools())
        assertEquals(2, out.steps.size)
        assertTrue(out.dropped.isEmpty())
        assertEquals("web_fetch", out.steps[0].toolName)
        assertEquals("https://sukiapps.web.id", out.steps[0].args.optString("url"))
    }

    @Test
    fun toolTakDikenal_dibuangDenganAlasan() {
        val raw = listOf(
            RawStep("Hapus semua", "rm_rf", mapOf("path" to "/")),
            RawStep("Info sistem", "system_info")
        )
        val out = PlanValidator.validate(raw, PlanValidator.knownTools())
        assertEquals(1, out.steps.size)
        assertEquals("system_info", out.steps[0].toolName)
        assertEquals(1, out.dropped.size)
        assertTrue(out.dropped[0].contains("rm_rf"))
    }

    @Test
    fun argumenWajibHilang_dibuang() {
        val raw = listOf(
            RawStep("Ambil halaman", "web_fetch", emptyMap()),
            RawStep("Baca file", "file_read", mapOf("path" to "/tmp/x"))
        )
        val out = PlanValidator.validate(raw, PlanValidator.knownTools())
        assertEquals(1, out.steps.size)
        assertEquals("file_read", out.steps[0].toolName)
        assertTrue(out.dropped[0].contains("url"))
    }

    @Test
    fun batasDelapanLangkah_dipotong() {
        val raw = (1..10).map {
            RawStep("Langkah $it", "system_info")
        }
        val out = PlanValidator.validate(raw, PlanValidator.knownTools())
        assertEquals(PlanValidator.MAX_STEPS, out.steps.size)
        assertEquals(2, out.dropped.size)
    }

    @Test
    fun judulKosong_dibuang() {
        val raw = listOf(
            RawStep("   ", "system_info"),
            RawStep("Info sistem", "system_info")
        )
        val out = PlanValidator.validate(raw, PlanValidator.knownTools())
        assertEquals(1, out.steps.size)
        assertEquals("Info sistem", out.steps[0].title)
    }

    // ---------------- TaskVerifier ----------------

    @Test
    fun webFetch_outputKosong_gagal() = runBlocking {
        val task = taskWithSteps(
            AgentStep(
                id = "t1-s1", title = "Ambil halaman",
                tool = ToolCall("web_fetch", mapOf("url" to "https://x")),
                status = StepStatus.DONE, output = "   "
            )
        )
        val result = TaskVerifier.verify(task) { null }
        assertFalse(result.passed)
        assertTrue(result.reason!!.contains("output kosong"))
    }

    @Test
    fun webFetch_outputNonKosong_lolos() = runBlocking {
        val task = taskWithSteps(
            AgentStep(
                id = "t1-s1", title = "Ambil halaman",
                tool = ToolCall("web_fetch", mapOf("url" to "https://x")),
                status = StepStatus.DONE, output = "isi halaman"
            )
        )
        val result = TaskVerifier.verify(task) { null }
        assertTrue(result.passed)
        assertTrue(result.evidence.contains("1/1"))
    }

    @Test
    fun fileWrite_readBackCocok_lolos() = runBlocking {
        val task = taskWithSteps(
            AgentStep(
                id = "t1-s1", title = "Tulis file",
                tool = ToolCall("file_write", mapOf("path" to "/tmp/a", "content" to "halo")),
                status = StepStatus.DONE, output = "ok"
            )
        )
        val result = TaskVerifier.verify(task) { "halo\n" }
        assertTrue(result.passed)
    }

    @Test
    fun fileWrite_readBackTidakCocok_gagal() = runBlocking {
        val task = taskWithSteps(
            AgentStep(
                id = "t1-s1", title = "Tulis file",
                tool = ToolCall("file_write", mapOf("path" to "/tmp/a", "content" to "halo")),
                status = StepStatus.DONE, output = "ok"
            )
        )
        val result = TaskVerifier.verify(task) { "lain" }
        assertFalse(result.passed)
        assertTrue(result.reason!!.contains("tidak cocok"))
    }

    @Test
    fun fileWrite_readBackNull_gagal() = runBlocking {
        val task = taskWithSteps(
            AgentStep(
                id = "t1-s1", title = "Tulis file",
                tool = ToolCall("file_write", mapOf("path" to "/tmp/a", "content" to "halo")),
                status = StepStatus.DONE, output = "ok"
            )
        )
        val result = TaskVerifier.verify(task) { null }
        assertFalse(result.passed)
        assertTrue(result.reason!!.contains("dibaca balik"))
    }

    @Test
    fun langkahSkipped_dikecualikanDariVerifikasi() = runBlocking {
        val task = taskWithSteps(
            AgentStep(
                id = "t1-s1", title = "Hapus file",
                tool = ToolCall("delete_file", mapOf("path" to "/tmp/x")),
                status = StepStatus.SKIPPED
            ),
            AgentStep(
                id = "t1-s2", title = "Info sistem",
                tool = ToolCall("system_info"),
                status = StepStatus.DONE, output = "Linux"
            )
        )
        val result = TaskVerifier.verify(task) { null }
        assertTrue(result.passed)
        assertTrue(result.evidence.contains("1/1"))
    }

    @Test
    fun tanpaLangkahTool_lolosDenganCatatanManual() = runBlocking {
        val task = taskWithSteps(
            AgentStep(
                id = "t1-s1", title = "Analisis hasil",
                status = StepStatus.DONE, output = null
            )
        )
        val result = TaskVerifier.verify(task) { null }
        assertTrue(result.passed)
        assertTrue(result.evidence.contains("manual"))
    }

    // ---------------- Reducer: VERIFYING / STOPPED / ReplacePlan ----------------

    @Test
    fun advance_tanpaLangkahBerikutnya_menujuVerifying_bukanCompleted() {
        var state = ManusUiState.empty()
        state = ManusReducer.reduce(state, submit(listOf("satu")), clock)
        val taskId = state.tasks.single().id
        state = ManusReducer.reduce(state, ManusEvent.StartTask(taskId), clock)
        state = ManusReducer.reduce(
            state, ManusEvent.ToolFinished(taskId, "$taskId-s1", "ok"), clock
        )
        assertEquals(TaskStatus.VERIFYING, state.task(taskId).status)
    }

    @Test
    fun verificationPassed_dariVerifying_menujuCompletedDenganBukti() {
        var state = ManusUiState.empty()
        state = ManusReducer.reduce(state, submit(listOf("satu")), clock)
        val taskId = state.tasks.single().id
        state = ManusReducer.reduce(state, ManusEvent.StartTask(taskId), clock)
        state = ManusReducer.reduce(
            state, ManusEvent.ToolFinished(taskId, "$taskId-s1", "ok"), clock
        )
        state = ManusReducer.reduce(
            state, ManusEvent.VerificationPassed(taskId, "Terverifikasi: 1/1"), clock
        )
        val done = state.task(taskId)
        assertEquals(TaskStatus.COMPLETED, done.status)
        assertEquals("Terverifikasi: 1/1", done.verificationEvidence)
    }

    @Test
    fun verificationFailed_dariVerifying_menujuStoppedDenganAlasan() {
        var state = ManusUiState.empty()
        state = ManusReducer.reduce(state, submit(listOf("satu")), clock)
        val taskId = state.tasks.single().id
        state = ManusReducer.reduce(state, ManusEvent.StartTask(taskId), clock)
        state = ManusReducer.reduce(
            state, ManusEvent.ToolFinished(taskId, "$taskId-s1", "ok"), clock
        )
        state = ManusReducer.reduce(
            state, ManusEvent.VerificationFailed(taskId, "output kosong"), clock
        )
        val stopped = state.task(taskId)
        assertEquals(TaskStatus.STOPPED, stopped.status)
        assertEquals("output kosong", stopped.stopReason)
        assertTrue(stopped.isFinished)
    }

    @Test
    fun verificationPassed_dariRunning_diabaikan() {
        var state = ManusUiState.empty()
        state = ManusReducer.reduce(state, submit(listOf("satu", "dua")), clock)
        val taskId = state.tasks.single().id
        state = ManusReducer.reduce(state, ManusEvent.StartTask(taskId), clock)
        assertEquals(TaskStatus.RUNNING, state.task(taskId).status)
        state = ManusReducer.reduce(
            state, ManusEvent.VerificationPassed(taskId, "palsu"), clock
        )
        assertEquals(TaskStatus.RUNNING, state.task(taskId).status)
        assertNull(state.task(taskId).verificationEvidence)
    }

    @Test
    fun resumeStopped_kembaliKeVerifying_untukVerifikasiUlang() {
        var state = ManusUiState.empty()
        state = ManusReducer.reduce(state, submit(listOf("satu")), clock)
        val taskId = state.tasks.single().id
        state = ManusReducer.reduce(state, ManusEvent.StartTask(taskId), clock)
        state = ManusReducer.reduce(
            state, ManusEvent.ToolFinished(taskId, "$taskId-s1", "ok"), clock
        )
        state = ManusReducer.reduce(
            state, ManusEvent.VerificationFailed(taskId, "output kosong"), clock
        )
        assertEquals(TaskStatus.STOPPED, state.task(taskId).status)
        state = ManusReducer.reduce(state, ManusEvent.ResumeStopped(taskId), clock)
        val resumed = state.task(taskId)
        assertEquals(TaskStatus.VERIFYING, resumed.status)
        assertNull(resumed.stopReason)
    }

    @Test
    fun replacePlan_saatIdle_menggantiLangkahDanLabel() {
        var state = ManusUiState.empty()
        state = ManusReducer.reduce(state, submit(listOf("Merencanakan langkah…")), clock)
        val taskId = state.tasks.single().id
        state = ManusReducer.reduce(
            state,
            ManusEvent.ReplacePlan(
                taskId, listOf("Ambil halaman", "Ringkas"), "LLM"
            ),
            clock
        )
        val task = state.task(taskId)
        assertEquals(TaskStatus.IDLE, task.status)
        assertEquals(listOf("Ambil halaman", "Ringkas"), task.steps.map { it.title })
        assertEquals("LLM", task.planLabel)
        assertEquals("$taskId-s1", task.steps[0].id)
        assertEquals("$taskId-s2", task.steps[1].id)
    }

    @Test
    fun replacePlan_saatRunning_diabaikan() {
        var state = ManusUiState.empty()
        state = ManusReducer.reduce(state, submit(listOf("satu")), clock)
        val taskId = state.tasks.single().id
        state = ManusReducer.reduce(state, ManusEvent.StartTask(taskId), clock)
        state = ManusReducer.reduce(
            state,
            ManusEvent.ReplacePlan(taskId, listOf("lain"), "LLM"),
            clock
        )
        val task = state.task(taskId)
        assertEquals(listOf("satu"), task.steps.map { it.title })
        assertEquals("", task.planLabel)
    }
}

package id.sukiapps.hermes.domain.manus

import id.sukiapps.hermes.tool.ToolLoop
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 14.27 (B6+B8): batas sesi ditegakkan + usage asli provider.
 * Reducer + parse diuji murni di JVM; penegakan runner diuji lewat
 * langkah TANPA tool (tidak menyentuh ToolLoop sama sekali).
 */
class SessionLimitsTest {

    private var fakeNow = 1_000L
    private val clock = { fakeNow }

    private fun planResponse(usageJson: String?): String {
        val usagePart = if (usageJson != null) ", \"usage\": $usageJson" else ""
        return """{"choices": [{"message": {"content": "[{\"title\": \"baca\", \"tool\": \"file_read\", \"args\": {\"path\": \"x\"}}]"}}]$usagePart}"""
    }

    @Test
    fun parse_membacaUsageAsli_dariResponsProvider() {
        val result = LlmPlanner.parse(
            planResponse("""{"prompt_tokens": 120, "completion_tokens": 30, "total_tokens": 150}""")
        )
        assertTrue(result is LlmPlanResult.Success)
        val ok = result as LlmPlanResult.Success
        assertEquals(150, ok.usageTotal)
        assertEquals(120, ok.usagePrompt)
        assertEquals(30, ok.usageCompletion)
    }

    @Test
    fun parse_tanpaFieldUsage_hasilNull_bukanKarangan() {
        val result = LlmPlanner.parse(planResponse(null))
        assertTrue(result is LlmPlanResult.Success)
        val ok = result as LlmPlanResult.Success
        assertNull(ok.usageTotal)
        assertNull(ok.usagePrompt)
        assertNull(ok.usageCompletion)
    }

    @Test
    fun parse_usageTanpaTotal_dijumlahDariPromptDanCompletion() {
        val result = LlmPlanner.parse(
            planResponse("""{"prompt_tokens": 80, "completion_tokens": 20}""")
        )
        val ok = result as LlmPlanResult.Success
        assertEquals(100, ok.usageTotal)
    }

    @Test
    fun replacePlan_menyimpanUsage_danTokenAwal() {
        var state = ManusReducer.reduce(
            ManusUiState.empty(),
            ManusEvent.SubmitGoal("uji", listOf("baca")),
            clock
        )
        val taskId = state.tasks.single().id
        state = ManusReducer.reduce(
            state,
            ManusEvent.ReplacePlan(
                taskId, listOf("baca"), "LLM",
                usageTotal = 150, usagePrompt = 120, usageCompletion = 30,
                initialTokens = 150
            ),
            clock
        )
        val task = state.tasks.single()
        assertEquals(150, task.plannerUsageTotal)
        assertEquals(150, task.tokensUsed)
        assertTrue(task.tokensReal)
        assertEquals("Token: 150 (planner asli: 150, prompt 120 + completion 30)", task.usageLine())
    }

    @Test
    fun replacePlan_tanpaUsage_tokenAwalEstimasi_danLabelKasar() {
        var state = ManusReducer.reduce(
            ManusUiState.empty(),
            ManusEvent.SubmitGoal("uji", listOf("baca")),
            clock
        )
        val taskId = state.tasks.single().id
        state = ManusReducer.reduce(
            state,
            ManusEvent.ReplacePlan(taskId, listOf("baca"), "LLM", initialTokens = 40),
            clock
        )
        val task = state.tasks.single()
        assertNull(task.plannerUsageTotal)
        assertFalse(task.tokensReal)
        assertEquals("Token: ~40 (estimasi kasar)", task.usageLine())
    }

    @Test
    fun limitReached_dariRunning_menjadiStopped_denganAlasan() {
        var state = ManusReducer.reduce(
            ManusUiState.empty(),
            ManusEvent.SubmitGoal("uji", listOf("baca")),
            clock
        )
        val taskId = state.tasks.single().id
        state = ManusReducer.reduce(state, ManusEvent.StartTask(taskId), clock)
        assertEquals(TaskStatus.RUNNING, state.tasks.single().status)
        state = ManusReducer.reduce(
            state,
            ManusEvent.LimitReached(taskId, "Batas iterasi tercapai (2/2)"),
            clock
        )
        val task = state.tasks.single()
        assertEquals(TaskStatus.STOPPED, task.status)
        assertEquals("Batas iterasi tercapai (2/2)", task.stopReason)
        assertNull(state.pendingPermission)
    }

    @Test
    fun usageUpdated_memperbaruiAngka_tanpaMengubahStatus() {
        var state = ManusReducer.reduce(
            ManusUiState.empty(),
            ManusEvent.SubmitGoal("uji", listOf("baca")),
            clock
        )
        val taskId = state.tasks.single().id
        state = ManusReducer.reduce(state, ManusEvent.StartTask(taskId), clock)
        state = ManusReducer.reduce(state, ManusEvent.UsageUpdated(taskId, 250), clock)
        val task = state.tasks.single()
        assertEquals(250, task.tokensUsed)
        assertEquals(TaskStatus.RUNNING, task.status)
    }

    /** Jalankan runner dengan langkah tanpa tool; kembalikan state akhir + event. */
    private fun runRunner(
        initial: ManusUiState,
        taskId: String,
        limits: SessionLimits
    ): Pair<ManusUiState, List<ManusEvent>> {
        var state = initial
        val events = ArrayList<ManusEvent>()
        val runner = AgentRunner(toolLoop = ToolLoop) { event ->
            events += event
            state = ManusReducer.reduce(state, event, clock)
        }
        runBlocking { runner.runTask(taskId, { state }, limits) }
        return state to events
    }

    private fun startedState(plan: List<String>): Pair<ManusUiState, String> {
        var state = ManusReducer.reduce(
            ManusUiState.empty(),
            ManusEvent.SubmitGoal("uji", plan),
            clock
        )
        val taskId = state.tasks.single().id
        state = ManusReducer.reduce(state, ManusEvent.StartTask(taskId), clock)
        return state to taskId
    }

    @Test
    fun runner_batasIterasi_menghentikanTugas_denganAlasan() {
        val (initial, taskId) = startedState(listOf("a", "b", "c"))
        val (final, events) = runRunner(
            initial, taskId,
            SessionLimits(maxIterations = 1, timeoutMs = 60_000, tokenBudget = 100_000)
        )
        val task = final.tasks.single()
        assertEquals(TaskStatus.STOPPED, task.status)
        assertTrue(task.stopReason!!.contains("iterasi"))
        assertTrue(events.any { it is ManusEvent.LimitReached })
        // Hanya 1 langkah sempat dieksekusi; sisanya tidak jalan.
        assertEquals(1, task.steps.count { it.status == StepStatus.DONE })
    }

    @Test
    fun runner_budgetTercapai_menghentikanSebelumLangkahJalan() {
        var (initial, taskId) = startedState(listOf("a", "b"))
        initial = ManusReducer.reduce(initial, ManusEvent.UsageUpdated(taskId, 100_000), clock)
        val (final, events) = runRunner(
            initial, taskId,
            SessionLimits(maxIterations = 10, timeoutMs = 60_000, tokenBudget = 100_000)
        )
        val task = final.tasks.single()
        assertEquals(TaskStatus.STOPPED, task.status)
        assertTrue(task.stopReason!!.contains("Budget token"))
        assertTrue(events.any { it is ManusEvent.LimitReached })
        assertEquals(0, task.steps.count { it.status == StepStatus.DONE })
    }

    @Test
    fun runner_tanpaBatasTerlampaui_tidakMengirimLimitReached() {
        val (initial, taskId) = startedState(listOf("a"))
        val (final, events) = runRunner(
            initial, taskId,
            SessionLimits(maxIterations = 10, timeoutMs = 60_000, tokenBudget = 100_000)
        )
        assertFalse(events.any { it is ManusEvent.LimitReached })
        // Langkah habis -> gerbang VERIFYING (runner memverifikasi lalu berhenti).
        assertTrue(final.tasks.single().status != TaskStatus.STOPPED)
    }
}

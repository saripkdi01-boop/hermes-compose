package id.sukiapps.hermes.ui.chat

import id.sukiapps.hermes.domain.manus.AgentStep
import id.sukiapps.hermes.domain.manus.AgentTask
import id.sukiapps.hermes.domain.manus.StepStatus
import id.sukiapps.hermes.domain.manus.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManusChatSummaryTest {

    private fun task(vararg steps: AgentStep) = AgentTask(
        id = "t1",
        goal = "Riset sukiapps",
        steps = steps.toList(),
        status = TaskStatus.COMPLETED
    )

    @Test
    fun summaryCountsStepsAndIncludesDoneOutputs() {
        val out = manusSummary(
            task(
                AgentStep(id = "s1", title = "Snapshot", status = StepStatus.DONE, output = "ok"),
                AgentStep(id = "s2", title = "Lewat", status = StepStatus.SKIPPED),
                AgentStep(id = "s3", title = "Gagal", status = StepStatus.FAILED)
            )
        )
        assertTrue(out.startsWith("🤖 Manus selesai: Riset sukiapps"))
        assertTrue(out.contains("1 selesai, 1 dilewati, 1 gagal, dari 3 langkah"))
        assertTrue(out.contains("- Snapshot: ok"))
        // Langkah gagal/tanpa output tidak ikut daftar output.
        assertTrue(!out.contains("Gagal:"))
    }

    @Test
    fun summaryWithoutOutputsIsJustHeader() {
        val out = manusSummary(task(AgentStep(id = "s1", title = "A", status = StepStatus.DONE)))
        assertEquals(
            "🤖 Manus selesai: Riset sukiapps\n" +
                "Status: COMPLETED • 1 selesai, 0 dilewati, 0 gagal, dari 1 langkah",
            out
        )
    }

    @Test
    fun summaryTruncatesLongOutputTo200Chars() {
        val long = "x".repeat(300)
        val out = manusSummary(
            task(AgentStep(id = "s1", title = "A", status = StepStatus.DONE, output = long))
        )
        val line = out.lines().last()
        assertTrue(line.startsWith("- A: "))
        assertEquals(200, line.removePrefix("- A: ").length)
    }

    @Test
    fun stepIconsCoverAllStatuses() {
        assertEquals("⏳", manusStepIcon(StepStatus.PENDING))
        assertEquals("▶", manusStepIcon(StepStatus.RUNNING))
        assertEquals("✓", manusStepIcon(StepStatus.DONE))
        assertEquals("✗", manusStepIcon(StepStatus.FAILED))
        assertEquals("⛔", manusStepIcon(StepStatus.BLOCKED))
        assertEquals("⊘", manusStepIcon(StepStatus.SKIPPED))
    }
}

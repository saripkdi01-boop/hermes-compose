package id.sukiapps.hermes.manus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifikasi state machine Manus di JVM, tanpa Android.
 * Urutan uji: submit -> plan -> propose(izin) -> grant -> eksekusi -> selesai.
 */
class ManusReducerTest {

    private var tick = 1_000L
    private val clock: () -> Long = { tick }
    private val ids: (String) -> String = { prefix -> "$prefix-${tick++}" }

    private fun fresh() = ManusUiState()

    private fun submit(state: ManusUiState, goal: String): ManusUiState =
        ManusReducer.reduce(
            state,
            ManusAction.SubmitGoal(goal),
            clock,
            ids
        )

    private fun advance(state: ManusUiState): ManusUiState =
        ManusReducer.reduce(
            state,
            ManusAction.AdvanceActiveTask,
            clock,
            ids
        )

    @Test
    fun submitGoal_kosong_ditolak_dengan_error() {
        val s = ManusReducer.reduce(fresh(), ManusAction.SubmitGoal("   "), clock, ids)
        assertEquals("Goal kosong.", s.lastError)
        assertTrue(s.tasks.isEmpty())
    }

    @Test
    fun submitGoal_buatTask_planning() {
        val s = submit(fresh(), "baca file readme")
        assertNull(s.lastError)
        assertEquals(1, s.tasks.size)
        assertEquals(AgentTaskStatus.PLANNING, s.activeTask?.status)
        assertTrue(s.events.last() is ManusEvent.TaskCreated)
    }

    @Test
    fun advance_planning_jadi_running_dengan_rencana() {
        val s = advance(submit(fresh(), "baca file readme"))
        val task = s.activeTask!!
        assertEquals(AgentTaskStatus.RUNNING, task.status)
        assertEquals(1, task.steps.size)
        assertEquals(StepStatus.PENDING, task.steps.first().status)
        assertTrue(s.events.any { it is ManusEvent.PlanReady })
    }

    @Test
    fun step_perlu_izin_muncul_permission_request() {
        val s = advance(submit(fresh(), "baca file readme"))
        val next = advance(s)
        val task = next.activeTask!!
        assertEquals(AgentTaskStatus.AWAITING_PERMISSION, task.status)
        assertEquals(1, next.pendingPermissions.size)
        assertEquals(PermissionScope.FS_READ, next.pendingPermissions.first().scope)
        assertTrue(next.events.any { it is ManusEvent.ToolProposed })
        assertTrue(next.events.any { it is ManusEvent.PermissionRequested })
    }

    @Test
    fun izin_diterima_lalu_advance_menjalankan_step() {
        var s = advance(submit(fresh(), "baca file readme"))
        s = advance(s)
        val req = s.pendingPermissions.first()
        s = ManusReducer.reduce(
            s,
            ManusAction.ResolvePermission(req.id, granted = true),
            clock,
            ids
        )
        val task = s.activeTask!!
        assertEquals(AgentTaskStatus.RUNNING, task.status)
        val run = task.steps.first().toolRuns.first()
        assertEquals(ToolRunStatus.APPROVED, run.status)

        s = advance(s)
        val done = s.activeTask!!
        assertEquals(AgentTaskStatus.DONE, done.status)
        assertEquals(StepStatus.DONE, done.steps.first().status)
        assertTrue(s.pendingPermissions.none { it.isPending })
    }

    @Test
    fun izin_ditolak_task_gagal() {
        var s = advance(submit(fresh(), "baca file readme"))
        s = advance(s)
        val req = s.pendingPermissions.first()
        s = ManusReducer.reduce(
            s,
            ManusAction.ResolvePermission(req.id, granted = false),
            clock,
            ids
        )
        val task = s.activeTask!!
        assertEquals(AgentTaskStatus.FAILED, task.status)
        assertEquals(StepStatus.FAILED, task.steps.first().status)
        assertEquals("Izin ditolak user.", task.steps.first().error)
        assertEquals(ToolRunStatus.DENIED, task.steps.first().toolRuns.first().status)
    }

    @Test
    fun multi_step_berjalan_berurutan() {
        val goal = "baca file readme\nshell jalankan\nbaca docs"
        var s = advance(submit(fresh(), goal))
        assertEquals(3, s.activeTask!!.steps.size)

        // Step 0: baca -> izin FS_READ
        s = advance(s)
        var task = s.activeTask!!
        assertEquals(AgentTaskStatus.AWAITING_PERMISSION, task.status)
        assertEquals(StepStatus.RUNNING, task.steps[0].status)

        s = ManusReducer.reduce(
            s,
            ManusAction.ResolvePermission(s.pendingPermissions.first().id, true),
            clock,
            ids
        )
        s = advance(s)
        task = s.activeTask!!
        assertEquals(StepStatus.DONE, task.steps[0].status)
        assertEquals(StepStatus.PENDING, task.steps[1].status)
        assertEquals(AgentTaskStatus.AWAITING_PERMISSION, task.status)

        // Step 1: shell -> izin SHELL
        s = ManusReducer.reduce(
            s,
            ManusAction.ResolvePermission(s.pendingPermissions.first().id, true),
            clock,
            ids
        )
        s = advance(s)
        task = s.activeTask!!
        assertEquals(StepStatus.DONE, task.steps[1].status)
        assertEquals(StepStatus.RUNNING, task.steps[2].status)

        // Step 2: baca lagi -> izin FS_READ
        s = ManusReducer.reduce(
            s,
            ManusAction.ResolvePermission(s.pendingPermissions.first().id, true),
            clock,
            ids
        )
        s = advance(s)
        assertEquals(AgentTaskStatus.DONE, s.activeTask!!.status)
        assertEquals(1f, s.activeTask!!.progress, 0.001f)
    }

    @Test
    fun autoApprove_skip_tapi_bukan_secrets() {
        var s = ManusReducer.reduce(
            fresh(),
            ManusAction.ToggleAutoApprove(true),
            clock,
            ids
        )
        s = advance(submit(s, "baca file readme"))
        s = advance(s)
        // autoApprove: run langsung APPROVED, tanpa request menunggu.
        assertEquals(0, s.pendingPermissions.count { it.isPending })
        assertEquals(ToolRunStatus.APPROVED, s.activeTask!!.steps[0].toolRuns.first().status)

        s = advance(s)
        assertEquals(AgentTaskStatus.DONE, s.activeTask!!.status)
    }

    @Test
    fun autoApprove_tidak_boleh_lewatkan_secrets() {
        var s = ManusReducer.reduce(
            fresh(),
            ManusAction.ToggleAutoApprove(true),
            clock,
            ids
        )
        s = advance(submit(s, "ambil token api"))
        s = advance(s)
        // SECRETS tetap minta izin walau autoApprove nyala.
        assertEquals(1, s.pendingPermissions.count { it.isPending })
        assertEquals(PermissionScope.SECRETS, s.pendingPermissions.first().scope)
        assertEquals(AgentTaskStatus.AWAITING_PERMISSION, s.activeTask!!.status)
    }

    @Test
    fun cancel_task_membatalkan_step_pending() {
        var s = advance(submit(fresh(), "baca file readme\nbaca docs\nbaca logs"))
        s = advance(s)
        s = ManusReducer.reduce(s, ManusAction.CancelActiveTask, clock, ids)
        val task = s.activeTask!!
        assertEquals(AgentTaskStatus.CANCELLED, task.status)
        assertTrue(task.steps.drop(1).all { it.status == StepStatus.SKIPPED })
        assertTrue(s.pendingPermissions.none { it.isPending })
    }

    @Test
    fun advance_tanpa_task_aktif_balik_error() {
        val s = advance(fresh())
        assertEquals("Tidak ada task aktif.", s.lastError)
    }

    @Test
    fun tool_finished_langsung_menyelesaikan_step() {
        var s = advance(submit(fresh(), "catatan bebas"))
        s = advance(s)
        val task = s.activeTask!!
        val step = task.steps.first()
        val run = step.toolRuns.first()
        s = ManusReducer.reduce(
            s,
            ManusAction.ToolFinished(step.id, run.id, success = true, output = "note tersimpan"),
            clock,
            ids
        )
        val done = s.activeTask!!
        assertEquals(AgentTaskStatus.DONE, done.status)
        assertEquals(StepStatus.DONE, done.steps.first().status)
        assertEquals("note tersimpan", done.steps.first().toolRuns.first().output)
    }

    @Test
    fun komentar_di_goal_diabaikan_di_rencana() {
        val goal = "# ini komentar\nbaca file readme\n\nbaca docs"
        val plan = ManusReducer.buildPlan(goal)
        assertEquals(2, plan.size)
        assertFalse(plan.any { it.title.startsWith("#") })
    }

    @Test
    fun timeline_append_only_urut() {
        var s = submit(fresh(), "baca file readme")
        val before = s.events.size
        s = advance(s)
        s = advance(s)
        assertTrue(s.events.size > before)
        s.events.zipWithNext { a, b ->
            assertTrue(a.at <= b.at)
        }
        assertNotNull(s.events.firstOrNull { it is ManusEvent.TaskCreated })
    }
}

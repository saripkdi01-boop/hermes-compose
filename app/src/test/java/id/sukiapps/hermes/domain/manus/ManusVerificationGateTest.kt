package id.sukiapps.hermes.domain.manus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Gerbang verifikasi 14.32: tidak ada jalan ke COMPLETED selagi ada
 * pekerjaan tersisa. Murni JVM, tanpa Android. Clock dipalsukan.
 *
 * Skenario yang dikunci (semuanya GAGAL pada reducer sebelum perbaikan):
 * - langkah selesai saat tugas dijeda tidak boleh membawa tugas ke VERIFYING
 * - StartTask pada tugas dijeda tidak boleh menggandakan langkah berjalan
 * - advance() tidak boleh memulai langkah baru selagi ada langkah berjalan
 * - VerificationPassed ditolak bila ada langkah belum settled / tanpa langkah
 *
 * Semua kode di sini orisinal untuk Hermes Suki; tidak ada yang disalin
 * dari proyek lain.
 */
class ManusVerificationGateTest {
    private var fakeNow = 1_000L
    private val clock = { fakeNow }

    private fun submit(plan: List<String>): ManusEvent =
        ManusEvent.SubmitGoal(goal = "uji", plan = plan)

    private fun ManusUiState.task(id: String): AgentTask =
        tasks.first { it.id == id }

    /** Tugas 2 langkah yang sedang berjalan di langkah pertama, lalu dijeda. */
    private fun pausedWithRunningStep(): Pair<ManusUiState, String> {
        var state = ManusUiState.empty()
        state = ManusReducer.reduce(state, submit(listOf("satu", "dua")), clock)
        val taskId = state.tasks.single().id
        state = ManusReducer.reduce(state, ManusEvent.StartTask(taskId), clock)
        state = ManusReducer.reduce(state, ManusEvent.PauseTask(taskId), clock)
        return state to taskId
    }

    @Test
    fun langkahSelesai_saatDijeda_tugasTetapDijeda_sampaiResume() {
        var (state, taskId) = pausedWithRunningStep()
        assertEquals(TaskStatus.PAUSED, state.task(taskId).status)
        // Pelaksana melaporkan langkah berjalan selesai SETELAH user menjeda.
        state = ManusReducer.reduce(
            state, ManusEvent.ToolFinished(taskId, "$taskId-s1", "ok"), clock
        )
        val paused = state.task(taskId)
        assertEquals(TaskStatus.PAUSED, paused.status)
        assertEquals(StepStatus.DONE, paused.steps[0].status)
        assertEquals(StepStatus.PENDING, paused.steps[1].status)
        // Resume: langkah kedua dimulai, bukan langsung VERIFYING.
        state = ManusReducer.reduce(state, ManusEvent.ResumeTask(taskId), clock)
        val resumed = state.task(taskId)
        assertEquals(TaskStatus.RUNNING, resumed.status)
        assertEquals(StepStatus.RUNNING, resumed.steps[1].status)
        state = ManusReducer.reduce(
            state, ManusEvent.ToolFinished(taskId, "$taskId-s2", "ok"), clock
        )
        assertEquals(TaskStatus.VERIFYING, state.task(taskId).status)
        state = ManusReducer.reduce(
            state, ManusEvent.VerificationPassed(taskId, "Terverifikasi: 2/2"), clock
        )
        assertEquals(TaskStatus.COMPLETED, state.task(taskId).status)
    }

    @Test
    fun startTask_dariDijeda_tidakMenggandakanLangkahBerjalan() {
        var (state, taskId) = pausedWithRunningStep()
        state = ManusReducer.reduce(state, ManusEvent.StartTask(taskId), clock)
        val task = state.task(taskId)
        assertEquals(TaskStatus.RUNNING, task.status)
        assertEquals(1, task.steps.count { it.status == StepStatus.RUNNING })
        assertEquals(StepStatus.RUNNING, task.steps[0].status)
        assertEquals(StepStatus.PENDING, task.steps[1].status)
    }

    @Test
    fun advance_tidakMemulaiLangkahBaru_saatAdaLangkahBerjalan() {
        var (state, taskId) = pausedWithRunningStep()
        // RetryStep memanggil advance(): langkah kedua sudah PENDING, tapi
        // langkah pertama masih RUNNING sehingga tidak boleh ikut jalan.
        state = ManusReducer.reduce(state, ManusEvent.RetryStep(taskId, "$taskId-s2"), clock)
        val task = state.task(taskId)
        assertEquals(StepStatus.RUNNING, task.steps[0].status)
        assertEquals(StepStatus.PENDING, task.steps[1].status)
        assertEquals(1, task.steps.count { it.status == StepStatus.RUNNING })
    }

    @Test
    fun verificationPassed_ditolak_saatLangkahBelumSettled() {
        val task = AgentTask(
            id = "t1",
            goal = "uji",
            steps = listOf(
                AgentStep(id = "t1-s1", title = "satu", status = StepStatus.DONE, output = "ok"),
                AgentStep(id = "t1-s2", title = "dua", status = StepStatus.PENDING)
            ),
            status = TaskStatus.VERIFYING
        )
        val state = ManusUiState(tasks = listOf(task), activeTaskId = "t1")
        val after = ManusReducer.reduce(
            state, ManusEvent.VerificationPassed("t1", "palsu"), clock
        )
        assertEquals(TaskStatus.VERIFYING, after.task("t1").status)
        assertNull(after.task("t1").verificationEvidence)
    }

    @Test
    fun verificationPassed_ditolak_untukTugasTanpaLangkah() {
        val task = AgentTask(
            id = "t1", goal = "uji", steps = emptyList(), status = TaskStatus.VERIFYING
        )
        val state = ManusUiState(tasks = listOf(task), activeTaskId = "t1")
        val after = ManusReducer.reduce(
            state, ManusEvent.VerificationPassed("t1", "palsu"), clock
        )
        assertEquals(TaskStatus.VERIFYING, after.task("t1").status)
        assertNull(after.task("t1").verificationEvidence)
    }

    @Test
    fun pauseResume_normal_tetapSelesaiTerverifikasi() {
        // Jalur bahagia lama tetap lolos: jeda saat langkah kedua berjalan,
        // resume, selesaikan, verifikasi, COMPLETED.
        var state = ManusUiState.empty()
        state = ManusReducer.reduce(state, submit(listOf("a", "b")), clock)
        val taskId = state.tasks.single().id
        state = ManusReducer.reduce(state, ManusEvent.StartTask(taskId), clock)
        state = ManusReducer.reduce(
            state, ManusEvent.ToolFinished(taskId, "$taskId-s1", "ok"), clock
        )
        state = ManusReducer.reduce(state, ManusEvent.PauseTask(taskId), clock)
        assertEquals(TaskStatus.PAUSED, state.task(taskId).status)
        state = ManusReducer.reduce(state, ManusEvent.ResumeTask(taskId), clock)
        assertEquals(TaskStatus.RUNNING, state.task(taskId).status)
        state = ManusReducer.reduce(
            state, ManusEvent.ToolFinished(taskId, "$taskId-s2", "ok"), clock
        )
        assertEquals(TaskStatus.VERIFYING, state.task(taskId).status)
        state = ManusReducer.reduce(
            state, ManusEvent.VerificationPassed(taskId, "Terverifikasi: 2/2"), clock
        )
        assertEquals(TaskStatus.COMPLETED, state.task(taskId).status)
    }
}

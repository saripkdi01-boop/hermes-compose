package id.sukiapps.hermes.domain.manus

/**
 * Seed & helper state Manus. Semua tipe diambil dari ManusModels.kt yang sudah
 * teruji — file ini tidak mendeklarasikan ulang tipe apa pun, jadi tidak ada
 * konflik nama dengan ManusReducer.kt / ManusModels.kt.
 */
object ManusStateEngine {

    /**
     * Snapshot awal untuk layar Manus: satu task contoh dengan 4 langkah,
     * campuran risiko LOW sampai CRITICAL supaya gate izin langsung terlihat.
     */
    fun seed(): ManusUiState {
        val now = System.currentTimeMillis()
        val taskId = "task-1"
        val steps = listOf(
            AgentStep(
                id = "$taskId-s1",
                title = "Petakan layar & navigasi",
                tool = ToolCall("list_files", mapOf("path" to "app/src/main"))
            ),
            AgentStep(
                id = "$taskId-s2",
                title = "Temukan celah UI/UX",
                tool = ToolCall("web_search", mapOf("q" to "compose ux audit"))
            ),
            AgentStep(
                id = "$taskId-s3",
                title = "Susun rencana upgrade",
                tool = ToolCall("create_task", mapOf("title" to "Rencana upgrade"))
            ),
            AgentStep(
                id = "$taskId-s4",
                title = "Eksekusi merge PR",
                tool = ToolCall("selfupgrade_merge", mapOf("pr" to "1"))
            )
        )
        val task = AgentTask(
            id = taskId,
            goal = "Audit UI Hermes",
            steps = steps,
            status = TaskStatus.IDLE,
            createdAt = now,
            updatedAt = now
        )
        return ManusUiState(
            tasks = listOf(task),
            activeTaskId = taskId,
            running = true,
            nextStepSeq = 2L,
            nextLogId = 1L
        )
    }
}

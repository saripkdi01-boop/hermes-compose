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
                tool = ToolCall("list_files", mapOf("path" to "app/src/main")),
                detail = "Membaca daftar file di app/src/main. Aksi baca saja — jalan otomatis tanpa izin.",
                sources = listOf("app/src/main (baca lokal)"),
                artifacts = listOf("daftar-file.txt (contoh)")
            ),
            AgentStep(
                id = "$taskId-s2",
                title = "Temukan celah UI/UX",
                tool = ToolCall("web_search", mapOf("q" to "compose ux audit")),
                detail = "Pencarian web publik tentang audit UX Compose. Hasil pencarian dicatat sebagai sumber, bukan jawaban jadi.",
                sources = listOf("web_search: compose ux audit"),
                artifacts = listOf("temuan-ux.md (contoh)")
            ),
            AgentStep(
                id = "$taskId-s3",
                title = "Susun rencana upgrade",
                tool = ToolCall("create_task", mapOf("title" to "Rencana upgrade")),
                detail = "Menulis tugas baru 'Rencana upgrade'. Aksi tulis — berhenti di kartu izin sebelum dijalankan.",
                sources = listOf("temuan langkah 1–2"),
                artifacts = listOf("rencana-upgrade.md (draft sandbox)")
            ),
            AgentStep(
                id = "$taskId-s4",
                title = "Eksekusi merge PR",
                tool = ToolCall("selfupgrade_merge", mapOf("pr" to "1")),
                detail = "Merge PR adalah aksi KRITIS: selalu butuh izin eksplisit, tidak bisa dibatalkan otomatis.",
                sources = listOf("PR #1 (contoh)"),
                artifacts = listOf()
            )
        )
        val task = AgentTask(
            id = taskId,
            goal = "Audit UI Hermes — contoh",
            steps = steps,
            status = TaskStatus.IDLE,
            createdAt = now,
            updatedAt = now,
            isDemo = true
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

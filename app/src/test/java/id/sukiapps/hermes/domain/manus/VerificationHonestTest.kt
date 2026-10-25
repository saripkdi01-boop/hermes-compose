package id.sukiapps.hermes.domain.manus

import id.sukiapps.hermes.ui.chat.manusSummary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test "verifikasi jujur" 14.24 (B1/B2/B10): persiapan tidak dihitung
 * hasil, audit tanpa bukti tidak bisa COMPLETED, dan ringkasan membawa
 * label rencana + peringatan fallback + daftar yang kurang.
 */
class VerificationHonestTest {

    private fun step(
        id: String,
        tool: String,
        args: Map<String, String> = emptyMap(),
        output: String = "ok",
        prep: Boolean = false
    ) = AgentStep(
        id = id, title = tool, tool = ToolCall(tool, args),
        status = StepStatus.DONE, output = output, isPreparation = prep
    )

    private fun task(goal: String, vararg steps: AgentStep, label: String = "") =
        AgentTask(id = "t1", goal = goal, steps = steps.toList(), planLabel = label)

    // Reproduksi foto 23:48 — audit fallback: system_info (persiapan) +
    // github_repos (daftar saja). HARUS sebagian, bukan lolos.
    @Test
    fun auditFallback_hanyaDaftarRepo_menjadiPartial() = runBlocking {
        val t = task(
            "Audit repo hermes-compose, cari celah",
            step("s1", "system_info", output = "OPPO CPH2375", prep = true),
            step("s2", "github_repos", output = "hermes-compose, suki-agent-skills"),
            label = "rencana fallback — tanpa LLM (LLM HTTP 504)"
        )
        val r = TaskVerifier.verify(t) { null }
        assertFalse(r.passed)
        assertTrue(r.isPartial)
        assertTrue(r.missing.any { it.contains("temuan celah") })
    }

    @Test
    fun audit_denganBacaFile_lolos() = runBlocking {
        val t = task(
            "Audit repo hermes-compose, cari celah",
            step("s1", "system_info", output = "OPPO", prep = true),
            step(
                "s2", "github_read_file",
                mapOf("path" to "AndroidManifest.xml"),
                output = "uses-permission INTERNET"
            )
        )
        val r = TaskVerifier.verify(t) { null }
        assertTrue(r.passed)
        // Bukti menghitung langkah HASIL saja (1/1), persiapan dicatat.
        assertTrue(r.evidence.contains("1/1"))
        assertTrue(r.evidence.contains("persiapan tidak dihitung"))
    }

    @Test
    fun audit_pernyataanNihilEksplisit_lolos() = runBlocking {
        val t = task(
            "Audit repo, cari celah",
            step("s1", "github_repos", output = "repo dicek, tidak ditemukan celah")
        )
        val r = TaskVerifier.verify(t) { null }
        assertTrue(r.passed)
    }

    @Test
    fun hanyaPersiapan_menjadiPartial() = runBlocking {
        val t = task(
            "Cek apa saja yang bisa diupgrade",
            step("s1", "system_info", output = "OPPO", prep = true),
            label = "rencana fallback — tanpa LLM (DNS)"
        )
        val r = TaskVerifier.verify(t) { null }
        assertFalse(r.passed)
        assertTrue(r.isPartial)
        assertTrue(r.missing.isNotEmpty())
    }

    @Test
    fun goalUmumFallback_denganLangkahHasil_lolos() = runBlocking {
        val t = task(
            "Cek upgrade diri",
            step("s1", "system_info", output = "OPPO", prep = true),
            step("s2", "web_fetch", mapOf("url" to "https://sukiapps.web.id"), output = "halaman"),
            label = "rencana fallback — tanpa LLM (DNS)"
        )
        // Bukan goal audit: langkah hasil non-kosong cukup untuk lolos.
        val r = TaskVerifier.verify(t) { null }
        assertTrue(r.passed)
    }

    @Test
    fun planner_menandaiPersiapan() {
        val plan = ManusToolPlanner.plan("cek apa saja yang bisa diupgrade")
        assertTrue(plan.first().toolName == "system_info" && plan.first().isPreparation)
        val generic = plan.firstOrNull { it.toolName == "web_fetch" }
        assertTrue(generic != null && generic!!.isPreparation)
        // URL eksplisit dari goal BUKAN persiapan.
        val explicit = ManusToolPlanner.plan("ambil https://example.com/data")
        assertTrue(explicit.any { it.toolName == "web_fetch" && !it.isPreparation })
    }

    @Test
    fun reducer_partialTerminal_danMenyimpanYangKurang() {
        var state = ManusUiState.empty()
        state = ManusReducer.reduce(
            state,
            ManusEvent.SubmitGoal("Audit repo, cari celah", listOf("a", "b")),
            { 1000L }
        )
        val taskId = state.tasks.single().id
        state = ManusReducer.reduce(state, ManusEvent.StartTask(taskId), { 1000L })
        // Habiskan langkah (tanpa tool = manual DONE) -> VERIFYING.
        state = ManusReducer.reduce(
            state, ManusEvent.ToolFinished(taskId, "$taskId-s1", "x"), { 1000L }
        )
        state = ManusReducer.reduce(
            state, ManusEvent.ToolFinished(taskId, "$taskId-s2", "y"), { 1000L }
        )
        assertEquals(TaskStatus.VERIFYING, state.tasks.single().status)
        state = ManusReducer.reduce(
            state,
            ManusEvent.VerificationPartial(taskId, "tanpa bukti", listOf("temuan celah")),
            { 1000L }
        )
        val done = state.tasks.single()
        assertEquals(TaskStatus.PARTIAL, done.status)
        assertTrue(done.isFinished)
        assertEquals(listOf("temuan celah"), done.missingItems)
        // Di luar VERIFYING, event partial tidak mengubah apa pun.
        val again = ManusReducer.reduce(
            state,
            ManusEvent.VerificationPartial(taskId, "lain", listOf("z")),
            { 1000L }
        )
        assertEquals(TaskStatus.PARTIAL, again.tasks.single().status)
        assertEquals(listOf("temuan celah"), again.tasks.single().missingItems)
    }

    @Test
    fun summary_membawaLabelFallbackPeringatanDanYangKurang() {
        val t = task(
            "Audit repo hermes-compose, cari celah",
            step("s1", "system_info", output = "OPPO", prep = true),
            step("s2", "github_repos", output = "hermes-compose"),
            label = "rencana fallback — tanpa LLM (LLM HTTP 504)"
        ).copy(status = TaskStatus.PARTIAL, partialReason = "tanpa bukti", missingItems = listOf("temuan celah"))
        val out = manusSummary(t)
        assertTrue(out.contains("Rencana: rencana fallback"))
        assertTrue(out.contains("Yang kurang: temuan celah"))
        // Output persiapan (snapshot) tidak ditempel sebagai hasil.
        assertTrue(!out.contains("OPPO"))
        // Hitungan jujur: 1 hasil dari 1 langkah (+ 1 persiapan).
        assertTrue(out.contains("1 selesai, 0 dilewati, 0 gagal, dari 1 langkah (+ 1 persiapan)"))
    }

    @Test
    fun summary_completedFallback_membawaPeringatan() {
        val t = task(
            "Cek upgrade diri",
            step("s1", "web_fetch", mapOf("url" to "https://x"), output = "halaman"),
            label = "rencana fallback — tanpa LLM (DNS)"
        ).copy(status = TaskStatus.COMPLETED)
        val out = manusSummary(t)
        assertTrue(out.contains("tanpa LLM"))
        assertTrue(out.contains("hasil belum tentu tuntas"))
    }
}

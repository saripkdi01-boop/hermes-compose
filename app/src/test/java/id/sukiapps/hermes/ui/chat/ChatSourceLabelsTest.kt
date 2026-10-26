package id.sukiapps.hermes.ui.chat

import id.sukiapps.hermes.domain.manus.AgentStep
import id.sukiapps.hermes.domain.manus.AgentTask
import id.sukiapps.hermes.domain.manus.StepStatus
import id.sukiapps.hermes.domain.manus.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 14.28 (penanda sumber + B9): setiap blok chat berlabel sumber; chip
 * tool chat membawa jalurnya; ringkasan agen mencantumkan sumber +
 * label rencana + jumlah langkah.
 */
class ChatSourceLabelsTest {

    @Test
    fun label_ringkasanAgen_dariAwalanManusSelesai() {
        assertEquals(
            "Ringkasan agen (output tool)",
            assistantSourceLabel("🤖 Manus selesai: audit\nStatus: COMPLETED")
        )
    }

    @Test
    fun label_balasanBiasa_adalahBalasanChatLLM() {
        assertEquals("Balasan chat (LLM)", assistantSourceLabel("Halo, ini jawaban."))
        assertEquals("Balasan chat (LLM)", assistantSourceLabel(""))
    }

    @Test
    fun chipToolChat_membawaNamaHasilDanJalur() {
        val label = chatToolEventLabel("web_fetch", true, 600)
        assertTrue(label.contains("web_fetch"))
        assertTrue(label.contains("✓"))
        assertTrue(label.contains("dtk"))
        assertTrue(label.contains("(output tool, jalur chat)"))
    }

    @Test
    fun chipToolChat_gagal_menandaiSilangDanJalur() {
        val label = chatToolEventLabel("github_read_file", false, 1200)
        assertTrue(label.contains("✗"))
        assertTrue(label.contains("(output tool, jalur chat)"))
    }

    @Test
    fun ringkasan_mencantumkanSumberRencanaDanJumlahLangkah() {
        val task = AgentTask(
            id = "t1",
            goal = "audit",
            steps = listOf(
                AgentStep(
                    id = "s1",
                    title = "baca",
                    status = StepStatus.DONE,
                    output = "hasil baca"
                ),
                AgentStep(id = "s2", title = "tulis", status = StepStatus.PENDING)
            ),
            status = TaskStatus.COMPLETED,
            planLabel = "LLM"
        )
        val summary = manusSummary(task)
        assertTrue(summary.contains("Sumber: Ringkasan agen (output tool)"))
        assertTrue(summary.contains("Rencana: LLM"))
        assertTrue(summary.contains("dari 2 langkah"))
    }
}

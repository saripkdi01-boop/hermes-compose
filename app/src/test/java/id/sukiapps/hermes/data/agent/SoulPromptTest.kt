package id.sukiapps.hermes.data.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** v19.3: perakitan system prompt Jiwa Hermes (murni, tanpa I/O). */
class SoulPromptTest {

    private val memorySeed = """
        # MEMORY.md
        <!-- Ingatan durable tentang user. Ditulis otomatis oleh aplikasi tiap sesi
             (maksimal 3 fakta), bisa diedit atau dihapus manual kapan saja.
             Format: - YYYY-MM-DD: fakta. -->
        - 2026-11-01: User bernama Kiki, suka jawaban singkat.
        - 2026-11-02: Proyek utama: aplikasi Hermes di Android.
        - 2026-11-03: Suka kopi tanpa gula.
    """.trimIndent()

    private val parts = SoulPrompt.Parts(
        soul = "# SOUL.md\nNama: Hermes.",
        skill = "Siklus: pahami, rencanakan, kerjakan.",
        agents = "- Jangan kirim data pribadi.\n- Koreksi user menang.",
        features = (1..50).joinToString("\n") { "- fitur nomor $it dijelaskan di sini" },
        memory = memorySeed,
        todo = "# TODO.md\n- [ ] Rilis Hermes 19.3\n- [x] Audit selesai\n- [ ] Tulis changelog"
    )

    @Test
    fun includesSectionsInOrder_withGuides() {
        val p = SoulPrompt.assemble(parts, "tolong bantu rilis hermes", maxWords = 5000)
        val iMem = p.indexOf("[Ingatan tentang user]")
        val iTodo = p.indexOf("[Tugas aktif user]")
        val iFeat = p.indexOf("[Fitur aplikasi Hermes")
        assertTrue("soul selalu paling depan", p.startsWith("# SOUL.md\nNama: Hermes."))
        // Statis (fitur) sebelum dinamis (ingatan → todo).
        assertTrue(iFeat in 1 until iMem)
        assertTrue(iMem < iTodo)
        assertTrue(p.contains(SoulPrompt.MEMORY_GUIDE))
        assertTrue(p.contains(SoulPrompt.TODO_GUIDE))
        assertTrue(p.contains("- [ ] Rilis Hermes 19.3"))
        assertFalse("tugas selesai tidak ikut", p.contains("Audit selesai"))
    }

    @Test
    fun htmlComment_neverLeaksAsMemory() {
        val p = SoulPrompt.assemble(parts, "kopi", maxWords = 5000)
        assertFalse(p.contains("maksimal 3 fakta"))
        assertFalse(p.contains("Format: - YYYY"))
        assertTrue(p.contains("Suka kopi tanpa gula"))
    }

    @Test
    fun overBudget_trimsFeaturesFirst_keepsMemoryAndTodo() {
        // Anggaran sempit: manual fitur harus dipangkas, ingatan & todo tetap.
        val p = SoulPrompt.assemble(parts, "hermes android", maxWords = 120)
        assertTrue(SoulPrompt.countWords(p) <= 120)
        assertTrue(p.contains("Nama: Hermes."))
        assertTrue(p.contains("Proyek utama: aplikasi Hermes di Android."))
        assertTrue(p.contains("- [ ] Rilis Hermes 19.3"))
        assertFalse(p.contains("fitur nomor 50"))
        // Struktur baris dipertahankan (dulu diratakan jadi satu paragraf).
        assertTrue(p.lines().size > 5)
    }

    @Test
    fun featuresBeyondOldFortyLineCap_reachPromptWhenBudgetAllows() {
        val p = SoulPrompt.assemble(parts, "x", maxWords = 5000)
        assertTrue(p.contains("fitur nomor 45"))
    }

    @Test
    fun stopwordsDoNotDriveMemoryRelevance() {
        // "yang" & "bagaimana" adalah kata umum — tanpa filter, baris mana pun
        // yang memuat "yang" akan dianggap relevan.
        val mem = "- A: suka kopi yang pahit\n- B: proyek hermes android"
        val r = SoulPrompt.relevantMemory(mem, "bagaimana yang hermes")
        assertEquals("- B: proyek hermes android", r)
    }

    @Test
    fun timeContext_isIndonesian() {
        val now = ZonedDateTime.of(2026, 11, 27, 13, 5, 0, 0, ZoneId.of("Asia/Jakarta"))
        val line = SoulPrompt.timeLine(now)
        assertTrue(line, line.startsWith("Sekarang: Jumat, 27 November 2026, 13.05"))
        val p = SoulPrompt.assemble(parts, "x", now = now, maxWords = 5000)
        // Bagian paling dinamis diletakkan paling akhir (ramah prefix cache).
        assertTrue(p.substringAfterLast("\n\n").startsWith("[Konteks waktu]"))
    }

    @Test
    fun activeTodos_parsesOnlyOpenItems() {
        assertEquals(
            listOf("Rilis Hermes 19.3", "Tulis changelog"),
            SoulPrompt.activeTodos(parts.todo)
        )
    }

    @Test
    fun emptyParts_giveEmptyPrompt() {
        assertEquals("", SoulPrompt.assemble(SoulPrompt.Parts(), "halo"))
    }
}

package id.sukiapps.hermes.data.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test parser impor ingatan Upin — MURNI, tanpa Context Android, jadi
 * bisa jalan di testReleaseUnitTest CI (bukan cuma di HP).
 * Meniru struktur ekspor nyata (format upin-memory-export, files[]
 * berisi path + content), tapi dengan isi kecil.
 */
class UpinMemoryImportTest {

    private fun exportJson(files: String): String =
        """{"format":"upin-memory-export","file_count":3,"files":[$files]}"""

    private fun fileEntry(path: String, content: String): String =
        """{"path":"$path","content":"$content"}"""

    @Test
    fun parsesRealExportShape_andFindsSummary() {
        val json = exportJson(
            fileEntry("MEMORY.md", "Suki, founder Suki Apps") + "," +
                fileEntry("memori/people/roni-sianturi.md", "Mentor") + "," +
                fileEntry("memori/2026-10-25.md", "Hari ini")
        )
        val plan = UpinMemoryImport.parse(json)
        assertTrue(plan.ok)
        assertEquals(3, plan.files.size)
        assertEquals("Suki, founder Suki Apps", plan.summary)
        assertTrue(plan.skipped.isEmpty())
    }

    @Test
    fun rejectsUnknownFormat_withClearError() {
        val json = """{"format":"hermes-memory-v1","agent_memory":{}}"""
        val plan = UpinMemoryImport.parse(json)
        assertFalse(plan.ok)
        assertTrue(plan.error.contains("hermes-memory-v1"))
        assertTrue(plan.files.isEmpty())
    }

    @Test
    fun rejectsInvalidJson() {
        val plan = UpinMemoryImport.parse("{bukan json")
        assertFalse(plan.ok)
        assertTrue(plan.error.isNotBlank())
    }

    @Test
    fun skipsTraversalAndNonMd_butKeepsValidFiles() {
        val json = exportJson(
            fileEntry("MEMORY.md", "ok") + "," +
                fileEntry("../evil.md", "x") + "," +
                fileEntry("/abs.md", "x") + "," +
                fileEntry("a\\b.md", "x") + "," +
                fileEntry("notes.txt", "x")
        )
        val plan = UpinMemoryImport.parse(json)
        assertTrue(plan.ok)
        assertEquals(1, plan.files.size)
        assertEquals(4, plan.skipped.size)
    }

    @Test
    fun failsWhenNoValidFiles() {
        val json = exportJson(fileEntry("../evil.md", "x"))
        val plan = UpinMemoryImport.parse(json)
        assertFalse(plan.ok)
        assertTrue(plan.files.isEmpty())
    }

    @Test
    fun failsWhenTotalCharsExceedsLimit() {
        val big = "x".repeat(101)
        val json = exportJson(fileEntry("MEMORY.md", big))
        // Pakai batas kecil lewat overload internal — menguji logika
        // tanpa harus membuat string 40 MB di test.
        val plan = UpinMemoryImport.parse(json, maxChars = 100, maxFiles = 500)
        assertFalse(plan.ok)
        assertTrue(plan.error.contains("terlalu besar"))
    }

    @Test
    fun okWithoutSummary_whenMemoryMdAbsent() {
        val json = exportJson(fileEntry("memori/people/a.md", "x"))
        val plan = UpinMemoryImport.parse(json)
        assertTrue(plan.ok)
        assertEquals(null, plan.summary)
    }
}

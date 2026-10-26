package id.sukiapps.hermes

import id.sukiapps.hermes.data.tool.CatalogToolRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D1 (14.30) — Test JVM murni untuk helper kontrak eksekusi katalog.
 * Eksekusi Chaquopy sendiri tidak bisa diuji di JVM (butuh runtime Android);
 * CI GitHub adalah gerbang penentu untuk kompilasi + test ini.
 */
class CatalogToolTest {

    @Test
    fun capOutput_teksPendekTidakDiubah() {
        assertEquals("halo", CatalogToolRunner.capOutput("halo"))
    }

    @Test
    fun capOutput_teksPanjangDipotongDenganPenanda() {
        val capped = CatalogToolRunner.capOutput("x".repeat(25_000))
        assertTrue(capped.startsWith("x".repeat(20_000)))
        assertTrue(capped.contains("dipotong"))
        assertTrue(capped.length <= 20_000 + 100)
    }

    @Test
    fun capDisplay_batas50000DenganPenanda() {
        val capped = CatalogToolRunner.capDisplay("y".repeat(60_000))
        assertTrue(capped.startsWith("y".repeat(50_000)))
        assertTrue(capped.contains("dipotong"))
    }

    @Test
    fun konstantaKontrakV1() {
        assertEquals(30_000L, CatalogToolRunner.TIMEOUT_MS)
        assertEquals(20_000, CatalogToolRunner.MAX_OUTPUT_CHARS)
        assertEquals(50_000, CatalogToolRunner.MAX_DISPLAY_CHARS)
    }
}

package id.sukiapps.hermes.domain.manus

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test rantai fallback provider 14.26 (B3): urutan rantai, pemetaan
 * nama, dan perilaku planner bila semua provider gagal.
 *
 * Test planner di sini hermetik: base URL localhost yang tidak listen
 * (connection refused, tanpa internet) — membuktikan rantai dilewati
 * dan alasan gabungan terbentuk, tanpa bergantung ke jaringan nyata.
 */
class ProviderFallbackTest {

    // ---- ProviderChain: urutan ----

    @Test
    fun jerouterDipilih_dicobaPertamaLaluOpenRouterDCompute() {
        val names = ProviderChain.ordered(ProviderChain.JEROUTER.baseUrl)
            .map { it.name }
        assertEquals(
            listOf("Jerouter", "OpenRouter", "DCompute"),
            names
        )
    }

    @Test
    fun openRouterDipilih_dicobaPertamaLaluSisanya() {
        val names = ProviderChain.ordered(ProviderChain.OPENROUTER.baseUrl)
            .map { it.name }
        assertEquals(
            listOf("OpenRouter", "Jerouter", "DCompute"),
            names
        )
    }

    @Test
    fun baseUrlKustom_jadiKustomPertamaLaluTigaPreset() {
        val ordered = ProviderChain.ordered("https://api.meta.ai/v1")
        assertEquals("Kustom", ordered[0].name)
        assertEquals(
            listOf("Jerouter", "OpenRouter", "DCompute"),
            ordered.drop(1).map { it.name }
        )
    }

    @Test
    fun nameFor_presetDanKustom() {
        assertEquals("Jerouter", ProviderChain.nameFor("https://je.jerouter.web.id/v1"))
        assertEquals("OpenRouter", ProviderChain.nameFor("https://openrouter.ai/api/v1"))
        assertEquals("DCompute", ProviderChain.nameFor("https://chat.decentralizedcompute.company/v1"))
        assertEquals("https://lain.example/v1", ProviderChain.nameFor("https://lain.example/v1"))
    }

    @Test
    fun keySecret_tigaProviderBerbedaDanTidakKosong() {
        val secrets = ProviderChain.ALL.map { it.keySecret }
        assertEquals(3, secrets.distinct().size)
        assertTrue(secrets.all { it.isNotBlank() })
    }

    // ---- LlmPlanner: semua gagal -> alasan gabungan ----

    @Test
    fun semuaProviderGagal_tanpaKeyCadangan_alasanGabunganJujur() = runBlocking {
        // Base URL kustom yang tidak listen: provider pertama gagal
        // koneksi, ketiga preset dilewati karena tanpa key tersimpan
        // (TIDAK pernah dicoba tanpa auth).
        val result = LlmPlanner.plan(
            goal = "uji fallback",
            model = "model-uji",
            baseUrl = "http://127.0.0.1:1/v1",
            client = OkHttpClient(),
            providerKeys = emptyMap()
        )
        assertTrue(result is LlmPlanResult.Failure)
        val reason = (result as LlmPlanResult.Failure).reason
        assertTrue(reason.startsWith("semua provider gagal"))
        assertTrue("Kustom harus tercatat gagal: $reason", reason.contains("Kustom:"))
        assertTrue(
            "preset tanpa key harus tercatat dilewati: $reason",
            reason.contains("tanpa key tersimpan")
        )
    }

    @Test
    fun modelKosong_gagalSebelumMencobaProvider() = runBlocking {
        val result = LlmPlanner.plan(
            goal = "uji",
            model = "",
            baseUrl = "http://127.0.0.1:1/v1",
            client = OkHttpClient()
        )
        assertTrue(result is LlmPlanResult.Failure)
        assertTrue((result as LlmPlanResult.Failure).reason.contains("model belum dipilih"))
    }
}

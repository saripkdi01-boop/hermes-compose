package id.sukiapps.hermes.domain.manus

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test rantai lane→model 14.32. Murni JVM; tes planner hermetik
 * (base URL localhost yang tidak listen, tanpa internet).
 */
class LaneModelChainTest {

    @Test
    fun lane_diklasifikasikanDariKataKunci() {
        assertEquals(LaneModelChain.TaskLane.AUDIT, LaneModelChain.laneFor("audit celah keamanan"))
        assertEquals(LaneModelChain.TaskLane.PERBAIKAN, LaneModelChain.laneFor("perbaiki crash login"))
        assertEquals(LaneModelChain.TaskLane.PERBAIKAN, LaneModelChain.laneFor("cari bug login"))
        assertEquals(LaneModelChain.TaskLane.RISET, LaneModelChain.laneFor("riset dokumentasi supabase"))
        assertEquals(LaneModelChain.TaskLane.UMUM, LaneModelChain.laneFor("tulis catatan harian"))
        assertEquals(LaneModelChain.TaskLane.UMUM, LaneModelChain.laneFor(""))
    }

    @Test
    fun maxTokens_perLane_sesuaiPilihanDesain() {
        assertEquals(1000, LaneModelChain.maxTokensFor(LaneModelChain.TaskLane.UMUM))
        assertEquals(1000, LaneModelChain.maxTokensFor(LaneModelChain.TaskLane.RISET))
        assertEquals(1200, LaneModelChain.maxTokensFor(LaneModelChain.TaskLane.PERBAIKAN))
        assertEquals(1500, LaneModelChain.maxTokensFor(LaneModelChain.TaskLane.AUDIT))
    }

    @Test
    fun attempts_jerouterDipilih_urutanTetapDanSatuModel() {
        val attempts = LaneModelChain.attempts(ProviderChain.JEROUTER.baseUrl, "m-x")
        assertEquals(
            listOf("Jerouter", "OpenRouter", "DCompute"),
            attempts.map { it.provider.name }
        )
        assertTrue(attempts.all { it.model == "m-x" })
    }

    @Test
    fun attempts_openRouterDipilih_urutanTetap() {
        val attempts = LaneModelChain.attempts(ProviderChain.OPENROUTER.baseUrl, "m-x")
        assertEquals(
            listOf("OpenRouter", "Jerouter", "DCompute"),
            attempts.map { it.provider.name }
        )
    }

    @Test
    fun attempts_metaDipilih_modelCadanganTepatSetelahModelPilihan() {
        val attempts = LaneModelChain.attempts("https://api.meta.ai/v1", "m-x")
        assertEquals(
            listOf(
                "Kustom" to "m-x",
                "Kustom" to "muse-spark-1.3",
                "Jerouter" to "m-x",
                "OpenRouter" to "m-x",
                "DCompute" to "m-x"
            ),
            attempts.map { it.provider.name to it.model }
        )
    }

    @Test
    fun attempts_metaDipilih_modelSudahCadangan_tanpaDuplikat() {
        val attempts = LaneModelChain.attempts("https://api.meta.ai/v1", "muse-spark-1.3")
        assertEquals(4, attempts.size)
        assertEquals(1, attempts.count { it.model == "muse-spark-1.3" })
    }

    @Test
    fun planner_rantaiBaru_semuaGagal_alasanGabunganTetapJujur() = runBlocking {
        // Kontrak lama (ProviderFallbackTest) harus tetap berlaku lewat
        // loop baru: Kustom gagal koneksi, preset tanpa key dilewati.
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
}

package id.sukiapps.hermes.domain.manus

/**
 * Rantai provider LLM untuk fallback agen (14.26, B3).
 *
 * Murni Kotlin (tanpa Android) supaya bisa di-unit-test di JVM.
 * Base URL di sini adalah preset PUBLIK yang sama dengan tombol preset
 * di Pengaturan — bukan rahasia. Kunci TIDAK PERNAH ada di file ini:
 * kunci cadangan dibaca pemanggil dari KeyVault lewat nama secret
 * [LlmProvider.keySecret], dan provider tanpa kunci tersimpan TIDAK
 * PERNAH dicoba (mencoba tanpa kunci = 401 sia-sia; memakai kunci
 * provider lain ke provider ini = kebocoran kunci — keduanya dilarang).
 *
 * Urutan: provider terpilih user selalu dicoba PERTAMA (dengan kunci
 * utama lewat AuthInterceptor), lalu sisanya sesuai urutan ALL.
 * Base URL kustom (bukan salah satu preset) dicoba pertama sebagai
 * "Kustom", lalu ketiga preset sebagai cadangan.
 */
data class LlmProvider(
    val name: String,
    val baseUrl: String,
    /** Nama secret KeyVault untuk kunci cadangan provider ini. */
    val keySecret: String
)

object ProviderChain {

    val JEROUTER = LlmProvider(
        name = "Jerouter",
        baseUrl = "https://je.jerouter.web.id/v1",
        keySecret = "provider_key_jerouter"
    )
    val OPENROUTER = LlmProvider(
        name = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        keySecret = "provider_key_openrouter"
    )
    val DCOMPUTE = LlmProvider(
        name = "DCompute",
        baseUrl = "https://chat.decentralizedcompute.company/v1",
        keySecret = "provider_key_dcompute"
    )

    /** Urutan cadangan tetap: Jerouter → OpenRouter → DCompute. */
    val ALL: List<LlmProvider> = listOf(JEROUTER, OPENROUTER, DCOMPUTE)

    /** Nama provider untuk sebuah base URL; base URL tak dikenal = apa adanya. */
    fun nameFor(baseUrl: String): String {
        val norm = baseUrl.trimEnd('/')
        return ALL.firstOrNull { it.baseUrl == norm }?.name ?: norm
    }

    /**
     * Urutan coba untuk base URL terpilih: terpilih dulu, lalu sisanya.
     * Base URL kustom menjadi entri "Kustom" pertama (kunci utamanya
     * lewat interceptor, seperti sebelum 14.26).
     */
    fun ordered(selectedBaseUrl: String): List<LlmProvider> {
        val sel = selectedBaseUrl.trimEnd('/')
        val selected = ALL.firstOrNull { it.baseUrl == sel }
        return if (selected != null) {
            listOf(selected) + (ALL - selected)
        } else {
            listOf(LlmProvider("Kustom", sel, "")) + ALL
        }
    }
}

package id.sukiapps.hermes.domain.manus

/**
 * Rantai lane→model 14.32 — urutan percobaan perencana LLM.
 *
 * Murni Kotlin (tanpa Android) supaya bisa di-unit-test di JVM.
 * Semua isi di bawah orisinal untuk Hermes Suki dan hanya dibangun dari
 * input yang terverifikasi di app ini:
 * - Base URL + model yang dipilih user di Pengaturan (selalu pertama).
 * - Urutan cadangan [ProviderChain] (14.26) — tidak diubah.
 * - Satu model cadangan terdokumentasi untuk Meta ("muse-spark-1.3",
 *   model awal preset Muse by Meta di SettingsScreen).
 *
 * Yang SENGAJA tidak ada: ID model karangan untuk provider lain. Tidak
 * ada katalog model terverifikasi untuk Jerouter/OpenRouter/DCompute di
 * app ini (model diisi user dari daftar yang dimuat provider), jadi
 * provider tanpa model cadangan terverifikasi hanya dicoba dengan model
 * pilihan user. Mengarang ID model = 404 sia-sia + klaim palsu.
 *
 * Urutan percobaan bersifat provider-major: semua model untuk satu
 * provider dicoba dulu sebelum pindah provider, supaya "provider menolak
 * model" jatuh ke model berikutnya pada provider yang sama. Upaya pada
 * base URL terpilih memakai kunci utama lewat interceptor (seperti
 * 14.26); upaya provider lain memakai kunci cadangannya sendiri —
 * tidak pernah silang. Model/provider yang akhirnya berhasil dilaporkan
 * lewat LlmPlanResult (providerUsed/modelUsed) supaya tidak ada
 * penurunan kualitas yang diam-diam.
 *
 * Lane juga menentukan anggaran token perencanaan (maxTokens): ini
 * pilihan desain internal (bukan klaim provider) — UMUM memakai nilai
 * lama 1000 tanpa perubahan, pekerjaan audit/perbaikan yang butuh
 * rencana lebih panjang mendapat anggaran lebih besar.
 */
object LaneModelChain {

    /** Base URL Meta Model API — sama dengan preset Muse by Meta. */
    const val META_BASE_URL = "https://api.meta.ai/v1"

    /** Model awal preset Muse by Meta — satu-satunya ID model cadangan terverifikasi di app. */
    const val META_DEFAULT_MODEL = "muse-spark-1.3"

    /** Jenis tugas untuk anggaran perencanaan (bukan klaim kualitas provider). */
    enum class TaskLane { UMUM, AUDIT, PERBAIKAN, RISET }

    /** Satu percobaan: provider + model yang dikirim ke /chat/completions. */
    data class LaneAttempt(val provider: LlmProvider, val model: String)

    /** Klasifikasi lane dari kata kunci goal (huruf kecil, substring). */
    fun laneFor(goal: String): TaskLane {
        val text = goal.lowercase()
        return when {
            text.contains("audit") || text.contains("keamanan") ||
                text.contains("celah") || text.contains("kerentanan") ||
                text.contains("vulnerab") || text.contains("security") -> TaskLane.AUDIT
            text.contains("perbaiki") || text.contains("debug") ||
                text.contains("error") || text.contains("gagal") ||
                text.contains("bug") || text.contains("crash") ||
                text.contains("tidak jalan") -> TaskLane.PERBAIKAN
            text.contains("riset") || text.contains("cari") ||
                text.contains("bandingkan") || text.contains("dokumentasi") ||
                text.contains("referensi") -> TaskLane.RISET
            else -> TaskLane.UMUM
        }
    }

    /** Anggaran token perencanaan per lane (pilihan desain, bukan klaim provider). */
    fun maxTokensFor(lane: TaskLane): Int = when (lane) {
        TaskLane.UMUM -> 1000
        TaskLane.RISET -> 1000
        TaskLane.PERBAIKAN -> 1200
        TaskLane.AUDIT -> 1500
    }

    /**
     * Daftar percobaan terurut untuk (base URL terpilih, model terpilih).
     * Provider terpilih selalu pertama (ProviderChain.ordered), lalu
     * sisanya. Untuk provider Meta, model cadangan muse-spark-1.3 dicoba
     * tepat setelah model pilihan user pada provider yang sama — bila
     * model pilihan sudah muse-spark-1.3, tidak ada duplikat.
     */
    fun attempts(selectedBaseUrl: String, selectedModel: String): List<LaneAttempt> {
        val chain = ProviderChain.ordered(selectedBaseUrl)
        val out = ArrayList<LaneAttempt>()
        for (provider in chain) {
            out += LaneAttempt(provider, selectedModel)
            if (provider.baseUrl.trimEnd('/') == META_BASE_URL &&
                selectedModel != META_DEFAULT_MODEL
            ) {
                out += LaneAttempt(provider, META_DEFAULT_MODEL)
            }
        }
        return out
    }
}

package id.sukiapps.hermes.ui.settings

/**
 * Status per model untuk dialog "Pilih model".
 *
 * Warna titik SESUAI SPESIFIKASI USER (jangan diganti hijau):
 * - ONLINE = kuning (probe validateModel lolos)
 * - DEAD = merah (probe gagal jelas: 401/403/404/5xx)
 * - UNKNOWN = abu-abu (belum dicek, timeout jaringan, atau hasil meragukan)
 */
enum class ModelStatusState { UNKNOWN, ONLINE, DEAD }

data class ModelStatus(
    val state: ModelStatusState = ModelStatusState.UNKNOWN,
    /** Durasi probe (ms), hanya terisi untuk ONLINE. */
    val latencyMs: Long? = null
)

object ModelStatusHelper {

    /**
     * Klasifikasi pesan error dari [id.sukiapps.hermes.domain.AIProvider.validateModel].
     * Pesan validateModel selalu memuat kode HTTP untuk kegagalan jelas
     * ("(401)", "(404)", "HTTP 500", ...). Tanpa kode HTTP (timeout,
     * koneksi putus, 429) hasilnya UNKNOWN = meragukan, bukan mati.
     */
    fun classifyError(message: String?): ModelStatusState {
        val code = Regex("""\b(401|403|404|5\d\d)\b""")
            .find(message.orEmpty())?.groupValues?.get(1)
        return if (code != null) ModelStatusState.DEAD else ModelStatusState.UNKNOWN
    }

    /** "842 mdtk" di bawah 1 detik, "1,3 dtk" di atasnya (koma = format id-ID). */
    fun formatLatency(latencyMs: Long?): String? {
        if (latencyMs == null) return null
        if (latencyMs < 1000) return "$latencyMs mdtk"
        val seconds = latencyMs / 1000.0
        return "%.1f".format(seconds).replace('.', ',') + " dtk"
    }
}

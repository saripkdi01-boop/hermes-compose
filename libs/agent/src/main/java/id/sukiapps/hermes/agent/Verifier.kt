package id.sukiapps.hermes.agent

import java.util.regex.Pattern

/**
 * Verifier klaim numerik.
 *
 * Aturan dari AGENTS.md: "Bedakan fakta terverifikasi, dugaan, dan belum tahu."
 * Angka tanpa bukti tool = dugaan, dan harus ditandai.
 *
 * Pola yang diperiksa:
 * - Format ribuan Indonesia: 1.500.000
 * - Satuan ekonomi: rb, jt, m, M, %, USD, IDR
 * - Persentase dan rasio
 *
 * Catatan desain: regex disengaja sempit agar tidak memaksa anotasi pada
 * angka biasa (versi, tanggal, nomor). Targetnya hanya klaim ekonomi/kinerja
 * yang membutuhkan verifikasi.
 */
object Verifier {

    private val NUMERIC_CLAIM = Pattern.compile(
        "\\b\\d{1,3}(?:\\.\\d{3})+\\b" +          // 1.500.000
            "|\\b\\d+(?:[.,]\\d+)?\\s?(?:rb|juta|jt|miliar|persen|%|usd|idr|rp)\\b" +
            "|\\b\\d{1,3}(?:[.,]\\d+)?\\s?%",
        Pattern.CASE_INSENSITIVE
    )

    fun requiresEvidence(text: String): Boolean =
        NUMERIC_CLAIM.matcher(text).find()

    /**
     * Anotasi otomatis. Dipanggil di layer UI sebelum render markdown,
     * sehingga model tidak bisa "melupakan" aturan ini.
     */
    fun annotate(text: String, hasToolEvidence: Boolean): String = when {
        !requiresEvidence(text) -> text
        hasToolEvidence -> text
        else -> text + "\n\n[CATATAN: angka di atas belum diverifikasi tool — status: dugaan.]"
    }

    /**
     * Ekstraksi sitasi wajib untuk jawaban berbasis web.
     * Format yang diharapkan: [^index^]: URL
     */
    private val CITATION = Pattern.compile("\\[\\^(\\d+)\\^\\]:\\s*(\\S+)")

    fun extractCitations(text: String): List<Citation> {
        val m = CITATION.matcher(text)
        val out = mutableListOf<Citation>()
        while (m.find()) {
            out += Citation(m.group(1).toInt(), m.group(2))
        }
        return out
    }

    data class Citation(val index: Int, val url: String)
}

package id.sukiapps.hermes.ui.chatv3

/**
 * V3EdgeCases — helper edge-case untuk chat (Fase H: Polish & Audit).
 *
 * Pure Kotlin, tanpa dependensi Compose: aman dipakai dari ViewModel,
 * repository, maupun Composable. Semua fungsi total (tidak pernah throw
 * untuk input null/aneh) dan idempoten.
 */

/** Batas teks sesuai spek Tahap 7 masterprompt v14. */
object V3TextLimits {
    /** Pesan lebih panjang dari ini dipaginasi, bukan dipotong. */
    const val MAX_MESSAGE_CHARS = 100_000
    /** Ukuran satu halaman saat paginasi. */
    const val PAGE_CHARS = 20_000
    /** Blok kode lebih panjang dari ini di-collapse (lebih ketat dari 40 baris UI). */
    const val CODE_COLLAPSE_LINES = 500
}

/** true bila teks kosong / hanya whitespace / null — pesan seperti ini tidak dikirim. */
fun v3IsEmptyContent(text: String?): Boolean = text.isNullOrBlank()

/**
 * Bersihkan teks untuk display: buang zero-width chars (U+200B, U+200C, U+200D, U+FEFF)
 * yang bisa mengacaukan layout/RTL, dan normalkan line ending ke \n.
 */
fun v3CleanForDisplay(text: String): String {
    if (text.isEmpty()) return text
    var out = text
    for (c in charArrayOf('\u200B', '\u200C', '\u200D', '\uFEFF')) {
        out = out.replace(c.toString(), "")
    }
    return out.replace("\r\n", "\n").replace('\r', '\n')
}

/**
 * Paginasi teks super-panjang (> MAX_MESSAGE_CHARS) menjadi beberapa halaman.
 * Potong di batas baris bila memungkinkan agar tidak membelah kata/kode.
 * Teks normal (<= batas) dikembalikan apa adanya dalam 1 halaman.
 */
fun v3ChunkLongText(
    text: String,
    pageChars: Int = V3TextLimits.PAGE_CHARS,
): List<String> {
    if (text.length <= V3TextLimits.MAX_MESSAGE_CHARS) return listOf(text)
    val pages = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        var end = (start + pageChars).coerceAtMost(text.length)
        if (end < text.length) {
            val nl = text.lastIndexOf('\n', end - 1)
            if (nl > start) end = nl + 1
        }
        pages.add(text.substring(start, end))
        start = end
    }
    return pages
}

/** true bila blok kode perlu di-collapse (lebih dari CODE_COLLAPSE_Lines baris). */
fun v3NeedsCodeCollapse(lineCount: Int): Boolean =
    lineCount > V3TextLimits.CODE_COLLAPSE_LINES

/**
 * Status pesan parsial akibat SSE terputus (edge case E4 / Tahap 7).
 *
 * @param text teks yang sudah diterima sebelum putus (bisa kosong)
 * @param isPartial true bila streaming berhenti tidak normal
 * @param canContinue true bila backend mendukung lanjutkan dari posisi terakhir
 */
data class V3PartialMessage(
    val text: String = "",
    val isPartial: Boolean = false,
    val canContinue: Boolean = false,
)

/**
 * Ringkasan aman untuk log/analytics: potong + samarkan.
 * Tidak pernah mengembalikan lebih dari [maxChars] karakter.
 */
fun v3SafeSnippet(text: String, maxChars: Int = 500): String {
    val clean = v3CleanForDisplay(text)
    if (clean.length <= maxChars) return clean
    return clean.take(maxChars) + "…"
}

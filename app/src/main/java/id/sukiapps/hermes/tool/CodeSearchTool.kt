package id.sukiapps.hermes.tool

import org.json.JSONObject

/**
 * code_search — cari teks/pola di dalam isi file kode (masterprompt §5.3:
 * `code.search`).
 *
 * Murni Kotlin, tanpa Android, tanpa jaringan. Isi file DITERIMA sebagai
 * argumen `content` (teks file yang dilampirkan user / sudah dibaca lewat
 * `file_read`) — tool ini TIDAK membaca filesystem sendiri, jadi tidak ada
 * permukaan akses file baru. Pencarian literal (default) atau regex
 * (`regex=true`). Hasil: nomor baris + isi baris (+ baris konteks bila
 * diminta), dibatasi [MAX_MATCHES] agar output tidak membanjiri LLM.
 */
class CodeSearchTool : HermesTool {

    override val id: String = "code_search"
    override val name: String = "Cari di Kode"
    override val description: String =
        "Cari teks atau pola di dalam isi file kode yang diberikan. " +
            "Gunakan untuk menemukan definisi, pemakaian fungsi, atau pola " +
            "di file yang dilampirkan user (isinya dikirim sebagai argumen " +
            "'content'). Pencarian literal secara default; set regex=true " +
            "untuk pola regex. Mengembalikan nomor baris yang cocok."
    override val inputSchema: JSONObject = toolInputSchema(
        JSONObject()
            .put(
                "content",
                stringProp("Isi lengkap file kode (teks) yang dicari. Maks 500.000 karakter."),
            )
            .put(
                "pattern",
                stringProp("Teks/pola yang dicari (literal, atau regex bila regex=true)."),
            )
            .put(
                "regex",
                booleanProp("True = 'pattern' dibaca sebagai regex. Default false."),
            )
            .put(
                "case_sensitive",
                booleanProp("True = bedakan huruf besar/kecil. Default false."),
            )
            .put(
                "context_lines",
                integerProp("Baris konteks di sekitar tiap cocok (0..3). Default 0."),
            )
            .put(
                "max_matches",
                integerProp("Maks cocok yang dikembalikan (1..200). Default 50."),
            ),
        listOf("content", "pattern"),
    )
    override val capabilities: Set<ToolCapability> = setOf(ToolCapability.READ_ONLY)

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val content = args.optString("content", "")
        val pattern = args.optString("pattern", "")
        if (content.isEmpty()) {
            return ToolResult("Error: parameter 'content' wajib diisi (isi file kode)", true)
        }
        if (pattern.isEmpty()) {
            return ToolResult("Error: parameter 'pattern' wajib diisi", true)
        }
        if (content.length > MAX_CONTENT_CHARS) {
            return ToolResult(
                "Error: isi file terlalu besar (${content.length} karakter, " +
                    "maks $MAX_CONTENT_CHARS) — baca per bagian dulu lewat file_read",
                true,
            )
        }
        val useRegex = args.optBoolean("regex", false)
        val caseSensitive = args.optBoolean("case_sensitive", false)
        val contextLines = args.optInt("context_lines", 0).coerceIn(0, 3)
        val maxMatches = args.optInt("max_matches", 50).coerceIn(1, 200)

        val regex: Regex? = if (useRegex) {
            val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            try {
                Regex(pattern, options)
            } catch (e: Exception) {
                return ToolResult("Error: pola regex tidak valid (${e.message})", true)
            }
        } else {
            null
        }

        val lines = content.split('\n')
        val hits = ArrayList<Int>()
        for ((idx, line) in lines.withIndex()) {
            val found = if (regex != null) {
                regex.containsMatchIn(line)
            } else if (caseSensitive) {
                line.contains(pattern)
            } else {
                line.contains(pattern, ignoreCase = true)
            }
            if (found) hits.add(idx)
            if (hits.size >= maxMatches) break
        }
        // Bila berhenti di maxMatches, tetap hitung total kasar? Tidak —
        // jujur: laporkan bahwa hasil dipotong, jangan klaim total.
        val truncated = hits.size >= maxMatches &&
            lines.drop(hits.last() + 1).any { lineMatches(it, regex, pattern, caseSensitive) }

        val out = StringBuilder()
        out.appendLine("FILE: ${lines.size} baris, ${hits.size} cocok untuk '$pattern'")
        if (truncated) {
            out.appendLine("[DIPOTONG: hanya $maxMatches cocok pertama ditampilkan]")
        }
        if (hits.isEmpty()) {
            out.append("Tidak ada baris yang cocok.")
            return ToolResult(out.toString())
        }
        val shown = HashSet<Int>()
        for (hit in hits) {
            for (i in (hit - contextLines).coerceAtLeast(0)..(hit + contextLines).coerceAtMost(lines.size - 1)) {
                if (shown.add(i)) {
                    val marker = if (i == hit) "L${i + 1}:" else "   L${i + 1}:"
                    out.appendLine("$marker ${lines[i].take(MAX_LINE_CHARS)}")
                }
            }
        }
        return ToolResult(out.toString().trimEnd())
    }

    private fun lineMatches(
        line: String,
        regex: Regex?,
        pattern: String,
        caseSensitive: Boolean,
    ): Boolean = if (regex != null) {
        regex.containsMatchIn(line)
    } else if (caseSensitive) {
        line.contains(pattern)
    } else {
        line.contains(pattern, ignoreCase = true)
    }

    companion object {
        const val MAX_CONTENT_CHARS = 500_000
        const val MAX_LINE_CHARS = 500
    }
}

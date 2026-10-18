package id.sukiapps.hermes.ui.chatv3

/**
 * Parser Markdown ringan untuk Hermes v14.
 * Pure Kotlin — tanpa dependensi Compose, bisa di-unit-test.
 * Regex dideklarasikan SEKALI di companion object.
 */
object MuseParser {

    // ===== Regex (sekali deklarasi) =====
    private val RE_HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val RE_CODE_FENCE = Regex("^```(\\w*)\\s*$")
    private val RE_QUOTE = Regex("^>\\s?(.*)$")
    private val RE_BULLET = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val RE_ORDERED = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)$")
    private val RE_DIVIDER = Regex("^([-*_])\\1{2,}\\s*$")
    private val RE_TABLE_SEP = Regex("^\\|?[\\s:|-]+\\|?\\s*$")
    private val RE_BOLD_ITALIC = Regex("\\*\\*\\*(.+?)\\*\\*\\*")
    private val RE_BOLD = Regex("\\*\\*(.+?)\\*\\*")
    private val RE_ITALIC = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)")
    private val RE_STRIKE = Regex("~~(.+?)~~")
    private val RE_INLINE_CODE = Regex("`([^`\\n]+?)`")
    private val RE_LINK = Regex("\\[([^\\]]+?)\\]\\(([^)\\s]+?)\\)")
    private val RE_ESCAPE = Regex("\\\\([\\\\`*_{}\\[\\]()#+\\-.!|>~])")

    // ===== Inline =====
    sealed interface MdInline {
        data class Plain(val text: String) : MdInline
        data class Bold(val text: String) : MdInline
        data class Italic(val text: String) : MdInline
        data class BoldItalic(val text: String) : MdInline
        data class Code(val text: String) : MdInline
        data class Strike(val text: String) : MdInline
        data class Link(val text: String, val url: String) : MdInline
    }

    // ===== Block =====
    sealed interface MdBlock {
        data class Paragraph(val inlines: List<MdInline>) : MdBlock
        data class Heading(val level: Int, val inlines: List<MdInline>) : MdBlock
        data class Quote(val inlines: List<MdInline>) : MdBlock
        data class Code(val language: String, val code: String) : MdBlock
        data class Divider(val ignored: Boolean = true) : MdBlock
        data class BulletList(val items: List<ListItem>) : MdBlock
        data class OrderedList(val items: List<ListItem>, val start: Int = 1) : MdBlock
        data class Table(val headers: List<String>, val aligns: List<Align>, val rows: List<List<String>>) : MdBlock
    }

    data class ListItem(val inlines: List<MdInline>, val level: Int = 0)
    enum class Align { LEFT, CENTER, RIGHT }

    /** Parse inline markdown dalam satu baris teks. */
    fun parseInline(raw: String): List<MdInline> {
        // Escape dulu: \* → placeholder agar tidak diparse
        val escaped = mutableMapOf<String, String>()
        var text = RE_ESCAPE.replace(raw) { m ->
            val key = "¨E${escaped.size}¨"
            escaped[key] = m.groupValues[1]
            key
        }

        data class Tok(val start: Int, val end: Int, val inline: MdInline)
        val tokens = mutableListOf<Tok>()

        fun claim(re: Regex, make: (MatchResult) -> MdInline) {
            re.findAll(text).forEach { m ->
                val s = m.range.first; val e = m.range.last + 1
                if (tokens.none { s < it.end && e > it.start }) {
                    tokens.add(Tok(s, e, make(m)))
                }
            }
        }

        claim(RE_BOLD_ITALIC) { MdInline.BoldItalic(it.groupValues[1]) }
        claim(RE_BOLD) { MdInline.Bold(it.groupValues[1]) }
        claim(RE_STRIKE) { MdInline.Strike(it.groupValues[1]) }
        claim(RE_INLINE_CODE) { MdInline.Code(it.groupValues[1]) }
        claim(RE_LINK) { MdInline.Link(it.groupValues[1], it.groupValues[2]) }
        claim(RE_ITALIC) { MdInline.Italic(it.groupValues[1]) }

        tokens.sortBy { it.start }
        val out = mutableListOf<MdInline>()
        var pos = 0
        for (t in tokens) {
            if (t.start > pos) out.add(MdInline.Plain(restore(text.substring(pos, t.start), escaped)))
            out.add(restoreInline(t.inline, escaped))
            pos = t.end
        }
        if (pos < text.length) out.add(MdInline.Plain(restore(text.substring(pos), escaped)))
        return out.ifEmpty { listOf(MdInline.Plain(restore(text, escaped))) }
    }

    private fun restore(s: String, escaped: Map<String, String>): String {
        var r = s
        escaped.forEach { (k, v) -> r = r.replace(k, v) }
        return r
    }

    private fun restoreInline(i: MdInline, escaped: Map<String, String>): MdInline = when (i) {
        is MdInline.Plain -> MdInline.Plain(restore(i.text, escaped))
        is MdInline.Bold -> MdInline.Bold(restore(i.text, escaped))
        is MdInline.Italic -> MdInline.Italic(restore(i.text, escaped))
        is MdInline.BoldItalic -> MdInline.BoldItalic(restore(i.text, escaped))
        is MdInline.Code -> MdInline.Code(restore(i.text, escaped))
        is MdInline.Strike -> MdInline.Strike(restore(i.text, escaped))
        is MdInline.Link -> MdInline.Link(restore(i.text, escaped), i.url)
    }

    /** Parse teks penuh menjadi daftar block. */
    fun parse(text: String): List<MdBlock> {
        val lines = text.split("\n")
        val blocks = mutableListOf<MdBlock>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            // Code fence
            val fence = RE_CODE_FENCE.matchEntire(line.trim())
            if (fence != null) {
                val lang = fence.groupValues[1].ifBlank { "teks" }
                val buf = StringBuilder()
                i++
                while (i < lines.size && lines[i].trim() != "```") {
                    buf.appendLine(lines[i]); i++
                }
                i++ // lewati penutup
                blocks.add(MdBlock.Code(lang, buf.toString().trimEnd('\n')))
                continue
            }

            if (line.isBlank()) { i++; continue }

            val headingM = RE_HEADING.matchEntire(line.trim())
            if (headingM != null) {
                val level = headingM.groupValues[1].length.coerceAtMost(6)
                blocks.add(MdBlock.Heading(level, parseInline(headingM.groupValues[2])))
                i++; continue
            }

            if (RE_DIVIDER.matchEntire(line.trim()) != null) {
                blocks.add(MdBlock.Divider()); i++; continue
            }

            val quoteM = RE_QUOTE.matchEntire(line)
            if (quoteM != null) {
                val buf = mutableListOf<String>()
                var qi = i
                while (qi < lines.size) {
                    val q = RE_QUOTE.matchEntire(lines[qi]) ?: break
                    buf.add(q.groupValues[1]); qi++
                }
                i = qi
                blocks.add(MdBlock.Quote(parseInline(buf.joinToString(" "))))
                continue
            }

            // Tabel: baris header + baris separator
            if (line.contains("|") && i + 1 < lines.size && RE_TABLE_SEP.matchEntire(lines[i + 1].trim()) != null) {
                val headers = splitRow(line)
                val aligns = parseAligns(lines[i + 1])
                val rows = mutableListOf<List<String>>()
                i += 2
                while (i < lines.size && lines[i].contains("|") && lines[i].isNotBlank()) {
                    rows.add(splitRow(lines[i])); i++
                }
                blocks.add(MdBlock.Table(headers, aligns, rows))
                continue
            }

            // List (bullet / ordered), dukung nested max 3 level (indent 2 spasi = 1 level)
            val bulletM = RE_BULLET.matchEntire(line)
            val orderedM = if (bulletM == null) RE_ORDERED.matchEntire(line) else null
            if (bulletM != null || orderedM != null) {
                val isOrdered = orderedM != null
                val items = mutableListOf<ListItem>()
                var start = 1
                var first = true
                while (i < lines.size) {
                    val l = lines[i]
                    val bm = RE_BULLET.matchEntire(l)
                    val om = RE_ORDERED.matchEntire(l)
                    if (isOrdered && om != null) {
                        if (first) { start = om.groupValues[2].toIntOrNull() ?: 1; first = false }
                        val lvl = (om.groupValues[1].length / 2).coerceAtMost(3)
                        items.add(ListItem(parseInline(om.groupValues[3]), lvl)); i++
                    } else if (!isOrdered && bm != null) {
                        val lvl = (bm.groupValues[1].length / 2).coerceAtMost(3)
                        items.add(ListItem(parseInline(bm.groupValues[2]), lvl)); i++
                    } else break
                }
                if (isOrdered) blocks.add(MdBlock.OrderedList(items, start))
                else blocks.add(MdBlock.BulletList(items))
                continue
            }

            // Paragraf: gabung baris berurutan sampai baris kosong / block lain
            val buf = mutableListOf<String>()
            while (i < lines.size && lines[i].isNotBlank()
                && RE_HEADING.matchEntire(lines[i].trim()) == null
                && RE_CODE_FENCE.matchEntire(lines[i].trim()) == null
                && RE_QUOTE.matchEntire(lines[i]) == null
                && RE_BULLET.matchEntire(lines[i]) == null
                && RE_ORDERED.matchEntire(lines[i]) == null
                && RE_DIVIDER.matchEntire(lines[i].trim()) == null
            ) {
                // hentikan bila baris ini awal tabel
                if (lines[i].contains("|") && i + 1 < lines.size
                    && RE_TABLE_SEP.matchEntire(lines[i + 1].trim()) != null) break
                buf.add(lines[i].trim()); i++
            }
            if (buf.isNotEmpty()) blocks.add(MdBlock.Paragraph(parseInline(buf.joinToString(" "))))
            else i++
        }
        return blocks
    }

    private fun splitRow(line: String): List<String> =
        line.trim().trim('|').split("|").map { it.trim() }

    private fun parseAligns(line: String): List<Align> =
        splitRow(line).map {
            val t = it.trim()
            when {
                t.startsWith(":") && t.endsWith(":") -> Align.CENTER
                t.endsWith(":") -> Align.RIGHT
                else -> Align.LEFT
            }
        }
}

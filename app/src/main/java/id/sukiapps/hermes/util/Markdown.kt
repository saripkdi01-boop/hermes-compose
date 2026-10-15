package id.sukiapps.hermes.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * Renderer markdown ringan untuk chat — dirancang untuk streaming incremental:
 * parse() dipanggil ulang tiap token masuk; kompleksitas linear terhadap
 * panjang teks dan tanpa cache global (aman untuk recomposition).
 *
 * Mendukung: heading (#), bold (**), italic (*), inline code (`),
 * code block (```), list (-/* / 1.), quote (>), link [t](url) → t.
 */
object Markdown {

    sealed interface Block {
        data class Paragraph(val text: AnnotatedString) : Block
        data class Heading(val level: Int, val text: AnnotatedString) : Block
        data class Code(val language: String, val code: String) : Block
        data class ListBlock(val items: List<AnnotatedString>, val ordered: Boolean) : Block
        data class Quote(val text: AnnotatedString) : Block
    }

    fun parseBlocks(src: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val lines = src.split("\n")
        var i = 0
        val para = StringBuilder()
        fun flushPara() {
            if (para.isNotEmpty()) {
                blocks.add(Block.Paragraph(inline(para.toString().trim())))
                para.clear()
            }
        }
        while (i < lines.size) {
            val line = lines[i]
            val t = line.trimStart()
            when {
                t.startsWith("```") -> {
                    flushPara()
                    val lang = t.removePrefix("```").trim().take(24)
                    val code = StringBuilder()
                    i++
                    var closed = false
                    while (i < lines.size) {
                        if (lines[i].trimStart().startsWith("```")) {
                            closed = true
                            break
                        }
                        code.append(lines[i]).append('\n')
                        i++
                    }
                    // Fence belum ditutup (streaming) → tetap render sebagai kode.
                    blocks.add(Block.Code(lang, code.toString().trimEnd('\n')))
                    if (!closed) {
                        i = lines.size // sisa teks sudah masuk kode
                    }
                }
                t.matches(Regex("#{1,6}\\s.*")) -> {
                    flushPara()
                    val level = t.takeWhile { it == '#' }.length
                    blocks.add(
                        Block.Heading(
                            level,
                            inline(t.drop(level).trim())
                        )
                    )
                }
                t.startsWith(">") -> {
                    flushPara()
                    blocks.add(Block.Quote(inline(t.removePrefix(">").trim())))
                }
                t.matches(Regex("([-*+]\\s|\\d+[.)]\\s).*")) -> {
                    flushPara()
                    val items = mutableListOf<AnnotatedString>()
                    var ordered = false
                    while (i < lines.size) {
                        val lt = lines[i].trimStart()
                        val mBullet = Regex("^[-*+]\\s+(.*)").find(lt)
                        val mNum = Regex("^\\d+[.)]\\s+(.*)").find(lt)
                        if (mBullet != null) {
                            items.add(inline(mBullet.groupValues[1]))
                            i++
                        } else if (mNum != null) {
                            ordered = true
                            items.add(inline(mNum.groupValues[1]))
                            i++
                        } else break
                    }
                    blocks.add(Block.ListBlock(items, ordered))
                    continue
                }
                t.isBlank() -> flushPara()
                else -> {
                    if (para.isNotEmpty()) para.append(' ')
                    para.append(t)
                }
            }
            i++
        }
        flushPara()
        return blocks
    }

    /** Inline spans: `code`, **bold**, *italic*, link → teks. */
    fun inline(src: String): AnnotatedString = buildAnnotatedString {
        appendInline(src, 0, src.length)
    }

    private fun AnnotatedString.Builder.appendInline(s: String, from: Int, to: Int) {
        var i = from
        val linkColor = Color(0xFF4DD0E1)
        while (i < to) {
            when {
                s.startsWith("**", i) -> {
                    val end = s.indexOf("**", i + 2)
                    if (end in (i + 2) until to) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        appendInline(s, i + 2, end)
                        pop()
                        i = end + 2
                    } else {
                        append(s[i]); i++
                    }
                }
                s.startsWith("__", i) -> {
                    val end = s.indexOf("__", i + 2)
                    if (end in (i + 2) until to) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        appendInline(s, i + 2, end)
                        pop()
                        i = end + 2
                    } else {
                        append(s[i]); i++
                    }
                }
                s[i] == '`' -> {
                    val end = s.indexOf('`', i + 1)
                    if (end in (i + 1) until to) {
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                background = Color(0x33000000)
                            )
                        )
                        append(s.substring(i + 1, end))
                        pop()
                        i = end + 1
                    } else {
                        append(s[i]); i++
                    }
                }
                s[i] == '*' || s[i] == '_' -> {
                    val c = s[i]
                    val end = s.indexOf(c, i + 1)
                    if (end in (i + 1) until to) {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        appendInline(s, i + 1, end)
                        pop()
                        i = end + 1
                    } else {
                        append(s[i]); i++
                    }
                }
                s[i] == '[' -> {
                    val mid = s.indexOf("](", i)
                    if (mid > i) {
                        val close = s.indexOf(')', mid + 2)
                        if (close > mid) {
                            pushStyle(
                                SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                            appendInline(s, i + 1, mid)
                            pop()
                            i = close + 1
                            continue
                        }
                    }
                    append(s[i]); i++
                }
                else -> {
                    append(s[i]); i++
                }
            }
        }
    }

    /** Estimasi kasar token (~4 karakter/token) untuk indikator header. */
    fun estimateTokens(text: String): Int = (text.length / 4).coerceAtLeast(0)

    /** Bersihkan markdown untuk TTS (fallback bila TtsReader tak dipakai). */
    fun stripForSpeech(md: String): String =
        id.sukiapps.hermes.TtsReader.stripMarkdown(md)
}

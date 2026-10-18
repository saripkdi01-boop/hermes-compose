package id.sukiapps.hermes.ui.chatv3

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sukiapps.hermes.ui.theme.LocalChatPalette
import kotlinx.coroutines.launch

private const val COLLAPSE_LINE_LIMIT = 40

// Regex highlight ringan — dideklarasikan sekali
private val RE_KW = Regex("\\b(fun|val|var|class|object|if|else|when|for|while|return|import|package|data|sealed|interface|enum|suspend|private|public|internal|override|lateinit|companion|true|false|null|this|super|try|catch|finally|throw|in|is|as|by|out|const|annotation)\\b")
private val RE_STRING = Regex("\"(\\\\.|[^\"\\\\])*\"")
private val RE_COMMENT = Regex("//.*$")
private val RE_NUMBER = Regex("\\b\\d[\\d_]*(\\.\\d+)?\\b")
private val RE_ANNOTATION = Regex("@\\w+")

/** Highlight sintaks ringan berbasis regex. */
private fun highlightCode(
    code: String,
    kw: androidx.compose.ui.graphics.Color,
    str: androidx.compose.ui.graphics.Color,
    cmt: androidx.compose.ui.graphics.Color,
    num: androidx.compose.ui.graphics.Color,
    ann: androidx.compose.ui.graphics.Color,
    base: androidx.compose.ui.graphics.Color,
): AnnotatedString = buildAnnotatedString {
    val lines = code.split("\n")
    lines.forEachIndexed { li, line ->
        if (li > 0) append("\n")
        data class T(val s: Int, val e: Int, val style: SpanStyle)
        val toks = mutableListOf<T>()
        fun claim(re: Regex, style: SpanStyle) {
            re.findAll(line).forEach { m ->
                val s = m.range.first; val e = m.range.last + 1
                if (toks.none { s < it.e && e > it.s }) toks.add(T(s, e, style))
            }
        }
        val kwS = SpanStyle(color = kw, fontWeight = FontWeight.SemiBold)
        val strS = SpanStyle(color = str)
        val cmtS = SpanStyle(color = cmt, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        val numS = SpanStyle(color = num)
        val annS = SpanStyle(color = ann)
        claim(RE_COMMENT, cmtS)
        claim(RE_STRING, strS)
        claim(RE_ANNOTATION, annS)
        claim(RE_KW, kwS)
        claim(RE_NUMBER, numS)
        toks.sortBy { it.s }
        var pos = 0
        val baseS = SpanStyle(color = base)
        for (t in toks) {
            if (t.s > pos) withStyle(baseS) { append(line.substring(pos, t.s)) }
            withStyle(t.style) { append(line.substring(t.s, t.e)) }
            pos = t.e
        }
        if (pos < line.length) withStyle(baseS) { append(line.substring(pos)) }
        if (toks.isEmpty()) withStyle(baseS) { append(line) }
    }
}

/**
 * Blok kode: header (bahasa + salin + expand), scroll horizontal,
 * collapse otomatis bila > 40 baris.
 */
@Composable
fun MuseCodeBlock(
    language: String,
    code: String,
    snackbarHostState: SnackbarHostState? = null,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChatPalette.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val lineCount = remember(code) { code.count { it == '\n' } + 1 }
    var expanded by remember(code) { mutableStateOf(lineCount <= COLLAPSE_LINE_LIMIT) }
    val shownCode = remember(code, expanded) {
        if (expanded || lineCount <= COLLAPSE_LINE_LIMIT) code
        else code.split("\n").take(COLLAPSE_LINE_LIMIT).joinToString("\n")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.codeBackground)
            .semantics { contentDescription = "Blok kode $language, $lineCount baris" }
            .animateContentSize(),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = language.lowercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textSecondary,
            )
            Spacer(Modifier.weight(1f))
            if (lineCount > COLLAPSE_LINE_LIMIT) {
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.semantics {
                        contentDescription = if (expanded) "Ciutkan kode" else "Bentangkan kode"
                    },
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = palette.textSecondary,
                    )
                }
            }
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                    snackbarHostState?.let { hs ->
                        scope.launch { hs.showSnackbar("Tersalin") }
                    }
                },
                modifier = Modifier.semantics { contentDescription = "Salin kode" },
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = null,
                    tint = palette.textSecondary,
                )
            }
        }
        // Isi kode
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = remember(shownCode) {
                    highlightCode(
                        shownCode,
                        kw = palette.accent,
                        str = palette.success,
                        cmt = palette.textTertiary,
                        num = palette.warning,
                        ann = palette.glowAccent,
                        base = palette.textPrimary,
                    )
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
        }
        if (!expanded && lineCount > COLLAPSE_LINE_LIMIT) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "+ ${lineCount - COLLAPSE_LINE_LIMIT} baris lainnya — ketuk ikon bentang",
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                )
            }
        }
        Spacer(Modifier.width(4.dp))
    }
}

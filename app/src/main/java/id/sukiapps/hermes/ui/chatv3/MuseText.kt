package id.sukiapps.hermes.ui.chatv3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sukiapps.hermes.ui.theme.ChatPalette
import id.sukiapps.hermes.ui.theme.LocalChatPalette

/** Render inline menjadi AnnotatedString dengan warna palette. */
private fun renderInline(inlines: List<MuseParser.MdInline>, p: ChatPalette): AnnotatedString =
    buildAnnotatedString {
        for (i in inlines) when (i) {
            is MuseParser.MdInline.Plain -> append(i.text)
            is MuseParser.MdInline.Bold ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = p.textPrimary)) { append(i.text) }
            is MuseParser.MdInline.Italic ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = p.textPrimary)) { append(i.text) }
            is MuseParser.MdInline.BoldItalic ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, color = p.textPrimary)) { append(i.text) }
            is MuseParser.MdInline.Code ->
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = p.codeBackground, color = p.accent, fontSize = 13.sp)) { append(i.text) }
            is MuseParser.MdInline.Strike ->
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = p.textSecondary)) { append(i.text) }
            is MuseParser.MdInline.Link -> {
                pushStringAnnotation(tag = "URL", annotation = i.url)
                withStyle(SpanStyle(color = p.accent, textDecoration = TextDecoration.Underline)) { append(i.text) }
                pop()
            }
        }
    }

@Composable
private fun MdBlockView(
    block: MuseParser.MdBlock,
    palette: ChatPalette,
    snackbarHostState: SnackbarHostState?,
) {
    when (block) {
        is MuseParser.MdBlock.Paragraph -> Text(
            text = renderInline(block.inlines, palette),
            fontSize = 15.sp, lineHeight = 24.sp, color = palette.textPrimary,
            modifier = Modifier.fillMaxWidth(),
        )
        is MuseParser.MdBlock.Heading -> {
            val (size, weight) = when (block.level) {
                1 -> 20.sp to FontWeight.Bold
                2 -> 18.sp to FontWeight.Bold
                3 -> 17.sp to FontWeight.SemiBold
                else -> 15.sp to FontWeight.SemiBold
            }
            Text(
                text = renderInline(block.inlines, palette),
                fontSize = size, fontWeight = weight, color = palette.textPrimary,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
        is MuseParser.MdBlock.Quote -> Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier.width(3.dp).height(48.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(palette.accent)
                    .align(Alignment.CenterVertically),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = renderInline(block.inlines, palette),
                fontSize = 15.sp, lineHeight = 24.sp,
                fontStyle = FontStyle.Italic, color = palette.textSecondary,
                modifier = Modifier.weight(1f),
            )
        }
        is MuseParser.MdBlock.Code -> MuseCodeBlock(
            language = block.language, code = block.code,
            snackbarHostState = snackbarHostState,
            modifier = Modifier.fillMaxWidth(),
        )
        is MuseParser.MdBlock.Divider -> Divider(
            color = palette.divider, thickness = 1.dp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        )
        is MuseParser.MdBlock.BulletList -> Column(Modifier.fillMaxWidth()) {
            block.items.forEach { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Spacer(Modifier.width((item.level * 16).dp))
                    Text("•", fontSize = 15.sp, color = palette.accent,
                        modifier = Modifier.width(20.dp))
                    Text(
                        text = renderInline(item.inlines, palette),
                        fontSize = 15.sp, lineHeight = 24.sp, color = palette.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        is MuseParser.MdBlock.OrderedList -> Column(Modifier.fillMaxWidth()) {
            block.items.forEachIndexed { idx, item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Spacer(Modifier.width((item.level * 16).dp))
                    Text("${block.start + idx}.", fontSize = 15.sp, color = palette.accent,
                        fontWeight = FontWeight.Medium, modifier = Modifier.width(28.dp))
                    Text(
                        text = renderInline(item.inlines, palette),
                        fontSize = 15.sp, lineHeight = 24.sp, color = palette.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        is MuseParser.MdBlock.Table -> {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surfaceVariant)
                    .padding(4.dp),
            ) {
                // header
                Row(Modifier.fillMaxWidth()) {
                    block.headers.forEach { h ->
                        Text(h, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = palette.textPrimary,
                            modifier = Modifier.weight(1f).padding(6.dp))
                    }
                }
                Divider(color = palette.divider, thickness = 1.dp)
                block.rows.forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        // samakan jumlah kolom dengan header
                        for (c in block.headers.indices) {
                            Text(row.getOrElse(c) { "" }, fontSize = 13.sp,
                                color = palette.textPrimary,
                                textAlign = when (block.aligns.getOrElse(c) { MuseParser.Align.LEFT }) {
                                    MuseParser.Align.CENTER -> TextAlign.Center
                                    MuseParser.Align.RIGHT -> TextAlign.End
                                    else -> TextAlign.Start
                                },
                                modifier = Modifier.weight(1f).padding(6.dp))
                        }
                    }
                    Divider(color = palette.divider, thickness = 0.5.dp)
                }
            }
        }
    }
}

/**
 * Renderer teks chat gaya Muse: parse sekali (remember), render block,
 * dukung caret streaming di akhir.
 */
@Composable
fun MuseText(
    text: String,
    isStreaming: Boolean = false,
    snackbarHostState: SnackbarHostState? = null,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChatPalette.current
    // Parse sekali per perubahan teks — bukan per recomposition
    val blocks = remember(text) { MuseParser.parse(text) }

    Column(
        modifier = modifier.semantics { contentDescription = "Pesan teks" },
    ) {
        blocks.forEachIndexed { idx, block ->
            if (idx > 0) Spacer(Modifier.height(8.dp))
            MdBlockView(block, palette, snackbarHostState)
        }
        if (isStreaming) {
            // Kursor streaming berdenyut
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.height(4.dp))
                StreamingCaret()
            }
        }
    }
}

/** Kursor blok kecil untuk status streaming. */
@Composable
private fun StreamingCaret() {
    val palette = LocalChatPalette.current
    Box(
        modifier = Modifier
            .width(8.dp)
            .height(18.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(palette.streamCaret),
    )
}

// LazyColumn tidak dipakai di sini agar MuseText bisa ditanam di dalam
// LazyColumn induk (hindari nested scroll). Tabel dirender sebagai Column biasa.

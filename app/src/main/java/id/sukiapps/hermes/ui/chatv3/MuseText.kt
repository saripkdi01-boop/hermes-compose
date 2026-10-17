package id.sukiapps.hermes.ui.chatv3

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.sukiapps.hermes.ui.theme.MuseChatDark

/**
 * Renderer teks gaya Muse: paragraf, list, heading, quote.
 * Spacing presisi seperti chat Muse AI.
 */
@Composable
fun MuseText(
    text: String,
    modifier: Modifier = Modifier
) {
    val c = MuseChatDark
    val blocks = remember(text) { parseMuseBlocks(text) }

    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MuseBlock.Paragraph -> {
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.textPrimary,
                        lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.6f
                    )
                }
                is MuseBlock.Heading -> {
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = c.textPrimary,
                        modifier = Modifier.padding(top = if (index > 0) 12.dp else 0.dp)
                    )
                }
                is MuseBlock.BulletList -> {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        block.items.forEach { item ->
                            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = c.textSecondary,
                                    modifier = Modifier.width(20.dp)
                                )
                                Text(
                                    text = item,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = c.textPrimary,
                                    lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.6f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                is MuseBlock.NumberedList -> {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        block.items.forEachIndexed { i, item ->
                            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                Text(
                                    text = "${i + 1}.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = c.textSecondary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(28.dp)
                                )
                                Text(
                                    text = item,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = c.textPrimary,
                                    lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.6f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                is MuseBlock.Quote -> {
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        Spacer(
                            modifier = Modifier
                                .width(3.dp)
                                .padding(vertical = 2.dp)
                        )
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = c.textSecondary,
                            lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.6f,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        )
                    }
                }
                is MuseBlock.Code -> {
                    MuseCodeBlock(code = block.code)
                }
            }
            // Spacing antar blok: 8dp (paragraf), 4dp (list item sudah handle).
            if (index < blocks.size - 1 && block is MuseBlock.Paragraph) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

// ─── Parser ───

sealed interface MuseBlock {
    data class Paragraph(val text: String) : MuseBlock
    data class Heading(val text: String) : MuseBlock
    data class BulletList(val items: List<String>) : MuseBlock
    data class NumberedList(val items: List<String>) : MuseBlock
    data class Quote(val text: String) : MuseBlock
    data class Code(val code: String) : MuseBlock
}

fun parseMuseBlocks(text: String): List<MuseBlock> {
    val blocks = mutableListOf<MuseBlock>()
    val lines = text.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        when {
            // Code block
            trimmed.startsWith("```") -> {
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                i++ // lewati penutup
                blocks.add(MuseBlock.Code(codeLines.joinToString("\n")))
            }
            // Heading
            trimmed.startsWith("#") -> {
                val level = trimmed.takeWhile { it == '#' }.length
                val content = trimmed.drop(level).trim()
                blocks.add(MuseBlock.Heading(content))
                i++
            }
            // Quote
            trimmed.startsWith(">") -> {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quoteLines.add(lines[i].trim().removePrefix(">").trim())
                    i++
                }
                blocks.add(MuseBlock.Quote(quoteLines.joinToString("\n")))
            }
            // Bullet list
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (t.startsWith("- ") || t.startsWith("* ")) {
                        items.add(t.drop(2))
                        i++
                    } else break
                }
                blocks.add(MuseBlock.BulletList(items))
            }
            // Numbered list
            Regex("^\\d+\\. ").containsMatchIn(trimmed) -> {
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val t = lines[i].trim()
                    val m = Regex("^(\\d+)\\. (.*)").find(t)
                    if (m != null) {
                        items.add(m.groupValues[2])
                        i++
                    } else break
                }
                blocks.add(MuseBlock.NumberedList(items))
            }
            // Baris kosong: lewati
            trimmed.isEmpty() -> i++
            // Paragraf: gabung baris berurutan
            else -> {
                val paraLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().isNotEmpty()
                    && !lines[i].trim().startsWith("#")
                    && !lines[i].trim().startsWith(">")
                    && !lines[i].trim().startsWith("```")
                    && !lines[i].trim().startsWith("- ")
                    && !Regex("^\\d+\\. ").containsMatchIn(lines[i].trim())
                ) {
                    paraLines.add(lines[i].trim())
                    i++
                }
                if (paraLines.isNotEmpty()) {
                    blocks.add(MuseBlock.Paragraph(paraLines.joinToString(" ")))
                }
            }
        }
    }
    return blocks
}

package id.sukiapps.hermes.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sukiapps.hermes.util.Markdown

/**
 * Render markdown per blok. Dipanggil ulang tiap token saat streaming —
 * parse linear, tanpa state global.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(text) { Markdown.parseBlocks(text) }
    SelectionContainer {
        Column(modifier = modifier) {
            for (b in blocks) {
                when (b) {
                    is Markdown.Block.Paragraph -> {
                        Text(
                            text = b.text,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    is Markdown.Block.Heading -> {
                        val style = when (b.level) {
                            1 -> MaterialTheme.typography.headlineSmall
                            2 -> MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp)
                            else -> MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(text = b.text, style = style)
                        Spacer(Modifier.height(6.dp))
                    }
                    is Markdown.Block.Code -> {
                        CodeBlock(language = b.language, code = b.code)
                        Spacer(Modifier.height(6.dp))
                    }
                    is Markdown.Block.ListBlock -> {
                        Column(Modifier.padding(start = 4.dp)) {
                            b.items.forEachIndexed { idx, item ->
                                val marker = if (b.ordered) "${idx + 1}." else "•"
                                Text(
                                    text = androidx.compose.ui.text.buildAnnotatedString {
                                        append(marker)
                                        append("  ")
                                        append(item)
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    is Markdown.Block.Quote -> {
                        Text(
                            text = b.text,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.padding(
                                start = 8.dp,
                                top = 2.dp,
                                bottom = 2.dp
                            )
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

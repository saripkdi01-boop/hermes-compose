package id.sukiapps.hermes.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
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
 * Jarak antar blok — SATU token (8 dp), bukan spacer tersebar.
 * Berlaku sama untuk pesan final dan pratinjau streaming.
 */
private val BlockSpacing = 8.dp

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
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(BlockSpacing)
        ) {
            for (b in blocks) {
                when (b) {
                    is Markdown.Block.Paragraph -> {
                        Text(
                            text = b.text,
                            style = MaterialTheme.typography.bodyLarge
                        )
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
                    }
                    is Markdown.Block.Code -> {
                        CodeBlock(language = b.language, code = b.code)
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
                    }
                    is Markdown.Block.Table -> {
                        MarkdownTable(table = b)
                    }
                }
            }
        }
    }
}

/**
 * Tabel GFM: header dibedakan (tebal + latar varian), sel wrap dalam
 * lebar min–maks, dan geser horizontal bila tabel lebih lebar dari
 * bubble — tabel tidak pernah memaksa bubble melebar melampaui layar.
 */
@Composable
private fun MarkdownTable(table: Markdown.Block.Table) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Column {
            Row {
                table.headers.forEach { header ->
                    Box(
                        modifier = Modifier
                            .widthIn(min = 88.dp, max = 220.dp)
                            .background(scheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = header,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = scheme.outlineVariant
            )
            table.rows.forEach { row ->
                Row {
                    row.forEach { cell ->
                        Box(
                            modifier = Modifier
                                .widthIn(min = 88.dp, max = 220.dp)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cell,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = scheme.outlineVariant
                )
            }
        }
    }
}

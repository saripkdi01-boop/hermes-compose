package id.sukiapps.hermes.ui.chatv3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import id.sukiapps.hermes.ui.theme.HermesColors

/**
 * Hasil pencarian dalam chat.
 */
data class V3SearchHit(
    val messageId: String,
    val messageIndex: Int
)

/**
 * Cari query di daftar pesan (case-insensitive).
 */
fun v3Search(messages: List<V3Message>, query: String): List<V3SearchHit> {
    if (query.isBlank()) return emptyList()
    val q = query.lowercase()
    return messages.mapIndexedNotNull { i, m ->
        if (m.text.lowercase().contains(q)) V3SearchHit(m.id, i) else null
    }
}

/**
 * Highlight query di teks.
 */
fun v3Highlight(text: String, query: String): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val q = query.lowercase()
    val lower = text.lowercase()
    return buildAnnotatedString {
        var start = 0
        while (true) {
            val idx = lower.indexOf(q, start)
            if (idx < 0) {
                append(text.substring(start))
                break
            }
            append(text.substring(start, idx))
            withStyle(SpanStyle(background = Color.Yellow.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)) {
                append(text.substring(idx, idx + q.length))
            }
            start = idx + q.length
        }
    }
}

/**
 * Bar pencarian dalam chat: query + counter "3 dari 12" + navigasi atas/bawah.
 */
@Composable
fun V3SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    current: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = HermesColors.bubbleIn(),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Cari",
                tint = HermesColors.textSecondary()
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(text = "Cari dalam percakapan") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            if (total > 0) {
                Text(
                    text = "${if (current >= 0) current + 1 else 0} dari $total",
                    style = MaterialTheme.typography.labelMedium,
                    color = HermesColors.textSecondary()
                )
                IconButton(onClick = onPrev) {
                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Sebelumnya")
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Berikutnya")
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "Tutup")
            }
        }
    }
}

package id.sukiapps.hermes.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Batas karakter sebelum pesan dilipat (v16). */
private const val COLLAPSE_THRESHOLD = 300

/**
 * v16 — Bungkus isi pesan agar pesan panjang (>300 karakter) tampil
 * ringkas (~4 baris) dengan tombol "Selengkapnya"; ketuk untuk
 * mengembangkan penuh dengan transisi fade. State per pesan (key = teks;
 * teks pesan tersimpan tidak berubah, jadi aman).
 *
 * Dipakai untuk pesan user (Text biasa) dan pesan assistant (MarkdownText)
 * — keduanya lewat lambda [content] yang sama. Tidak dipakai saat
 * streaming (teks masih tumbuh).
 */
@Composable
fun ExpandableMessageContent(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit
) {
    var expanded by remember(text) { mutableStateOf(false) }
    val collapsible = text.length > COLLAPSE_THRESHOLD
    Column(modifier = modifier.animateContentSize()) {
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                fadeIn(animationSpec = tween(200)) togetherWith
                    fadeOut(animationSpec = tween(200))
            },
            label = "expandMessage"
        ) { isExpanded ->
            if (isExpanded || !collapsible) {
                content(text)
            } else {
                content(collapsedPreview(text))
            }
        }
        if (collapsible) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(if (expanded) "Tutup" else "Selengkapnya")
            }
        }
    }
}

/** Potong ke ~300 karakter di batas kata + elipsis. */
private fun collapsedPreview(text: String): String {
    if (text.length <= COLLAPSE_THRESHOLD) return text
    val cut = text.take(COLLAPSE_THRESHOLD)
    val atWord = cut.substringBeforeLast(' ', cut)
    return (if (atWord.isBlank()) cut else atWord).trimEnd() + "…"
}

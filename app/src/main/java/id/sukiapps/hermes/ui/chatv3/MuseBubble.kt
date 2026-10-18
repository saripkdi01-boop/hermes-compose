package id.sukiapps.hermes.ui.chatv3

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sukiapps.hermes.ui.theme.LocalChatPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val timeFmt = SimpleDateFormat("HH:mm", Locale("id", "ID"))

/** Indikator titik berpikir berdenyut (3 titik). */
@Composable
private fun ThinkingDots() {
    val palette = LocalChatPalette.current
    val t = rememberInfiniteTransition(label = "thinking")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { i ->
            val a by t.animateFloat(
                initialValue = 0.3f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = i * 200),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                Modifier.size(8.dp).alpha(a)
                    .clip(CircleShape).background(palette.textSecondary),
            )
        }
    }
}

/**
 * Bubble chat v14.
 *
 * @param message V3Message (sender: "user"/"ai"/"upin"/"system")
 * @param isStreaming true bila pesan ini sedang di-streaming
 * @param isThinking true bila AI sedang berpikir (tampilkan 3 titik)
 * @param isError true bila pesan gagal (tampilkan tombol coba lagi)
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MuseBubble(
    message: V3Message,
    isStreaming: Boolean = false,
    isThinking: Boolean = false,
    isError: Boolean = false,
    extrasStore: V3ExtrasStore? = null,
    snackbarHostState: SnackbarHostState? = null,
    onRetry: (() -> Unit)? = null,
    onEdit: ((String) -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChatPalette.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }

    val extras by extrasStore?.state?.collectAsState()
        ?: remember { mutableStateOf(emptyMap<String, V3ExtrasStore.Extras>()) }
    val myExtras = extras[message.id]
    val displayText = myExtras?.editedText ?: message.text
    val isUser = message.sender == "user"

    val timeStr = remember(message.timestamp) {
        if (message.timestamp > 0) timeFmt.format(Date(message.timestamp)) else ""
    }

    // Status kirim: ✓ terkirim, ⟳ mengirim, ! gagal
    val statusGlyph = when {
        isError -> "!"
        isStreaming -> "⟳"
        else -> "✓"
    }
    val statusColor = if (isError) palette.errorColor else palette.textTertiary

    Column(modifier.fillMaxWidth()) {
        if (!isUser) {
            // Label pengirim AI
            Text(
                text = message.senderName.ifBlank { "Hermes" },
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = palette.accent,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        ) {
            if (isUser) Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showMenu = true },
                    )
                    .semantics {
                        contentDescription =
                            "${if (isUser) "Anda" else "Hermes"}: $displayText"
                    },
            ) {
                if (isUser) {
                    // Bubble pengguna: radius 20dp
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(palette.userBubble)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        MuseText(text = displayText, isStreaming = false)
                        if (timeStr.isNotEmpty()) {
                            Row(
                                modifier = Modifier.align(Alignment.End),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(timeStr, fontSize = 11.sp, color = palette.textTertiary)
                                Spacer(Modifier.width(4.dp))
                                Text(statusGlyph, fontSize = 11.sp, color = statusColor)
                            }
                        }
                    }
                } else {
                    // AI: tanpa bubble, teks penuh
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isError)
                                    Modifier.border(1.dp, palette.errorColor, RoundedCornerShape(12.dp))
                                else Modifier
                            )
                            .padding(if (isError) 10.dp else 0.dp),
                    ) {
                        if (isThinking) {
                            ThinkingDots()
                        } else {
                            MuseText(
                                text = displayText,
                                isStreaming = isStreaming,
                                snackbarHostState = snackbarHostState,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            if (timeStr.isNotEmpty()) {
                                Text(timeStr, fontSize = 11.sp, color = palette.textTertiary)
                            }
                            if (isError && onRetry != null) {
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = onRetry) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null,
                                        tint = palette.errorColor,
                                        modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Coba lagi", fontSize = 12.sp, color = palette.errorColor)
                                }
                            }
                        }
                    }
                }
            }

            if (!isUser) Spacer(Modifier.weight(1f))
        }

        // Bar reaksi (bila ada) — pakai V3_REACTIONS dari V3Models.kt
        myExtras?.reaction?.let { r ->
            Row(
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    .clip(CircleShape)
                    .background(palette.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(r, fontSize = 14.sp)
            }
        }
    }

    // Bottom sheet menu long-press
    if (showMenu) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showMenu = false },
            sheetState = sheetState,
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                // Bar reaksi cepat (dari V3Models.kt)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    V3_REACTIONS.forEach { emoji ->
                        IconButton(onClick = {
                            extrasStore?.toggleReaction(message.id, emoji)
                            showMenu = false
                        }) {
                            Text(
                                emoji, fontSize = 24.sp,
                                modifier = Modifier.alpha(
                                    if (myExtras?.reaction == emoji) 1f else 0.6f,
                                ),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                SheetItem("Salin teks") {
                    clipboard.setText(AnnotatedString(displayText))
                    snackbarHostState?.let { hs ->
                        scope.launch { hs.showSnackbar("Tersalin") }
                    }
                    showMenu = false
                }
                SheetItem(if (myExtras?.starred == true) "Batalkan bintang" else "Simpan (bintang)") {
                    extrasStore?.toggleStar(message.id); showMenu = false
                }
                if (!isUser && onRetry != null) {
                    SheetItem("Ulangi respons") { onRetry(); showMenu = false }
                }
                if (isUser && onEdit != null) {
                    SheetItem("Edit pesan") { onEdit(displayText); showMenu = false }
                }
                extrasStore?.let { store ->
                    SheetItem("Ekspor sebagai Markdown") {
                        val md = store.exportMd(message.id, message.text,
                            if (isUser) "Anda" else "Hermes")
                        clipboard.setText(AnnotatedString(md))
                        snackbarHostState?.let { hs ->
                            scope.launch { hs.showSnackbar("Markdown tersalin") }
                        }
                        showMenu = false
                    }
                }
                if (onReport != null) {
                    SheetItem("Laporkan masalah") { onReport(); showMenu = false }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SheetItem(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            label, fontSize = 15.sp,
            color = LocalChatPalette.current.textPrimary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

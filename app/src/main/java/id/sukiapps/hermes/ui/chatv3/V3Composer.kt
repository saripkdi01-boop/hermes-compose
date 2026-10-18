package id.sukiapps.hermes.ui.chatv3

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sukiapps.hermes.ui.theme.LocalChatPalette

/**
 * Komposer chat v14: tumbuh 1→8 baris, tombol kirim kapsul 48dp,
 * tombol Stop merah saat streaming, sheet lampiran, chip model+token,
 * draft tersimpan per chat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V3Composer(
    onSend: (String) -> Unit,
    isStreaming: Boolean = false,
    onStop: (() -> Unit)? = null,
    onAttach: ((String) -> Unit)? = null,
    onVoiceInput: (() -> Unit)? = null,
    modelName: String? = null,
    tokenEstimate: Int? = null,
    extrasStore: V3ExtrasStore? = null,
    chatId: String? = null, // kompatibilitas: diabaikan bila extrasStore diberikan
    modifier: Modifier = Modifier,
) {
    val palette = LocalChatPalette.current
    var text by remember { mutableStateOf("") }
    var showAttach by remember { mutableStateOf(false) }
    var draftLoaded by remember { mutableStateOf(false) }

    // Muat draft sekali
    LaunchedEffect(extrasStore) {
        if (!draftLoaded) {
            extrasStore?.loadDraft { d ->
                if (d.isNotEmpty()) text = d
                draftLoaded = true
            } ?: run { draftLoaded = true }
        }
    }

    fun doSend() {
        val t = text.trim()
        if (t.isEmpty() || isStreaming) return
        onSend(t)
        text = ""
        extrasStore?.saveDraft("")
    }

    Column(modifier.fillMaxWidth()) {
        // Chip model + token
        if (modelName != null || tokenEstimate != null) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                modelName?.let {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = palette.surfaceVariant,
                    ) {
                        Text(
                            it, fontSize = 12.sp, color = palette.textSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                tokenEstimate?.let {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = palette.surfaceVariant,
                    ) {
                        Text(
                            "±$it token", fontSize = 12.sp, color = palette.textSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        // Bar komposer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Tombol +
            IconButton(
                onClick = { showAttach = true },
                modifier = Modifier.size(48.dp)
                    .semantics { contentDescription = "Lampirkan" },
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = palette.textSecondary)
            }

            // Kolom teks
            TextField(
                value = text,
                onValueChange = {
                    text = it
                    extrasStore?.saveDraft(it)
                },
                placeholder = { Text("Tulis pesan…", fontSize = 15.sp, color = palette.textTertiary) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp, max = 192.dp) // 1→8 baris @24sp
                    .clip(RoundedCornerShape(24.dp))
                    .background(palette.inputBackground)
                    .animateContentSize()
                    .semantics { contentDescription = "Kolom tulis pesan" },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = palette.cursorColor,
                ),
                maxLines = 8,
            )

            Spacer(Modifier.width(4.dp))

            if (isStreaming) {
                // Tombol Stop merah
                IconButton(
                    onClick = { onStop?.invoke() },
                    modifier = Modifier.size(48.dp)
                        .clip(CircleShape)
                        .background(palette.errorColor)
                        .semantics { contentDescription = "Hentikan respons" },
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, tint = palette.textPrimary)
                }
            } else if (text.isBlank()) {
                // Tombol mic bila kosong
                IconButton(
                    onClick = { onVoiceInput?.invoke() },
                    modifier = Modifier.size(48.dp)
                        .semantics { contentDescription = "Input suara" },
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null, tint = palette.textSecondary)
                }
            } else {
                // Tombol kirim kapsul
                IconButton(
                    onClick = ::doSend,
                    modifier = Modifier.size(48.dp)
                        .clip(CircleShape)
                        .background(palette.accent)
                        .semantics { contentDescription = "Kirim pesan" },
                ) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = null,
                        tint = palette.textPrimary)
                }
            }
        }
    }

    // Sheet lampiran
    if (showAttach) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showAttach = false },
            sheetState = sheetState,
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    "Lampirkan", fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                AttachItem("Kamera") { onAttach?.invoke("kamera"); showAttach = false }
                AttachItem("Galeri (multi)") { onAttach?.invoke("galeri"); showAttach = false }
                AttachItem("File / Dokumen") { onAttach?.invoke("file"); showAttach = false }
                AttachItem("Ambil dari web") { onAttach?.invoke("web"); showAttach = false }
                AttachItem("Tanya Upin") { onAttach?.invoke("upin"); showAttach = false }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AttachItem(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            label, fontSize = 15.sp,
            color = LocalChatPalette.current.textPrimary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Estimasi token kasar: ~4 karakter per token. */
fun v3EstimateTokens(text: String): Int = (text.length / 4).coerceAtLeast(0)

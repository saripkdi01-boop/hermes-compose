package id.sukiapps.hermes.ui.chatv3

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import id.sukiapps.hermes.ui.theme.HermesColors

/**
 * Composer Tahap 4: tumbuh hingga 6 baris, mic→send morph,
 * sheet "+", draft per-chat via V3ExtrasStore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V3Composer(
    chatId: String,
    onSend: (String) -> Unit,
    onMic: () -> Unit = {},
    onCamera: () -> Unit = {},
    onGallery: () -> Unit = {},
    onDocument: () -> Unit = {},
    onWeb: () -> Unit = {},
    onAskUpin: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var text by remember(chatId) { mutableStateOf(V3ExtrasStore.loadDraft(chatId)) }
    var sheetOpen by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Simpan draft saat teks berubah (debounce sederhana via LaunchedEffect).
    LaunchedEffect(text) {
        V3ExtrasStore.saveDraft(chatId, text)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = HermesColors.bubbleIn(),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(onClick = { sheetOpen = true }) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Lampiran",
                    tint = HermesColors.textSecondary()
                )
            }
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp, max = 168.dp) // ~6 baris
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        text = "Ketik pesan",
                        style = MaterialTheme.typography.bodyLarge,
                        color = HermesColors.textSecondary()
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (text.isNotBlank()) {
                            onSend(text.trim())
                            text = ""
                        }
                    }
                ),
                maxLines = 6
            )
            // Mic → Send morph.
            IconButton(onClick = {
                if (text.isNotBlank()) {
                    onSend(text.trim())
                    text = ""
                } else {
                    onMic()
                }
            }) {
                Icon(
                    imageVector = if (text.isNotBlank()) Icons.Outlined.Send else Icons.Outlined.Mic,
                    contentDescription = if (text.isNotBlank()) "Kirim" else "Rekam suara",
                    tint = if (text.isNotBlank()) HermesColors.badgeUnread() else HermesColors.textSecondary(),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                V3SheetRow("Kamera", Icons.Outlined.CameraAlt) {
                    onCamera(); sheetOpen = false
                }
                V3SheetRow("Galeri", Icons.Outlined.Image) {
                    onGallery(); sheetOpen = false
                }
                V3SheetRow("Dokumen", Icons.Outlined.Description) {
                    onDocument(); sheetOpen = false
                }
                V3SheetRow("Cari di web", Icons.Outlined.Language) {
                    onWeb(); sheetOpen = false
                }
                V3SheetRow("Tanya Upin", Icons.Outlined.Mic) {
                    onAskUpin(); sheetOpen = false
                }
            }
        }
    }
}

@Composable
private fun V3SheetRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    androidx.compose.material3.DropdownMenuItem(
        text = { Text(text = label) },
        leadingIcon = { Icon(imageVector = icon, contentDescription = null) },
        onClick = onClick
    )
}

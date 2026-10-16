package id.sukiapps.hermes.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.sukiapps.hermes.util.FileUtil

/**
 * Input bar: pill-shaped, multiline auto-grow (maks 6 baris),
 * tombol kirim ↔ stop saat streaming, chip lampiran yang bisa di-dismiss.
 */
@Composable
fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    isStreaming: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    attachments: List<AttachmentItem>,
    onRemoveAttachment: (AttachmentItem) -> Unit,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickDocument: () -> Unit,
    onWebFetch: () -> Unit,
    onAskUpin: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        if (attachments.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(attachments, key = { it.uri.toString() }) { a ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = {
                            Text(
                                text = "${a.name} (${FileUtil.formatSize(a.size)})",
                                maxLines = 1
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (a.kind == AttachmentItem.Kind.IMAGE)
                                    Icons.Filled.Image else Icons.Filled.Description,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { onRemoveAttachment(a) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Hapus lampiran",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    )
                }
            }
        }
        Surface(
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Tambah lampiran"
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Foto dari galeri") },
                        leadingIcon = {
                            Icon(Icons.Filled.Image, contentDescription = null)
                        },
                        onClick = { menuOpen = false; onPickImage() }
                    )
                    DropdownMenuItem(
                        text = { Text("Ambil foto (kamera)") },
                        leadingIcon = {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                        },
                        onClick = { menuOpen = false; onTakePhoto() }
                    )
                    DropdownMenuItem(
                        text = { Text("Dokumen / file") },
                        leadingIcon = {
                            Icon(Icons.Filled.Description, contentDescription = null)
                        },
                        onClick = { menuOpen = false; onPickDocument() }
                    )
                    DropdownMenuItem(
                        text = { Text("Ambil dari web") },
                        leadingIcon = {
                            Icon(Icons.Filled.Language, contentDescription = null)
                        },
                        onClick = { menuOpen = false; onWebFetch() }
                    )
                    // v9.4: Tanya Upin — sejajar "Ambil dari web".
                    DropdownMenuItem(
                        text = { Text("Tanya Upin") },
                        leadingIcon = {
                            Icon(Icons.Filled.Chat, contentDescription = null)
                        },
                        enabled = value.isNotBlank(),
                        onClick = { menuOpen = false; onAskUpin() }
                    )
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = { Text("Tulis pesan…") },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(max = 150.dp),
                    maxLines = 6,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                Spacer(Modifier.width(4.dp))
                AnimatedContent(
                    targetState = isStreaming,
                    label = "sendStop"
                ) { streaming ->
                    if (streaming) {
                        FilledIconButton(
                            onClick = onStop,
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Filled.Stop,
                                contentDescription = "Hentikan"
                            )
                        }
                    } else {
                        FilledIconButton(
                            onClick = onSend,
                            enabled = value.isNotBlank() || attachments.isNotEmpty(),
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Filled.Send,
                                contentDescription = "Kirim"
                            )
                        }
                    }
                }
            }
        }
    }
}

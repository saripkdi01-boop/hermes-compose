package id.sukiapps.hermes.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sukiapps.hermes.ui.theme.LocalSettingsMotion
import id.sukiapps.hermes.ui.theme.LocalSettingsShape
import id.sukiapps.hermes.ui.theme.LocalSettingPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ===== KOMP-5: TestButton =====

sealed interface TestState {
    data object Idle : TestState
    data object Loading : TestState
    data class Success(val latencyMs: Long) : TestState
    data class Error(val message: String) : TestState
}

@Composable
fun TestButton(
    state: TestState,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSettingPalette.current
    val shape = LocalSettingsShape.current

    val (label, enabled) = when (state) {
        is TestState.Idle -> "Uji koneksi" to true
        is TestState.Loading -> "Memeriksa…" to false
        is TestState.Success -> "Tersambung ${state.latencyMs}ms" to true
        is TestState.Error -> "Coba lagi" to true
    }

    Button(
        onClick = onTest,
        enabled = enabled,
        shape = shape.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = when (state) {
                is TestState.Success -> palette.statusReady
                is TestState.Error -> palette.statusError
                else -> palette.accent
            },
            contentColor = palette.textOnAccent,
            disabledContainerColor = palette.textTertiary.copy(alpha = 0.3f),
        ),
        modifier = modifier
            .height(48.dp)
            .semantics { contentDescription = "Tombol uji: $label" },
    ) {
        if (state is TestState.Loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = palette.textOnAccent,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }

    if (state is TestState.Error) {
        Text(
            text = state.message,
            fontSize = 12.sp,
            color = palette.statusError,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// ===== KOMP-6: SecretField =====

@Composable
fun SecretField(
    hasValue: Boolean,
    isDirty: Boolean,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onCopy: (() -> Unit)? = null,
    label: String = "Kunci rahasia",
    modifier: Modifier = Modifier,
) {
    val palette = LocalSettingPalette.current
    val shape = LocalSettingsShape.current
    val clipboard = LocalClipboardManager.current

    var input by remember { mutableStateOf("") }
    var showPlain by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textPrimary,
                modifier = Modifier.weight(1f),
            )
            // Chip status: TIDAK PERNAH tampilkan nilai asli
            val chipText = when {
                isDirty -> "Belum disimpan"
                hasValue -> "Tersimpan"
                else -> "Belum diisi"
            }
            val chipColor = when {
                isDirty -> palette.statusPending
                hasValue -> palette.statusReady
                else -> palette.textTertiary
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(chipColor.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(chipText, fontSize = 12.sp, color = chipColor)
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = if (hasValue && input.isEmpty() && !isDirty) "••••••••••••" else input,
            onValueChange = { input = it },
            placeholder = { Text("Tempel kunci baru…", fontSize = 14.sp) },
            visualTransformation = if (showPlain) VisualTransformation.None
                else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            shape = shape.field,
            trailingIcon = {
                Row {
                    IconButton(
                        onClick = { showPlain = !showPlain },
                        modifier = Modifier.semantics {
                            contentDescription = if (showPlain) "Sembunyikan" else "Tampilkan"
                        },
                    ) {
                        Icon(
                            if (showPlain) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null,
                            tint = palette.textSecondary,
                        )
                    }
                    if (hasValue && onCopy != null) {
                        IconButton(
                            onClick = {
                                // Copy hanya bila user eksplisit — nilai tidak ditampilkan
                                onCopy()
                            },
                            modifier = Modifier.semantics { contentDescription = "Salin" },
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null,
                                tint = palette.textSecondary)
                        }
                    }
                    if (hasValue) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.semantics { contentDescription = "Hapus kunci" },
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null,
                                tint = palette.statusError)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (input.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ActionBarPair(
                leftLabel = "Batal",
                onLeftClick = { input = "" },
                rightLabel = "Simpan",
                onRightClick = { onSave(input); input = "" },
            )
        }
    }
}

// ===== KOMP-11: HoldToConfirmButton =====

@Composable
fun HoldToConfirmButton(
    label: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    holdingLabel: String = "Tahan untuk konfirmasi…",
) {
    val palette = LocalSettingPalette.current
    val shape = LocalSettingsShape.current
    val motion = LocalSettingsMotion.current
    val haptic = LocalHapticFeedback.current

    var progress by remember { mutableFloatStateOf(0f) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = remember { CoroutineScope(Dispatchers.Main) }

    fun cancel() {
        job?.cancel()
        job = null
        progress = 0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape.button)
            .background(palette.statusError.copy(alpha = 0.06f))
            .border(1.dp, palette.statusError.copy(alpha = 0.4f), shape.button)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        // Mulai tahan
                        job = scope.launch {
                            val start = System.currentTimeMillis()
                            while (true) {
                                val elapsed = System.currentTimeMillis() - start
                                progress = (elapsed.toFloat() / motion.holdToConfirmMs)
                                    .coerceIn(0f, 1f)
                                if (progress >= 1f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onConfirm()
                                    cancel()
                                    break
                                }
                                delay(16)
                            }
                        }
                        tryAwaitRelease()
                        // Lepas sebelum selesai = reset
                        cancel()
                    }
                )
            }
            .semantics {
                contentDescription = "$label. Tahan 800ms untuk konfirmasi."
            },
        contentAlignment = Alignment.Center,
    ) {
        // Progress radial (disederhanakan jadi bar horizontal di bawah)
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(48.dp)
                .align(Alignment.CenterStart)
                .background(palette.statusError.copy(alpha = 0.18f)),
        )
        Text(
            text = if (progress > 0f) holdingLabel else label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.statusError,
        )
    }
}

// ===== KOMP-10: DangerZoneCard =====

@Composable
fun DangerZoneCard(
    title: String = "Zona Berbahaya",
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSettingPalette.current
    val shape = LocalSettingsShape.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape.card)
            .background(palette.statusError.copy(alpha = 0.06f))
            .border(1.dp, palette.statusError.copy(alpha = 0.4f), shape.card)
            .padding(16.dp),
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = palette.statusError,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tindakan di bawah ini tidak bisa dibatalkan. Data bisa dipulihkan dari Backup.",
            fontSize = 13.sp,
            color = palette.textSecondary,
        )
        Spacer(Modifier.height(12.dp))
        actions()
    }
}

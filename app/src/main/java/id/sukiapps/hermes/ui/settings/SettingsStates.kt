package id.sukiapps.hermes.ui.settings

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sukiapps.hermes.ui.theme.LocalSettingsMotion
import id.sukiapps.hermes.ui.theme.LocalSettingsShape
import id.sukiapps.hermes.ui.theme.LocalSettingPalette

// ===== KOMP-12: EmptyFilterState =====

@Composable
fun EmptyFilterState(
    suggestion: String = "Coba 'konektor'",
    onSuggestionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSettingPalette.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.SearchOff, contentDescription = null,
            tint = palette.textTertiary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Tidak ada pengaturan yang cocok",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = palette.textPrimary,
        )
        Spacer(Modifier.height(8.dp))
        if (onSuggestionClick != null) {
            TextButton(onClick = onSuggestionClick) {
                Text(suggestion, fontSize = 13.sp, color = palette.accent)
            }
        } else {
            Text(suggestion, fontSize = 13.sp, color = palette.textSecondary)
        }
    }
}

// ===== KOMP-13: ShimmerSkeleton =====

@Composable
fun ShimmerSkeleton(
    modifier: Modifier = Modifier,
) {
    val palette = LocalSettingPalette.current
    val motion = LocalSettingsMotion.current
    val shape = LocalSettingsShape.current

    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(motion.shimmerMs),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerX",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape.cardSmall)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        palette.skeletonBase,
                        palette.shimmerHighlight,
                        palette.skeletonBase,
                    ),
                    start = Offset(x * 800f - 400f, 0f),
                    end = Offset(x * 800f + 400f, 0f),
                )
            )
            .semantics { contentDescription = "Memuat…" },
    )
}

// ===== KOMP-14: UnsavedChangesDialog =====

@Composable
fun UnsavedChangesDialog(
    dirtyCount: Int,
    onDismiss: () -> Unit,
    onSaveAndExit: () -> Unit,
    onExitWithoutSaving: () -> Unit,
) {
    val palette = LocalSettingPalette.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Perubahan belum disimpan",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
            )
        },
        text = {
            Text(
                "$dirtyCount perubahan belum disimpan. Keluar tanpa menyimpan?",
                fontSize = 14.sp,
                color = palette.textSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onSaveAndExit) {
                Text("Simpan & keluar", color = palette.accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = palette.textSecondary)
            }
        },
        // Tombol ketiga via teks kustom
    )
}

/**
 * Varian dengan 3 tombol: Batal / Simpan & keluar / Keluar.
 * Dipakai bila butuh aksi "Keluar" eksplisit.
 */
@Composable
fun UnsavedChangesDialogThree(
    dirtyCount: Int,
    onDismiss: () -> Unit,
    onSaveAndExit: () -> Unit,
    onExitWithoutSaving: () -> Unit,
) {
    val palette = LocalSettingPalette.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Perubahan belum disimpan",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
            )
        },
        text = {
            Column {
                Text(
                    "$dirtyCount perubahan belum disimpan. Keluar tanpa menyimpan?",
                    fontSize = 14.sp,
                    color = palette.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = onExitWithoutSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Keluar", color = palette.statusError)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSaveAndExit) {
                Text("Simpan & keluar", color = palette.accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = palette.textSecondary)
            }
        },
    )
}

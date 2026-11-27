package id.sukiapps.hermes.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** v20: alat yang bisa dipilih dari tombol "+" composer. */
enum class ComposerTool { GALLERY, CAMERA, DOCUMENT, WEB, UPIN, EMOJI }

private data class ToolTile(
    val tool: ComposerTool,
    val label: String,
    val icon: ImageVector
)

/** Urutan tile = urutan menu lama (+ Emoji di akhir). Murni → bisa dites. */
internal fun composerTiles(showEmoji: Boolean): List<ComposerTool> = buildList {
    add(ComposerTool.GALLERY)
    add(ComposerTool.CAMERA)
    add(ComposerTool.DOCUMENT)
    add(ComposerTool.WEB)
    add(ComposerTool.UPIN)
    if (showEmoji) add(ComposerTool.EMOJI)
}

private fun tileOf(tool: ComposerTool): ToolTile = when (tool) {
    ComposerTool.GALLERY -> ToolTile(tool, "Galeri", Icons.Outlined.Image)
    ComposerTool.CAMERA -> ToolTile(tool, "Kamera", Icons.Outlined.PhotoCamera)
    ComposerTool.DOCUMENT -> ToolTile(tool, "Dokumen", Icons.Outlined.Description)
    ComposerTool.WEB -> ToolTile(tool, "Ambil dari web", Icons.Outlined.Language)
    ComposerTool.UPIN -> ToolTile(tool, "Tanya Upin", Icons.AutoMirrored.Filled.Chat)
    ComposerTool.EMOJI -> ToolTile(tool, "Emoji", Icons.Outlined.EmojiEmotions)
}

/**
 * v20 (gaya Muse): bottom sheet alat composer — grid 3 kolom, tile 96 dp
 * (ikon dalam lingkaran + label pendek), area sentuh penuh tile. "Tanya
 * Upin" butuh teks di kotak input (sama seperti menu lama): tile tetap
 * tampil tetapi redup dan tidak bisa diketuk bila kosong.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerToolsSheet(
    canAskUpin: Boolean,
    showEmoji: Boolean,
    onDismiss: () -> Unit,
    onPick: (ComposerTool) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        ComposerToolsContent(
            canAskUpin = canAskUpin,
            showEmoji = showEmoji,
            onPick = onPick,
            modifier = Modifier.navigationBarsPadding()
        )
    }
}

/** Isi sheet alat (dipisah agar bisa dirender/diuji tanpa popup). */
@Composable
fun ComposerToolsContent(
    canAskUpin: Boolean,
    showEmoji: Boolean,
    onPick: (ComposerTool) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        Text(
            text = "Tambahkan ke chat",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false,
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            items(composerTiles(showEmoji), key = { it.name }) { tool ->
                val tile = tileOf(tool)
                val enabled = tool != ComposerTool.UPIN || canAskUpin
                ToolTileView(tile, enabled) { onPick(tool) }
            }
        }
    }
}

@Composable
private fun ToolTileView(tile: ToolTile, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = scheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = tile.icon,
                        contentDescription = null,
                        tint = scheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = tile.label,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

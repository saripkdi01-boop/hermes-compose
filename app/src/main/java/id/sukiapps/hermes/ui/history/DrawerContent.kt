package id.sukiapps.hermes.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.sukiapps.hermes.data.local.ConversationEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Drawer kiri: daftar percakapan (pinned/archived/search).
 * Tekan lama → rename/pin/archive/delete (+konfirmasi hapus).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DrawerContent(
    onSelect: (Long) -> Unit,
    onNewChat: () -> Unit,
    onOpenAktivitas: () -> Unit,
    onOpenMintaFitur: () -> Unit,
    onOpenAudit: () -> Unit,
    vm: HistoryViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val conversations by vm.conversations.collectAsState(initial = emptyList())

    var menuFor by remember { mutableStateOf<ConversationEntity?>(null) }
    var renaming by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<ConversationEntity?>(null) }
    // NOTE: menuFor dipakai di dalam item (di-anchor ke Box item).

    ModalDrawerSheet(
        modifier = Modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Riwayat Chat",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = onNewChat,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Chat baru")
            }
            Spacer(Modifier.height(8.dp))
            NavigationDrawerItem(
                label = { Text("Aktivitas") },
                selected = false,
                onClick = onOpenAktivitas,
                icon = {
                    Icon(Icons.Filled.Checklist, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth()
            )
            NavigationDrawerItem(
                label = { Text("Minta Fitur") },
                selected = false,
                onClick = onOpenMintaFitur,
                icon = {
                    Icon(Icons.Filled.Build, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth()
            )
            NavigationDrawerItem(
                label = { Text("Riwayat Izin") },
                selected = false,
                onClick = onOpenAudit,
                icon = {
                    Icon(Icons.Filled.History, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Cari percakapan…") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !state.showArchived,
                    onClick = { vm.setShowArchived(false) },
                    label = { Text("Aktif") }
                )
                FilterChip(
                    selected = state.showArchived,
                    onClick = { vm.setShowArchived(true) },
                    label = { Text("Arsip") }
                )
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            // Fase 2: pin dikelompokkan di bawah header seksi.
            val pinned = conversations.filter { it.pinned }
            val rest = conversations.filter { !it.pinned }
            if (pinned.isNotEmpty()) {
                item(key = "header-pinned") { DrawerSectionHeader("Disematkan") }
                items(pinned, key = { it.id }) { c ->
                    ConversationRow(
                        c = c,
                        active = c.id == state.activeId,
                        menuFor = menuFor,
                        onMenuChange = { menuFor = it },
                        onSelect = onSelect,
                        onRename = {
                            renaming = it
                            renameText = it.title
                            menuFor = null
                        },
                        onTogglePin = { vm.togglePin(it); menuFor = null },
                        onToggleArchive = { vm.toggleArchive(it); menuFor = null },
                        onDelete = { deleting = it; menuFor = null }
                    )
                }
                if (rest.isNotEmpty()) {
                    item(key = "header-all") { DrawerSectionHeader("Semua percakapan") }
                }
                items(rest, key = { it.id }) { c ->
                    ConversationRow(
                        c = c,
                        active = c.id == state.activeId,
                        menuFor = menuFor,
                        onMenuChange = { menuFor = it },
                        onSelect = onSelect,
                        onRename = {
                            renaming = it
                            renameText = it.title
                            menuFor = null
                        },
                        onTogglePin = { vm.togglePin(it); menuFor = null },
                        onToggleArchive = { vm.toggleArchive(it); menuFor = null },
                        onDelete = { deleting = it; menuFor = null }
                    )
                }
            } else {
                items(conversations, key = { it.id }) { c ->
                    ConversationRow(
                        c = c,
                        active = c.id == state.activeId,
                        menuFor = menuFor,
                        onMenuChange = { menuFor = it },
                        onSelect = onSelect,
                        onRename = {
                            renaming = it
                            renameText = it.title
                            menuFor = null
                        },
                        onTogglePin = { vm.togglePin(it); menuFor = null },
                        onToggleArchive = { vm.toggleArchive(it); menuFor = null },
                        onDelete = { deleting = it; menuFor = null }
                    )
                }
            }
            if (conversations.isEmpty()) {
                // Fase 2: layar kosong yang membantu — ikon + ajakan
                // bertindak, bukan sekadar teks.
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp)
                    ) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (state.query.isBlank()) "Belum ada percakapan"
                            else "Tidak ditemukan.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (state.query.isBlank())
                                "Mulai chat pertama — riwayatmu tersimpan di HP ini."
                            else "Coba kata kunci lain.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        if (state.query.isBlank()) {
                            FilledTonalButton(onClick = onNewChat) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Mulai chat")
                            }
                        } else {
                            TextButton(onClick = { vm.setQuery("") }) {
                                Text("Bersihkan pencarian")
                            }
                        }
                    }
                }
            }
        }
    }

/** Fase 2: header seksi daftar riwayat (overline kecil, bukan judul). */
@Composable
private fun DrawerSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

/**
 * Fase 2: satu baris riwayat sebagai fungsi sendiri — supaya daftar bisa
 * disisipi header seksi ("Disematkan" / "Semua percakapan") tanpa
 * menduplikasi isi baris. Semua aksi diteruskan sebagai lambda dari
 * DrawerContent (state renaming/deleting/menu tetap di sana).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    c: ConversationEntity,
    active: Boolean,
    menuFor: ConversationEntity?,
    onMenuChange: (ConversationEntity?) -> Unit,
    onSelect: (Long) -> Unit,
    onRename: (ConversationEntity) -> Unit,
    onTogglePin: (ConversationEntity) -> Unit,
    onToggleArchive: (ConversationEntity) -> Unit,
    onDelete: (ConversationEntity) -> Unit
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (active) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onSelect(c.id) },
                    onLongClick = { onMenuChange(c) }
                )
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = 14.dp,
                    vertical = 10.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (c.pinned) {
                            Icon(
                                Icons.Filled.PushPin,
                                contentDescription = "Disematkan",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = c.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = SimpleDateFormat(
                            "d MMM yyyy HH:mm",
                            Locale("id", "ID")
                        ).format(Date(c.updatedAt)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        // Menu tekan-lama, di-anchor ke item ini.
        if (menuFor?.id == c.id) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { onMenuChange(null) }
            ) {
                DropdownMenuItem(
                    text = { Text("Ganti nama") },
                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                    onClick = { onRename(c) }
                )
                DropdownMenuItem(
                    text = { Text(if (c.pinned) "Lepas sematan" else "Sematkan") },
                    leadingIcon = { Icon(Icons.Filled.PushPin, null) },
                    onClick = { onTogglePin(c) }
                )
                DropdownMenuItem(
                    text = { Text(if (c.archived) "Keluarkan dari arsip" else "Arsipkan") },
                    leadingIcon = {
                        Icon(
                            if (c.archived) Icons.Filled.Unarchive
                            else Icons.Filled.Archive,
                            null
                        )
                    },
                    onClick = { onToggleArchive(c) }
                )
                DropdownMenuItem(
                    text = { Text("Hapus") },
                    leadingIcon = { Icon(Icons.Filled.Delete, null) },
                    onClick = { onDelete(c) }
                )
            }
        }
    }
}

    // Dialog ganti nama.
    renaming?.let { c ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Ganti nama") },
            text = {
                TextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.rename(c.id, renameText)
                    renaming = null
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text("Batal") }
            }
        )
    }

    // Konfirmasi hapus.
    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Hapus percakapan?") },
            text = { Text("\"${c.title}\" dan seluruh pesannya akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(c.id)
                    deleting = null
                }) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Batal") }
            }
        )
    }
}

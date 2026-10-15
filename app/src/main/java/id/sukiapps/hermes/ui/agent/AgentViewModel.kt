package id.sukiapps.hermes.ui.agent

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import id.sukiapps.hermes.data.agent.AgentFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

data class AgentUiState(
    val soul: String = "",
    val memory: String = "",
    val todo: String = "",
    val agents: String = "",
    val loading: Boolean = true,
    /** rev naik tiap reload — dipakai sebagai remember key di editor. */
    val rev: Int = 0,
    val snack: String? = null,
    /** Daftar tugas ter-parse dari TODO.md (urutan file). */
    val todos: List<TodoItem> = emptyList()
)

/** Satu baris tugas: `- [ ] 2026-10-16: teks` / `- [x] teks`. */
data class TodoItem(
    val checked: Boolean,
    val date: String?,
    val text: String
)

@HiltViewModel
class AgentViewModel @Inject constructor(
    @ApplicationContext private val appCtx: Context
) : ViewModel() {

    private val _state = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() = viewModelScope.launch {
        val soul = AgentFiles.read(appCtx, AgentFiles.SOUL)
        val memory = AgentFiles.read(appCtx, AgentFiles.MEMORY)
        val todo = AgentFiles.read(appCtx, AgentFiles.TODO)
        val agents = AgentFiles.read(appCtx, AgentFiles.AGENTS)
        _state.update {
            it.copy(
                soul = soul, memory = memory, todo = todo, agents = agents,
                loading = false, rev = it.rev + 1,
                todos = parseTodoItems(todo)
            )
        }
    }

    /** Parse baris `- [ ]` / `- [x]`; tanggal opsional `YYYY-MM-DD:` di depan. */
    private fun parseTodoItems(raw: String): List<TodoItem> =
        raw.lines().mapNotNull { line ->
            val m = Regex("""^-\s*\[( |x|X)\]\s*(.*)$""")
                .matchEntire(line.trim()) ?: return@mapNotNull null
            val rest = m.groupValues[2].trim()
            val dm = Regex("""^(\d{4}-\d{2}-\d{2})\s*:\s*(.*)$""").matchEntire(rest)
            if (dm != null) {
                TodoItem(
                    checked = m.groupValues[1].trim().equals("x", ignoreCase = true),
                    date = dm.groupValues[1],
                    text = dm.groupValues[2].trim()
                )
            } else {
                TodoItem(
                    checked = m.groupValues[1].trim().equals("x", ignoreCase = true),
                    date = null,
                    text = rest
                )
            }
        }

    /**
     * Toggle checkbox tugas ke-[index] (urutan tampil) dan tulis balik ke
     * TODO.md. Baris non-tugas (komentar/header) dipertahankan utuh.
     */
    fun toggleTodo(index: Int) = viewModelScope.launch(Dispatchers.IO) {
        val raw = AgentFiles.read(appCtx, AgentFiles.TODO)
        val lines = raw.lines().toMutableList()
        var taskIdx = -1
        for (i in lines.indices) {
            val m = Regex("""^-\s*\[( |x|X)\]""").find(lines[i].trim()) ?: continue
            taskIdx++
            if (taskIdx == index) {
                val newMark = if (m.groupValues[1].trim()
                        .equals("x", ignoreCase = true)
                ) " " else "x"
                // replaceFirst(String) = literal, bukan regex — aman.
                lines[i] = lines[i].replaceFirst(m.value, "- [$newMark]")
                break
            }
        }
        AgentFiles.write(appCtx, AgentFiles.TODO, lines.joinToString("\n"))
        reload()
    }

    /** Simpan satu file jiwa. */
    fun saveOne(name: String, text: String) = viewModelScope.launch {
        if (name !in AgentFiles.ALL) return@launch
        AgentFiles.write(appCtx, name, text)
        if (name == AgentFiles.TODO) {
            _state.update { it.copy(todos = parseTodoItems(text)) }
        }
        _state.update { it.copy(snack = "$name tersimpan.") }
    }

    fun saveAll(soul: String, memory: String, todo: String, agents: String) =
        viewModelScope.launch {
            AgentFiles.write(appCtx, AgentFiles.SOUL, soul)
            AgentFiles.write(appCtx, AgentFiles.MEMORY, memory)
            AgentFiles.write(appCtx, AgentFiles.TODO, todo)
            AgentFiles.write(appCtx, AgentFiles.AGENTS, agents)
            _state.update {
                it.copy(snack = "Jiwa tersimpan.", todos = parseTodoItems(todo))
            }
        }

    fun note(msg: String) {
        _state.update { it.copy(snack = msg) }
    }

    fun consumeSnack() {
        _state.update { it.copy(snack = null) }
    }

    /**
     * Export 4 file jiwa ke ZIP di folder backups (bisa dibagikan via
     * ApkShareProvider.zipUriFor). Return file hasil atau null bila gagal.
     */
    suspend fun exportZip(): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(appCtx.filesDir, "backups").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val out = File(dir, "hermes-soul-$stamp.zip")
            ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { zos ->
                for (name in AgentFiles.ALL) {
                    val f = File(AgentFiles.dir(appCtx), name)
                    if (!f.exists()) continue
                    zos.putNextEntry(ZipEntry(name))
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            out
        }.getOrNull()
    }

    /**
     * Import ZIP hasil export: hanya 4 nama file yang dikenal yang diterima,
     * tolak path traversal & batasi total 1MB (anti zip-bomb).
     */
    fun importZip(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val ok = runCatching {
            val agentDir = AgentFiles.dir(appCtx)
            val canonicalDir = agentDir.canonicalPath
            var count = 0
            var total = 0L
            appCtx.contentResolver.openInputStream(uri)?.use { ins ->
                ZipInputStream(BufferedInputStream(ins)).use { zis ->
                    var e = zis.nextEntry
                    while (e != null) {
                        val name = e.name.substringAfterLast('/').trim()
                        if (!e.isDirectory && name in AgentFiles.ALL) {
                            val out = File(agentDir, name)
                            // Trust boundary: pastikan tidak keluar dari dir agent.
                            require(out.canonicalPath == "$canonicalDir${File.separator}$name") {
                                "path traversal"
                            }
                            val bytes = zis.readBytes()
                            total += bytes.size
                            require(total <= 1_000_000) { "ZIP terlalu besar" }
                            out.writeBytes(bytes)
                            count++
                        }
                        zis.closeEntry()
                        e = zis.nextEntry
                    }
                }
            } ?: return@runCatching false
            count > 0
        }.getOrDefault(false)
        if (ok) {
            reload()
            _state.update { it.copy(snack = "Jiwa berhasil di-import.") }
        } else {
            _state.update { it.copy(snack = "Import gagal: file tidak valid.") }
        }
    }
}

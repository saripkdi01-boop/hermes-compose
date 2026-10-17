package id.sukiapps.hermes.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel daftar chat (Tahap 2).
 *
 * Sumber data chat mentah disuntik via [chatProvider] — adaptor ke
 * penyimpanan chat yang sudah ada di app (Room/JSON/file). ViewModel ini
 * TIDAK menyentuh Room; ia hanya membaca daftar dan menerapkan metadata
 * dari ChatListStore.
 *
 * Integrasi:
 * 1. Implementasikan ChatProvider dengan sumber chat existing.
 * 2. Panggil refresh() setelah chat berubah (pesan baru, chat dibuat/dihapus).
 * 3. Status streaming/Upin: panggil setTransient() dari observer masing-masing.
 */
interface ChatProvider {
    /** Maks [limit] chat, offset [offset], diurut timestamp desc. */
    suspend fun loadChats(limit: Int, offset: Int): List<ChatListItem>
    suspend fun totalCount(): Int
    suspend fun deleteChats(ids: List<String>)
    /** Dipakai untuk undo hapus: kembalikan chat yang baru dihapus. */
    suspend fun restoreChats(items: List<ChatListItem>)
}

class ChatListViewModel(
    private val provider: ChatProvider
) : ViewModel() {

    companion object {
        const val PAGE_SIZE = 30
    }

    private val _items = MutableStateFlow<List<ChatListItem>>(emptyList())
    val items: StateFlow<List<ChatListItem>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private var transientOverrides: Map<String, Pair<Boolean, UpinStatus>> = emptyMap()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            val raw = provider.loadChats(PAGE_SIZE, 0)
            _items.value = applyAll(raw)
            _hasMore.value = raw.size >= PAGE_SIZE
            _isLoading.value = false
        }
    }

    fun loadMore() {
        if (_isLoading.value || !_hasMore.value) return
        viewModelScope.launch {
            _isLoading.value = true
            val raw = provider.loadChats(PAGE_SIZE, _items.value.size)
            _items.value = applyAll(_items.value + raw)
            _hasMore.value = raw.size >= PAGE_SIZE
            _isLoading.value = false
        }
    }

    private fun applyAll(list: List<ChatListItem>): List<ChatListItem> {
        val withMeta = ChatListStore.applyMeta(list)
        return withMeta.map { item ->
            val (streaming, upin) = transientOverrides[item.id] ?: (false to UpinStatus.NONE)
            item.copy(streaming = streaming, upinStatus = upin)
        }
    }

    /** Update status transien (streaming AI / Tanya Upin) untuk satu chat. */
    fun setTransient(chatId: String, streaming: Boolean, upin: UpinStatus) {
        transientOverrides = transientOverrides + (chatId to (streaming to upin))
        _items.value = applyAll(_items.value)
    }

    fun togglePin(id: String) {
        val cur = _items.value.firstOrNull { it.id == id } ?: return
        ChatListStore.setPinned(id, !cur.pinned)
        _items.value = applyAll(_items.value)
    }

    fun setPin(id: String, pinned: Boolean) {
        ChatListStore.setPinned(id, pinned)
        _items.value = applyAll(_items.value)
    }

    fun reorderPins(orderedIds: List<String>) {
        ChatListStore.reorderPins(orderedIds)
        _items.value = applyAll(_items.value)
    }

    fun toggleArchive(id: String) {
        val cur = _items.value.firstOrNull { it.id == id } ?: return
        ChatListStore.setArchived(id, !cur.archived)
        _items.value = applyAll(_items.value)
    }

    fun setArchive(id: String, archived: Boolean) {
        ChatListStore.setArchived(id, archived)
        _items.value = applyAll(_items.value)
    }

    fun markUnread(ids: List<String>) {
        ids.forEach { ChatListStore.setUnread(it, 1) }
        _items.value = applyAll(_items.value)
    }

    fun markRead(id: String) {
        ChatListStore.setUnread(id, 0)
        _items.value = applyAll(_items.value)
    }

    fun rename(id: String, name: String) {
        ChatListStore.rename(id, name)
        _items.value = applyAll(_items.value)
    }

    fun delete(ids: List<String>) {
        viewModelScope.launch {
            provider.deleteChats(ids)
            ChatListStore.removeMany(ids)
            _items.value = _items.value.filter { it.id !in ids }
        }
    }

    fun restore(items: List<ChatListItem>) {
        viewModelScope.launch {
            provider.restoreChats(items)
            refresh()
        }
    }
}

package id.sukiapps.hermes.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sukiapps.hermes.data.local.AppPrefs
import id.sukiapps.hermes.data.local.ChatRepository
import id.sukiapps.hermes.data.local.ConversationEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val query: String = "",
    val showArchived: Boolean = false,
    val activeId: Long = -1L
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repo: ChatRepository,
    private val prefs: AppPrefs
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState(activeId = prefs.currentConvId))
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val conversations: Flow<List<ConversationEntity>> = combine(
        _state.flatMapLatest { s -> repo.searchConversations(s.query) },
        _state
    ) { list, s ->
        if (s.showArchived) list.filter { it.archived }
        else list.filter { !it.archived }
    }

    fun setQuery(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    fun setShowArchived(show: Boolean) {
        _state.value = _state.value.copy(showArchived = show)
    }

    fun setActive(id: Long) {
        _state.value = _state.value.copy(activeId = id)
    }

    fun rename(id: Long, title: String) = viewModelScope.launch {
        repo.rename(id, title.ifBlank { "Percakapan" })
    }

    fun togglePin(c: ConversationEntity) = viewModelScope.launch {
        repo.setPinned(c.id, !c.pinned)
    }

    fun toggleArchive(c: ConversationEntity) = viewModelScope.launch {
        repo.setArchived(c.id, !c.archived)
    }

    fun delete(id: Long) = viewModelScope.launch {
        repo.deleteConversation(id)
        if (_state.value.activeId == id) {
            _state.value = _state.value.copy(activeId = -1L)
        }
    }
}

package id.sukiapps.hermes.ui.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sukiapps.hermes.data.audit.AuditEntry
import id.sukiapps.hermes.data.audit.AuditLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuditFilter { ALL, GRANTED, DENIED }

data class AuditUiState(
    val entries: List<AuditEntry> = emptyList(),
    val filter: AuditFilter = AuditFilter.ALL,
    val loading: Boolean = true
)

@HiltViewModel
class AuditViewModel @Inject constructor(
    private val audit: AuditLog
) : ViewModel() {

    private val _state = MutableStateFlow(AuditUiState())
    val state: StateFlow<AuditUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val all = audit.readAll()
            _state.update { it.copy(entries = all, loading = false) }
        }
    }

    fun setFilter(f: AuditFilter) {
        _state.update { it.copy(filter = f) }
    }

    fun visible(): List<AuditEntry> {
        val s = _state.value
        return when (s.filter) {
            AuditFilter.ALL -> s.entries
            AuditFilter.GRANTED -> s.entries.filter { it.decision == "granted" }
            AuditFilter.DENIED -> s.entries.filter { it.decision == "denied" }
        }
    }
}

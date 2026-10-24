package id.sukiapps.hermes.ui.manus

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import id.sukiapps.hermes.domain.manus.ManusEvent
import id.sukiapps.hermes.domain.manus.ManusReducer
import id.sukiapps.hermes.domain.manus.ManusStateEngine
import id.sukiapps.hermes.domain.manus.ManusUiState
import id.sukiapps.hermes.domain.manus.StepStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * ViewModel layar Manus. State machine-nya murni (ManusReducer.reduce), jadi
 * ViewModel hanya membungkus dispatch event + expose StateFlow ke Compose.
 *
 * Persistensi: seluruh ManusUiState disimpan sebagai JSON di SharedPreferences
 * "manus_state" setiap kali berubah, dan dibaca kembali saat ViewModel dibuat.
 * Tujuannya replay: tugas lama + timeline-nya tetap bisa dibuka ulang setelah
 * aplikasi ditutup. Bila JSON rusak / tidak bisa dibaca, jatuh kembali ke
 * seed — tidak pernah crash karena data simpanan.
 *
 * Yang TIDAK dilakukan ViewModel ini: menjalankan tool nyata. Tombol
 * "Selesai Langkah" adalah simulasi pelaksana (lihat finishActiveStep) —
 * eksekusi tool nyata milik ToolLoop tetap lewat PermissionGate-nya sendiri.
 * Label ini ditulis jujur di UI, bukan diklaim sebagai eksekusi live.
 */
@HiltViewModel
class ManusViewModel @Inject constructor(
    @ApplicationContext private val appCtx: Context
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(restore() ?: ManusStateEngine.seed())
    val state: StateFlow<ManusUiState> = _state.asStateFlow()

    fun submitGoal(goal: String, plan: List<String>) =
        dispatch(ManusEvent.SubmitGoal(goal, plan))

    fun startTask(taskId: String) = dispatch(ManusEvent.StartTask(taskId))

    fun pauseTask(taskId: String) = dispatch(ManusEvent.PauseTask(taskId))

    fun resumeTask(taskId: String) = dispatch(ManusEvent.ResumeTask(taskId))

    fun cancelTask(taskId: String) = dispatch(ManusEvent.CancelTask(taskId))

    fun grantPermission(taskId: String) = dispatch(ManusEvent.PermissionGranted(taskId))

    fun denyPermission(taskId: String) = dispatch(ManusEvent.PermissionDenied(taskId))

    fun selectTask(taskId: String) = dispatch(ManusEvent.SelectTask(taskId))

    fun steerTask(taskId: String, message: String) =
        dispatch(ManusEvent.SteerTask(taskId, message))

    fun clearHistory() = dispatch(ManusEvent.ClearHistory)

    /**
     * Tandai langkah RUNNING pertama sebagai selesai — simulasi pelaksana tool
     * supaya state machine bisa diajak jalan dari UI tanpa backend.
     */
    fun finishActiveStep(output: String? = null) {
        val snapshot = _state.value
        val task = snapshot.activeTask ?: return
        val step = task.steps.firstOrNull { it.status == StepStatus.RUNNING } ?: return
        dispatch(ManusEvent.ToolFinished(task.id, step.id, output))
    }

    private fun dispatch(event: ManusEvent) {
        _state.update { prev -> ManusReducer.reduce(prev, event) }
        save(_state.value)
    }

    private fun prefs() =
        appCtx.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    private fun restore(): ManusUiState? {
        val raw = prefs().getString(KEY_STATE, null) ?: return null
        if (raw.isBlank()) return null
        return runCatching { json.decodeFromString<ManusUiState>(raw) }.getOrNull()
    }

    private fun save(state: ManusUiState) {
        runCatching {
            prefs().edit().putString(KEY_STATE, json.encodeToString(state)).apply()
        }
    }

    companion object {
        private const val PREFS_FILE = "manus_state"
        private const val KEY_STATE = "state_json"
    }
}

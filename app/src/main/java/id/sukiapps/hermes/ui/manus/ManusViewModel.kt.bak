package id.sukiapps.hermes.ui.manus

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sukiapps.hermes.domain.manus.ManusEvent
import id.sukiapps.hermes.domain.manus.ManusReducer
import id.sukiapps.hermes.domain.manus.ManusStateEngine
import id.sukiapps.hermes.domain.manus.ManusUiState
import id.sukiapps.hermes.domain.manus.StepStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * ViewModel layar Manus. State machine-nya murni (ManusReducer.reduce), jadi
 * ViewModel hanya membungkus dispatch event + expose StateFlow ke Compose.
 */
@HiltViewModel
class ManusViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(ManusStateEngine.seed())
    val state: StateFlow<ManusUiState> = _state.asStateFlow()

    fun submitGoal(goal: String, plan: List<String>) =
        dispatch(ManusEvent.SubmitGoal(goal, plan))

    fun startTask(taskId: String) = dispatch(ManusEvent.StartTask(taskId))

    fun pauseTask(taskId: String) = dispatch(ManusEvent.PauseTask(taskId))

    fun resumeTask(taskId: String) = dispatch(ManusEvent.ResumeTask(taskId))

    fun cancelTask(taskId: String) = dispatch(ManusEvent.CancelTask(taskId))

    fun grantPermission(taskId: String) = dispatch(ManusEvent.PermissionGranted(taskId))

    fun denyPermission(taskId: String) = dispatch(ManusEvent.PermissionDenied(taskId))

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
    }
}

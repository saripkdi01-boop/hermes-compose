package id.sukiapps.hermes.ui.manus

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import id.sukiapps.hermes.domain.manus.AgentRunner
import id.sukiapps.hermes.domain.manus.ManusEvent
import id.sukiapps.hermes.domain.manus.ManusReducer
import id.sukiapps.hermes.domain.manus.ManusStateEngine
import id.sukiapps.hermes.domain.manus.ManusToolPlanner
import id.sukiapps.hermes.domain.manus.ManusUiState
import id.sukiapps.hermes.domain.manus.StepStatus
import id.sukiapps.hermes.domain.manus.ToolCall
import id.sukiapps.hermes.tool.ToolLoop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * ViewModel layar Manus. State machine-nya murni (ManusReducer.reduce), jadi
 * ViewModel hanya membungkus dispatch event + expose StateFlow ke Compose.
 *
 * PERSISTENSI: seluruh ManusUiState disimpan sebagai JSON di SharedPreferences
 * "manus_state" setiap kali berubah, dan dibaca kembali saat ViewModel dibuat.
 * Tujuannya replay: tugas lama + timeline-nya tetap bisa dibuka ulang setelah
 * aplikasi ditutup. Bila JSON rusak / tidak bisa dibaca, jatuh kembali ke
 * seed — tidak pernah crash karena data simpanan.
 *
 * EKSEKUSI NYATA (upgrade 14.16): `submitAndRun(goal)` memetakan goal ke
 * rencana + tool nyata lewat [ManusToolPlanner], menyematkan tiap ToolCall ke
 * langkah lewat [ManusEvent.AttachStepTool], lalu menjalankan [AgentRunner]
 * yang memanggil [ToolLoop.execute] sungguhan. Tombol "Selesai Langkah"
 * (finishActiveStep) tetap ada sebagai simulasi manual untuk langkah tanpa
 * tool / debugging, tapi bukan lagi jalur utama.
 */
@HiltViewModel
class ManusViewModel @Inject constructor(
    @ApplicationContext private val appCtx: Context
) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(restore() ?: ManusStateEngine.seed())
    val state: StateFlow<ManusUiState> = _state.asStateFlow()

    private val runner = AgentRunner(toolLoop = ToolLoop, onEvent = ::dispatch)

    fun submitGoal(goal: String, plan: List<String>) =
        dispatch(ManusEvent.SubmitGoal(goal, plan))

    fun startTask(taskId: String) = dispatch(ManusEvent.StartTask(taskId))

    fun pauseTask(taskId: String) = dispatch(ManusEvent.PauseTask(taskId))

    fun resumeTask(taskId: String) {
        dispatch(ManusEvent.ResumeTask(taskId))
        ensureRunner(_state.value.activeTaskId)
    }

    fun cancelTask(taskId: String) {
        runner.requestStop()
        dispatch(ManusEvent.CancelTask(taskId))
    }

    fun grantPermission(taskId: String) {
        dispatch(ManusEvent.PermissionGranted(taskId))
        ensureRunner(taskId)
    }

    fun denyPermission(taskId: String) {
        dispatch(ManusEvent.PermissionDenied(taskId))
        ensureRunner(taskId)
    }

    fun selectTask(taskId: String) = dispatch(ManusEvent.SelectTask(taskId))

    fun steerTask(taskId: String, message: String) =
        dispatch(ManusEvent.SteerTask(taskId, message))

    fun clearHistory() = dispatch(ManusEvent.ClearHistory)

    /**
     * Jalur UTAMA baru: goal bahasa biasa → planner → sematkan tool nyata →
     * mulai task → jalankan [AgentRunner] di background.
     *
     * Urutan event sengaja dipisah (bukan satu event besar) supaya tiap
     * transisi tercatat di timeline — persis seperti tab Rencana/Komputer/Hasil
     * di UI: user bisa melihat langkah apa yang direncanakan sebelum dijalankan.
     */
    fun submitAndRun(goal: String) {
        if (goal.isBlank()) return
        val planned = ManusToolPlanner.plan(goal)
        if (planned.isEmpty()) return

        // 1) Buat task + langkah judul saja.
        dispatch(ManusEvent.SubmitGoal(goal, planned.map { it.title }))
        val taskId = _state.value.activeTaskId ?: return

        // 2) Sematkan ToolCall nyata ke setiap langkah (panel Komputer hidup).
        planned.forEachIndexed { index, step ->
            dispatch(
                ManusEvent.AttachStepTool(
                    taskId = taskId,
                    stepId = "$taskId-s${index + 1}",
                    tool = ToolCall(name = step.toolName, args = step.args.toStringMap()),
                    detail = step.detail
                )
            )
        }

        // 3) Mulai: reducer menandai langkah pertama RUNNING (atau meminta izin).
        dispatch(ManusEvent.StartTask(taskId))

        // 4) Jalankan pelaksana nyata di IO thread.
        ensureRunner(taskId)
    }

    /**
     * Jalankan [AgentRunner] untuk taskId bila state memang RUNNING.
     * Dipanggil setelah StartTask, PermissionGranted, ResumeTask, dan setiap
     * kali ToolFinished memicu advance() — supaya loop pelaksana selalu
     * bekerja dari snapshot terbaru, bukan state basi.
     */
    private fun ensureRunner(taskId: String?) {
        if (taskId == null) return
        val task = _state.value.tasks.firstOrNull { it.id == taskId } ?: return
        if (task.status != id.sukiapps.hermes.domain.manus.TaskStatus.RUNNING) return
        viewModelScope.launch {
            runner.runTask(taskId) { _state.value }
        }
    }

    /**
     * Tandai langkah RUNNING pertama sebagai selesai — simulasi manual untuk
     * langkah tanpa tool atau debugging UI tanpa menjalankan eksekutor.
     * Jalur utama tetap [submitAndRun].
     */
    fun finishActiveStep(output: String? = null) {
        val snapshot = _state.value
        val task = snapshot.activeTask ?: return
        val step = task.steps.firstOrNull { it.status == StepStatus.RUNNING } ?: return
        dispatch(ManusEvent.ToolFinished(task.id, step.id, output))
        ensureRunner(task.id)
    }

    /** Stop pelaksana (mis. saat layar ditutup paksa atau user menekan Batal). */
    fun stopRunner() = runner.requestStop()

    private fun dispatch(event: ManusEvent) {
        _state.update { prev -> ManusReducer.reduce(prev, event) }
        save(_state.value)
        // Setiap ToolFinished/PermissionDenied memicu advance(); pastikan
        // pelaksana nyata ikut berjalan untuk langkah RUNNING berikutnya.
        when (event) {
            is ManusEvent.ToolFinished -> ensureRunner(event.taskId)
            is ManusEvent.PermissionDenied -> ensureRunner(event.taskId)
            else -> Unit
        }
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

/** Konversi argumen planner (JSONObject) ke Map<String,String> kontrak ToolCall. */
private fun org.json.JSONObject.toStringMap(): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    val keys = this.keys()
    while (keys.hasNext()) {
        val k = keys.next()
        out[k] = this.opt(k)?.toString() ?: ""
    }
    return out
}

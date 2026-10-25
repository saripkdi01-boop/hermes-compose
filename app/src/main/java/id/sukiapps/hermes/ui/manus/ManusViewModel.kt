package id.sukiapps.hermes.ui.manus

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import id.sukiapps.hermes.data.local.AppPrefs
import id.sukiapps.hermes.domain.manus.AgentRunner
import id.sukiapps.hermes.domain.manus.LlmPlanResult
import id.sukiapps.hermes.domain.manus.LlmPlanner
import id.sukiapps.hermes.domain.manus.ManusEvent
import id.sukiapps.hermes.domain.manus.ManusReducer
import id.sukiapps.hermes.domain.manus.ManusStateEngine
import id.sukiapps.hermes.domain.manus.ManusToolPlanner
import id.sukiapps.hermes.domain.manus.ManusUiState
import id.sukiapps.hermes.domain.manus.PlanValidator
import id.sukiapps.hermes.domain.manus.PlannedStep
import id.sukiapps.hermes.domain.manus.ProviderChain
import id.sukiapps.hermes.domain.manus.SessionLimits
import id.sukiapps.hermes.domain.manus.StepStatus
import id.sukiapps.hermes.domain.manus.estimateSessionTokens
import id.sukiapps.hermes.domain.manus.TaskStatus
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
import okhttp3.OkHttpClient
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
    @ApplicationContext private val appCtx: Context,
    // 14.22: agen tunggal memakai model + base URL Pengaturan (AppPrefs)
    // dan OkHttpClient ber-AuthInterceptor (Bearer dari KeyVault) untuk
    // perencanaan LLM. Keduanya binding @Singleton yang sudah ada — tidak
    // ada modul Hilt baru. Nama `appPrefs` sengaja dibedakan dari fun
    // prefs() (SharedPreferences manus_state) di bawah.
    private val appPrefs: AppPrefs,
    private val httpClient: OkHttpClient
) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(restore() ?: ManusStateEngine.seed())
    val state: StateFlow<ManusUiState> = _state.asStateFlow()

    /**
     * 14.22: true bila tugas yang direstore dari manus_state berstatus
     * RUNNING (atau VERIFYING) saat ViewModel dibuat — artinya pelaksana
     * sempat mati bersama aplikasi dan tugas itu menggantung tanpa
     * pelaksana. UI menampilkan tombol "Lanjutkan" yang benar-benar
     * menjalankan runner (resumeRestored), bukan sekadar label.
     * Dibersihkan saat tugas dilanjutkan, dibatalkan, atau selesai.
     */
    private val _resumeNeeded = MutableStateFlow(
        _state.value.tasks.any {
            it.status == TaskStatus.RUNNING || it.status == TaskStatus.VERIFYING
        }
    )
    val resumeNeeded: StateFlow<Boolean> = _resumeNeeded.asStateFlow()

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
     * Jalur UTAMA agen tunggal (14.22): goal bahasa biasa → rencana LLM
     * (atau fallback kata kunci BERLABEL bila LLM gagal) → sematkan tool
     * nyata → mulai task → jalankan [AgentRunner] di background.
     *
     * Task dibuat SEGERA dengan satu langkah placeholder "Merencanakan
     * langkah…" supaya chat langsung menampilkan kartu (tidak ada jeda
     * kosong selama LLM berpikir), lalu ReplacePlan mengganti placeholder
     * dengan rencana tervalidasi. Bila rencana tervalidasi kosong (semua
     * langkah dibuang validator), fallback kata kunci dipakai dan labelnya
     * menyebut alasannya — timeline TIDAK PERNAH mengklaim rencana LLM
     * padahal bukan.
     */
    fun submitAndRun(goal: String) {
        if (goal.isBlank()) return
        dispatch(ManusEvent.SubmitGoal(goal, listOf("Merencanakan langkah…")))
        val taskId = _state.value.activeTaskId ?: return
        viewModelScope.launch {
            // 14.26: kunci cadangan per provider (KeyVault, nama secret
            // dari ProviderChain). Hanya provider yang punya kunci
            // tersimpan yang dicoba sebagai cadangan — tanpa kunci,
            // provider itu dilewati, bukan dicoba tanpa auth.
            val providerKeys = ProviderChain.ALL
                .filter { it.baseUrl != appPrefs.baseUrl.trimEnd('/') }
                .mapNotNull { provider ->
                    val key = id.sukiapps.hermes.KeyVault.getSecret(appCtx, provider.keySecret)
                    if (key.isNotBlank()) provider.baseUrl to key else null
                }
                .toMap()
            val llm = LlmPlanner.plan(
                goal = goal,
                model = appPrefs.model,
                baseUrl = appPrefs.baseUrl,
                client = httpClient,
                providerKeys = providerKeys
            )
            // Nama tool runtime dari ToolLoop.toolsArray() (struktur
            // OpenAI: entry.type=function, entry.function.name). Bila
            // ekstraksi gagal, validator memakai daftar dikenalnya sendiri.
            val toolsArr = ToolLoop.toolsArray()
            val allowed = HashSet<String>()
            for (i in 0 until toolsArr.length()) {
                runCatching {
                    toolsArr.getJSONObject(i).getJSONObject("function").getString("name")
                }.getOrNull()?.let { allowed += it }
            }
            val validated = when (llm) {
                is LlmPlanResult.Success -> PlanValidator.validate(
                    llm.steps, allowed
                )
                is LlmPlanResult.Failure -> null
            }
            val plan: List<PlannedStep>
            val label: String
            if (validated != null && validated.steps.isNotEmpty()) {
                plan = validated.steps
                // 14.26: bila rencana datang dari provider cadangan,
                // label menyebutnya — kartu tidak boleh mengklaim
                // rencana provider terpilih padahal cadangan yang jalan.
                val via = if (llm is LlmPlanResult.Success && llm.fallback) {
                    "LLM via ${llm.providerUsed} (fallback)"
                } else {
                    "LLM"
                }
                label = if (validated.dropped.isEmpty()) via
                else "$via (${validated.dropped.size} langkah dibuang validator)"
            } else {
                plan = ManusToolPlanner.plan(goal)
                val why = when (llm) {
                    // 14.26: alasan gabungan per provider bisa panjang
                    // (3 provider) — 200 karakter supaya rantai kegagalan
                    // tetap terbaca di kartu, bukan terpotong di tengah.
                    is LlmPlanResult.Failure -> llm.reason.take(200)
                    else -> "rencana LLM kosong / semua langkah ditolak validator"
                }
                label = "rencana fallback — tanpa LLM ($why)"
            }
            if (plan.isEmpty()) {
                dispatch(ManusEvent.CancelTask(taskId))
                return@launch
            }
            // Ganti placeholder dengan rencana nyata, lalu sematkan tool.
            // 14.27 (B8): usage planner asli (bila provider mengirimnya)
            // dicatat di task; token awal sesi = usage asli itu, atau
            // estimasi kasar atas goal + judul rencana bila tidak ada.
            val plannerUsage = llm as? LlmPlanResult.Success
            val planText = goal + "\n" + plan.joinToString("\n") { it.title }
            dispatch(
                ManusEvent.ReplacePlan(
                    taskId = taskId,
                    plan = plan.map { it.title },
                    planLabel = label,
                    usageTotal = plannerUsage?.usageTotal,
                    usagePrompt = plannerUsage?.usagePrompt,
                    usageCompletion = plannerUsage?.usageCompletion,
                    initialTokens = plannerUsage?.usageTotal
                        ?: estimateSessionTokens(planText)
                )
            )
            plan.forEachIndexed { index, step ->
                dispatch(
                    ManusEvent.AttachStepTool(
                        taskId = taskId,
                        stepId = "$taskId-s${index + 1}",
                        tool = ToolCall(name = step.toolName, args = step.args.toStringMap()),
                        detail = step.detail,
                        isPreparation = step.isPreparation
                    )
                )
            }
            // Mulai: reducer menandai langkah pertama RUNNING (atau meminta izin).
            dispatch(ManusEvent.StartTask(taskId))
            ensureRunner(taskId)
        }
    }

    /** 14.22: lanjutkan tugas STOPPED (verifikasi ulang / langkah tersisa). */
    fun resumeStopped(taskId: String) {
        dispatch(ManusEvent.ResumeStopped(taskId))
        ensureRunner(taskId)
    }

    /**
     * 14.22: lanjutkan tugas yang direstore dalam keadaan RUNNING/VERIFYING
     * tanpa pelaksana (aplikasi sempat ditutup). Sekadar menjalankan runner
     * untuk task itu — tidak mengubah rencana maupun menghapus capaian.
     */
    fun resumeRestored(taskId: String) {
        _resumeNeeded.value = false
        ensureRunner(taskId)
    }

    /**
     * Jalankan [AgentRunner] untuk taskId bila state memang RUNNING atau
     * VERIFYING (14.22: gerbang verifikasi juga butuh pelaksana — bila
     * runner mati saat task VERIFYING, verifikasi tidak pernah jalan).
     * Dipanggil setelah StartTask, PermissionGranted, ResumeTask, dan setiap
     * kali ToolFinished memicu advance() — supaya loop pelaksana selalu
     * bekerja dari snapshot terbaru, bukan state basi.
     * 14.27 (B6): batas sesi dibaca SEGAR dari Pengaturan tiap kali
     * runner dimulai, lalu ditegakkan di dalam runTask — bukan hanya
     * ditampilkan di Settings.
     */
    private fun ensureRunner(taskId: String?) {
        if (taskId == null) return
        val task = _state.value.tasks.firstOrNull { it.id == taskId } ?: return
        if (task.status != TaskStatus.RUNNING && task.status != TaskStatus.VERIFYING) return
        val limitsNow = id.sukiapps.hermes.data.agent.AgentLimits.get(appCtx)
        val limits = SessionLimits(
            maxIterations = limitsNow.maxIterations,
            timeoutMs = limitsNow.timeoutMs,
            tokenBudget = limitsNow.tokenBudget
        )
        viewModelScope.launch {
            runner.runTask(taskId, { _state.value }, limits)
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
        // Tugas yang direstore sudah settle (atau dibatalkan) -> tidak
        // perlu lagi tombol "Lanjutkan" untuk tugas itu.
        val task = _state.value.tasks.firstOrNull {
            when (event) {
                is ManusEvent.CancelTask -> it.id == event.taskId
                is ManusEvent.VerificationPassed -> it.id == event.taskId
                is ManusEvent.VerificationFailed -> it.id == event.taskId
                is ManusEvent.VerificationPartial -> it.id == event.taskId
                else -> false
            }
        }
        if (task != null && task.isFinished) _resumeNeeded.value = false
        // Setiap ToolFinished/PermissionDenied memicu advance(); pastikan
        // pelaksana nyata ikut berjalan untuk langkah RUNNING berikutnya
        // (atau gerbang VERIFYING bila semua langkah habis).
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

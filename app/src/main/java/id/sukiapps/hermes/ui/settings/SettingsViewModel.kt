package id.sukiapps.hermes.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import id.sukiapps.hermes.KeyVault
import id.sukiapps.hermes.data.auth.SupabaseAuth
import id.sukiapps.hermes.data.auth.SupabaseAuthCallback
import id.sukiapps.hermes.data.auth.UserProfile
import id.sukiapps.hermes.data.audit.DangerLevel
import id.sukiapps.hermes.data.audit.PermissionGate
import id.sukiapps.hermes.data.connectors.GitHubConnector
import id.sukiapps.hermes.data.connectors.ServerlessConnector
import id.sukiapps.hermes.data.connectors.TelegramConnector
import id.sukiapps.hermes.data.connectors.TermuxConnector
import id.sukiapps.hermes.data.connectors.UpinConnector
import id.sukiapps.hermes.data.local.AppPrefs
import id.sukiapps.hermes.domain.AIProvider
import id.sukiapps.hermes.domain.PersonaPreset
import id.sukiapps.hermes.util.BackupManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class SettingsUiState(
    val model: String = "",
    val models: List<String> = emptyList(),
    val modelsLoading: Boolean = false,
    // Status per model (titik di ModelPickerDialog). Bertahan selama
    // ViewModel hidup — buka-tutup dialog dalam waktu singkat tidak
    // mengulang probe untuk model yang sudah ONLINE/DEAD.
    val modelStatuses: Map<String, ModelStatus> = emptyMap(),
    val modelsRefreshedAt: String = "",
    val temperature: Float = 1.0f,
    val topP: Float = 1.0f,
    val maxTokens: Int = 0,
    val persona: String = "",
    val presets: List<PersonaPreset> = emptyList(),
    val darkTheme: Boolean = true,
    // v14.18: mode tema ("system"/"dark"/"light"), aksen, kecepatan TTS.
    val themeMode: String = "dark",
    val accentColor: String = "coral",
    val ttsSpeed: Float = 1.0f,
    val baseUrl: String = "",
    val busyMessage: String? = null,
    val snack: String? = null,
    val keyBroken: Boolean = false,
    // v9.1: token konektor (disimpan terenkripsi via KeyVault, bukan plaintext)
    val githubPat: String = "",
    val tgBotToken: String = "",
    val tgChatId: String = "",
    // v9.2: direktori project Serverless (prefs biasa, bukan secret)
    val serverlessDir: String = "",
    // v9.4: konektor Upin (URL prefs biasa, secret via KeyVault)
    val upinUrl: String = "",
    val upinSecret: String = "",
    val upinStatus: String? = null,
    // Sprint 1 Gate 3: Supabase + Vercel read-only (via KeyVault)
    val supabaseUrl: String = "",
    val supabaseKey: String = "",
    val vercelToken: String = "",
    // v14.31 (D3): sesi login Google via Supabase Auth. Token TIDAK PERNAH
    // masuk state — hanya nama/email tampil + status.
    val authEmail: String = "",
    val authName: String = "",
    val authStatus: String? = null,
    val authBusy: Boolean = false,
    // v14.10: toggle notifikasi eksekusi & izin.
    val notifEnabled: Boolean = true,
    val notifExec: Boolean = true,
    val notifApproval: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appCtx: Context,
    private val prefs: AppPrefs,
    private val ai: AIProvider,
    private val backup: BackupManager,
    private val github: GitHubConnector,
    private val telegram: TelegramConnector,
    private val serverless: ServerlessConnector,
    private val upin: UpinConnector,
    private val gate: PermissionGate
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        _state.update {
            it.copy(
                model = prefs.model,
                models = prefs.cachedModels() ?: emptyList(),
                temperature = prefs.temperature,
                topP = prefs.topP,
                maxTokens = prefs.maxTokens,
                persona = prefs.persona,
                presets = prefs.personaPresets(),
                darkTheme = prefs.themeDark,
                themeMode = prefs.themeMode,
                accentColor = prefs.accentColor,
                ttsSpeed = prefs.ttsSpeed,
                baseUrl = prefs.baseUrl,
                notifEnabled = prefs.notifEnabled,
                notifExec = prefs.notifExec,
                notifApproval = prefs.notifApproval,
                keyBroken = KeyVault.broken,
                serverlessDir = prefs.serverlessDir,
                upinUrl = prefs.upinUrl
            )
        }
        // Token konektor via Keystore (IPC) — baca di IO.
        viewModelScope.launch(Dispatchers.IO) {
            val pat = KeyVault.getSecret(appCtx, "github_pat")
            val tgToken = KeyVault.getSecret(appCtx, "tg_bot_token")
            val chatId = KeyVault.getSecret(appCtx, "tg_chat_id")
            val upinSecret = KeyVault.getSecret(appCtx, UpinConnector.SECRET_KEY)
            val sbUrl = KeyVault.getSecret(appCtx, "supabase_url")
            val sbKey = KeyVault.getSecret(appCtx, "supabase_key")
            val vcToken = KeyVault.getSecret(appCtx, "vercel_token")
            // v14.31 (D3): profil login tersimpan (tanpa token ke state).
            val auEmail = KeyVault.getSecret(appCtx, SupabaseAuth.SECRET_USER_EMAIL)
            val auName = KeyVault.getSecret(appCtx, SupabaseAuth.SECRET_USER_NAME)
            _state.update {
                it.copy(
                    githubPat = pat,
                    tgBotToken = tgToken,
                    tgChatId = chatId,
                    upinSecret = upinSecret,
                    supabaseUrl = sbUrl,
                    supabaseKey = sbKey,
                    vercelToken = vcToken,
                    authEmail = auEmail,
                    authName = auName,
                    authStatus = if (auEmail.isNotBlank() || auName.isNotBlank()) "Login" else null
                )
            }
        }
    }

    fun consumeSnack() = _state.update { it.copy(snack = null) }

    fun note(msg: String) {
        _state.update { it.copy(snack = msg) }
    }

    fun refreshModels() {
        viewModelScope.launch {
            _state.update { it.copy(modelsLoading = true, busyMessage = "Mengambil daftar model…") }
            try {
                // v9.3: force=true — lewati cache 24 jam, tombol ini harus live.
                val ids = withContext(Dispatchers.IO) { ai.models(force = true) }
                val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                _state.update {
                    it.copy(
                        models = ids,
                        modelsLoading = false,
                        busyMessage = null,
                        modelsRefreshedAt = now,
                        snack = "Ditemukan ${ids.size} model."
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        modelsLoading = false,
                        busyMessage = null,
                        snack = (e.message ?: "Gagal mengambil model.").take(200)
                    )
                }
            }
        }
    }

    fun setModel(m: String) {
        prefs.model = m
        _state.update { it.copy(model = m) }
    }

    /**
     * v9.3: pilih model + validasi LIVE bahwa API key user bisa memakainya
     * (POST /chat/completions minimal). Model tetap tersimpan walau
     * validasi gagal — user diberi pesan error yang jelas, tanpa crash.
     */
    // Job pengecekan status model (dialog "Pilih model"). Dibatalkan saat
    // dialog ditutup agar tidak bocor coroutine / request berjalan liar.
    private var statusCheckJob: Job? = null

    /**
     * Cek status OTOMATIS untuk semua model yang belum punya status sesi
     * ini (UNKNOWN). Model yang sudah ONLINE/DEAD tidak dicek ulang bila
     * dialog dibuka-tutup dalam waktu singkat — hemat 1 ping/model.
     * Konkurensi dibatasi 3 paralel agar daftar panjang tidak menghantam
     * provider sekaligus (risiko 429). Hasil masuk satu per satu via state.
     */
    fun startModelStatusChecks() {
        val pending = _state.value.models.filter {
            (_state.value.modelStatuses[it]?.state ?: ModelStatusState.UNKNOWN) ==
                ModelStatusState.UNKNOWN
        }
        if (pending.isEmpty()) return
        statusCheckJob?.cancel()
        statusCheckJob = viewModelScope.launch {
            val semaphore = Semaphore(3)
            pending.map { m ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val t0 = System.currentTimeMillis()
                        try {
                            ai.validateModel(m)
                            val ms = System.currentTimeMillis() - t0
                            _state.update {
                                it.copy(
                                    modelStatuses = it.modelStatuses +
                                        (m to ModelStatus(ModelStatusState.ONLINE, ms))
                                )
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            _state.update {
                                it.copy(
                                    modelStatuses = it.modelStatuses +
                                        (m to ModelStatus(
                                            ModelStatusHelper.classifyError(e.message),
                                            null
                                        ))
                                )
                            }
                        }
                    }
                }
            }.awaitAll()
        }
    }

    fun stopModelStatusChecks() {
        statusCheckJob?.cancel()
        statusCheckJob = null
    }

    fun selectModel(m: String) {
        prefs.model = m
        _state.update { it.copy(model = m, busyMessage = "Memvalidasi model…") }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { ai.validateModel(m) }
                _state.update {
                    it.copy(busyMessage = null, snack = "Model $m aktif ✓")
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        busyMessage = null,
                        snack = (e.message ?: "Model gagal divalidasi.").take(200)
                    )
                }
            }
        }
    }

    fun setTemperature(v: Float) {
        val c = v.coerceIn(0f, 2f)
        prefs.temperature = c
        _state.update { it.copy(temperature = c) }
    }

    fun setTopP(v: Float) {
        val c = v.coerceIn(0f, 1f)
        prefs.topP = c
        _state.update { it.copy(topP = c) }
    }

    fun setMaxTokens(v: Int) {
        val c = v.coerceIn(0, 8192)
        prefs.maxTokens = c
        _state.update { it.copy(maxTokens = c) }
    }

    fun setPersona(text: String) {
        prefs.persona = text
        _state.update { it.copy(persona = text) }
    }

    fun applyPreset(p: PersonaPreset) = setPersona(p.t)

    fun addPreset(name: String, text: String) {
        val n = name.trim()
        val t = text.trim()
        if (n.isEmpty() || t.isEmpty()) {
            _state.update { it.copy(snack = "Nama dan isi preset wajib diisi.") }
            return
        }
        val list = prefs.personaPresets().toMutableList()
        list.removeAll { it.n == n }
        list.add(PersonaPreset(n, t))
        prefs.savePersonaPresets(list)
        _state.update { it.copy(presets = list, snack = "Preset \"$n\" disimpan.") }
    }

    fun deletePreset(p: PersonaPreset) {
        val list = prefs.personaPresets().filter { it.n != p.n }
        prefs.savePersonaPresets(list)
        _state.update { it.copy(presets = list) }
    }

    fun setDarkTheme(dark: Boolean) {
        // Kompatibilitas: pemanggil lama memetakan ke mode dua nilai.
        setThemeMode(if (dark) "dark" else "light")
    }

    // v14.18: setter tema/aksen/TTS — semuanya langsung persist ke AppPrefs.
    fun setThemeMode(mode: String) {
        val clean = when (mode) {
            "system", "light", "dark" -> mode
            else -> "dark"
        }
        prefs.themeMode = clean
        _state.update {
            it.copy(themeMode = clean, darkTheme = clean != "light")
        }
    }

    fun setAccentColor(accent: String) {
        val clean = when (accent) {
            "teal", "sand", "coral" -> accent
            else -> "coral"
        }
        prefs.accentColor = clean
        _state.update { it.copy(accentColor = clean) }
    }

    fun setTtsSpeed(speed: Float) {
        val clean = speed.coerceIn(0.5f, 2.0f)
        prefs.ttsSpeed = clean
        _state.update { it.copy(ttsSpeed = clean) }
    }

    // v14.10: setter toggle notifikasi (persist ke AppPrefs).
    fun setNotifEnabled(v: Boolean) {
        prefs.notifEnabled = v
        _state.update { it.copy(notifEnabled = v) }
    }

    fun setNotifExec(v: Boolean) {
        prefs.notifExec = v
        _state.update { it.copy(notifExec = v) }
    }

    fun setNotifApproval(v: Boolean) {
        prefs.notifApproval = v
        _state.update { it.copy(notifApproval = v) }
    }

    fun setBaseUrl(url: String) {
        val u = url.trim().trimEnd('/')
        prefs.baseUrl = u.ifBlank { id.sukiapps.hermes.domain.Prefs.DEFAULT_BASE }
        _state.update { it.copy(baseUrl = prefs.baseUrl, snack = "Base URL disimpan.") }
    }

    fun resetBaseUrl() = setBaseUrl(id.sukiapps.hermes.domain.Prefs.DEFAULT_BASE)

    fun clearKey(onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            KeyVault.clearKey(appCtx)
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun doBackup() {
        viewModelScope.launch {
            _state.update { it.copy(busyMessage = "Membuat backup…") }
            val r = withContext(Dispatchers.IO) { backup.backup() }
            _state.update {
                it.copy(busyMessage = null, snack = r.message)
            }
        }
    }

    fun listBackups(): List<File> = backup.listBackups()

    /** Restore dari file ZIP; kembalikan true bila aplikasi harus di-restart. */
    fun doRestore(file: File, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busyMessage = "Me-restore backup…") }
            val r = withContext(Dispatchers.IO) { backup.restore(file) }
            _state.update { it.copy(busyMessage = null, snack = r.message) }
            onDone(r.ok)
        }
    }

    // ---------- v9.1: Konektor (aksi manual) ----------

    /** Simpan token konektor terenkripsi via KeyVault (jangan plaintext). */
    fun saveConnectorTokens(pat: String, tgToken: String, chatId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = runCatching {
                KeyVault.putSecret(appCtx, "github_pat", pat.trim())
                KeyVault.putSecret(appCtx, "tg_bot_token", tgToken.trim())
                KeyVault.putSecret(appCtx, "tg_chat_id", chatId.trim())
            }
            _state.update {
                it.copy(
                    githubPat = pat.trim(),
                    tgBotToken = tgToken.trim(),
                    tgChatId = chatId.trim(),
                    snack = if (r.isSuccess) "Token konektor tersimpan terenkripsi."
                    else "Gagal menyimpan: ${r.exceptionOrNull()?.message}".take(200)
                )
            }
        }
    }

    /** Sprint 1 Gate 3: simpan kredensial Supabase + Vercel (KeyVault). */
    fun saveCloudTokens(sbUrl: String, sbKey: String, vcToken: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = runCatching {
                KeyVault.putSecret(appCtx, "supabase_url", sbUrl.trim())
                KeyVault.putSecret(appCtx, "supabase_key", sbKey.trim())
                KeyVault.putSecret(appCtx, "vercel_token", vcToken.trim())
            }
            _state.update {
                it.copy(
                    supabaseUrl = sbUrl.trim(),
                    supabaseKey = sbKey.trim(),
                    vercelToken = vcToken.trim(),
                    snack = if (r.isSuccess) "Kredensial Supabase & Vercel tersimpan terenkripsi."
                    else "Gagal menyimpan: ${r.exceptionOrNull()?.message}".take(200)
                )
            }
        }
    }

    /** Tes GitHub: list repo milik user. */
    fun testGitHub() {
        val pat = _state.value.githubPat
        if (pat.isBlank()) {
            _state.update { it.copy(snack = "Isi & simpan PAT GitHub dulu.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busyMessage = "Menghubungi GitHub…") }
            val r = github.listRepos(pat)
            _state.update {
                it.copy(
                    busyMessage = null,
                    snack = r.fold(
                        onSuccess = { repos ->
                            if (repos.isEmpty()) "PAT valid, tapi tidak ada repo."
                            else "Terhubung! ${repos.size} repo. Terbaru: ${repos.first()}."
                        },
                        onFailure = { (it.message ?: "Gagal.").take(200) }
                    )
                )
            }
        }
    }

    /** Tes Telegram: kirim pesan tes ke chat ID (lewat gate, MEDIUM). */
    fun testTelegram() {
        val token = _state.value.tgBotToken
        val chatId = _state.value.tgChatId
        if (token.isBlank() || chatId.isBlank()) {
            _state.update { it.copy(snack = "Isi & simpan bot token + chat ID dulu.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busyMessage = "Mengirim pesan tes…") }
            val r = telegram.sendGated(gate, token, chatId, "Tes koneksi Hermes ✅ (v9.2)")
            _state.update {
                it.copy(
                    busyMessage = null,
                    snack = r.fold(
                        onSuccess = { "Pesan tes terkirim ke Telegram." },
                        onFailure = { (it.message ?: "Gagal.").take(200) }
                    )
                )
            }
        }
    }

    /**
     * Kirim perintah ke Termux — WAJIB lewat PermissionGate (HIGH: selalu
     * popup, tanpa opsi ingat). Ditolak → snackbar + tercatat di audit.
     */
    fun runTermux(path: String, args: List<String>, workdir: String, background: Boolean) {
        viewModelScope.launch {
            val intent = TermuxConnector.buildIntent(path, args, workdir, background)
            val raw = TermuxConnector.rawCommand(path, args, workdir, background)
            val ok = TermuxConnector.sendGated(appCtx, gate, intent, raw)
            _state.update {
                it.copy(
                    snack = if (ok) "Perintah dikirim ke Termux."
                    else "Aksi ditolak & dicatat di Riwayat Izin."
                )
            }
        }
    }

    // ---------- v9.2: Telegram Serverless (via Termux) ----------

    fun setServerlessDir(v: String) {
        prefs.serverlessDir = v
        _state.update { it.copy(serverlessDir = v) }
    }

    /**
     * Jalankan aksi Serverless — selalu lewat PermissionGate HIGH (popup,
     * tanpa opsi ingat). Token login dipegang Termux, tidak disimpan di app.
     */
    fun runServerless(action: ServerlessConnector.Action) {
        viewModelScope.launch {
            val dir = _state.value.serverlessDir.trim()
            val ok = serverless.runGated(appCtx, gate, dir, action)
            _state.update {
                it.copy(
                    snack = if (ok) "${action.label}: perintah dikirim ke Termux."
                    else "Aksi ditolak & dicatat di Riwayat Izin."
                )
            }
        }
    }

    // ---------- v9.4: Konektor Upin ----------

    /** Simpan URL (prefs biasa) + secret (terenkripsi via KeyVault). */
    fun saveUpinSettings(url: String, secret: String) {
        val u = url.trim().trimEnd('/')
        viewModelScope.launch(Dispatchers.IO) {
            val r = runCatching {
                prefs.upinUrl = u
                KeyVault.putSecret(appCtx, UpinConnector.SECRET_KEY, secret.trim())
            }
            _state.update {
                it.copy(
                    upinUrl = u,
                    upinSecret = secret.trim(),
                    snack = if (r.isSuccess) "Pengaturan Upin tersimpan."
                    else "Gagal menyimpan: ${r.exceptionOrNull()?.message}".take(200)
                )
            }
        }
    }

    /**
     * Tes koneksi Upin — lewat PermissionGate (MEDIUM, boleh diingat).
     * Hasil ditampilkan di status grup, bukan snackbar.
     */
    fun testUpin() {
        viewModelScope.launch {
            val url = _state.value.upinUrl.ifBlank { UpinConnector.DEFAULT_URL }
            val granted = gate.request(
                "Upin",
                "Tes koneksi ke Upin ($url).",
                DangerLevel.MEDIUM
            )
            if (!granted) {
                _state.update {
                    it.copy(upinStatus = "Ditolak — dicatat di Riwayat Izin.")
                }
                return@launch
            }
            _state.update {
                it.copy(busyMessage = "Menghubungi Upin…", upinStatus = null)
            }
            val r = runCatching {
                withContext(Dispatchers.IO) { upin.testConnection() }
            }
            _state.update {
                it.copy(
                    busyMessage = null,
                    upinStatus = r.fold(
                        onSuccess = { "Terhubung ✓" },
                        onFailure = { (it.message ?: "Gagal.").take(200) }
                    )
                )
            }
        }
    }

    // ---------- v14.31 (D3): Login Google via Supabase Auth (PKCE) ----------

    /**
     * Buka Custom Tab login Google. Memakai URL + anon key yang SUDAH
     * tersimpan (bukan yang baru diketik tapi belum disimpan) — bila kosong,
     * tolak dengan pesan jelas tanpa network call.
     */
    fun startGoogleLogin() {
        val url = _state.value.supabaseUrl.trim().trimEnd('/')
        val key = _state.value.supabaseKey.trim()
        if (url.isBlank() || key.isBlank()) {
            _state.update { it.copy(snack = "Isi & simpan URL Supabase + anon key dulu.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val verifier = SupabaseAuth.generateCodeVerifier()
            val challenge = SupabaseAuth.codeChallenge(verifier)
            val authUrl = SupabaseAuth.buildAuthorizeUrl(url, challenge)
            if (authUrl == null) {
                _state.update { it.copy(snack = "URL Supabase harus https.") }
                return@launch
            }
            val saved = runCatching {
                KeyVault.putSecret(appCtx, SupabaseAuth.SECRET_PKCE_VERIFIER, verifier)
            }
            if (saved.isFailure) {
                _state.update {
                    it.copy(snack = "Gagal menyiapkan login: ${saved.exceptionOrNull()?.message}".take(200))
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                runCatching {
                    val cti = CustomTabsIntent.Builder().build()
                    cti.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    cti.launchUrl(appCtx, Uri.parse(authUrl))
                }.onFailure {
                    _state.update {
                        it.copy(snack = "Gagal membuka browser: ${it.message}".take(200))
                    }
                }
            }
        }
    }

    /**
     * Konsumsi callback deep link (dipanggil sekali saat Pengaturan dibuka).
     * No-op bila tidak ada callback. Verifier dihapus di semua jalur setelah
     * percobaan tukar (sekali pakai).
     */
    fun consumeAuthCallback() {
        val (code, error) = SupabaseAuthCallback.take()
        if (error != null) {
            _state.update { it.copy(snack = "Login dibatalkan/ditolak: $error".take(200)) }
            return
        }
        if (code == null) return
        val url = _state.value.supabaseUrl.trim().trimEnd('/')
        val key = _state.value.supabaseKey.trim()
        if (url.isBlank() || key.isBlank()) {
            _state.update {
                it.copy(snack = "Konfigurasi Supabase belum tersimpan — callback login dibuang.")
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(authBusy = true) }
            try {
                val verifier = KeyVault.getSecret(appCtx, SupabaseAuth.SECRET_PKCE_VERIFIER)
                if (verifier.isBlank()) throw Exception("Verifier PKCE tidak ditemukan — ulangi login.")
                val session = SupabaseAuth.exchangeCode(url, key, code, verifier).getOrThrow()
                val profile = SupabaseAuth.fetchUser(url, key, session.accessToken)
                    .getOrElse { UserProfile(session.userId, session.email, session.name) }
                runCatching {
                    KeyVault.putSecret(appCtx, SupabaseAuth.SECRET_ACCESS_TOKEN, session.accessToken)
                    KeyVault.putSecret(appCtx, SupabaseAuth.SECRET_REFRESH_TOKEN, session.refreshToken)
                    KeyVault.putSecret(appCtx, SupabaseAuth.SECRET_USER_ID, profile.id)
                    KeyVault.putSecret(appCtx, SupabaseAuth.SECRET_USER_EMAIL, profile.email)
                    KeyVault.putSecret(appCtx, SupabaseAuth.SECRET_USER_NAME, profile.name)
                    KeyVault.putSecret(appCtx, SupabaseAuth.SECRET_EXPIRES_AT, session.expiresAtEpochSec.toString())
                }
                _state.update {
                    it.copy(
                        authEmail = profile.email,
                        authName = profile.name,
                        authStatus = "Login",
                        authBusy = false,
                        snack = "Login Google berhasil ✓"
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(authBusy = false, snack = (e.message ?: "Login gagal.").take(200))
                }
            } finally {
                runCatching { KeyVault.clearSecret(appCtx, SupabaseAuth.SECRET_PKCE_VERIFIER) }
            }
        }
    }

    /**
     * Refresh diam-diam bila perlu: hanya bila refresh token ada DAN token
     * sudah kedaluwarsa (buffer 60 detik). Bila refresh gagal, sesi dianggap
     * berakhir — secret sesi dihapus, status jujur (bukan token mati diam-diam).
     */
    fun refreshAuthStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val refresh = KeyVault.getSecret(appCtx, SupabaseAuth.SECRET_REFRESH_TOKEN)
            if (refresh.isBlank()) return@launch
            val expiresAt = KeyVault.getSecret(appCtx, SupabaseAuth.SECRET_EXPIRES_AT)
                .toLongOrNull() ?: 0L
            if (expiresAt > System.currentTimeMillis() / 1000) return@launch
            val url = _state.value.supabaseUrl.trim().trimEnd('/')
            val key = _state.value.supabaseKey.trim()
            if (url.isBlank() || key.isBlank()) return@launch
            _state.update { it.copy(authBusy = true) }
            try {
                val session = SupabaseAuth.refreshSession(url, key, refresh).getOrThrow()
                val profile = SupabaseAuth.fetchUser(url, key, session.accessToken)
                    .getOrElse { UserProfile(session.userId, session.email, session.name) }
                runCatching {
                    KeyVault.putSecret(appCtx, SupabaseAuth.SECRET_ACCESS_TOKEN, session.accessToken)
                    KeyVault.putSecret(appCtx, SupabaseAuth.SECRET_REFRESH_TOKEN, session.refreshToken)
                    KeyVault.putSecret(appCtx, SupabaseAuth.SECRET_USER_ID, profile.id)
                    KeyVault.putSecret(appCtx, SupabaseAuth.SECRET_USER_EMAIL, profile.email)
                    KeyVault.putSecret(appCtx, SupabaseAuth.SECRET_USER_NAME, profile.name)
                    KeyVault.putSecret(appCtx, SupabaseAuth.SECRET_EXPIRES_AT, session.expiresAtEpochSec.toString())
                }
                _state.update {
                    it.copy(
                        authEmail = profile.email,
                        authName = profile.name,
                        authStatus = "Login",
                        authBusy = false
                    )
                }
            } catch (e: Exception) {
                clearSessionSecrets()
                _state.update {
                    it.copy(
                        authEmail = "",
                        authName = "",
                        authStatus = "Sesi berakhir, login ulang",
                        authBusy = false
                    )
                }
            }
        }
    }

    /** Logout: POST server best-effort, lalu hapus SEMUA secret sesi lokal. */
    fun logoutGoogle() {
        viewModelScope.launch(Dispatchers.IO) {
            val url = _state.value.supabaseUrl.trim().trimEnd('/')
            val key = _state.value.supabaseKey.trim()
            val token = KeyVault.getSecret(appCtx, SupabaseAuth.SECRET_ACCESS_TOKEN)
            if (url.isNotBlank() && key.isNotBlank() && token.isNotBlank()) {
                runCatching { SupabaseAuth.logout(url, key, token) }
            }
            clearSessionSecrets()
            _state.update {
                it.copy(authEmail = "", authName = "", authStatus = null, snack = "Logout.")
            }
        }
    }

    private fun clearSessionSecrets() {
        listOf(
            SupabaseAuth.SECRET_ACCESS_TOKEN,
            SupabaseAuth.SECRET_REFRESH_TOKEN,
            SupabaseAuth.SECRET_USER_ID,
            SupabaseAuth.SECRET_USER_EMAIL,
            SupabaseAuth.SECRET_USER_NAME,
            SupabaseAuth.SECRET_PKCE_VERIFIER,
            SupabaseAuth.SECRET_EXPIRES_AT
        ).forEach { runCatching { KeyVault.clearSecret(appCtx, it) } }
    }
}

package id.sukiapps.hermes.di
import id.sukiapps.hermes.diagnostics.CrashReporter
import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import id.sukiapps.hermes.data.store.ApprovalNotifier
import id.sukiapps.hermes.data.store.ApprovalStore
import id.sukiapps.hermes.data.store.JsonStore
import id.sukiapps.hermes.data.notif.ExecNotifier
import id.sukiapps.hermes.data.work.V12Scheduler
import id.sukiapps.hermes.tool.JsRenderer
import id.sukiapps.hermes.tool.ToolAllowlist
import id.sukiapps.hermes.tool.ToolLoop
import id.sukiapps.hermes.data.audit.PermissionGate
import id.sukiapps.hermes.data.connectors.GitHubConnector
import id.sukiapps.hermes.data.connectors.SupabaseConnector
import id.sukiapps.hermes.data.connectors.TelegramConnector
import id.sukiapps.hermes.data.connectors.UpinConnector
import id.sukiapps.hermes.data.connectors.VercelConnector
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
@HiltAndroidApp
class HermesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        JsonStore.init(this)
        // Sprint 1 Gate 2 (W1): renderer JS untuk web_fetch render_js=true.
        JsRenderer.init(this)
        // Sprint 1 Gate 2: allowlist domain web_fetch (SharedPreferences).
        ToolAllowlist.init(this)
        // Sprint 1 Gate 2 slice 2: sambungkan DevTools ke PermissionGate.
        val gate = EntryPointAccessors.fromApplication(
            this, GateEntryPoint::class.java
        ).permissionGate()
        ToolLoop.attach(this, gate)
        // Sprint 1 Gate 3: daftarkan konektor sebagai tool chat.
        val cep = EntryPointAccessors.fromApplication(
            this, ConnectorEntryPoint::class.java
        )
        ToolLoop.attachConnectors(
            cep.gitHubConnector(),
            cep.telegramConnector(),
            cep.upinConnector(),
            cep.supabaseConnector(),
            cep.vercelConnector()
        )
        // v15.0: daftarkan channel notifikasi eksekusi & izin.
        ExecNotifier.ensureChannels(this)
        // v12 F2: approval pending baru -> notifikasi "Persetujuan".
        ApprovalStore.onPendingApproval = { item ->
            ApprovalNotifier.notifyApproval(this, item.id, item.title, item.detail)
        }
        // v12 F4/F5: jadwalkan brief harian + refleksi mingguan (idempoten).
        runCatching { V12Scheduler.scheduleAll(this) }
    }
}
/** Entry point Hilt untuk mengambil PermissionGate dari Application. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface GateEntryPoint {
    fun permissionGate(): PermissionGate
}
/** Entry point Hilt untuk konektor (Sprint 1 Gate 3). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ConnectorEntryPoint {
    fun gitHubConnector(): GitHubConnector
    fun telegramConnector(): TelegramConnector
    fun upinConnector(): UpinConnector
    fun supabaseConnector(): SupabaseConnector
    fun vercelConnector(): VercelConnector
}

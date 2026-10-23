package id.sukiapps.hermes.di

import id.sukiapps.hermes.diagnostics.CrashReporter
import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import id.sukiapps.hermes.data.store.ApprovalNotifier
import id.sukiapps.hermes.data.store.ApprovalStore
import id.sukiapps.hermes.data.store.JsonStore
import id.sukiapps.hermes.data.work.V12Scheduler
import id.sukiapps.hermes.tool.JsRenderer
import id.sukiapps.hermes.tool.ToolAllowlist

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
        // v12 F2: approval pending baru -> notifikasi "Persetujuan".
        ApprovalStore.onPendingApproval = { item ->
            ApprovalNotifier.notifyApproval(this, item.id, item.title, item.detail)
        }
        // v12 F4/F5: jadwalkan brief harian + refleksi mingguan (idempoten).
        runCatching { V12Scheduler.scheduleAll(this) }
    }
}

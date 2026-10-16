package id.sukiapps.hermes.di

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import id.sukiapps.hermes.data.store.ApprovalNotifier
import id.sukiapps.hermes.data.store.ApprovalStore
import id.sukiapps.hermes.data.store.JsonStore
import id.sukiapps.hermes.data.work.V12Scheduler

@HiltAndroidApp
class HermesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        JsonStore.init(this)
        // v12 F2: approval pending baru -> notifikasi "Persetujuan".
        ApprovalStore.onPendingApproval = { item ->
            ApprovalNotifier.notifyApproval(this, item.id, item.title, item.detail)
        }
        // v12 F4/F5: jadwalkan brief harian + refleksi mingguan (idempoten).
        runCatching { V12Scheduler.scheduleAll(this) }
    }
}

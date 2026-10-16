package id.sukiapps.hermes.di

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import id.sukiapps.hermes.data.store.JsonStore
import id.sukiapps.hermes.data.work.V12Scheduler

@HiltAndroidApp
class HermesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        JsonStore.init(this)
        // v12 F4/F5: jadwalkan brief harian + refleksi mingguan (idempoten).
        runCatching { V12Scheduler.scheduleAll(this) }
    }
}

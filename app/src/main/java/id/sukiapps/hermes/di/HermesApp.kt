package id.sukiapps.hermes.di

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import id.sukiapps.hermes.data.store.JsonStore

@HiltAndroidApp
class HermesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        JsonStore.init(this)
    }
}

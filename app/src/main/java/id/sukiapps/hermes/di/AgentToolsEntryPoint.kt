package id.sukiapps.hermes.di

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import id.sukiapps.hermes.data.local.AppPrefs
import id.sukiapps.hermes.data.local.ChatRepository
import id.sukiapps.hermes.data.notif.ExecNotifier
import okhttp3.OkHttpClient

/**
 * v20.1 — dependensi singleton untuk tool agen (session_search, cronjob,
 * delegate_task) dan CronJobWorker. Tool/Worker dibuat di luar graf Hilt,
 * jadi diambil lewat EntryPoint (pola sama dengan GateEntryPoint).
 * OkHttpClient = client bersama ber-AuthInterceptor (key hanya ke host
 * base URL — lihat AuthScope).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AgentToolsEntryPoint {
    fun chatRepository(): ChatRepository
    fun appPrefs(): AppPrefs
    fun okHttpClient(): OkHttpClient
    fun execNotifier(): ExecNotifier
}

fun agentDeps(context: Context): AgentToolsEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, AgentToolsEntryPoint::class.java)

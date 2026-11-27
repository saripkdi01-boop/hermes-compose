package id.sukiapps.hermes.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import id.sukiapps.hermes.KeyVault
import id.sukiapps.hermes.data.local.AppPrefs
import id.sukiapps.hermes.data.remote.ChatRemoteRepository
import id.sukiapps.hermes.data.remote.JerouterApi
import id.sukiapps.hermes.domain.AIProvider
import id.sukiapps.hermes.domain.Prefs
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Menyuntikkan Authorization: Bearer <key> ke request menuju base URL
 * provider AI yang dikonfigurasi user.
 * Key dibaca via KeyVault.getApiKeyCached (disentuh maksimal sekali
 * per proses — aman dipanggil dari thread IO OkHttp).
 *
 * v19.1 (audit P1, hardening): OkHttpClient ini singleton bersama. Dulu
 * key ditempel ke SEMUA host — satu pemakaian client ini untuk host lain
 * (konektor, web fetch, penguji koneksi) cukup untuk membocorkan API key
 * ke pihak ketiga. Sekarang key hanya dikirim bila scheme+host+port
 * request sama dengan base URL di Pengaturan ([AuthScope]), dan header
 * Authorization yang sudah dipasang pemanggil tidak pernah digandakan.
 */
class AuthInterceptor @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val prefs: AppPrefs
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val original = chain.request()
        if (original.header("Authorization") != null ||
            !AuthScope.matches(original.url, prefs.baseUrl)
        ) {
            return chain.proceed(original)
        }
        val key = KeyVault.getApiKeyCached(ctx)
        val req = if (key.isNotBlank()) {
            original.newBuilder()
                .addHeader("Authorization", "Bearer $key")
                .build()
        } else {
            original
        }
        return chain.proceed(req)
    }
}

/** v19.1: aturan "boleh menerima API key" — murni, bisa di-unit-test. */
object AuthScope {
    fun matches(url: HttpUrl, baseUrl: String): Boolean {
        val base = baseUrl.trim().toHttpUrlOrNull() ?: return false
        return url.scheme == base.scheme &&
            url.host.equals(base.host, ignoreCase = true) &&
            url.port == base.port
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // NOTE: AuthInterceptor & SseClient memakai @Inject constructor —
    // tidak perlu @Provides (akan duplicate binding).

    @Provides
    @Singleton
    fun provideOkHttp(auth: AuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(auth)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS) // stream bisa lama
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    /**
     * baseUrl Retrofit = default; URL aktual (dari prefs base_url, bisa
     * diubah user) dilewatkan per panggilan via @Url di JerouterApi.
     */
    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit {
        val json = Json { ignoreUnknownKeys = true }
        return Retrofit.Builder()
            .baseUrl(Prefs.DEFAULT_BASE + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): JerouterApi =
        retrofit.create(JerouterApi::class.java)

    @Provides
    @Singleton
    fun provideAIProvider(repo: ChatRemoteRepository): AIProvider = repo
}

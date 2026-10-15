package id.sukiapps.hermes.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import id.sukiapps.hermes.KeyVault
import id.sukiapps.hermes.data.remote.ChatRemoteRepository
import id.sukiapps.hermes.data.remote.JerouterApi
import id.sukiapps.hermes.domain.AIProvider
import id.sukiapps.hermes.domain.Prefs
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Menyuntikkan Authorization: Bearer <key> ke setiap request.
 * Key dibaca via KeyVault.getApiKeyCached (disentuh maksimal sekali
 * per proses — aman dipanggil dari thread IO OkHttp).
 */
class AuthInterceptor @Inject constructor(
    @ApplicationContext private val ctx: Context
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val key = KeyVault.getApiKeyCached(ctx)
        val req = if (key.isNotBlank()) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $key")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(req)
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

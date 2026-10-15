package id.sukiapps.hermes.data.remote

import retrofit2.http.GET
import retrofit2.http.Url

/**
 * API Jerouter (OpenAI-compatible).
 * base_url dinamis (dari prefs) → pakai @Url absolut per panggilan,
 * sehingga NetworkModule tidak perlu dibangun ulang saat base_url berubah.
 * Authorization ditambahkan oleh AuthInterceptor.
 */
interface JerouterApi {
    @GET
    suspend fun models(@Url url: String): ModelsResponse
}

package id.sukiapps.hermes.data.remote

import id.sukiapps.hermes.data.local.AppPrefs
import id.sukiapps.hermes.domain.AIHttpException
import id.sukiapps.hermes.domain.AIProvider
import id.sukiapps.hermes.domain.ChatRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementasi AIProvider untuk Jerouter (OpenAI-compatible).
 * models(): GET {base}/models dengan cache 24 jam (perbaikan v7.1).
 */
@Singleton
class ChatRemoteRepository @Inject constructor(
    private val api: JerouterApi,
    private val sse: SseClient,
    private val prefs: AppPrefs
) : AIProvider {

    override suspend fun models(): List<String> {
        prefs.cachedModels()?.let { return it }
        val base = prefs.baseUrl.trimEnd('/')
        val resp = try {
            api.models("$base/models")
        } catch (e: Exception) {
            throw AIHttpException(mapFetchError(e))
        }
        val ids = resp.data.map { it.id }.filter { it.isNotBlank() }.distinct()
        if (ids.isNotEmpty()) prefs.saveModelsCache(ids)
        return ids
    }

    override fun chatStream(request: ChatRequest): Flow<String> =
        sse.stream(prefs.baseUrl, request).flowOn(Dispatchers.IO)

    private fun mapFetchError(e: Exception): String {
        val m = e.message ?: ""
        return when {
            m.contains("401") -> "API key salah atau kedaluwarsa (401). Perbarui di Pengaturan."
            m.contains("429") -> "Batas request tercapai (429). Coba lagi sebentar."
            m.contains("Unable to resolve host") || m.contains("UnknownHostException") ->
                "Tidak ada koneksi internet. Periksa jaringan lalu coba lagi."
            m.contains("timeout", ignoreCase = true) || m.contains("timed out", ignoreCase = true) ->
                "Koneksi timeout. Coba lagi."
            else -> "Gagal mengambil daftar model: ${m.take(160)}"
        }
    }
}

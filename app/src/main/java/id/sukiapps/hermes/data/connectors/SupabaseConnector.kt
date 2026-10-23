package id.sukiapps.hermes.data.connectors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Konektor Supabase — READ-ONLY (Sprint 1 Gate 3 opsi A).
 * Hanya GET ke PostgREST (/rest/v1/<tabel>) dengan key milik user
 * (tersimpan terenkripsi via KeyVault: supabase_url + supabase_key).
 * Tidak ada operasi tulis sama sekali di kelas ini.
 */
@Singleton
class SupabaseConnector @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val tableName = Regex("^[A-Za-z0-9_]+$")

    /** Ambil baris dari [table] (select=*), maks [limit] baris (1..50). */
    suspend fun queryTable(
        baseUrl: String,
        key: String,
        table: String,
        limit: Int = 10
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(tableName.matches(table)) { "Nama tabel tidak valid: $table" }
            val base = baseUrl.trim().trimEnd('/')
            require(base.startsWith("https://")) { "URL Supabase harus https." }
            val n = limit.coerceIn(1, 50)
            val url = "$base/rest/v1/$table?select=*&limit=$n"
            val req = Request.Builder()
                .url(url)
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw Exception(mapError(resp.code, body))
                }
                body
            }
        }
    }

    private fun mapError(code: Int, body: String): String = when (code) {
        401, 403 -> "Key Supabase ditolak ($code) — cek key/RLS tabel."
        404 -> "Tabel tidak ditemukan (404)."
        else -> "Supabase menolak (HTTP $code): ${body.take(160)}"
    }
}

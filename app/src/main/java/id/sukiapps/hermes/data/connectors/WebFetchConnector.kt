package id.sukiapps.hermes.data.connectors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ambil isi halaman web sebagai teks — AKSI MANUAL via tombol "Ambil dari web"
 * di ChatScreen. BUKAN dipakai agen otomatis (butuh function-calling model —
 * belum didukung; lihat features.md).
 *
 * shortcut: tanpa Jsoup — HTML→teks via regex sederhana. Cukup untuk artikel
 * dan dokumentasi; bisa berantakan pada halaman yang berat JavaScript.
 * Upgrade ke parser beneran bila dibutuhkan.
 */
@Singleton
class WebFetchConnector @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** GET url → teks halaman (respons di-cap 200KB). Hanya http/https. */
    suspend fun fetch(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Result.success(fetchOrThrow(url.trim()))
        } catch (e: Exception) {
            Result.failure(Exception(friendly(e)))
        }
    }

    private fun fetchOrThrow(url: String): String {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw IllegalArgumentException("scheme")
        }
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "HermesApp/9.2")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("http${resp.code}")
            val ct = resp.header("Content-Type").orEmpty()
            if (ct.isNotBlank() && !ct.contains("html", ignoreCase = true)) {
                throw Exception("notml")
            }
            val bytes = resp.body?.bytes() ?: throw Exception("empty")
            if (bytes.size > MAX_BYTES) throw Exception("big")
            return htmlToText(bytes.toString(Charsets.UTF_8))
                .ifBlank { throw Exception("empty") }
        }
    }

    private fun friendly(e: Exception): String = when {
        e is IllegalArgumentException && e.message == "scheme" ->
            "Hanya URL http/https yang didukung."
        e is SocketTimeoutException ->
            "Timeout — halaman tidak merespons dalam 15 detik."
        e is UnknownHostException ->
            "Alamat tidak dikenal — periksa URL / koneksi."
        e.message == "notml" -> "Bukan halaman HTML."
        e.message == "big" -> "Halaman terlalu besar (>200KB)."
        e.message == "empty" -> "Tidak ada teks yang bisa dibaca."
        e.message?.startsWith("http") == true ->
            "Halaman tidak dapat diambil (${e.message})."
        else -> (e.message ?: "Gagal mengambil halaman.").take(200)
    }

    /** HTML→teks: buang script/style/komentar, strip tag, decode entities umum. */
    internal fun htmlToText(html: String): String {
        var t = html
        t = t.replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), " ")
        t = t.replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), " ")
        t = t.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), " ")
        t = t.replace(Regex("<[^>]+>"), " ")
        t = t.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
        t = t.replace(Regex("&#(\\d+);")) { m ->
            runCatching { m.groupValues[1].toInt().toChar().toString() }
                .getOrDefault(m.value)
        }
        return t.replace(Regex("\\s+"), " ").trim()
    }

    companion object {
        const val MAX_BYTES = 200 * 1024
    }
}

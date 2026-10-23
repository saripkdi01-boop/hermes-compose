package id.sukiapps.hermes.tool

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Hasil eksekusi tool: konten untuk dimasukkan sebagai pesan role=tool. */
data class ToolResult(
    val content: String,
    val isError: Boolean = false,
)

/**
 * WebFetchTool — ambil isi teks dari URL dengan guard keamanan.
 *
 * Guard wajib (sesuai spek): SSRF block (resolve IP dulu, tolak privat/loopback/
 * link-local/CGNAT), redirect manual max 5 hop (diaudit per hop), size cap 2 MB,
 * timeout terpisah (connect 10s / read 15s / call 25s), tanpa cookie, tanpa
 * custom header selain UA + Accept.
 */
class WebFetchTool {

    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .cookieJar(CookieJar.NO_COOKIES)
        .build()

    suspend fun fetch(
        url: String,
        renderJs: Boolean = false,
        maxChars: Int = 20_000,
    ): ToolResult = withContext(Dispatchers.IO) {
        try {
            var current = normalize(url)
            var hops = 0

            while (hops <= 5) {
                val host = runCatching { URI(current).host }.getOrNull()
                    ?: return@withContext ToolResult("Error: URL tidak punya host: $url", true)

                if (!ToolAllowlist.isAllowed(host)) {
                    return@withContext ToolResult(
                        "Error: domain '$host' tidak ada di allowlist web_fetch. " +
                            "Tambahkan dulu di Pengaturan > Tool Web.",
                        true,
                    )
                }

                val ips = runCatching { InetAddress.getAllByName(host) }.getOrElse {
                    return@withContext ToolResult("Error: host '$host' tidak bisa di-resolve: ${it.message}", true)
                }
                for (ip in ips) {
                    if (isPrivate(ip)) {
                        return@withContext ToolResult(
                            "Error: host '$host' resolve ke IP privat/loopback (${ip.hostAddress}). " +
                                "Akses jaringan internal diblokir.",
                            true,
                        )
                    }
                }

                val req = Request.Builder()
                    .url(current)
                    .header("User-Agent", "HermesBot/9.3 (+https://id.sukiapps.hermes)")
                    .header("Accept", "text/html,application/xhtml+xml,text/plain,application/json;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "id-ID,id;q=0.9,en;q=0.8")
                    .build()

                // Catatan: jangan pakai continue/break di dalam use{} (inline) —
                // pakai flag next/result agar kompilasi aman.
                var next: String? = null
                var result: ToolResult? = null
                client.newCall(req).execute().use { res ->
                    val code = res.code
                    val type = res.header("Content-Type").orEmpty().lowercase(Locale.US)

                    if (code in 300..399) {
                        val loc = res.header("Location")
                        if (loc == null) {
                            result = ToolResult("Error: redirect $code tanpa Location header", true)
                        } else {
                            next = normalize(URI(current).resolve(loc).toString())
                        }
                    } else if (code !in 200..299) {
                        result = ToolResult("Error: HTTP $code dari $current", true)
                    } else if (type.contains("image") || type.contains("video") ||
                        type.contains("audio") || type.contains("font") ||
                        type.contains("application/octet-stream")
                    ) {
                        result = ToolResult(
                            "Error: content-type '$type' bukan teks. Tidak bisa di-fetch.",
                            true,
                        )
                    } else {
                        val stream = res.body?.byteStream()
                        if (stream == null) {
                            result = ToolResult("Error: body kosong", true)
                        } else {
                            val raw = stream.use { it.readLimited(2 * 1024 * 1024) }
                            val title = extractTitle(raw)
                            val cleaned = if (type.contains("html") && !renderJs) stripHtml(raw) else raw
                            result = ToolResult(formatResult(current, code, type, title, cleaned, maxChars))
                        }
                    }
                }
                result?.let { return@withContext it }
                if (next != null) {
                    current = next
                    hops++
                } else {
                    return@withContext ToolResult("Error: alur tidak terduga", true)
                }
            }
            ToolResult("Error: melebihi 5 redirect", true)
        } catch (e: Exception) {
            ToolResult("Error: ${e.javaClass.simpleName}: ${e.message}", true)
        }
    }

    /**
     * Jalur render_js: HTML sudah di-render oleh JsRenderer — tidak ada
     * request jaringan lagi di sini, langsung bersihkan + format.
     */
    fun fetchFromHtml(finalUrl: String, html: String, maxChars: Int = 20_000): ToolResult {
        val title = extractTitle(html)
        val cleaned = stripHtml(html)
        return ToolResult(formatResult(finalUrl, 200, "text/html (rendered)", title, cleaned, maxChars))
    }

    private fun formatResult(
        finalUrl: String,
        code: Int,
        type: String,
        title: String,
        cleaned: String,
        maxChars: Int,
    ): String {
        val header = buildString {
            appendLine("STATUS: $code")
            appendLine("FINAL_URL: $finalUrl")
            appendLine("CONTENT_TYPE: $type")
            appendLine("TITLE: $title")
            appendLine("BYTES: ${cleaned.length}")
            appendLine("---")
        }
        val body = if (cleaned.length > maxChars) {
            cleaned.take(maxChars) +
                "\n\n[TRUNCATED: ${cleaned.length - maxChars} karakter berikutnya dipotong]"
        } else {
            cleaned
        }
        return header + body
    }

    private fun normalize(u: String): String {
        val t = u.trim()
        return if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
    }

    private fun isPrivate(ip: InetAddress): Boolean = ip.isLoopbackAddress ||
        ip.isAnyLocalAddress ||
        ip.isLinkLocalAddress ||
        ip.isSiteLocalAddress ||
        ip.hostAddress.startsWith("169.254.") ||
        ip.hostAddress.startsWith("100.64.") || // CGNAT
        ip.hostAddress.startsWith("0.") ||
        ip.hostAddress.startsWith("127.")

    private fun stripHtml(html: String): String {
        var s = html
        val drop = listOf(
            "script" to true, "style" to true, "noscript" to true,
            "svg" to true, "head" to false, "nav" to true,
            "footer" to true, "aside" to true, "iframe" to true,
        )
        for ((tag, removeContent) in drop) {
            val rx = if (removeContent) {
                Regex("(?is)<$tag\\b[^>]*>.*?</$tag>")
            } else {
                Regex("(?is)<$tag\\b[^>]*>")
            }
            s = rx.replace(s, "")
        }
        s = Regex("(?is)<!--.*?-->").replace(s, "")
        s = Regex("(?is)</?(p|div|br|li|tr|h[1-6]|section|article)[^>]*>").replace(s, "\n")
        s = Regex("(?s)<[^>]+>").replace(s, " ")
        s = s.replace("&nbsp;", " ").replace("&amp;", "&")
            .replace("&lt;", "<").replace("&gt;", ">")
            .replace("&#39;", "'").replace("&quot;", "\"")
        s = s.replace(Regex("[ \\t]+"), " ").replace(Regex("\\n\\s*\\n\\s*\\n+"), "\n\n")
        return s.trim()
    }

    private fun extractTitle(html: String): String {
        return Regex("(?is)<title[^>]*>(.*?)</title>").find(html)
            ?.groupValues?.getOrNull(1)?.trim().orEmpty()
            .replace(Regex("\\s+"), " ")
            .take(200)
    }

    private fun java.io.InputStream.readLimited(max: Int): String {
        val buf = ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = read(chunk)
            if (n <= 0) break
            val allowed = minOf(n, max - total)
            if (allowed <= 0) break
            buf.write(chunk, 0, allowed)
            total += allowed
        }
        return buf.toString("UTF-8")
    }
}

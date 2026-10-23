package id.sukiapps.hermes.tool

import org.json.JSONArray
import org.json.JSONObject

/**
 * ToolLoop — registry + eksekutor tool Hermes (adaptasi v14, org.json).
 *
 * Perbedaan dari spek asli:
 * - Pakai org.json (sesuai layer net/ v14), bukan Gson.
 * - Skema parameters diperbaiki mengikuti format OpenAI function calling
 *   (versi spek tidak kompilasi: JsonPrimitive 2-argumen + "properties" ganda).
 * - render_js: bila JsRenderer gagal/null → fallback ke fetch biasa.
 */
object ToolLoop {

    const val MAX_ITERATIONS = 6

    /** Sprint 1 Gate 2: iterasi default tool-loop di stack live. */
    const val DEFAULT_ITERATIONS = 5

    /** Sprint 1 Gate 2: batas keras iterasi tool-loop (clamp pemanggil). */
    const val HARD_MAX_ITERATIONS = 10

    private val webFetchParams = JSONObject()
        .put("type", "object")
        .put(
            "properties",
            JSONObject()
                .put(
                    "url",
                    JSONObject()
                        .put("type", "string")
                        .put("description", "URL lengkap, contoh: https://sukiapps.web.id/pricing"),
                )
                .put(
                    "render_js",
                    JSONObject()
                        .put("type", "boolean")
                        .put("description", "Render dengan WebView headless bila konten utama muncul setelah JavaScript jalan. Default false."),
                )
                .put(
                    "max_chars",
                    JSONObject()
                        .put("type", "integer")
                        .put("description", "Batas karakter output. Default 20000, max 100000."),
                ),
        )
        .put("required", JSONArray().put("url"))

    /** Rakit blok tools[] untuk request OpenAI-compatible. */
    fun toolsArray(): JSONArray = JSONArray().put(
        JSONObject()
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", "web_fetch")
                    .put(
                        "description",
                        "Ambil isi teks dari sebuah URL (halaman web, dokumentasi, JSON API publik). " +
                            "Gunakan saat butuh isi faktual dari internet, verifikasi klaim, atau audit isi halaman. " +
                            "Set render_js=true hanya jika konten utama muncul setelah JavaScript jalan.",
                    )
                    .put("parameters", webFetchParams),
            ),
    )

    /** Jalankan tool yang diminta model. Argumen dinormalisasi & di-clamp. Tidak pernah throw. */
    suspend fun execute(name: String, args: JSONObject): ToolResult = when (name) {
        "web_fetch" -> {
            val url = args.optString("url", "").trim()
            if (url.isEmpty()) {
                ToolResult("Error: parameter 'url' wajib diisi", true)
            } else {
                val renderJs = args.optBoolean("render_js", false)
                val maxChars = args.optInt("max_chars", 20_000).coerceIn(500, 100_000)
                val tool = WebFetchTool()
                if (renderJs) {
                    val html = JsRenderer.get(url)
                    if (html != null) tool.fetchFromHtml(url, html, maxChars)
                    else tool.fetch(url, renderJs = false, maxChars = maxChars)
                } else {
                    tool.fetch(url, renderJs = false, maxChars = maxChars)
                }
            }
        }
        else -> ToolResult("Error: tool '$name' tidak dikenal", true)
    }
}

package id.sukiapps.hermes.tool

import org.json.JSONObject

/**
 * web_fetch — ambil isi halaman web (masterprompt §5.3: `web.fetch`).
 *
 * ADAPTER, bukan implementasi baru: eksekusi didelegasikan ke [WebFetchTool]
 * (fetcher yang sudah ada sejak Sprint 1) supaya SEMUA guard lama tetap
 * berlaku tanpa duplikasi: SSRF block (resolve IP dulu, tolak privat /
 * loopback / link-local / CGNAT), allowlist domain [ToolAllowlist]
 * (kosong = semua domain publik boleh), redirect manual maks 5 hop,
 * size cap 2 MB, timeout connect 10 dtk / read 15 dtk / call 25 dtk,
 * tanpa cookie, tanpa custom header selain UA + Accept.
 *
 * `requiresApproval` tetap false (seperti perilaku lama: tidak ada popup
 * PermissionGate untuk web_fetch) — penjaganya adalah allowlist + SSRF
 * guard di atas, bukan dialog. Bila suatu saat web_fetch perlu popup,
 * ubah flag ini + dokumentasikan di TOOLS.md.
 */
class WebFetchHermesTool : HermesTool {

    override val id: String = "web_fetch"
    override val name: String = "Ambil Halaman Web"
    override val description: String =
        "Ambil isi teks dari sebuah URL (halaman web, dokumentasi, JSON API " +
            "publik). Gunakan saat butuh isi faktual dari internet, verifikasi " +
            "klaim, atau audit isi halaman. Set render_js=true hanya jika " +
            "konten utama muncul setelah JavaScript jalan."
    override val inputSchema: JSONObject = toolInputSchema(
        JSONObject()
            .put(
                "url",
                stringProp("URL lengkap, contoh: https://sukiapps.web.id/pricing"),
            )
            .put(
                "render_js",
                booleanProp(
                    "Render dengan WebView headless bila konten utama muncul " +
                        "setelah JavaScript jalan. Default false.",
                ),
            )
            .put(
                "max_chars",
                integerProp("Batas karakter output. Default 20000, max 100000."),
            ),
        listOf("url"),
    )
    override val capabilities: Set<ToolCapability> = setOf(ToolCapability.NETWORK)

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val url = args.optString("url", "").trim()
        if (url.isEmpty()) {
            return ToolResult("Error: parameter 'url' wajib diisi", true)
        }
        val renderJs = args.optBoolean("render_js", false)
        val maxChars = args.optInt("max_chars", 20_000).coerceIn(500, 100_000)
        val tool = WebFetchTool()
        return if (renderJs) {
            val html = JsRenderer.get(url)
            if (html != null) tool.fetchFromHtml(url, html, maxChars)
            else tool.fetch(url, renderJs = false, maxChars = maxChars)
        } else {
            tool.fetch(url, renderJs = false, maxChars = maxChars)
        }
    }
}

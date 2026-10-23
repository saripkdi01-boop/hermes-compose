package id.sukiapps.hermes.tool

import android.content.Context
import id.sukiapps.hermes.data.audit.PermissionGate
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

    // Sprint 1 Gate 2 slice 2: konteks + gerbang izin untuk DevTools.
    @Volatile
    private var appCtx: Context? = null

    @Volatile
    private var gate: PermissionGate? = null

    /** Dipanggil HermesApp.onCreate sekali. */
    fun attach(ctx: Context, permissionGate: PermissionGate) {
        appCtx = ctx.applicationContext
        gate = permissionGate
    }

    private fun toolDef(name: String, description: String, params: JSONObject): JSONObject =
        JSONObject()
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", name)
                    .put("description", description)
                    .put("parameters", params),
            )

    private fun params(props: JSONObject, required: List<String>): JSONObject {
        val o = JSONObject().put("type", "object").put("properties", props)
        if (required.isNotEmpty()) {
            val r = JSONArray()
            required.forEach { r.put(it) }
            o.put("required", r)
        }
        return o
    }

    private fun prop(type: String, description: String): JSONObject =
        JSONObject().put("type", type).put("description", description)

    /** Rakit blok tools[] untuk request OpenAI-compatible. */
    fun toolsArray(): JSONArray {
        val arr = JSONArray()
        arr.put(
            toolDef(
                "web_fetch",
                "Ambil isi teks dari sebuah URL (halaman web, dokumentasi, JSON API publik). " +
                    "Gunakan saat butuh isi faktual dari internet, verifikasi klaim, atau audit isi halaman. " +
                    "Set render_js=true hanya jika konten utama muncul setelah JavaScript jalan.",
                webFetchParams,
            ),
        )
        arr.put(
            toolDef(
                "shell_exec",
                "Jalankan perintah shell di sandbox terminal Hermes (cwd terkunci di folder sandbox aplikasi). " +
                    "Perintah destruktif selalu butuh persetujuan user. Timeout 30 detik.",
                params(
                    JSONObject().put("command", prop("string", "Perintah shell lengkap")),
                    listOf("command"),
                ),
            ),
        )
        arr.put(
            toolDef(
                "file_read",
                "Baca file teks di dalam sandbox terminal (path relatif terhadap sandbox).",
                params(
                    JSONObject().put("path", prop("string", "Path relatif di sandbox")),
                    listOf("path"),
                ),
            ),
        )
        arr.put(
            toolDef(
                "file_write",
                "Tulis file teks di dalam sandbox terminal (path relatif terhadap sandbox).",
                params(
                    JSONObject()
                        .put("path", prop("string", "Path relatif di sandbox"))
                        .put("content", prop("string", "Isi file")),
                    listOf("path", "content"),
                ),
            ),
        )
        arr.put(
            toolDef(
                "net_scan",
                "Scan port TCP (connect) ke host yang ada di allowlist scan (default localhost/127.0.0.1). " +
                    "Maks 32 port.",
                params(
                    JSONObject()
                        .put("host", prop("string", "Host/IP target (harus di allowlist scan)"))
                        .put(
                            "ports",
                            JSONObject()
                                .put("type", "array")
                                .put("items", JSONObject().put("type", "integer"))
                                .put("description", "Daftar port; default 22,80,443,8080,8443"),
                        ),
                    listOf("host"),
                ),
            ),
        )
        arr.put(
            toolDef(
                "conn_health",
                "Health check HTTP ke URL di allowlist scan: status code + latensi.",
                params(
                    JSONObject().put("url", prop("string", "URL lengkap")),
                    listOf("url"),
                ),
            ),
        )
        arr.put(
            toolDef(
                "code_run",
                "Jalankan potongan kode shell di sandbox (timeout 10 detik, selalu butuh persetujuan user).",
                params(
                    JSONObject().put("code", prop("string", "Kode shell")),
                    listOf("code"),
                ),
            ),
        )
        arr.put(
            toolDef(
                "system_info",
                "Info perangkat & sandbox (read-only): model, versi Android, memori, isi sandbox.",
                params(JSONObject(), emptyList()),
            ),
        )
        return arr
    }

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
        "shell_exec", "file_read", "file_write", "net_scan",
        "conn_health", "code_run", "system_info" -> {
            val c = appCtx
            val g = gate
            if (c == null || g == null) {
                ToolResult("Error: DevTools belum diinisialisasi.", true)
            } else {
                DevTools.execute(name, args, c, g)
            }
        }
        else -> ToolResult("Error: tool '$name' tidak dikenal", true)
    }
}

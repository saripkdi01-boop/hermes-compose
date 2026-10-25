package id.sukiapps.hermes.tool

import android.content.Context
import id.sukiapps.hermes.data.audit.PermissionGate
import id.sukiapps.hermes.data.connectors.GitHubConnector
import id.sukiapps.hermes.data.connectors.SupabaseConnector
import id.sukiapps.hermes.data.connectors.TelegramConnector
import id.sukiapps.hermes.data.connectors.UpinConnector
import id.sukiapps.hermes.data.connectors.VercelConnector
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

    @Volatile
    private var connectorBag: ConnectorTools.Bag? = null

    /** Dipanggil HermesApp.onCreate sekali. */
    fun attach(ctx: Context, permissionGate: PermissionGate) {
        appCtx = ctx.applicationContext
        gate = permissionGate
    }

    /** Sprint 1 Gate 3: daftarkan konektor untuk ConnectorTools. */
    fun attachConnectors(
        github: GitHubConnector,
        telegram: TelegramConnector,
        upin: UpinConnector,
        supabase: SupabaseConnector,
        vercel: VercelConnector
    ) {
        connectorBag = ConnectorTools.Bag(github, telegram, upin, supabase, vercel)
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

    /**
     * Rakit blok tools[] untuk request OpenAI-compatible.
     *
     * 14.25 (B5): jalur CHAT tidak pernah menerima definisi selfupgrade_*.
     * Pemanggil chat (ChatRepository, SseClient.streamWithTools) WAJIB
     * memakai `includeSelfUpgrade = false`; jalur agen Manus (validator +
     * eksekusi lewat reducer) memakai default true. Ini gerbang lapis
     * pertama — lapis kedua adalah [chatRefusal] di titik eksekusi chat,
     * yang menolak panggilan selfupgrade_* hasil halusinasi model sekalipun
     * namanya tidak ada di tools[].
     */
    fun toolsArray(includeSelfUpgrade: Boolean = true): JSONArray {
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
                "github_repos",
                "Daftar repo GitHub milik user (read-only, dari PAT tersimpan).",
                params(JSONObject(), emptyList()),
            ),
        )
        arr.put(
            toolDef(
                "github_runs",
                "5 workflow run GitHub Actions terakhir sebuah repo (read-only).",
                params(
                    JSONObject()
                        .put("owner", prop("string", "Owner repo"))
                        .put("repo", prop("string", "Nama repo")),
                    listOf("owner", "repo"),
                ),
            ),
        )
        arr.put(
            toolDef(
                "github_read_file",
                "Baca isi file teks dari repo GitHub (read-only).",
                params(
                    JSONObject()
                        .put("owner", prop("string", "Owner repo"))
                        .put("repo", prop("string", "Nama repo"))
                        .put("path", prop("string", "Path file di repo")),
                    listOf("owner", "repo", "path"),
                ),
            ),
        )
        arr.put(
            toolDef(
                "github_write_file",
                "Tulis/update file di repo GitHub (selalu minta persetujuan user).",
                params(
                    JSONObject()
                        .put("owner", prop("string", "Owner repo"))
                        .put("repo", prop("string", "Nama repo"))
                        .put("path", prop("string", "Path file di repo"))
                        .put("content", prop("string", "Isi file"))
                        .put("message", prop("string", "Pesan commit")),
                    listOf("owner", "repo", "path", "content"),
                ),
            ),
        )
        arr.put(
            toolDef(
                "github_dispatch",
                "Trigger workflow_dispatch GitHub Actions (minta persetujuan user).",
                params(
                    JSONObject()
                        .put("owner", prop("string", "Owner repo"))
                        .put("repo", prop("string", "Nama repo"))
                        .put("workflow", prop("string", "Nama file workflow, mis. build.yml"))
                        .put("ref", prop("string", "Branch/ref, default main")),
                    listOf("owner", "repo", "workflow"),
                ),
            ),
        )
        arr.put(
            toolDef(
                "telegram_send",
                "Kirim pesan Telegram lewat bot milik user ke chat ID tersimpan (minta persetujuan user).",
                params(
                    JSONObject().put("text", prop("string", "Isi pesan")),
                    listOf("text"),
                ),
            ),
        )
        arr.put(
            toolDef(
                "upin_ask",
                "Tanya agent Upin di VM (minta persetujuan user; jawaban bisa 1-2 menit, batas tunggu tool ±100 detik).",
                params(
                    JSONObject().put("question", prop("string", "Pertanyaan untuk Upin")),
                    listOf("question"),
                ),
            ),
        )
        arr.put(
            toolDef(
                "supabase_query",
                "Baca baris dari tabel Supabase via PostgREST (read-only; URL+key tersimpan).",
                params(
                    JSONObject()
                        .put("table", prop("string", "Nama tabel"))
                        .put("limit", prop("integer", "Maks baris 1..50, default 10")),
                    listOf("table"),
                ),
            ),
        )
        arr.put(
            toolDef(
                "vercel_projects",
                "Daftar project Vercel milik user (read-only).",
                params(JSONObject(), emptyList()),
            ),
        )
        arr.put(
            toolDef(
                "vercel_deployments",
                "5 deployment Vercel terakhir sebuah project (read-only).",
                params(
                    JSONObject().put("project_id", prop("string", "ID project (dari vercel_projects)")),
                    listOf("project_id"),
                ),
            ),
        )
        // 14.25: definisi upgrade-diri hanya untuk jalur agen. Jalur chat
        // memanggil toolsArray(includeSelfUpgrade = false) sehingga model
        // chat tidak pernah ditawari tool ini.
        if (includeSelfUpgrade) {
        arr.put(
            toolDef(
                "selfupgrade_branch",
                "Buat branch upgrade-diri (awalan selfupgrade/) dari main. " +
                    "Hanya jalan bila Upgrade Diri Otomatis menyala.",
                params(
                    JSONObject().put(
                        "branch",
                        prop(
                            "string",
                            "Nama branch; kosong = otomatis dari tanggal/jam"
                        )
                    ),
                    emptyList(),
                ),
            ),
        )
        arr.put(
            toolDef(
                "selfupgrade_write_file",
                "Tulis file LENGKAP ke branch upgrade-diri (kontrak " +
                    "full-file, bukan diff).",
                params(
                    JSONObject()
                        .put("branch", prop("string", "Branch selfupgrade/"))
                        .put("path", prop("string", "Path file di repo"))
                        .put("content", prop("string", "Isi file lengkap"))
                        .put("message", prop("string", "Pesan commit")),
                    listOf("branch", "path", "content", "message"),
                ),
            ),
        )
        arr.put(
            toolDef(
                "selfupgrade_pr",
                "Buat pull request branch upgrade-diri ke main.",
                params(
                    JSONObject()
                        .put("branch", prop("string", "Branch selfupgrade/"))
                        .put("title", prop("string", "Judul PR"))
                        .put("body", prop("string", "Deskripsi PR")),
                    listOf("branch", "title"),
                ),
            ),
        )
        arr.put(
            toolDef(
                "selfupgrade_ci",
                "Status CI gabungan sebuah PR: success, pending, " +
                    "failure, atau error.",
                params(
                    JSONObject().put("pr", prop("integer", "Nomor PR")),
                    listOf("pr"),
                ),
            ),
        )
        arr.put(
            toolDef(
                "selfupgrade_merge",
                "Merge PR upgrade-diri ke main. DITOLAK bila CI belum " +
                    "success.",
                params(
                    JSONObject().put("pr", prop("integer", "Nomor PR")),
                    listOf("pr"),
                ),
            ),
        )
        arr.put(
            toolDef(
                "selfupgrade_release",
                "Buat DRAFT rilis GitHub. Body wajib memuat baris " +
                    "versionCode:NN. Maintainer sign APK, lampirkan ke " +
                    "draft, lalu publish.",
                params(
                    JSONObject()
                        .put("tag", prop("string", "Tag, contoh: v14.9"))
                        .put("name", prop("string", "Judul rilis"))
                        .put("body", prop("string", "Isi rilis")),
                    listOf("tag", "name", "body"),
                ),
            ),
        )
        } // if (includeSelfUpgrade)
        arr.put(
            toolDef(
                "memory_save",
                "Simpan catatan/fakta penting ke ingatan persisten agen (bertahan antar sesi; minta izin ringan).",
                params(
                    JSONObject().put("text", prop("string", "Catatan yang disimpan")),
                    listOf("text"),
                ),
            ),
        )
        arr.put(
            toolDef(
                "memory_list",
                "Baca semua catatan di ingatan persisten agen.",
                params(JSONObject(), emptyList()),
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

    /** True bila tool termasuk keluarga upgrade-diri (hanya jalur agen). */
    fun isSelfUpgradeTool(name: String): Boolean = name.startsWith("selfupgrade_")

    /**
     * 14.25, lapis kedua gating chat: pesan penolakan bila tool
     * upgrade-diri dipanggil dari jalur chat biasa, null bila boleh.
     * Dipakai ChatRepository + SseClient.streamWithTools SEBELUM execute(),
     * sehingga model yang menghalusinasikan panggilan selfupgrade_* tetap
     * ditolak tanpa pernah mengeksekusi — dan penolakan itu tercatat di
     * Aktivitas Live lewat jalur log pemanggil (decision=error).
     * Jalur agen Manus TIDAK memakai fungsi ini: di sana selfupgrade_*
     * dijaga reducer (kartu izin HIGH/CRITICAL) + kill switch.
     */
    fun chatRefusal(name: String): String? =
        if (isSelfUpgradeTool(name)) {
            "Error: tool '$name' tidak tersedia di chat biasa — upgrade-diri " +
                "hanya lewat Mode Agen (kartu izin HIGH/CRITICAL tetap " +
                "berlaku; toggle Upgrade Diri Otomatis saja tidak cukup). " +
                "Minta user mengaktifkan Mode Agen bila memang bermaksud upgrade diri."
        } else null

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
        "github_repos", "github_runs", "github_read_file", "github_write_file",
        "github_dispatch", "telegram_send", "upin_ask", "supabase_query",
        "vercel_projects", "vercel_deployments" -> {
            val c = appCtx
            val g = gate
            val bag = connectorBag
            if (c == null || g == null || bag == null) {
                ToolResult("Error: konektor belum diinisialisasi.", true)
            } else {
                ConnectorTools.execute(name, args, c, g, bag)
            }
        }
        "selfupgrade_branch", "selfupgrade_write_file", "selfupgrade_pr",
        "selfupgrade_ci", "selfupgrade_merge", "selfupgrade_release" -> {
            val c = appCtx
            val bag = connectorBag
            if (c == null || bag == null) {
                ToolResult("Error: konektor belum diinisialisasi.", true)
            } else {
                SelfUpgradeTools.execute(name, args, c, bag)
            }
        }
        "memory_save", "memory_list" -> {
            val g = gate
            if (g == null) {
                ToolResult("Error: permission gate belum diinisialisasi.", true)
            } else {
                MemoryTools.execute(name, args, g)
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

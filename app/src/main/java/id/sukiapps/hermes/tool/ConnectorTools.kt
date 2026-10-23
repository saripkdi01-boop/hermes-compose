package id.sukiapps.hermes.tool

import android.content.Context
import id.sukiapps.hermes.KeyVault
import id.sukiapps.hermes.data.audit.DangerLevel
import id.sukiapps.hermes.data.audit.PermissionGate
import id.sukiapps.hermes.data.connectors.GitHubConnector
import id.sukiapps.hermes.data.connectors.SupabaseConnector
import id.sukiapps.hermes.data.connectors.TelegramConnector
import id.sukiapps.hermes.data.connectors.UpinConnector
import id.sukiapps.hermes.data.connectors.VercelConnector
import id.sukiapps.hermes.data.store.ExecutionLog
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * ConnectorTools — konektor Hermes sebagai tool chat (Sprint 1 Gate 3 A).
 *
 * Kredensial dibaca dari KeyVault (tidak pernah lewat argumen model).
 * Baca (repos/runs/file/Supabase/Vercel) = read-only, tercatat di
 * ExecutionLog. Tulis/dispatch/kirim = lewat fungsi *Gated konektor
 * (popup PermissionGate) atau gate.request di sini (upin_ask).
 */
object ConnectorTools {

    /** Paket konektor dari Hilt (di-attach ToolLoop dari HermesApp). */
    class Bag(
        val github: GitHubConnector,
        val telegram: TelegramConnector,
        val upin: UpinConnector,
        val supabase: SupabaseConnector,
        val vercel: VercelConnector
    )

    private fun secret(ctx: Context, key: String): String =
        KeyVault.getSecret(ctx, key).orEmpty().trim()

    private fun cap(s: String, max: Int = 20_000): String =
        if (s.length > max) s.take(max) + "\n[TRUNCATED]" else s

    suspend fun execute(
        name: String,
        args: JSONObject,
        ctx: Context,
        gate: PermissionGate,
        bag: Bag
    ): ToolResult {
        return try {
            when (name) {
                "github_repos" -> githubRepos(ctx, bag)
                "github_runs" -> githubRuns(ctx, args, bag)
                "github_read_file" -> githubReadFile(ctx, args, bag)
                "github_write_file" -> githubWriteFile(ctx, args, gate, bag)
                "github_dispatch" -> githubDispatch(ctx, args, gate, bag)
                "telegram_send" -> telegramSend(ctx, args, gate, bag)
                "upin_ask" -> upinAsk(args, gate, bag)
                "supabase_query" -> supabaseQuery(ctx, args, bag)
                "vercel_projects" -> vercelProjects(ctx, bag)
                "vercel_deployments" -> vercelDeployments(ctx, args, bag)
                else -> ToolResult("Error: connector tool '$name' tidak dikenal", true)
            }
        } catch (e: Exception) {
            ToolResult("Error: ${e.javaClass.simpleName}: ${e.message}", true)
        }
    }

    private fun pat(ctx: Context): String? =
        secret(ctx, "github_pat").ifEmpty { null }

    private suspend fun githubRepos(ctx: Context, bag: Bag): ToolResult {
        val pat = pat(ctx)
            ?: return ToolResult("Error: PAT GitHub belum diisi (Pengaturan > Konektor).", true)
        val r = bag.github.listRepos(pat)
        return r.fold(
            onSuccess = { repos ->
                ExecutionLog.log("connector", "github_repos", "${repos.size} repo", "ok")
                ToolResult(
                    if (repos.isEmpty()) "(tidak ada repo)"
                    else cap(repos.joinToString("\n"))
                )
            },
            onFailure = { ToolResult("Error: ${it.message}", true) }
        )
    }

    private suspend fun githubRuns(
        ctx: Context,
        args: JSONObject,
        bag: Bag
    ): ToolResult {
        val pat = pat(ctx)
            ?: return ToolResult("Error: PAT GitHub belum diisi (Pengaturan > Konektor).", true)
        val owner = args.optString("owner", "").trim()
        val repo = args.optString("repo", "").trim()
        if (owner.isEmpty() || repo.isEmpty()) {
            return ToolResult("Error: parameter 'owner' dan 'repo' wajib diisi", true)
        }
        val r = bag.github.latestRuns(pat, owner, repo)
        return r.fold(
            onSuccess = { runs ->
                ExecutionLog.log("connector", "github_runs", "$owner/$repo", "ok")
                ToolResult(
                    if (runs.isEmpty()) "(belum ada workflow run)"
                    else runs.joinToString("\n") {
                        "${it.name} — ${it.status}${it.conclusion?.let { c -> "/$c" } ?: ""} — ${it.url}"
                    }
                )
            },
            onFailure = { ToolResult("Error: ${it.message}", true) }
        )
    }

    private suspend fun githubReadFile(
        ctx: Context,
        args: JSONObject,
        bag: Bag
    ): ToolResult {
        val pat = pat(ctx)
            ?: return ToolResult("Error: PAT GitHub belum diisi (Pengaturan > Konektor).", true)
        val owner = args.optString("owner", "").trim()
        val repo = args.optString("repo", "").trim()
        val path = args.optString("path", "").trim()
        if (owner.isEmpty() || repo.isEmpty() || path.isEmpty()) {
            return ToolResult("Error: parameter 'owner', 'repo', 'path' wajib diisi", true)
        }
        val r = bag.github.getFileContent(pat, owner, repo, path)
        return r.fold(
            onSuccess = { content ->
                ExecutionLog.log("connector", "github_read_file", "$owner/$repo/$path", "ok")
                if (content == null) ToolResult("Error: file tidak ditemukan (404): $path", true)
                else ToolResult(cap(content))
            },
            onFailure = { ToolResult("Error: ${it.message}", true) }
        )
    }

    private suspend fun githubWriteFile(
        ctx: Context,
        args: JSONObject,
        gate: PermissionGate,
        bag: Bag
    ): ToolResult {
        val pat = pat(ctx)
            ?: return ToolResult("Error: PAT GitHub belum diisi (Pengaturan > Konektor).", true)
        val owner = args.optString("owner", "").trim()
        val repo = args.optString("repo", "").trim()
        val path = args.optString("path", "").trim()
        if (owner.isEmpty() || repo.isEmpty() || path.isEmpty() || !args.has("content")) {
            return ToolResult("Error: parameter 'owner', 'repo', 'path', 'content' wajib diisi", true)
        }
        val content = args.optString("content", "")
        val message = args.optString("message", "Update via Hermes").ifBlank { "Update via Hermes" }
        val r = bag.github.putContentsGated(gate, pat, owner, repo, path, content, message)
        return r.fold(
            onSuccess = {
                ExecutionLog.log("connector", "github_write_file", "$owner/$repo/$path", "ok")
                ToolResult("OK: $path tertulis di $owner/$repo@main")
            },
            onFailure = { ToolResult("Error: ${it.message}", true) }
        )
    }

    private suspend fun githubDispatch(
        ctx: Context,
        args: JSONObject,
        gate: PermissionGate,
        bag: Bag
    ): ToolResult {
        val pat = pat(ctx)
            ?: return ToolResult("Error: PAT GitHub belum diisi (Pengaturan > Konektor).", true)
        val owner = args.optString("owner", "").trim()
        val repo = args.optString("repo", "").trim()
        val workflow = args.optString("workflow", "").trim()
        if (owner.isEmpty() || repo.isEmpty() || workflow.isEmpty()) {
            return ToolResult("Error: parameter 'owner', 'repo', 'workflow' wajib diisi", true)
        }
        val ref = args.optString("ref", "main").ifBlank { "main" }
        val r = bag.github.triggerDispatchGated(gate, pat, owner, repo, workflow, ref)
        return r.fold(
            onSuccess = {
                ExecutionLog.log("connector", "github_dispatch", "$owner/$repo $workflow@$ref", "ok")
                ToolResult("OK: workflow $workflow terpicu di $owner/$repo@$ref")
            },
            onFailure = { ToolResult("Error: ${it.message}", true) }
        )
    }

    private suspend fun telegramSend(
        ctx: Context,
        args: JSONObject,
        gate: PermissionGate,
        bag: Bag
    ): ToolResult {
        val token = secret(ctx, "tg_bot_token")
        val chatId = secret(ctx, "tg_chat_id")
        if (token.isEmpty() || chatId.isEmpty()) {
            return ToolResult("Error: token bot/chat ID Telegram belum diisi (Pengaturan > Konektor).", true)
        }
        val text = args.optString("text", "")
        if (text.isBlank()) return ToolResult("Error: parameter 'text' wajib diisi", true)
        val r = bag.telegram.sendGated(gate, token, chatId, text)
        return r.fold(
            onSuccess = {
                ExecutionLog.log("connector", "telegram_send", "chat $chatId", "ok")
                ToolResult("OK: pesan terkirim ke Telegram chat $chatId")
            },
            onFailure = { ToolResult("Error: ${it.message}", true) }
        )
    }

    private suspend fun upinAsk(
        args: JSONObject,
        gate: PermissionGate,
        bag: Bag
    ): ToolResult {
        val q = args.optString("question", "").trim()
        if (q.isEmpty()) return ToolResult("Error: parameter 'question' wajib diisi", true)
        val granted = gate.request("Upin", "Kirim ke Upin:\n${q.take(200)}", DangerLevel.MEDIUM)
        if (!granted) return ToolResult("Ditolak: izin Upin ditolak user.", true)
        val jobId = bag.upin.sendMessage(q)
        val deadline = System.currentTimeMillis() + 100_000
        while (true) {
            val (status, answer) = bag.upin.pollReply(jobId)
            if (status == "done") {
                ExecutionLog.log("connector", "upin_ask", q.take(120), "ok")
                return ToolResult(cap(answer?.trim()?.ifBlank { "(jawaban kosong)" } ?: "(jawaban kosong)"))
            }
            if (status == "unknown") {
                return ToolResult("Error: Upin tidak mengenal job $jobId.", true)
            }
            if (System.currentTimeMillis() >= deadline) {
                return ToolResult(
                    "(Upin belum selesai menjawab dalam ±100 detik. Job: $jobId — " +
                        "tanyakan lagi nanti atau pakai tombol Tanya Upin di UI.)"
                )
            }
            delay(5000)
        }
    }

    private suspend fun supabaseQuery(
        ctx: Context,
        args: JSONObject,
        bag: Bag
    ): ToolResult {
        val url = secret(ctx, "supabase_url")
        val key = secret(ctx, "supabase_key")
        if (url.isEmpty() || key.isEmpty()) {
            return ToolResult("Error: URL/key Supabase belum diisi (Pengaturan > Konektor).", true)
        }
        val table = args.optString("table", "").trim()
        if (table.isEmpty()) return ToolResult("Error: parameter 'table' wajib diisi", true)
        val limit = args.optInt("limit", 10)
        val r = bag.supabase.queryTable(url, key, table, limit)
        return r.fold(
            onSuccess = {
                ExecutionLog.log("connector", "supabase_query", table, "ok")
                ToolResult(cap(it))
            },
            onFailure = { ToolResult("Error: ${it.message}", true) }
        )
    }

    private suspend fun vercelProjects(ctx: Context, bag: Bag): ToolResult {
        val token = secret(ctx, "vercel_token")
        if (token.isEmpty()) {
            return ToolResult("Error: token Vercel belum diisi (Pengaturan > Konektor).", true)
        }
        val r = bag.vercel.listProjects(token)
        return r.fold(
            onSuccess = {
                ExecutionLog.log("connector", "vercel_projects", "${it.size} project", "ok")
                ToolResult(if (it.isEmpty()) "(tidak ada project)" else it.joinToString("\n"))
            },
            onFailure = { ToolResult("Error: ${it.message}", true) }
        )
    }

    private suspend fun vercelDeployments(
        ctx: Context,
        args: JSONObject,
        bag: Bag
    ): ToolResult {
        val token = secret(ctx, "vercel_token")
        if (token.isEmpty()) {
            return ToolResult("Error: token Vercel belum diisi (Pengaturan > Konektor).", true)
        }
        val projectId = args.optString("project_id", "").trim()
        if (projectId.isEmpty()) {
            return ToolResult("Error: parameter 'project_id' wajib diisi (lihat vercel_projects)", true)
        }
        val r = bag.vercel.listDeployments(token, projectId)
        return r.fold(
            onSuccess = {
                ExecutionLog.log("connector", "vercel_deployments", projectId, "ok")
                ToolResult(if (it.isEmpty()) "(belum ada deployment)" else it.joinToString("\n"))
            },
            onFailure = { ToolResult("Error: ${it.message}", true) }
        )
    }
}

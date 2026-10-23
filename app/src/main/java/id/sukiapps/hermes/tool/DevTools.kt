package id.sukiapps.hermes.tool

import android.content.Context
import android.os.Build
import id.sukiapps.hermes.data.audit.DangerLevel
import id.sukiapps.hermes.data.audit.PermissionGate
import id.sukiapps.hermes.data.store.ExecutionLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * DevTools — tool developer Hermes (Sprint 1 Gate 2 slice 2).
 *
 * Semua aksi lewat [PermissionGate] (popup izin global; HIGH selalu popup).
 * Shell & file HANYA di sandbox `filesDir/terminal-sandbox` — path di luar
 * sandbox ditolak via cek canonical. net_scan hanya ke host allowlist scan
 * (SharedPreferences "hermes_tools"/scan_allowlist; default localhost).
 * code_run: isolasi jaringan per-proses tidak tersedia di Android —
 * yang diisolasi adalah filesystem (cwd sandbox) + timeout 10 detik.
 */
object DevTools {

    private val DESTRUCTIVE = Regex(
        """(^|\s)(rm|dd|mkfs|shutdown|reboot|su|kill|chown)(\s|$)|>\s*/|chmod\s+777"""
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    fun sandbox(ctx: Context): File =
        File(ctx.filesDir, "terminal-sandbox").apply { mkdirs() }

    private fun scanHosts(ctx: Context): Set<String> {
        val set = ToolAllowlist.scanCurrent(ctx)
        return if (set.isEmpty()) setOf("localhost", "127.0.0.1") else set
    }

    private fun resolveInSandbox(ctx: Context, rel: String): File? {
        val sb = sandbox(ctx).canonicalFile
        val f = File(sb, rel).canonicalFile
        return if (f.path == sb.path || f.path.startsWith(sb.path + File.separator)) f else null
    }

    suspend fun execute(
        name: String,
        args: JSONObject,
        ctx: Context,
        gate: PermissionGate
    ): ToolResult = withContext(Dispatchers.IO) {
        try {
            when (name) {
                "shell_exec" -> shellExec(args, ctx, gate)
                "file_read" -> fileRead(args, ctx, gate)
                "file_write" -> fileWrite(args, ctx, gate)
                "net_scan" -> netScan(args, ctx, gate)
                "conn_health" -> connHealth(args, ctx, gate)
                "code_run" -> codeRun(args, ctx, gate)
                "system_info" -> systemInfo(ctx, gate)
                else -> ToolResult("Error: dev tool '$name' tidak dikenal", true)
            }
        } catch (e: Exception) {
            ToolResult("Error: ${e.javaClass.simpleName}: ${e.message}", true)
        }
    }

    private suspend fun denied(
        gate: PermissionGate,
        type: String,
        detail: String,
        level: DangerLevel
    ): Boolean = !gate.request(type, detail, level)

    private suspend fun shellExec(
        args: JSONObject,
        ctx: Context,
        gate: PermissionGate
    ): ToolResult {
        val cmd = args.optString("command", "").trim()
        if (cmd.isEmpty()) return ToolResult("Error: parameter 'command' wajib diisi", true)
        val level = if (DESTRUCTIVE.containsMatchIn(cmd)) DangerLevel.HIGH else DangerLevel.MEDIUM
        if (denied(gate, "shell_exec", "Perintah shell (sandbox):\n$cmd", level)) {
            return ToolResult("Ditolak: izin shell_exec ditolak user.", true)
        }
        val res = runShell(ctx, cmd, 30)
        ExecutionLog.log("devtool", "shell_exec", cmd.take(200), if (res.isError) "fail" else "ok")
        return res
    }

    private suspend fun codeRun(
        args: JSONObject,
        ctx: Context,
        gate: PermissionGate
    ): ToolResult {
        val code = args.optString("code", "")
        if (code.isBlank()) return ToolResult("Error: parameter 'code' wajib diisi", true)
        if (denied(gate, "code_run", "Jalankan kode shell (sandbox, 10 dtk):\n${code.take(300)}",
                DangerLevel.HIGH)) {
            return ToolResult("Ditolak: izin code_run ditolak user.", true)
        }
        val f = File(sandbox(ctx), "code_run.sh")
        f.writeText(code)
        val res = runShell(ctx, "sh code_run.sh", 10)
        ExecutionLog.log("devtool", "code_run", code.take(200), if (res.isError) "fail" else "ok")
        val note = "\n[Catatan: isolasi jaringan per-proses tidak tersedia di Android; " +
            "isolasi berlaku untuk filesystem sandbox + timeout.]"
        return ToolResult(res.content + note, res.isError)
    }

    private fun runShell(ctx: Context, command: String, timeoutSec: Long): ToolResult {
        val sb = sandbox(ctx)
        val pb = ProcessBuilder("/system/bin/sh", "-c", command)
            .directory(sb)
            .redirectErrorStream(true)
        val env = pb.environment()
        env.clear()
        env["PATH"] = "/system/bin:/system/xbin"
        env["HOME"] = sb.absolutePath
        env["TMPDIR"] = sb.absolutePath
        val proc = pb.start()
        val out = ByteArrayOutputStream()
        val reader = Thread {
            try {
                val buf = ByteArray(8192)
                while (true) {
                    val n = proc.inputStream.read(buf)
                    if (n <= 0) break
                    synchronized(out) {
                        val room = 64 * 1024 - out.size()
                        if (room > 0) out.write(buf, 0, min(n, room))
                    }
                }
            } catch (_: Exception) {
            }
        }
        reader.start()
        val done = proc.waitFor(timeoutSec, TimeUnit.SECONDS)
        if (!done) {
            proc.destroyForcibly()
            reader.join(500)
            val partial = out.toString("UTF-8").take(4000)
            return ToolResult("Error: timeout ${timeoutSec} dtk, proses dibunuh. Output parsial:\n$partial", true)
        }
        reader.join(1000)
        val text = out.toString("UTF-8")
        val body = if (text.length > 20_000) {
            text.take(20_000) + "\n[TRUNCATED: output dipotong 20.000 karakter]"
        } else {
            text
        }
        return ToolResult("EXIT: ${proc.exitValue()}\n---\n$body")
    }

    private suspend fun fileRead(
        args: JSONObject,
        ctx: Context,
        gate: PermissionGate
    ): ToolResult {
        val rel = args.optString("path", "").trim()
        if (rel.isEmpty()) return ToolResult("Error: parameter 'path' wajib diisi", true)
        val f = resolveInSandbox(ctx, rel)
            ?: return ToolResult("Error: path di luar sandbox ditolak: $rel", true)
        if (denied(gate, "file_read", "Baca file sandbox: $rel", DangerLevel.MEDIUM)) {
            return ToolResult("Ditolak: izin file_read ditolak user.", true)
        }
        if (!f.exists() || !f.isFile) return ToolResult("Error: file tidak ada: $rel", true)
        val text = f.readText(Charsets.UTF_8)
        val body = if (text.length > 20_000) {
            text.take(20_000) + "\n[TRUNCATED]"
        } else {
            text
        }
        ExecutionLog.log("devtool", "file_read", rel, "ok")
        return ToolResult("FILE: $rel (${text.length} karakter)\n---\n$body")
    }

    private suspend fun fileWrite(
        args: JSONObject,
        ctx: Context,
        gate: PermissionGate
    ): ToolResult {
        val rel = args.optString("path", "").trim()
        if (rel.isEmpty()) return ToolResult("Error: parameter 'path' wajib diisi", true)
        if (!args.has("content")) return ToolResult("Error: parameter 'content' wajib diisi", true)
        val content = args.optString("content", "")
        val f = resolveInSandbox(ctx, rel)
            ?: return ToolResult("Error: path di luar sandbox ditolak: $rel", true)
        if (denied(gate, "file_write", "Tulis file sandbox: $rel (${content.length} karakter)",
                DangerLevel.MEDIUM)) {
            return ToolResult("Ditolak: izin file_write ditolak user.", true)
        }
        f.parentFile?.mkdirs()
        f.writeText(content, Charsets.UTF_8)
        ExecutionLog.log("devtool", "file_write", "$rel (${content.length} kar)", "ok")
        return ToolResult("OK: $rel tertulis (${content.toByteArray(Charsets.UTF_8).size} byte)")
    }

    private suspend fun netScan(
        args: JSONObject,
        ctx: Context,
        gate: PermissionGate
    ): ToolResult {
        val host = args.optString("host", "").trim().lowercase()
        if (host.isEmpty()) return ToolResult("Error: parameter 'host' wajib diisi", true)
        val allowed = scanHosts(ctx)
        if (!ToolAllowlist.hostAllowed(host, allowed)) {
            return ToolResult(
                "Error: host '$host' tidak ada di allowlist scan " +
                    "(default: localhost, 127.0.0.1; ubah via key scan_allowlist).",
                true
            )
        }
        val portsArr = args.optJSONArray("ports")
        val ports = mutableListOf<Int>()
        if (portsArr != null) {
            for (i in 0 until portsArr.length()) ports.add(portsArr.optInt(i, -1))
        }
        val usePorts = ports.filter { it in 1..65535 }.distinct().take(32)
            .ifEmpty { listOf(22, 80, 443, 8080, 8443) }
        if (denied(gate, "net_scan", "TCP scan $host port ${usePorts.joinToString(",")}",
                DangerLevel.MEDIUM)) {
            return ToolResult("Ditolak: izin net_scan ditolak user.", true)
        }
        val lines = mutableListOf<String>()
        for (port in usePorts) {
            val open = try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(host, port), 600)
                    true
                }
            } catch (_: Exception) {
                false
            }
            lines.add("$host:$port -> ${if (open) "OPEN" else "closed/timeout"}")
        }
        ExecutionLog.log("devtool", "net_scan", "$host ${usePorts.size} port", "ok")
        return ToolResult(lines.joinToString("\n"))
    }

    private suspend fun connHealth(
        args: JSONObject,
        ctx: Context,
        gate: PermissionGate
    ): ToolResult {
        var url = args.optString("url", "").trim()
        if (url.isEmpty()) return ToolResult("Error: parameter 'url' wajib diisi", true)
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase()
            ?: return ToolResult("Error: URL tidak punya host: $url", true)
        if (!ToolAllowlist.hostAllowed(host, scanHosts(ctx))) {
            return ToolResult("Error: host '$host' tidak ada di allowlist scan.", true)
        }
        if (denied(gate, "conn_health", "Health check: $url", DangerLevel.LOW)) {
            return ToolResult("Ditolak: izin conn_health ditolak user.", true)
        }
        val t0 = System.currentTimeMillis()
        return try {
            http.newCall(Request.Builder().url(url).build()).execute().use { res ->
                val ms = System.currentTimeMillis() - t0
                ExecutionLog.log("devtool", "conn_health", "$url -> ${res.code}", "ok")
                ToolResult(
                    "STATUS: ${res.code}\nMS: $ms\nSERVER: ${res.header("Server") ?: "-"}\nURL: $url"
                )
            }
        } catch (e: Exception) {
            ToolResult("Error: ${e.javaClass.simpleName}: ${e.message}", true)
        }
    }

    private suspend fun systemInfo(
        ctx: Context,
        gate: PermissionGate
    ): ToolResult {
        if (denied(gate, "system_info", "Baca info sistem (read-only)", DangerLevel.LOW)) {
            return ToolResult("Ditolak: izin system_info ditolak user.", true)
        }
        val sb = sandbox(ctx)
        val files = sb.listFiles()?.take(50)?.joinToString(", ") { it.name } ?: ""
        val rt = Runtime.getRuntime()
        val text = buildString {
            appendLine("MODEL: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("ANDROID: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString(",")}")
            appendLine("MEMORI: terpakai ${(rt.totalMemory() - rt.freeMemory()) / 1_048_576} MB / maks ${rt.maxMemory() / 1_048_576} MB")
            appendLine("SANDBOX: ${sb.absolutePath} (bebas ${sb.freeSpace / 1_048_576} MB)")
            appendLine("ISI SANDBOX: ${files.ifEmpty { "(kosong)" }}")
        }
        ExecutionLog.log("devtool", "system_info", "read-only", "ok")
        return ToolResult(text)
    }
}

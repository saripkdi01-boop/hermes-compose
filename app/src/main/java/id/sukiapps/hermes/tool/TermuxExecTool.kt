package id.sukiapps.hermes.tool

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import id.sukiapps.hermes.data.audit.DangerLevel
import id.sukiapps.hermes.data.connectors.TermuxConnector
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * termux_exec — jalankan satu perintah shell di aplikasi Termux dan
 * kembalikan output-nya (masterprompt §5.1: tool disuntikkan, bukan
 * hardcode; arahan pemilik 13 Nov 2026: Hermes memakai full Linux
 * Termux, bukan menanam Linux di APK).
 *
 * MEKANISME (kontrak RUN_COMMAND, diverifikasi dari wiki resmi Termux
 * + TermuxConstants termux-shared master — lihat TERMUX-TOOLS-REPORT.md):
 * - Intent `com.termux.RUN_COMMAND` dikirim via `startService()` ke
 *   komponen `com.termux.app.RunCommandService`.
 * - Perintah dijalankan sebagai `bash -c "<command>"` (bash Termux di
 *   `/data/data/com.termux/files/usr/bin/bash`), mode BACKGROUND=true —
 *   jadi tidak membuka sesi terminal dan hasil stdout/stderr terpisah.
 * - Hasil kembali lewat PendingIntent (butuh Termux >= 0.109) ke
 *   [TermuxResultReceiver]; tool menunggu hasilnya dengan timeout.
 * - RUN_COMMAND itu sendiri fire-and-forget: TANPA PendingIntent tidak
 *   ada output yang kembali. Tool ini TIDAK berpura-pura intent
 *   mengembalikan output langsung.
 *
 * PRASYARAT (di luar kode — harus dipenuhi user sekali, lihat laporan):
 * - Aplikasi Termux terpasang; izin `com.termux.permission.RUN_COMMAND`
 *   diberikan ke Hermes (Pengaturan > Aplikasi > Hermes > Izin >
 *   Izin tambahan); `allow-external-apps=true` di
 *   `~/.termux/termux.properties` di Termux.
 * - Manifest sudah memenuhi keduanya sejak v9.1 (permission +
 *   `<queries>` untuk `com.termux`); tool ini hanya menambah receiver
 *   hasil (lihat AndroidManifest.xml).
 *
 * KEAMANAN (§5.2): `requiresApproval = true` + gate HIGH (selalu popup,
 * tanpa opsi "ingat") — TIDAK ADA perintah yang jalan tanpa persetujuan
 * eksplisit user. Perintah berjalan di lingkungan Termux MILIK USER
 * sendiri (bukan sandbox Hermes): ia bisa mengubah/menghapus file user,
 * menginstal paket, dan mengakses jaringan. Gate persetujuan + tampilan
 * perintah mentah di dialog adalah mitigasi utamanya. Tool ini TIDAK
 * menangani kredensial/token/API key; jangan pernah mengirimnya sebagai
 * bagian perintah.
 *
 * BATAS: perintah maks 4.000 karakter (jauh di bawah batas intent
 * Android ~500 KB dan batas string perintah Termux ~128 KB); output
 * diformat maks 100.000 karakter (Termux sendiri memotong stdout+stderr
 * gabungan maks 100 KB); timeout default 60 dtk (5–300 dtk). Bila timeout
 * habis, perintah MUNGKIN masih berjalan di Termux (tool tidak bisa
 * membatalkannya dari sini) — pesan timeout menjelaskannya.
 */
class TermuxExecTool : HermesTool {

    override val id: String = "termux_exec"
    override val name: String = "Jalankan Perintah Termux"
    override val description: String =
        "Jalankan satu perintah shell di aplikasi Termux (lingkungan Linux " +
            "lengkap di HP ini) dan kembalikan output-nya. Gunakan untuk " +
            "build, instal paket (mis. \"pkg install -y git\"), menjalankan " +
            "server/script, atau perintah Linux lain yang tidak bisa dilakukan " +
            "sandbox Hermes. Perintah dijalankan sebagai \"bash -c\" di " +
            "Termux; butuh persetujuan user setiap kali. Output dibatasi " +
            "100.000 karakter. Termux harus terpasang dan izin RUN_COMMAND " +
            "harus diberikan (lihat Pengaturan > Tool Web)."
    override val inputSchema: JSONObject = toolInputSchema(
        JSONObject()
            .put(
                "command",
                stringProp(
                    "Perintah shell lengkap untuk dijalankan di Termux, " +
                        "mis. \"pkg install -y git\" atau \"ls -la ~/proyek\". " +
                        "Maks 4000 karakter.",
                ),
            )
            .put(
                "workdir",
                stringProp(
                    "Direktori kerja di Termux (opsional; kosong = home " +
                        "Termux \"~/\"). Boleh memakai awalan \"~/\" atau " +
                        "\"\$PREFIX/\".",
                ),
            )
            .put(
                "timeout",
                integerProp(
                    "Batas tunggu hasil dalam detik (opsional; default 60, " +
                        "min 5, maks 300).",
                ),
            ),
        listOf("command"),
    )
    override val capabilities: Set<ToolCapability> = setOf(ToolCapability.EXECUTE)
    override val requiresApproval: Boolean = true

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val command = args.optString("command", "").trim()
        if (command.isEmpty()) {
            return ToolResult("Error: parameter 'command' wajib diisi", true)
        }
        if (command.length > MAX_COMMAND_CHARS) {
            return ToolResult(
                "Error: perintah terlalu panjang (${command.length} karakter, " +
                    "maks $MAX_COMMAND_CHARS)",
                true,
            )
        }
        val workdir = args.optString("workdir", "").trim()
        val timeoutSec = args.optInt("timeout", DEFAULT_TIMEOUT_SEC)
            .coerceIn(MIN_TIMEOUT_SEC, MAX_TIMEOUT_SEC)

        val appCtx = ctx.appContext
        val gate = ctx.gate
        if (appCtx == null || gate == null) {
            return ToolResult(
                "Error: termux_exec belum diinisialisasi " +
                    "(konteks aplikasi belum dipasang).",
                true,
            )
        }
        if (!isTermuxInstalled(appCtx)) {
            return ToolResult(
                "Error: aplikasi Termux belum terpasang di HP ini. Instal " +
                    "Termux dulu (F-Droid/GitHub), lalu coba lagi.",
                true,
            )
        }

        val executionId = TermuxResultStore.register()
        try {
            // PendingIntent hasil: eksplisit ke TermuxResultReceiver
            // (manifest, exported=false). requestCode = id eksekusi agar
            // tiap PendingIntent unik. FLAG_IMMUTABLE wajib untuk
            // targetSdk 31+; hasil dikirim Termux sebagai intent terpisah
            // lewat pendingIntent.send(), bukan dengan mengubah intent ini.
            val resultIntent = Intent(appCtx, TermuxResultReceiver::class.java)
                .putExtra(TermuxResultReceiver.EXTRA_EXECUTION_ID, executionId)
            val pendingIntent = PendingIntent.getBroadcast(
                appCtx,
                executionId,
                resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            // Intent RUN_COMMAND (kontrak resmi Termux — startService ke
            // RunCommandService, BUKAN broadcast; lihat laporan).
            val runIntent = Intent(TermuxConnector.ACTION_RUN_COMMAND).apply {
                component = ComponentName(
                    TERMUX_PACKAGE,
                    TermuxConnector.RUN_COMMAND_SERVICE_NAME,
                )
                putExtra(TermuxConnector.EXTRA_PATH, TERMUX_BASH_PATH)
                putExtra(TermuxConnector.EXTRA_ARGUMENTS, arrayOf("-c", command))
                if (workdir.isNotEmpty()) {
                    putExtra(TermuxConnector.EXTRA_WORKDIR, workdir)
                }
                // Background=true: tanpa sesi terminal, stdout/stderr
                // terpisah di result bundle. Session action tidak relevan
                // untuk perintah background (hanya untuk foreground).
                putExtra(TermuxConnector.EXTRA_BACKGROUND, true)
                putExtra(TermuxConnector.EXTRA_PENDING_INTENT, pendingIntent)
            }

            // Gate HIGH (selalu popup, tanpa "ingat") — pola yang sama
            // dengan TermuxConnector.sendGated. Perintah mentah ditampilkan
            // agar user meninjau sebelum menyetujui.
            val rawDetail = buildString {
                appendLine("Perintah Termux (bash -c):")
                appendLine(command.take(GATE_DETAIL_MAX_CHARS))
                appendLine("workdir: ${workdir.ifEmpty { "~ (home Termux)" }}")
                append("timeout: ${timeoutSec}s")
            }
            if (!gate.request("Termux", rawDetail, DangerLevel.HIGH)) {
                return ToolResult("Ditolak: izin termux_exec ditolak user.", true)
            }

            try {
                appCtx.startService(runIntent)
            } catch (e: Exception) {
                return ToolResult(
                    "Error: gagal mengirim perintah ke Termux " +
                        "(${e.javaClass.simpleName}: ${e.message}). " +
                        "Periksa: izin RUN_COMMAND Hermes sudah diberikan " +
                        "(Pengaturan > Aplikasi > Hermes > Izin > Izin " +
                        "tambahan) dan allow-external-apps=true di " +
                        "~/.termux/termux.properties.",
                    true,
                )
            }

            val outcome = try {
                withTimeout(timeoutSec * 1_000L) {
                    TermuxResultStore.await(executionId)
                }
            } catch (e: TimeoutCancellationException) {
                return ToolResult(
                    "Error: waktu habis setelah ${timeoutSec}s menunggu " +
                        "hasil dari Termux. Perintah MUNGKIN masih berjalan " +
                        "di Termux — cek di aplikasi Termux; coba lagi " +
                        "dengan timeout lebih besar bila perlu.",
                    true,
                )
            }
            return formatTermuxResult(outcome)
        } finally {
            TermuxResultStore.cancel(executionId)
        }
    }

    companion object {
        /** Paket aplikasi Termux (manifest <queries> sudah memuatnya). */
        const val TERMUX_PACKAGE = "com.termux"

        /** Bash Termux ($PREFIX/bin/bash) — path absolut standar Termux. */
        const val TERMUX_BASH_PATH = "/data/data/com.termux/files/usr/bin/bash"

        /** Batas panjang perintah (jauh di bawah batas intent ~500 KB). */
        const val MAX_COMMAND_CHARS = 4_000

        /** Batas karakter perintah yang ditampilkan di dialog izin. */
        const val GATE_DETAIL_MAX_CHARS = 500

        /** Timeout default (detik) bila argumen timeout tidak diberikan. */
        const val DEFAULT_TIMEOUT_SEC = 60

        /** Timeout minimum/maksimum (detik) yang diizinkan. */
        const val MIN_TIMEOUT_SEC = 5
        const val MAX_TIMEOUT_SEC = 300

        /** Batas karakter hasil format untuk LLM. */
        const val MAX_RESULT_CHARS = 100_000

        /** true bila aplikasi Termux terpasang (butuh <queries> com.termux). */
        fun isTermuxInstalled(ctx: Context): Boolean = try {
            @Suppress("DEPRECATION")
            ctx.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }

        /**
         * Format hasil Termux menjadi [ToolResult] (fungsi murni — bisa
         * di-unit-test di JVM tanpa Android).
         *
         * - `err != -1` berarti error INTERNAL Termux (bukan perintahnya):
         *   isError=true dengan errmsg.
         * - Exit code bukan 0 BUKAN error tool (perintahnya jalan, hanya
         *   melaporkan gagal) — isError=false, tapi exit code ditampilkan
         *   jelas di baris pertama agar LLM membacanya.
         */
        fun formatTermuxResult(
            outcome: TermuxExecOutcome,
            maxChars: Int = MAX_RESULT_CHARS,
        ): ToolResult {
            if (outcome.err != -1) {
                val msg = outcome.errmsg?.takeIf { it.isNotBlank() }
                    ?: "Termux melaporkan error internal (err=${outcome.err})."
                return ToolResult("Error: $msg", true)
            }
            val body = buildString {
                appendLine("[termux_exec] exit code: ${outcome.exitCode}")
                if (outcome.exitCode != 0) {
                    appendLine("Perintah selesai dengan exit code bukan 0.")
                }
                val stdoutTruncated = outcome.stdoutOriginalLength
                    ?.toIntOrNull()
                    ?.let { it > outcome.stdout.length } == true
                val stderrTruncated = outcome.stderrOriginalLength
                    ?.toIntOrNull()
                    ?.let { it > outcome.stderr.length } == true
                appendLine("STDOUT:")
                appendLine(outcome.stdout.ifEmpty { "(kosong)" })
                if (stdoutTruncated) {
                    appendLine(
                        "(stdout dipotong Termux; panjang asli " +
                            "${outcome.stdoutOriginalLength} karakter)",
                    )
                }
                appendLine("STDERR:")
                appendLine(outcome.stderr.ifEmpty { "(kosong)" })
                if (stderrTruncated) {
                    appendLine(
                        "(stderr dipotong Termux; panjang asli " +
                            "${outcome.stderrOriginalLength} karakter)",
                    )
                }
            }
            val text = if (body.length > maxChars) {
                body.take(maxChars) +
                    "\n... (output dipotong Hermes; maks $maxChars karakter)"
            } else {
                body
            }
            return ToolResult(text)
        }
    }
}

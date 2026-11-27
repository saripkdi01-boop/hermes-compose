package id.sukiapps.hermes.data.connectors

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import id.sukiapps.hermes.data.audit.DangerLevel
import id.sukiapps.hermes.data.audit.PermissionGate

/**
 * Konektor Termux — AKSI MANUAL via tombol di Pengaturan.
 * BUKAN dipakai agen otomatis.
 *
 * ATURAN KERAS: setiap eksekusi WAJIB lewat PermissionGate (HIGH — selalu
 * popup, tanpa opsi ingat). [send] bersifat private; satu-satunya jalan
 * publik adalah [sendGated]. Inilah anti-bypass-nya.
 * Memerlukan aplikasi Termux:API terinstal + permission
 * com.termux.permission.RUN_COMMAND (lihat AndroidManifest).
 */
object TermuxConnector {
    const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"

    /**
     * Tambahan untuk tool registry `termux_exec` (bukan untuk konektor
     * manual di atas). Nilai string di bawah adalah nilai LITERAL dari
     * `TermuxConstants` (termux-shared, master, repo termux/termux-app) —
     * diverifikasi dari sumber resmi, bukan tebakan. Lihat
     * TERMUX-TOOLS-REPORT.md untuk sumber + tanggal verifikasi.
     *
     * - Nama service penerima RUN_COMMAND (dipakai setClassName).
     * - EXTRA_PENDING_INTENT: PendingIntent untuk menerima hasil kembali.
     *   Wajib Termux >= 0.109; tanpa ini perintah fire-and-forget.
     * - Kunci result bundle (TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_*):
     *   bundle "result" berisi stdout/stderr/exitCode/err/errmsg.
     *   Perintah background (BACKGROUND=true) mengembalikan stdout dan
     *   stderr TERPISAH; perintah foreground mengembalikan transkrip sesi.
     */
    const val RUN_COMMAND_SERVICE_NAME = "com.termux.app.RunCommandService"
    const val TERMUX_PACKAGE = "com.termux"
    const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
    const val RESULT_BUNDLE_KEY = "result"
    const val RESULT_STDOUT = "stdout"
    const val RESULT_STDOUT_ORIGINAL_LENGTH = "stdout_original_length"
    const val RESULT_STDERR = "stderr"
    const val RESULT_STDERR_ORIGINAL_LENGTH = "stderr_original_length"
    const val RESULT_EXIT_CODE = "exitCode"
    const val RESULT_ERR = "err"
    const val RESULT_ERRMSG = "errmsg"

    /** true bila aplikasi Termux:API terinstal. */
    fun isApiInstalled(ctx: Context): Boolean = try {
        @Suppress("DEPRECATION")
        ctx.packageManager.getPackageInfo("com.termux.api", 0)
        true
    } catch (e: Exception) {
        false
    }

    /**
     * Bangun intent mentah. Panggil [rawCommand] untuk teks konfirmasi,
     * tampilkan ke user, dan HANYA kirim via [send] setelah user menekan
     * "Jalankan" di dialog.
     */
    fun buildIntent(
        path: String,
        args: List<String>,
        workdir: String,
        background: Boolean
    ): Intent = Intent(ACTION_RUN_COMMAND).apply {
        // v19.1 (audit P0, keamanan): intent EKSPLISIT ke RunCommandService
        // Termux. Dulu intent implisit dikirim sebagai broadcast tanpa
        // package: (1) Termux tidak menerima RUN_COMMAND sebagai broadcast
        // (kontraknya startService) sehingga konektor manual tidak pernah
        // jalan, dan (2) aplikasi lain yang mendaftarkan receiver untuk
        // action yang sama bisa menerima perintah shell user (berikut
        // token/path di dalamnya). Pola sama dengan tool termux_exec.
        component = ComponentName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE_NAME)
        putExtra(EXTRA_PATH, path)
        putExtra(EXTRA_ARGUMENTS, args.toTypedArray())
        putExtra(EXTRA_WORKDIR, workdir)
        putExtra(EXTRA_BACKGROUND, background)
    }

    /** Representasi mentah perintah untuk dialog konfirmasi. */
    fun rawCommand(
        path: String,
        args: List<String>,
        workdir: String,
        background: Boolean
    ): String = buildString {
        appendLine("path: $path")
        appendLine("args: ${args.joinToString(" ")}")
        appendLine("workdir: $workdir")
        append("background: $background")
    }

    /**
     * Kirim ke RunCommandService Termux (intent eksplisit). Private — hanya
     * via [sendGated]. Gagal (Termux tak terpasang / izin RUN_COMMAND belum
     * diberikan) = false, bukan crash.
     */
    private fun send(ctx: Context, intent: Intent): Boolean = try {
        ctx.startService(intent) != null
    } catch (e: Exception) {
        false
    }

    /**
     * Chokepoint publik: kirim ke Termux — SELALU popup izin (HIGH),
     * tanpa opsi "ingat". Menolak = tidak jalan + tercatat di audit.
     * @return true bila diizinkan & dikirim.
     */
    suspend fun sendGated(
        ctx: Context,
        gate: PermissionGate,
        intent: Intent,
        rawDetail: String
    ): Boolean {
        val ok = gate.request("Termux", rawDetail, DangerLevel.HIGH)
        if (!ok) return false
        // Hanya intent eksplisit ke Termux yang boleh lewat chokepoint ini.
        if (intent.component?.packageName != TERMUX_PACKAGE) return false
        return send(ctx, intent)
    }
}

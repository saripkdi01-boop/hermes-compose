package id.sukiapps.hermes.data.connectors

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

    /** Kirim broadcast ke Termux:API. Private — hanya via [sendGated]. */
    private fun send(ctx: Context, intent: Intent) {
        ctx.sendBroadcast(intent)
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
        if (ok) send(ctx, intent)
        return ok
    }
}

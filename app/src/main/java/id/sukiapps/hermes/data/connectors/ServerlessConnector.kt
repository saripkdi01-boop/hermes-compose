package id.sukiapps.hermes.data.connectors

import android.content.Context
import android.content.Intent
import id.sukiapps.hermes.data.audit.PermissionGate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Konektor Telegram Serverless — deploy backend bot JS ke infrastruktur
 * Telegram via CLI `tgcloud` (npm @tgcloud/cli, butuh Node 18+) yang jalan
 * di Termux. Ini = orkestrasi command Termux, BUKAN HTTP API langsung
 * (protokol tgcloud tidak publik; jangan coba reverse-engineer).
 *
 * Batas jujur: bot Python existing (mis. SUKI Digital) TIDAK bisa migrasi ke
 * Serverless — Serverless JS-only. Konektor ini untuk project JS baru.
 * Login (`npx tgcloud login`) dilakukan manual sekali di Termux (tempel CLI
 * access token dari BotFather → bot → Serverless → CLI Access); token
 * dipegang Termux, TIDAK disimpan di app.
 */
@Singleton
class ServerlessConnector @Inject constructor() {

    enum class Action(val label: String) {
        CHECK_INSTALL("Cek instalasi"),
        STATUS("Status"),
        PUSH("Push"),
        MIGRATE("Migrate")
    }

    /** Command shell Termux untuk tiap aksi. */
    fun commandFor(action: Action): String = when (action) {
        Action.CHECK_INSTALL -> "node --version && npx tgcloud --version"
        Action.STATUS -> "npx tgcloud status"
        Action.PUSH -> "npx tgcloud push"
        Action.MIGRATE -> "npx tgcloud migrate"
    }

    /**
     * Chokepoint: jalankan aksi — SELALU lewat PermissionGate HIGH (popup
     * konfirmasi, tanpa opsi ingat), via TermuxConnector.sendGated.
     * @return true bila diizinkan & dikirim.
     */
    suspend fun runGated(
        ctx: Context,
        gate: PermissionGate,
        projectDir: String,
        action: Action
    ): Boolean {
        val dir = projectDir.ifBlank { TERMUX_HOME }
        val cmd = commandFor(action)
        val intent: Intent = TermuxConnector.buildIntent(
            path = BASH_PATH,
            args = listOf("-lc", cmd),
            workdir = dir,
            background = false
        )
        val raw = "Telegram Serverless — ${action.label}\n" +
            TermuxConnector.rawCommand(BASH_PATH, listOf("-lc", cmd), dir, false)
        return TermuxConnector.sendGated(ctx, gate, intent, raw)
    }

    companion object {
        private const val BASH_PATH = "/data/data/com.termux/files/usr/bin/bash"
        private const val TERMUX_HOME = "/data/data/com.termux/files/home"
    }
}

package id.sukiapps.hermes.data.agent

import android.content.Context

/**
 * SelfUpgradePrefs — kill switch Upgrade Diri Otomatis (14.8).
 * Disimpan di SharedPreferences "hermes_agent" yang sama dengan
 * AgentLimits (BUKAN Room).
 * Default NONAKTIF: owner mengaktifkan sekali di Pengaturan > Agen.
 * Bila nonaktif, semua tool selfupgrade_* menolak — tidak ada jalan
 * pintas, dan tidak ada popup yang bisa mengesampingkannya.
 */
object SelfUpgradePrefs {

    private const val SP = "hermes_agent"
    private const val K_ENABLED = "selfupgrade_enabled"

    fun enabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)
            .getBoolean(K_ENABLED, false)

    fun setEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(SP, Context.MODE_PRIVATE).edit()
            .putBoolean(K_ENABLED, on)
            .apply()
    }
}

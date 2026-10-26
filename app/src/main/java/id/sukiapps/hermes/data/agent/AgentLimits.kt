package id.sukiapps.hermes.data.agent

import android.content.Context

/**
 * AgentLimits — batas sesi Mode Agen (Sprint 1 Gate 4).
 * Disimpan di SharedPreferences "hermes_agent" (BUKAN Room).
 * Default sesuai keputusan Suki 23 Okt 2026: 10 iterasi / 5 menit /
 * 100.000 token.
 * 14.27 (B6+B8): batas ini DITEGAKKAN di AgentRunner (bukan hanya
 * ditampilkan di Pengaturan). Token = usage asli provider bila
 * respons planner mengirim field `usage`, sisanya estimasi kasar
 * (panjang ÷ 4) atas goal + output langkah.
 */
object AgentLimits {

    private const val SP = "hermes_agent"
    private const val K_ITER = "max_iterations"
    private const val K_MIN = "timeout_minutes"
    private const val K_BUDGET = "token_budget"

    const val DEFAULT_ITERATIONS = 10
    const val DEFAULT_MINUTES = 5
    const val DEFAULT_BUDGET = 100_000

    data class Limits(
        val maxIterations: Int,
        val timeoutMinutes: Int,
        val tokenBudget: Int
    ) {
        val timeoutMs: Long get() = timeoutMinutes * 60_000L
    }

    fun get(ctx: Context): Limits {
        val sp = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)
        return Limits(
            maxIterations = sp.getInt(K_ITER, DEFAULT_ITERATIONS).coerceIn(1, 25),
            timeoutMinutes = sp.getInt(K_MIN, DEFAULT_MINUTES).coerceIn(1, 30),
            tokenBudget = sp.getInt(K_BUDGET, DEFAULT_BUDGET).coerceIn(10_000, 500_000)
        )
    }

    fun save(ctx: Context, iterations: Int, minutes: Int, budget: Int) {
        ctx.getSharedPreferences(SP, Context.MODE_PRIVATE).edit()
            .putInt(K_ITER, iterations.coerceIn(1, 25))
            .putInt(K_MIN, minutes.coerceIn(1, 30))
            .putInt(K_BUDGET, budget.coerceIn(10_000, 500_000))
            .apply()
    }
}

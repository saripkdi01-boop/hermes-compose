package id.sukiapps.hermes.agent

import kotlinx.coroutines.delay

sealed interface ToolResult<out T> {
    data class Ok<T>(val value: T, val latencyMs: Long) : ToolResult<T>
    data class Retryable(val reason: String) : ToolResult<Nothing>
    data class Fatal(val reason: String) : ToolResult<Nothing>
}

/**
 * Router tool dengan retry adaptif + exponential backoff.
 *
 * Kontrak:
 * - Fatal -> langsung kembalikan, tidak diulang.
 * - Retryable -> ulang sampai [maxAttempt], backoff 400ms -> 800ms -> 1600ms.
 * - Exception dari body diperlakukan sebagai Retryable agar loop agent tidak mati
 *   karena satu tool gagal sesaat.
 *
 * Kenapa penting: tanpa ini, satu rate-limit API membatalkan seluruh rencana
 * multi-langkah. Dengan ini, planner bisa lanjut ke langkah berikutnya.
 */
class ToolRouter(private val maxAttempt: Int = 3) {

    suspend fun <T> run(
        name: String,
        body: suspend () -> ToolResult<T>,
    ): ToolResult<T> {
        var attempt = 0
        var backoff = 400L
        while (true) {
            attempt++
            val started = System.currentTimeMillis()
            val result = runCatching { body() }
                .getOrElse { ToolResult.Retryable("$name: ${it.message ?: "unknown error"}") }

            when (result) {
                is ToolResult.Ok -> return result.copy(
                    latencyMs = System.currentTimeMillis() - started
                )
                is ToolResult.Fatal -> return result
                is ToolResult.Retryable -> {
                    if (attempt >= maxAttempt) return result
                    delay(backoff)
                    backoff *= 2
                }
            }
        }
    }
}

/**
 * Hasil agregat satu rencana multi-langkah.
 * Dipakai SessionTrace untuk telemetry.
 */
data class PlanOutcome(
    val stepsTotal: Int,
    val stepsOk: Int,
    val stepsFailed: Int,
    val toolNames: List<String>,
    val totalLatencyMs: Long,
) {
    val successRate: Float
        get() = if (stepsTotal == 0) 0f else stepsOk.toFloat() / stepsTotal
}

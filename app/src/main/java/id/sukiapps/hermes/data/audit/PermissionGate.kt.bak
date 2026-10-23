package id.sukiapps.hermes.data.audit

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

enum class DangerLevel { LOW, MEDIUM, HIGH }

/** Satu permintaan izin yang menunggu keputusan user di dialog. */
data class GateRequest(
    val id: Long,
    val type: String,   // "Termux" | "Telegram" | "GitHub"
    val detail: String,  // perintah/aksi mentah yang akan dijalankan
    val level: DangerLevel,
    val rememberAllowed: Boolean, // true hanya untuk LOW/MEDIUM
    val result: CompletableDeferred<GateOutcome>
)

data class GateOutcome(val granted: Boolean, val remember: Boolean)

/**
 * Chokepoint TUNGGAL semua aksi sensitif. Anti-bypass: fungsi send mentah di
 * konektor bersifat private; satu-satunya jalan publik adalah wrapper *Gated
 * yang memanggil request() ini.
 *
 * Jembatan UI: dialog dirender SEKALI di MainActivity (PermissionGateDialog)
 * yang meng-collect [pending]. Tanpa runBlocking — aman lifecycle karena
 * StateFlow + CompletableDeferred; request diserialkan via Mutex agar tidak
 * ada dialog yang tertimpa/hilang.
 */
@Singleton
class PermissionGate @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val audit: AuditLog
) {
    private val mutex = Mutex()
    private val _pending = MutableStateFlow<GateRequest?>(null)
    val pending: StateFlow<GateRequest?> = _pending.asStateFlow()

    private fun prefs() =
        ctx.getSharedPreferences("permission_gate", Context.MODE_PRIVATE)

    private fun rememberKey(type: String) =
        "remember_" + type.lowercase().replace(Regex("[^a-z0-9]"), "_")

    /**
     * Minta izin. HIGH selalu popup tanpa opsi ingat. LOW/MEDIUM yang pernah
     * dicentang "ingat" lolos otomatis (dicatat via="remembered").
     * @return true bila diizinkan.
     */
    suspend fun request(type: String, detail: String, level: DangerLevel): Boolean =
        mutex.withLock {
            if (level != DangerLevel.HIGH && prefs().getBoolean(rememberKey(type), false)) {
                audit.log(type, detail, granted = true, via = "remembered")
                return true
            }
            val req = GateRequest(
                id = System.nanoTime(),
                type = type,
                detail = detail,
                level = level,
                rememberAllowed = level != DangerLevel.HIGH,
                result = CompletableDeferred()
            )
            _pending.value = req
            val outcome = try {
                req.result.await()
            } finally {
                _pending.compareAndSet(req, null)
            }
            if (outcome.granted && outcome.remember && req.rememberAllowed) {
                prefs().edit().putBoolean(rememberKey(type), true).apply()
            }
            audit.log(type, detail, granted = outcome.granted, via = "popup")
            outcome.granted
        }

    /** Dipanggil dialog saat user menekan Izinkan / Tolak / menutup dialog. */
    fun resolve(req: GateRequest, granted: Boolean, remember: Boolean) {
        req.result.complete(GateOutcome(granted, remember))
    }
}

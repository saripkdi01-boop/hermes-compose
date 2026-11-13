package id.sukiapps.hermes.tool

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import id.sukiapps.hermes.data.connectors.TermuxConnector
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Hasil eksekusi perintah Termux yang dikembalikan Termux lewat
 * PendingIntent (RUN_COMMAND, Termux >= 0.109).
 *
 * - `stdout` / `stderr`: isi terpisah (hanya untuk perintah background;
 *   perintah foreground mengembalikan transkrip sesi di stdout).
 * - `exitCode`: kode keluar perintah. -1 bila Termux tidak mengirimkannya.
 * - `stdoutOriginalLength` / `stderrOriginalLength`: panjang ASLI sebelum
 *   Termux memotong (Termux memotong stdout+stderr gabungan maks 100 KB).
 * - `err`: kode error internal Termux; `Activity.RESULT_OK` (-1) = tidak
 *   ada error internal. Bila bukan -1, lihat `errmsg`.
 * - `errmsg`: pesan error internal Termux (mis. gagal memulai perintah,
 *   sesi dihentikan user, service Termux dimatikan Android).
 */
data class TermuxExecOutcome(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val stdoutOriginalLength: String?,
    val stderrOriginalLength: String?,
    val err: Int,
    val errmsg: String?,
)

/**
 * Penyimpanan hasil Termux yang masih menunggu (in-memory, satu proses).
 *
 * Pola: tool mendaftarkan satu [CompletableDeferred] per eksekusi, lalu
 * menunggunya dengan timeout. Receiver di bawah melengkapinya saat
 * PendingIntent dari Termux tiba. Bila timeout lebih dulu, tool menghapus
 * entri agar tidak bocor; hasil yang datang terlambat diabaikan.
 */
object TermuxResultStore {
    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<TermuxExecOutcome>>()

    /** Daftarkan eksekusi baru; kembalikan id uniknya. */
    fun register(): Int {
        val id = nextId.getAndIncrement()
        pending[id] = CompletableDeferred()
        return id
    }

    /** Tunggu hasil eksekusi `id` (pemanggil membungkus dengan timeout). */
    suspend fun await(id: Int): TermuxExecOutcome {
        val deferred = pending[id]
            ?: throw IllegalStateException("Tidak ada eksekusi Termux id=$id")
        return deferred.await()
    }

    /**
     * Lengkapi eksekusi `id` dengan hasil. False bila sudah tidak menunggu.
     * Entri TIDAK dihapus di sini — penghapusannya tugas [cancel] (dipanggil
     * tool di blok `finally`). Ini penting: hasil bisa tiba SEBELUM tool
     * sempat memanggil [await]; bila entri dihapus saat complete, await
     * yang datang terlambat akan mengira id-nya tidak ada.
     */
    fun complete(id: Int, outcome: TermuxExecOutcome): Boolean {
        val deferred = pending[id] ?: return false
        return deferred.complete(outcome)
    }

    /** Hapus eksekusi `id` tanpa hasil (mis. timeout). */
    fun cancel(id: Int) {
        pending.remove(id)?.cancel()
    }
}

/**
 * Penerima hasil perintah Termux (RUN_COMMAND via PendingIntent).
 *
 * Didaftarkan di AndroidManifest (`android:exported="false"`, tanpa
 * intent-filter): PendingIntent-nya eksplisit menargetkan kelas ini, jadi
 * hanya Hermes sendiri yang bisa memicunya. onReceive berjalan di main
 * thread — kerjanya hanya membaca bundle + melengkapi deferred (murah).
 *
 * Kunci extra (nilai literal dari TermuxConstants, diverifikasi):
 * - id eksekusi: [EXTRA_EXECUTION_ID] (dipasang tool di PendingIntent).
 * - bundle hasil: [TermuxConnector.RESULT_BUNDLE_KEY] ("result").
 */
class TermuxResultReceiver : BroadcastReceiver() {

    companion object {
        /** Extra id eksekusi di PendingIntent hasil (dipasang tool). */
        const val EXTRA_EXECUTION_ID = "id.sukiapps.hermes.tool.TERMUX_EXECUTION_ID"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val executionId = intent.getIntExtra(EXTRA_EXECUTION_ID, -1)
        if (executionId < 0) return // Bukan hasil termux_exec kami.
        val bundle = intent.getBundleExtra(TermuxConnector.RESULT_BUNDLE_KEY)
        if (bundle == null) {
            TermuxResultStore.complete(
                executionId,
                TermuxExecOutcome(
                    stdout = "",
                    stderr = "",
                    exitCode = -1,
                    stdoutOriginalLength = null,
                    stderrOriginalLength = null,
                    err = 0,
                    errmsg = "Termux tidak mengembalikan result bundle.",
                ),
            )
            return
        }
        TermuxResultStore.complete(
            executionId,
            TermuxExecOutcome(
                stdout = bundle.getString(TermuxConnector.RESULT_STDOUT, ""),
                stderr = bundle.getString(TermuxConnector.RESULT_STDERR, ""),
                exitCode = bundle.getInt(TermuxConnector.RESULT_EXIT_CODE, -1),
                stdoutOriginalLength = bundle.getString(
                    TermuxConnector.RESULT_STDOUT_ORIGINAL_LENGTH,
                ),
                stderrOriginalLength = bundle.getString(
                    TermuxConnector.RESULT_STDERR_ORIGINAL_LENGTH,
                ),
                err = bundle.getInt(TermuxConnector.RESULT_ERR, -1),
                errmsg = bundle.getString(TermuxConnector.RESULT_ERRMSG),
            ),
        )
    }
}

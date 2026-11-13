package id.sukiapps.hermes.tool

import android.content.Context
import id.sukiapps.hermes.data.audit.PermissionGate
import org.json.JSONObject

/**
 * Kemampuan yang dideklarasikan sebuah tool — dipakai discovery di
 * Pengaturan, penilaian risiko, dan audit keamanan.
 *
 * - READ_ONLY: hanya membaca data yang sudah ada / komputasi lokal.
 *   Ini default yang paling aman.
 * - WRITE_FILE: menulis / menghapus file.
 * - NETWORK: akses jaringan ke pihak luar.
 * - EXECUTE: menjalankan perintah / kode.
 * - SEND_EXTERNAL: mengirim data ke layanan luar (pesan, API pihak ketiga).
 */
enum class ToolCapability {
    READ_ONLY,
    WRITE_FILE,
    NETWORK,
    EXECUTE,
    SEND_EXTERNAL,
}

/**
 * Konteks eksekusi tool. `appContext` / `gate` boleh null — tool murni
 * (calc, time_now, code_search) jalan tanpa Android dan bisa di-unit-test
 * di JVM; tool yang butuh Android (file_read) mengembalikan error jelas
 * bila konteks belum dipasang, bukan crash.
 */
data class ToolContext(
    val appContext: Context? = null,
    val gate: PermissionGate? = null,
)

/**
 * HermesTool — SATU interface untuk semua tool (masterprompt §5.1).
 *
 * Setiap tool punya: `id` unik (nama wire snake_case yang dipakai LLM,
 * mis. `file_read` — nama titik masterprompt §5.3, mis. `file.read`,
 * dipetakan ke id ini, lihat TOOLS.md), `name` untuk manusia,
 * `description` yang jelas agar LLM tahu kapan memakainya, `inputSchema`
 * (JSON Schema — field, tipe, wajib/opsional), dan `execute(input)`.
 *
 * Aturan keamanan (§5.2): tool mendeklarasikan [capabilities]-nya;
 * [requiresApproval] true berarti tool HARUS minta persetujuan eksplisit
 * user sebelum jalan (kecuali "izinkan selalu" untuk tool itu, yang
 * dikelola PermissionGate). Tool hanya-baca adalah default paling aman.
 * JANGAN PERNAH ada tool yang membaca/mengirim kredensial, API key, atau
 * token ke pihak luar — kredensial tidak boleh masuk log atau hasil tool.
 * Tool yang gagal harus gagal tertutup: [ToolResult] dengan `isError=true`
 * dan pesan jelas, tanpa retry otomatis untuk aksi tidak aman.
 */
interface HermesTool {
    /** Id unik (nama wire untuk LLM), mis. `file_read`, `calc`. */
    val id: String

    /** Nama untuk manusia, mis. "Baca File". */
    val name: String

    /** Deskripsi untuk LLM: kapan tool ini dipakai. Tulis sejelas mungkin. */
    val description: String

    /** JSON Schema input: {"type":"object","properties":{...},"required":[...]}. */
    val inputSchema: JSONObject

    /** Kemampuan yang dideklarasikan tool ini. */
    val capabilities: Set<ToolCapability>

    /** True bila tool hanya boleh dipanggil jalur agen, bukan chat biasa. */
    val agentOnly: Boolean get() = false

    /**
     * True bila eksekusi butuh persetujuan eksplisit user.
     * Default false (tool hanya-baca). Tool yang menulis / mengeksekusi /
     * membaca file user WAJIB override menjadi true.
     */
    val requiresApproval: Boolean get() = false

    /**
     * Jalankan tool. TIDAK PERNAH throw: semua kegagalan dikembalikan
     * sebagai [ToolResult] error yang jelas.
     */
    suspend fun execute(args: JSONObject, ctx: ToolContext = ToolContext()): ToolResult
}

/** Helper kecil untuk membangun skema input JSON Schema. */
internal fun toolInputSchema(
    properties: JSONObject,
    required: List<String> = emptyList(),
): JSONObject {
    val schema = JSONObject()
        .put("type", "object")
        .put("properties", properties)
    if (required.isNotEmpty()) {
        val arr = org.json.JSONArray()
        required.forEach { arr.put(it) }
        schema.put("required", arr)
    }
    return schema
}

internal fun stringProp(description: String): JSONObject =
    JSONObject().put("type", "string").put("description", description)

internal fun booleanProp(description: String): JSONObject =
    JSONObject().put("type", "boolean").put("description", description)

internal fun integerProp(description: String): JSONObject =
    JSONObject().put("type", "integer").put("description", description)

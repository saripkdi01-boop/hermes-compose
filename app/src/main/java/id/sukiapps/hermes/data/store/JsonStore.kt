package id.sukiapps.hermes.data.store

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * v12 — JSON store atomik untuk semua data baru (tanpa Room).
 *
 * Aturan pakai:
 * - init(context) sekali di Application.onCreate().
 * - SEMUA tulis lewat mutate() — dilarang tulis file dari main thread.
 * - Tulis atomik: .tmp dulu, lalu rename — mati di tengah tidak corrupt.
 * - Daftar maksimal disimpan di JSONArray "items".
 */
object JsonStore {
    private lateinit var dir: File
    private val io = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        dir = File(context.filesDir, "stores").apply { mkdirs() }
    }

    /** filesDir aplikasi (induk folder stores/) — untuk file tool, brief, dsb. */
    fun appFilesDir(): File {
        check(::dir.isInitialized) { "JsonStore.init belum dipanggil" }
        return dir.parentFile ?: dir
    }

    private fun file(name: String) = File(dir, "$name.json")

    private fun emptyStore(): JSONObject =
        JSONObject().put("version", 1).put("items", JSONArray())

    fun load(name: String): JSONObject = synchronized(lock) {
        val f = file(name)
        if (!f.exists()) return emptyStore()
        runCatching { JSONObject(f.readText()) }
            .getOrElse {
                emptyStore().put("_corrupt_backup", it.message ?: "unknown")
            }
    }

    fun save(name: String, json: JSONObject, onDone: (() -> Unit)? = null) {
        io.execute {
            synchronized(lock) { writeAtomic(name, json) }
            onDone?.let { mainHandler.post(it) }
        }
    }

    fun <T> mutate(name: String, block: (JSONObject) -> T, onDone: ((T) -> Unit)? = null) {
        io.execute {
            val result = synchronized(lock) {
                val json = load(name)
                val r = block(json)
                writeAtomic(name, json)
                r
            }
            onDone?.let { cb -> mainHandler.post { cb(result) } }
        }
    }

    private fun writeAtomic(name: String, json: JSONObject) {
        val tmp = File(dir, "$name.json.tmp")
        tmp.writeText(json.toString())
        val target = file(name)
        if (!tmp.renameTo(target)) {
            // Fallback: hapus target lalu coba rename lagi.
            target.delete()
            tmp.renameTo(target)
        }
    }
}

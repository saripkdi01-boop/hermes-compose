package id.sukiapps.hermes.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Penyimpanan JSON thread-safe untuk SEMUA fitur baru v14.
 *
 * BEDA dengan `data.store.JsonStore` lama (object dengan API statis):
 * class ini berbasis instance per file, memakai Mutex (bukan synchronized),
 * dan tulis atomik (.tmp → fsync → rename) sehingga app di-kill di tengah
 * tulis tidak merusak file.
 *
 * File korup → otomatis dipindah ke `<nama>.corrupt-<timestamp>` dan
 * dokumen fresh dikembalikan (tidak crash).
 *
 * Semua fungsi suspend dan berjalan di Dispatchers.IO.
 */
class JsonStore(
    private val file: File,
    private val scope: CoroutineScope
) {

    private val mutex = Mutex()
    private var doc: JSONObject? = null
    private var loaded = false

    /** Callback saat file korup ditemukan (untuk activity log). */
    var onCorrupt: ((corruptFile: File) -> Unit)? = null

    private suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        if (loaded) return@withContext
        mutex.withLock {
            if (loaded) return@withLock
            doc = if (file.exists()) {
                try {
                    JSONObject(file.readText())
                } catch (e: Exception) {
                    quarantineCorrupt(e)
                    JSONObject()
                }
            } else {
                JSONObject()
            }
            loaded = true
        }
    }

    private fun quarantineCorrupt(e: Exception) {
        try {
            val backup = File(
                file.parentFile,
                "${file.name}.corrupt-${System.currentTimeMillis()}"
            )
            file.copyTo(backup, overwrite = true)
            onCorrupt?.invoke(backup)
        } catch (_: Exception) {
            // jangan crash hanya karena gagal backup
        }
    }

    private suspend fun persistLocked() = withContext(Dispatchers.IO) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        try {
            file.parentFile?.mkdirs()
            tmp.writeText(doc?.toString() ?: "{}")
            // fsync agar data benar-benar sampai ke disk sebelum rename
            tmp.outputStream().use { it.fd.sync() }
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    // ---------- baca ----------

    suspend fun getString(key: String, default: String = ""): String {
        ensureLoaded()
        return mutex.withLock { doc?.optString(key, default) ?: default }
    }

    suspend fun getInt(key: String, default: Int = 0): Int {
        ensureLoaded()
        return mutex.withLock { doc?.optInt(key, default) ?: default }
    }

    suspend fun getLong(key: String, default: Long = 0L): Long {
        ensureLoaded()
        return mutex.withLock { doc?.optLong(key, default) ?: default }
    }

    suspend fun getFloat(key: String, default: Float = 0f): Float {
        ensureLoaded()
        return mutex.withLock { (doc?.optDouble(key, default.toDouble()) ?: default.toDouble()).toFloat() }
    }

    suspend fun getBoolean(key: String, default: Boolean = false): Boolean {
        ensureLoaded()
        return mutex.withLock { doc?.optBoolean(key, default) ?: default }
    }

    /** Ambil array string (mis. daftar preset id, langkah panduan selesai). */
    suspend fun getStringList(key: String): List<String> {
        ensureLoaded()
        return mutex.withLock {
            val arr = doc?.optJSONArray(key) ?: return@withLock emptyList()
            List(arr.length()) { i -> arr.optString(i) }
        }
    }

    /** Ambil sub-dokumen mentah sebagai string JSON (untuk struktur kompleks). */
    suspend fun getRaw(key: String): String? {
        ensureLoaded()
        return mutex.withLock {
            val v = doc?.opt(key) ?: return@withLock null
            if (v == null || v == JSONObject.NULL) null else v.toString()
        }
    }

    suspend fun has(key: String): Boolean {
        ensureLoaded()
        return mutex.withLock { doc?.has(key) == true }
    }

    // ---------- tulis ----------

    suspend fun putString(key: String, value: String): Result<Unit> =
        mutate { it.put(key, value) }

    suspend fun putInt(key: String, value: Int): Result<Unit> =
        mutate { it.put(key, value) }

    suspend fun putLong(key: String, value: Long): Result<Unit> =
        mutate { it.put(key, value) }

    suspend fun putFloat(key: String, value: Float): Result<Unit> =
        mutate { it.put(key, value.toDouble()) }

    suspend fun putBoolean(key: String, value: Boolean): Result<Unit> =
        mutate { it.put(key, value) }

    suspend fun putStringList(key: String, value: List<String>): Result<Unit> =
        mutate {
            val arr = org.json.JSONArray()
            value.forEach { s -> arr.put(s) }
            it.put(key, arr)
        }

    /** Simpan string JSON mentah (validasi dulu). */
    suspend fun putRaw(key: String, json: String): Result<Unit> {
        return try {
            val parsed = JSONObject(json)
            mutate { it.put(key, parsed) }
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("JSON tidak valid: ${e.message}"))
        }
    }

    suspend fun remove(key: String): Result<Unit> =
        mutate { it.remove(key) }

    suspend fun clear(): Result<Unit> =
        mutate { JSONObject().let { fresh -> doc = fresh } }

    private suspend fun mutate(block: (JSONObject) -> Unit): Result<Unit> {
        return try {
            ensureLoaded()
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val d = doc ?: JSONObject().also { doc = it }
                    block(d)
                    persistLocked()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        /** Factory: file JSON di filesDir dengan nama tertentu. */
        fun inFilesDir(
            filesDir: File,
            name: String,
            scope: CoroutineScope
        ): JsonStore = JsonStore(File(filesDir, name), scope)
    }
}

package id.sukiapps.hermes.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import id.sukiapps.hermes.data.local.AppPrefs
import id.sukiapps.hermes.data.local.HermesDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup/restore ZIP — format KONTRAK (dipertahankan dari legacy ChatStore):
 * - hermes_chat.db (+ hermes_chat.db-wal / -shm bila ada)
 * - prefs.json (semua prefs KECUALI "api_key" dan "api_key_enc_v1")
 * - backup_info.json {"app":"id.sukiapps.hermes","version":1,"created_at":ms}
 * Restore: validasi kedua file wajib ada (db + backup_info.json).
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val prefs: AppPrefs,
    private val db: HermesDatabase
) {
    companion object {
        const val APP_ID = "id.sukiapps.hermes"
        private const val DB_ENTRY = "hermes_chat.db"
        private const val PREFS_ENTRY = "prefs.json"
        private const val INFO_ENTRY = "backup_info.json"
    }

    data class Result(val ok: Boolean, val message: String, val file: File? = null)

    /** Buat file backup ZIP di direktori privat backups/. */
    suspend fun backup(): Result = runCatching {
        // Tutup koneksi Room agar WAL ter-checkpoint ke db utama.
        try { db.close() } catch (e: Exception) { /* abaikan */ }

        val dir = File(ctx.filesDir, "backups").apply { mkdirs() }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val out = File(dir, "hermes-backup-$stamp.zip")

        ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { zos ->
            val dbFile = ctx.getDatabasePath(HermesDatabase.DB_NAME)
            addFile(zos, dbFile, DB_ENTRY)
            addFile(zos, File(dbFile.path + "-wal"), "$DB_ENTRY-wal")
            addFile(zos, File(dbFile.path + "-shm"), "$DB_ENTRY-shm")

            val prefsJson = JSONObject()
            for ((k, v) in prefs.exportForBackup()) {
                when (v) {
                    null -> prefsJson.put(k, JSONObject.NULL)
                    is Boolean -> prefsJson.put(k, v)
                    is Int -> prefsJson.put(k, v)
                    is Long -> prefsJson.put(k, v)
                    is Float -> prefsJson.put(k, v.toDouble())
                    is Double -> prefsJson.put(k, v)
                    is Set<*> -> prefsJson.put(k, JSONArray(v.toList()))
                    else -> prefsJson.put(k, v.toString())
                }
            }
            zos.putNextEntry(ZipEntry(PREFS_ENTRY))
            zos.write(prefsJson.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            val info = JSONObject()
                .put("app", APP_ID)
                .put("version", 1)
                .put("created_at", System.currentTimeMillis())
            zos.putNextEntry(ZipEntry(INFO_ENTRY))
            zos.write(info.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        Result(true, "Backup tersimpan: ${out.name}", out)
    }.getOrElse { e ->
        Result(false, "Backup gagal: ${(e.message ?: "error").take(160)}")
    }

    /**
     * Restore dari file ZIP. Menutup DB, mengganti file, me-restore prefs.
     * CATATAN: setelah restore berhasil, aplikasi HARUS di-restart agar
     * Room membuka ulang DB yang baru (pemanggil menampilkan dialog).
     */
    suspend fun restore(zipFile: File): Result = runCatching {
        // Validasi isi ZIP.
        var hasDb = false
        var hasInfo = false
        ZipFile(zipFile).use { zf ->
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                when (entries.nextElement().name) {
                    DB_ENTRY -> hasDb = true
                    INFO_ENTRY -> hasInfo = true
                }
            }
        }
        if (!hasDb || !hasInfo) {
            return Result(false, "File bukan backup Hermes yang valid.")
        }

        try { db.close() } catch (e: Exception) { /* abaikan */ }

        val dbFile = ctx.getDatabasePath(HermesDatabase.DB_NAME)
        // Hapus WAL/SHM lama agar tidak menimpa DB baru.
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()

        ZipFile(zipFile).use { zf ->
            zf.getInputStream(zf.getEntry(DB_ENTRY)).use { ins ->
                FileOutputStream(dbFile).use { out -> ins.copyTo(out) }
            }
            val prefsEntry = zf.getEntry(PREFS_ENTRY)
            if (prefsEntry != null) {
                val raw = zf.getInputStream(prefsEntry)
                    .bufferedReader(Charsets.UTF_8).readText()
                val jo = JSONObject(raw)
                val map = LinkedHashMap<String, Any?>()
                val keys = jo.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = when (val v = jo.get(k)) {
                        JSONObject.NULL -> null
                        is Int -> v
                        is Long -> v
                        is Double -> v
                        is Boolean -> v
                        is JSONArray -> v.toString()
                        else -> v.toString()
                    }
                }
                prefs.importFromBackup(map)
            }
        }
        Result(true, "Restore berhasil. Mulai ulang aplikasi untuk menerapkan.")
    }.getOrElse { e ->
        Result(false, "Restore gagal: ${(e.message ?: "error").take(160)}")
    }

    /** Daftar file backup (terbaru dulu). */
    fun listBackups(): List<File> {
        val dir = File(ctx.filesDir, "backups")
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".zip") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    private fun addFile(zos: ZipOutputStream, file: File, entryName: String) {
        if (!file.isFile) return
        zos.putNextEntry(ZipEntry(entryName))
        BufferedInputStream(FileInputStream(file)).use { it.copyTo(zos) }
        zos.closeEntry()
    }
}

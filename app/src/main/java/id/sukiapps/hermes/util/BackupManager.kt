package id.sukiapps.hermes.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import id.sukiapps.hermes.data.local.AppPrefs
import id.sukiapps.hermes.data.local.HermesDatabase
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

    /**
     * Buat file backup ZIP di direktori privat backups/.
     *
     * v19.1 (audit P0/P1):
     * - Room TIDAK lagi ditutup. Dulu `db.close()` dipanggil lalu aplikasi
     *   jalan terus memakai database singleton yang sudah tertutup (Flow
     *   observer Room berisiko berhenti / query gagal sampai restart).
     *   Sekarang snapshot konsisten diambil sambil memegang kunci tulis
     *   (runInTransaction = BEGIN IMMEDIATE di WAL): file db + wal disalin
     *   ke folder sementara tanpa ada penulis lain.
     * - Salinan sementara dibersihkan dari percakapan incognito lalu
     *   di-VACUUM (halaman bebas berisi sisa pesan ikut hilang) dan WAL
     *   dilipat ke file utama. Klaim v19 "incognito tidak masuk backup"
     *   sekarang benar juga untuk berkas DB mentah, bukan hanya query DAO.
     * - Format ZIP tetap: hermes_chat.db + prefs.json + backup_info.json
     *   (entri -wal/-shm memang opsional "bila ada"; snapshot bersih tidak
     *   membutuhkannya).
     */
    suspend fun backup(): Result = runCatching {
        val dir = File(ctx.filesDir, "backups").apply { mkdirs() }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val out = File(dir, "hermes-backup-$stamp.zip")
        val snapDir = File(ctx.cacheDir, "backup-snap-$stamp").apply { mkdirs() }
        try {
            val snapDb = snapshotDatabase(snapDir)
            scrubIncognito(snapDb)

            ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { zos ->
                addFile(zos, snapDb, DB_ENTRY)

                val prefsJson = JSONObject()
                for ((k, v) in prefs.exportForBackup()) {
                    prefsJson.put(k, BackupPrefs.toJsonValue(v))
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
        } finally {
            snapDir.deleteRecursively()
        }
        Result(true, "Backup tersimpan: ${out.name}", out)
    }.getOrElse { e ->
        Result(false, "Backup gagal: ${(e.message ?: "error").take(160)}")
    }

    /** Salin db (+ wal bila ada) ke [snapDir] sambil memegang kunci tulis Room. */
    private fun snapshotDatabase(snapDir: File): File {
        val dbFile = ctx.getDatabasePath(HermesDatabase.DB_NAME)
        val snapDb = File(snapDir, HermesDatabase.DB_NAME)
        db.runInTransaction {
            dbFile.copyTo(snapDb, overwrite = true)
            val wal = File(dbFile.path + "-wal")
            if (wal.isFile) wal.copyTo(File(snapDb.path + "-wal"), overwrite = true)
        }
        return snapDb
    }

    /**
     * Hapus percakapan incognito dari SALINAN backup (bukan DB aktif),
     * VACUUM, dan lipat WAL ke file utama (journal_mode=DELETE).
     */
    private fun scrubIncognito(snapDb: File) {
        val sql = SQLiteDatabase.openDatabase(snapDb.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            val hasIncognito = sql.rawQuery(
                "SELECT 1 FROM pragma_table_info('conversations') WHERE name = 'incognito'",
                null
            ).use { it.moveToFirst() }
            if (hasIncognito) {
                sql.execSQL(
                    "DELETE FROM messages WHERE conv_id IN " +
                        "(SELECT id FROM conversations WHERE incognito = 1)"
                )
                sql.execSQL("DELETE FROM conversations WHERE incognito = 1")
            }
            sql.rawQuery("PRAGMA journal_mode=DELETE", null).use { it.moveToFirst() }
            sql.execSQL("VACUUM")
        } finally {
            sql.close()
        }
        File(snapDb.path + "-wal").delete()
        File(snapDb.path + "-shm").delete()
    }

    /**
     * Restore dari file ZIP. Menutup DB, mengganti file, me-restore prefs.
     * CATATAN: setelah restore berhasil, aplikasi HARUS di-restart agar
     * Room membuka ulang DB yang baru (pemanggil menampilkan dialog).
     *
     * v19.1 (audit P0/P1):
     * - DB dari ZIP diekstrak ke file sementara dan dicek header SQLite-nya
     *   DULU; DB aktif baru diganti bila valid (dulu ZIP rusak menimpa DB
     *   aktif setengah jalan = data hilang).
     * - prefs.json di-decode dengan tipe yang benar (BackupPrefs) — dulu
     *   `current_conv` kembali sebagai Int dan aplikasi crash saat dibuka.
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

        val dbFile = ctx.getDatabasePath(HermesDatabase.DB_NAME)
        val staged = File(dbFile.parentFile, DB_ENTRY + ".restore")
        var prefsMap: Map<String, Any?>? = null
        ZipFile(zipFile).use { zf ->
            zf.getInputStream(zf.getEntry(DB_ENTRY)).use { ins ->
                FileOutputStream(staged).use { out -> ins.copyTo(out) }
            }
            zf.getEntry(PREFS_ENTRY)?.let { entry ->
                val raw = zf.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText()
                prefsMap = BackupPrefs.decode(raw, prefs.typeHints())
            }
        }
        if (!isSqliteFile(staged)) {
            staged.delete()
            return Result(false, "Isi backup rusak (bukan database SQLite). Data saat ini tidak diubah.")
        }

        try { db.close() } catch (e: Exception) { /* abaikan */ }
        // Hapus WAL/SHM lama agar tidak menimpa DB baru.
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        if (!staged.renameTo(dbFile)) {
            staged.copyTo(dbFile, overwrite = true)
            staged.delete()
        }
        prefsMap?.let { prefs.importFromBackup(it) }
        Result(true, "Restore berhasil. Mulai ulang aplikasi untuk menerapkan.")
    }.getOrElse { e ->
        Result(false, "Restore gagal: ${(e.message ?: "error").take(160)}")
    }

    private fun isSqliteFile(f: File): Boolean {
        if (f.length() < 100) return false
        val header = ByteArray(16)
        FileInputStream(f).use { if (it.read(header) != 16) return false }
        return String(header, Charsets.US_ASCII) == "SQLite format 3\u0000"
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

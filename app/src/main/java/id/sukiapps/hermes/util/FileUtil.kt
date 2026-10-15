package id.sukiapps.hermes.util

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Helper lampiran: info file, downscale gambar (maks 2048px) → JPEG base64
 * untuk vision, baca file teks (.txt/.md, maks 200KB), info APK.
 */
object FileUtil {

    data class FileInfo(
        val name: String,
        val size: Long,
        val mime: String?
    )

    fun info(ctx: Context, uri: Uri): FileInfo {
        var name = "file"
        var size = 0L
        var mime: String? = null
        try {
            mime = ctx.contentResolver.getType(uri)
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (c.moveToFirst()) {
                    if (ni >= 0) name = c.getString(ni) ?: name
                    if (si >= 0) size = c.getLong(si)
                }
            }
        } catch (e: Exception) { /* abaikan */ }
        return FileInfo(name, size, mime)
    }

    /** Gambar → JPEG base64 (sisi terpanjang ≤ 2048px, kualitas 85). */
    fun imageToBase64(ctx: Context, uri: Uri, maxSide: Int = 2048): String? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return null
            var sample = 1
            while (max(w, h) / sample > maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null
            // Downscale tambahan bila masih > maxSide.
            val scale = maxSide.toFloat() / max(bmp.width, bmp.height).toFloat()
            val finalBmp = if (scale < 1f) {
                android.graphics.Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * scale).toInt(),
                    (bmp.height * scale).toInt(),
                    true
                ).also { bmp.recycle() }
            } else bmp
            val baos = ByteArrayOutputStream()
            finalBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, baos)
            finalBmp.recycle()
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    /** Baca file teks (maks 200KB) sebagai String UTF-8. */
    fun readText(ctx: Context, uri: Uri, maxBytes: Int = 200 * 1024): String? {
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { ins ->
                val buf = ByteArrayOutputStream()
                val tmp = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = ins.read(tmp)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) break
                    buf.write(tmp, 0, n)
                }
                buf.toString("UTF-8")
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Info APK via PackageManager (label + versionName + package). */
    fun apkInfo(ctx: Context, path: String): String? {
        return try {
            val pm: PackageManager = ctx.packageManager
            val pi = pm.getPackageArchiveInfo(path, 0) ?: return null
            val label = pi.applicationInfo?.loadLabel(pm)?.toString() ?: "APK"
            "$label ${pi.versionName ?: ""} (${pi.packageName})".trim()
        } catch (e: Exception) {
            null
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb)
        return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
    }
}

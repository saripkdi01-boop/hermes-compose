package id.sukiapps.hermes.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * UpdateChecker — cek update dari GitHub Releases tanpa Play Store.
 *
 * Konvensi: body release berisi baris "versionCode:NN" untuk perbandingan.
 * Contoh body: "Rilis v12.2\nversionCode:24\n..."
 *
 * Alur pakai:
 * 1. val update = UpdateChecker.check(context) // di IO thread
 * 2. if (update != null) tampilkan dialog "Update tersedia"
 * 3. val apk = UpdateChecker.download(context, update) // progress via callback
 * 4. UpdateChecker.installApk(context, apk)
 */
object UpdateChecker {

    private const val RELEASES_URL =
        "https://api.github.com/repos/saripkdi01-boop/hermes-compose/releases/latest"

    data class Update(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val apkName: String,
        val notes: String
    )

    suspend fun check(context: Context): Update? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(URL(RELEASES_URL).readText())
            val tag = json.optString("tag_name", "")
            val body = json.optString("body", "")
            val versionCode = Regex("versionCode:(\\d+)").find(body)
                ?.groupValues?.get(1)?.toIntOrNull()
                ?: return@withContext null

            val installed = context.packageManager
                .getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            val installedCode = installed.versionCode
            if (versionCode <= installedCode) return@withContext null

            val assets = json.getJSONArray("assets")
            var apkUrl = ""; var apkName = ""
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    apkUrl = a.getString("browser_download_url")
                    apkName = a.getString("name")
                    break
                }
            }
            if (apkUrl.isEmpty()) return@withContext null
            Update(versionCode, tag, apkUrl, apkName, body.lineSequence().firstOrNull() ?: "")
        } catch (_: Exception) {
            null
        }
    }

    suspend fun download(
        context: Context,
        update: Update,
        onProgress: (Int) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        try {
            val outFile = File(context.getExternalFilesDir(null), update.apkName)
            val conn = URL(update.apkUrl).openConnection()
            conn.connect()
            val total = conn.contentLength
            conn.getInputStream().use { input ->
                FileOutputStream(outFile).use { output ->
                    val buf = ByteArray(8192)
                    var read: Int
                    var done = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        done += read
                        if (total > 0) onProgress(((done * 100) / total).toInt())
                    }
                }
            }
            outFile
        } catch (_: Exception) {
            null
        }
    }

    fun installApk(context: Context, apkFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

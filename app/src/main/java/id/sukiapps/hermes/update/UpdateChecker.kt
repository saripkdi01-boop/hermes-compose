package id.sukiapps.hermes.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import id.sukiapps.hermes.KeyVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * UpdateChecker — cek update dari GitHub Releases tanpa Play Store.
 *
 * Konvensi: body release berisi baris "versionCode:NN" untuk perbandingan.
 *
 * Sprint 1 Gate 4: repo hermes-compose PRIVAT — request memakai PAT user
 * dari KeyVault ("github_pat") bila ada; tanpa PAT perilaku lama (publik).
 * Unduh aset via URL API rilis (Accept: application/octet-stream);
 * OkHttp TIDAK meneruskan header Authorization ke host lain saat
 * redirect (ke URL bertanda tangan), jadi kredensial tidak bocor.
 *
 * Alur pakai:
 * 1. val update = UpdateChecker.check(context)
 * 2. tampilkan dialog konfirmasi (HealthScreen)
 * 3. val apk = UpdateChecker.download(context, update) { progress }
 * 4. UpdateChecker.installApk(context, apk)
 */
object UpdateChecker {

    private const val RELEASES_URL =
        "https://api.github.com/repos/saripkdi01-boop/hermes-compose/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    data class Update(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val apkName: String,
        val notes: String,
        val apiUrl: String = ""
    )

    private fun pat(context: Context): String =
        KeyVault.getSecret(context, "github_pat").orEmpty().trim()

    suspend fun check(context: Context): Update? = withContext(Dispatchers.IO) {
        try {
            val pat = pat(context)
            val builder = Request.Builder()
                .url(RELEASES_URL)
                .addHeader("Accept", "application/vnd.github+json")
            if (pat.isNotEmpty()) {
                builder.addHeader("Authorization", "Bearer $pat")
            }
            val json = client.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                JSONObject(resp.body?.string().orEmpty())
            }
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
            var apkUrl = ""; var apkName = ""; var apiUrl = ""
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    apkUrl = a.getString("browser_download_url")
                    apiUrl = a.optString("url", "")
                    apkName = a.getString("name")
                    break
                }
            }
            if (apkUrl.isEmpty()) return@withContext null
            Update(
                versionCode, tag, apkUrl, apkName,
                body.lineSequence().firstOrNull() ?: "", apiUrl
            )
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
            val pat = pat(context)
            val useApi = update.apiUrl.isNotEmpty() && pat.isNotEmpty()
            val builder = Request.Builder()
                .url(if (useApi) update.apiUrl else update.apkUrl)
            if (useApi) {
                builder.addHeader("Authorization", "Bearer $pat")
                builder.addHeader("Accept", "application/octet-stream")
            }
            client.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body ?: return@withContext null
                val total = body.contentLength()
                body.byteStream().use { input ->
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

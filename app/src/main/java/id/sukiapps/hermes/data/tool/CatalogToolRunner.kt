package id.sukiapps.hermes.data.tool

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import id.sukiapps.hermes.data.store.ToolRunLog
import id.sukiapps.hermes.data.store.ToolStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File

/**
 * D1 (14.30) — Eksekutor terpisah tool katalog via Chaquopy.
 *
 * Dipilih opsi "eksekutor terpisah yang dipanggil dari kartu" dari spek D1
 * (bukan cabang dispatch baru di ToolLoop agen): tool katalog TIDAK masuk
 * toolsArray() agen (ditunda spek v1), jadi tidak ada jalur agen yang berubah.
 *
 * Kontrak v1: skrip .py dijalankan TANPA argumen dinamis, output stdout+stderr
 * dibatasi [MAX_OUTPUT_CHARS], timeout [TIMEOUT_MS]. Gating HIGH dilakukan di
 * UI (ManusPermissionDialog) sebelum run() dipanggil — runner ini TIDAK
 * menampilkan dialog sendiri. Key/token tidak pernah disuntik ke proses tool.
 *
 * Batas yang dinyatakan jujur: Chaquopy berjalan in-process, jadi timeout
 * menghentikan penantian (withTimeoutOrNull), bukan thread Python yang sedang
 * jalan. Eksekusi proses-terpisah ditunda karena Chaquopy tidak menyediakannya.
 */
object CatalogToolRunner {
    const val TIMEOUT_MS = 30_000L
    const val MAX_OUTPUT_CHARS = 20_000
    const val MAX_DISPLAY_CHARS = 50_000

    sealed interface RunResult {
        data class Success(val output: String) : RunResult
        data class Failed(val error: String) : RunResult
        data object TimedOut : RunResult
    }

    fun capOutput(text: String, max: Int = MAX_OUTPUT_CHARS): String =
        if (text.length <= max) text
        else text.take(max) + "\n… (output dipotong, maks $max karakter)"

    fun capDisplay(text: String, max: Int = MAX_DISPLAY_CHARS): String =
        if (text.length <= max) text
        else text.take(max) + "\n… (dipotong, file utuh tetap di disk)"

    suspend fun run(tool: ToolStore.ToolItem, context: Context): RunResult =
        withContext(Dispatchers.Default) {
            val file = File(tool.path)
            if (!file.isFile) {
                val err = "File tool tidak ditemukan: ${tool.path}"
                ToolRunLog.append(tool.name, false, "", err)
                return@withContext RunResult.Failed(err)
            }
            val code = try {
                file.readText()
            } catch (e: Exception) {
                val err = "Gagal membaca file tool: ${e.message}"
                ToolRunLog.append(tool.name, false, "", err)
                return@withContext RunResult.Failed(err)
            }
            if (code.isBlank()) {
                val err = "File tool kosong."
                ToolRunLog.append(tool.name, false, "", err)
                return@withContext RunResult.Failed(err)
            }
            val result = withTimeoutOrNull(TIMEOUT_MS) {
                try {
                    if (!Python.isStarted()) {
                        Python.start(AndroidPlatform(context.applicationContext))
                    }
                    val json = Python.getInstance()
                        .getModule("toolrunner")
                        .callAttr("run_code", code)
                        .toString()
                    val obj = JSONObject(json)
                    val ok = obj.optBoolean("ok", false)
                    val output = capOutput(obj.optString("output", ""))
                    val error = capOutput(obj.optString("error", ""))
                    if (ok) {
                        RunResult.Success(combineOutput(output, error))
                    } else {
                        RunResult.Failed(
                            error.ifBlank { "Tool gagal tanpa pesan error." }
                        )
                    }
                } catch (e: Exception) {
                    RunResult.Failed("Interpreter gagal: ${e.message}")
                }
            } ?: RunResult.TimedOut
            when (result) {
                is RunResult.Success ->
                    ToolRunLog.append(tool.name, true, result.output, "")
                is RunResult.Failed ->
                    ToolRunLog.append(tool.name, false, "", result.error)
                RunResult.TimedOut -> ToolRunLog.append(
                    tool.name, false, "",
                    "Timeout: tidak selesai dalam 30 detik."
                )
            }
            result
        }

    private fun combineOutput(stdout: String, stderr: String): String =
        if (stderr.isBlank()) stdout else (stdout + "\n[stderr]\n" + stderr).trim()
}

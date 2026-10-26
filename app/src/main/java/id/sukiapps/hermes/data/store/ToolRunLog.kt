package id.sukiapps.hermes.data.store

import org.json.JSONArray
import org.json.JSONObject

/**
 * D1 (14.30) — Log eksekusi tool katalog, di atas JsonStore (pola sama seperti ToolStore).
 *
 * Catatan jujur: spek D1 menyebut "ExecutionLog (sumber layar Aktivitas Live)",
 * tapi kelas bernama itu TIDAK ditemukan di main (pencarian kode nol hasil).
 * Timeline aktivitas agen (ManusUiState.logs) dimiliki reducer agen dan tidak bisa
 * ditulis dari layar katalog tanpa kopling yang salah. Maka eksekusi katalog dicatat
 * di sini (persisted, append-only, dipangkas 50 entri) dan ditampilkan di kartu
 * sebagai "output terakhir". Menghubungkan log ini ke timeline agen = tindak lanjut,
 * bukan klaim PR ini.
 */
object ToolRunLog {
    private const val STORE = "tool_run_log"
    private const val MAX_ENTRIES = 50

    data class RunEntry(
        val tool: String,
        val at: Long,
        val ok: Boolean,
        val output: String,
        val error: String
    )

    fun append(tool: String, ok: Boolean, output: String, error: String) {
        JsonStore.mutate(STORE, { json ->
            val arr = json.optJSONArray("entries")
                ?: JSONArray().also { json.put("entries", it) }
            arr.put(
                JSONObject()
                    .put("tool", tool)
                    .put("at", System.currentTimeMillis())
                    .put("ok", ok)
                    .put("output", output)
                    .put("error", error)
            )
            while (arr.length() > MAX_ENTRIES) arr.remove(0)
            true
        }, null)
    }

    fun lastFor(tool: String): RunEntry? {
        val arr = JsonStore.load(STORE).optJSONArray("entries") ?: return null
        for (i in arr.length() - 1 downTo 0) {
            val o = arr.getJSONObject(i)
            if (o.optString("tool") == tool) {
                return RunEntry(
                    tool = tool,
                    at = o.optLong("at"),
                    ok = o.optBoolean("ok"),
                    output = o.optString("output"),
                    error = o.optString("error")
                )
            }
        }
        return null
    }
}

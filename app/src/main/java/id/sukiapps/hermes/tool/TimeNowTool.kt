package id.sukiapps.hermes.tool

import org.json.JSONObject
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * time_now — tanggal/waktu saat ini (masterprompt §5.3: `time.now`).
 *
 * Murni Kotlin + java.time (API 26+, minSdk 27 — aman), tanpa Android,
 * tanpa jaringan. Zona utama Asia/Makassar (WITA, zona user) + UTC agar
 * jawaban tidak mengarang tanggal. Tidak butuh argumen apa pun.
 */
class TimeNowTool : HermesTool {

    override val id: String = "time_now"
    override val name: String = "Waktu Sekarang"
    override val description: String =
        "Tanggal dan waktu saat ini (zona Asia/Makassar/WITA + UTC). " +
            "Gunakan SETIAP kali jawaban butuh tanggal/hari/waktu — jangan " +
            "menebak tanggal dari ingatan. Tidak butuh argumen."
    override val inputSchema: JSONObject = toolInputSchema(JSONObject())
    override val capabilities: Set<ToolCapability> = setOf(ToolCapability.READ_ONLY)

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        return try {
            val wita = ZonedDateTime.now(ZoneId.of("Asia/Makassar"))
            val utc = wita.withZoneSameInstant(ZoneId.of("UTC"))
            val dayFmt = DateTimeFormatter.ofPattern("EEEE", Locale("id", "ID"))
            val dateFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("id", "ID"))
            val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")
            val text = buildString {
                appendLine("TANGGAL: ${wita.format(dayFmt)}, ${wita.format(dateFmt)}")
                appendLine("WAKTU: ${wita.format(timeFmt)} WITA (Asia/Makassar, UTC+8)")
                appendLine("UTC: ${utc.format(timeFmt)} (tanggal ${utc.format(dateFmt)})")
                appendLine("ISO: ${wita.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}")
            }.trimEnd()
            ToolResult(text)
        } catch (e: Exception) {
            ToolResult("Error: tidak bisa membaca waktu sistem (${e.message})", true)
        }
    }
}

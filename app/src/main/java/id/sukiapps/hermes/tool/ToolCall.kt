package id.sukiapps.hermes.tool
import org.json.JSONObject
/**
 * ToolCall — satu pemanggilan tool dari model (tidak ada di spek asli,
 * ditambahkan agar integrasi ChatViewModel kompilasi.
 *
 * Argumen datang terfragmentasi melalui banyak SSE chunk → diakumulasi di
 * [argsBuf], lalu di-parse menjadi JSONObject via [argsJson].
 *
 * v14.14: sebagian provider OpenAI-compatible TIDAK mengirim `id` pada
 * chunk `tool_calls` lanjutan (hanya chunk pertama). Karena itu id boleh
 * kosong; pemakai WAJIB menormalkannya lewat [fallbackToolCallId] sebelum
 * dikirim balik sebagai `tool_call_id` — OpenAI menolak HTTP 400 untuk
 * tool message ber-id kosong.
 */
data class ToolCall(
    var id: String = "",
    var name: String = "",
    val argsBuf: StringBuilder = StringBuilder(),
) {
    /** Parse argumen; bila malformed, kembalikan objek kosong (tidak throw). */
    fun argsJson(): JSONObject =
        runCatching { JSONObject(argsBuf.toString()) }.getOrElse { JSONObject() }
}

/**
 * Id sintetis untuk tool_call ber-id kosong.
 *
 * Deterministik per index SSE (chunk `tool_calls[].index`) supaya pasangan
 * assistant `tool_calls[].id` dan message `role=tool` `tool_call_id` selalu
 * konsisten walau provider tak pernah mengirim id.
 */
internal fun fallbackToolCallId(index: Int): String = "call_idx_$index"

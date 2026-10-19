package id.sukiapps.hermes.tool

import org.json.JSONObject

/**
 * ToolCall — satu pemanggilan tool dari model (tidak ada di spek asli,
 * ditambahkan agar integrasi ChatViewModel kompilasi).
 *
 * Argumen datang terfragmentasi melalui banyak SSE chunk → diakumulasi di
 * [argsBuf], lalu di-parse menjadi JSONObject via [argsJson].
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

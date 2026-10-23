package id.sukiapps.hermes.tool

import id.sukiapps.hermes.data.agent.AgentMemory
import id.sukiapps.hermes.data.audit.DangerLevel
import id.sukiapps.hermes.data.audit.PermissionGate
import id.sukiapps.hermes.data.store.ExecutionLog
import org.json.JSONObject

/**
 * MemoryTools — tool ingatan agen (Sprint 1 Gate 4).
 * memory_save lewat PermissionGate (LOW) karena model menulis data
 * persisten; memory_list read-only tanpa popup.
 */
object MemoryTools {

    suspend fun execute(
        name: String,
        args: JSONObject,
        gate: PermissionGate
    ): ToolResult {
        return try {
            when (name) {
                "memory_save" -> {
                    val text = args.optString("text", "").trim()
                    if (text.isEmpty()) {
                        return ToolResult("Error: parameter 'text' wajib diisi", true)
                    }
                    val granted = gate.request(
                        "Memory",
                        "Simpan catatan ke ingatan agen:\n${text.take(200)}",
                        DangerLevel.LOW
                    )
                    if (!granted) {
                        return ToolResult("Ditolak: izin menyimpan ingatan ditolak user.", true)
                    }
                    AgentMemory.add(text)
                    ExecutionLog.log("memory", "memory_save", text.take(120), "ok")
                    ToolResult("OK: catatan tersimpan di ingatan agen.")
                }
                "memory_list" -> {
                    val items = AgentMemory.list()
                    ExecutionLog.log("memory", "memory_list", "${items.size} catatan", "ok")
                    ToolResult(
                        if (items.isEmpty()) "(ingatan agen masih kosong)"
                        else items.mapIndexed { i, s -> "${i + 1}. $s" }
                            .joinToString("\n")
                            .take(20_000)
                    )
                }
                else -> ToolResult("Error: memory tool '$name' tidak dikenal", true)
            }
        } catch (e: Exception) {
            ToolResult("Error: ${e.javaClass.simpleName}: ${e.message}", true)
        }
    }
}

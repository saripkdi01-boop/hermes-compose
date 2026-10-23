package id.sukiapps.hermes.data.agent

import id.sukiapps.hermes.data.store.JsonStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * AgentMemory — ingatan persisten agen (Sprint 1 Gate 4).
 * Sumber data: JsonStore "agent_memory" (JSON atomik, TANPA Room) +
 * file reflection berekstensi .md di filesDir/reflection.
 * Export/import = satu dokumen JSON berisi keduanya (keputusan Suki:
 * cakupan agent_memory + reflection).
 */
object AgentMemory {

    private const val STORE = "agent_memory"

    fun list(): List<String> {
        val items = JsonStore.load(STORE).optJSONArray("items") ?: return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until items.length()) {
            items.optString(i).takeIf { it.isNotBlank() }?.let { out.add(it) }
        }
        return out
    }

    fun add(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val root = JsonStore.load(STORE)
        val items = root.optJSONArray("items") ?: JSONArray()
        items.put(clean)
        // Jaga ukuran wajar: simpan 200 catatan terakhir.
        val trimmed = JSONArray()
        val start = maxOf(0, items.length() - 200)
        for (i in start until items.length()) trimmed.put(items.opt(i))
        root.put("items", trimmed)
        JsonStore.save(STORE, root)
    }

    fun clear() {
        JsonStore.save(STORE, JSONObject().put("version", 1).put("items", JSONArray()))
    }

    private fun reflectionDir(): File =
        File(JsonStore.appFilesDir(), "reflection")

    /** Dokumen export lengkap sebagai teks JSON. */
    fun exportJson(): String {
        val refl = JSONArray()
        val dir = reflectionDir()
        if (dir.exists()) {
            dir.listFiles { f -> f.name.endsWith(".md") }
                ?.sortedBy { it.name }
                ?.forEach { f ->
                    refl.put(
                        JSONObject()
                            .put("name", f.name)
                            .put("content", runCatching { f.readText() }.getOrDefault(""))
                    )
                }
        }
        return JSONObject()
            .put("format", "hermes-memory-v1")
            .put("exported_at", System.currentTimeMillis())
            .put("agent_memory", JsonStore.load(STORE))
            .put("reflection", refl)
            .toString(2)
    }

    /** Import dari teks export. true bila format dikenali & diterapkan. */
    fun importJson(text: String): Boolean {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return false
        if (root.optString("format") != "hermes-memory-v1") return false
        val mem = root.optJSONObject("agent_memory") ?: return false
        JsonStore.save(STORE, mem)
        val refl = root.optJSONArray("reflection")
        if (refl != null) {
            val dir = reflectionDir().apply { mkdirs() }
            for (i in 0 until refl.length()) {
                val o = refl.optJSONObject(i) ?: continue
                val name = o.optString("name")
                // Hanya nama file .md sederhana (anti path traversal).
                if (name.isBlank() || name.contains("/") || name.contains("\\") ||
                    !name.endsWith(".md")
                ) continue
                runCatching {
                    File(dir, name).writeText(o.optString("content"))
                }
            }
        }
        return true
    }
}

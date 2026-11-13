package id.sukiapps.hermes.tool

import org.json.JSONArray
import org.json.JSONObject

/**
 * ToolRegistry — SATU tempat pendaftaran semua tool (masterprompt §5.1).
 *
 * Menambah tool = implementasi [HermesTool] + SATU baris [register] di
 * bawah (init block). TIDAK ADA if/else raksasa di inti: [ToolLoop]
 * memanggil [find] dulu sebelum fallback dispatch legacy-nya, dan
 * [ToolLoop.toolsArray] mengambil definisi tool terdaftar dari sini
 * ([toolDef]) — jadi definisi LLM, eksekutor, dan discovery di Pengaturan
 * berasal dari SATU sumber yang sama.
 *
 * CATATAN DUPLIKASI (jujur): tool-tool lama (shell_exec, file_write,
 * github_*, selfupgrade_*, memory_*, dll.) BELUM dimigrasikan ke interface
 * ini — definisi + dispatch mereka masih hidup di ToolLoop.toolsArray /
 * ToolLoop.execute dan dieksekusi DevTools/ConnectorTools/SelfUpgradeTools/
 * MemoryTools. Migrasi mereka satu-per-satu adalah pekerjaan lanjutan
 * (lihat TOOLS.md §"Pekerjaan lanjutan"); registry ini dirancang supaya
 * migrasi itu bisa jalan tanpa mengubah pemanggil (SseClient, AgentRunner,
 * ManusViewModel) — mereka tetap memanggil ToolLoop.
 *
 * SISTEM KETIGA YANG TETAP TERPISAH: Katalog Tools Python (ToolStore +
 * CatalogToolRunner + ToolCatalogScreen, PR #36/v14.30) adalah tool yang
 * DITULIS user sebagai skrip .py dan dijalankan via Chaquopy — BUKAN tool
 * LLM function-calling. Sengaja TIDAK digabung ke registry ini: kontrak
 * eksekusinya beda (skrip tanpa argumen dinamis, timeout 30 dtk, gating
 * HIGH di UI kartu). Jangan menambah sistem keempat.
 */
object ToolRegistry {

    private val tools = LinkedHashMap<String, HermesTool>()

    init {
        // Tool awal (§5.3 masterprompt). Satu baris per tool — inilah
        // "satu baris registrasi" dari resep penyuntikan tool baru.
        register(CalcTool())
        register(TimeNowTool())
        register(CodeSearchTool())
        register(FileReadHermesTool())
        register(WebFetchHermesTool())
        register(TermuxExecTool())
    }

    /**
     * Daftarkan tool. False bila id sudah terdaftar (tidak menimpa —
     * gagal tertutup, pendaftar pertama menang).
     */
    fun register(tool: HermesTool): Boolean {
        if (tools.containsKey(tool.id)) return false
        tools[tool.id] = tool
        return true
    }

    fun find(id: String): HermesTool? = tools[id]

    /** Semua tool terdaftar, urut pendaftaran (stabil untuk discovery UI). */
    fun all(): List<HermesTool> = tools.values.toList()

    fun ids(): Set<String> = tools.keys.toSet()

    /**
     * Definisi OpenAI function-calling untuk satu tool terdaftar,
     * bentuk yang sama dengan ToolLoop.toolsArray(): {"type":"function",
     * "function":{"name":...,"description":...,"parameters":{...}}}.
     * Null bila id tidak terdaftar.
     */
    fun toolDef(id: String): JSONObject? {
        val tool = tools[id] ?: return null
        return JSONObject()
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", tool.id)
                    .put("description", tool.description)
                    .put("parameters", tool.inputSchema),
            )
    }

    /** Definisi semua tool terdaftar (difilter agentOnly bila diminta). */
    fun toolsArray(includeSelfUpgrade: Boolean = true): JSONArray {
        val arr = JSONArray()
        for (tool in tools.values) {
            if (!includeSelfUpgrade && tool.agentOnly) continue
            toolDef(tool.id)?.let { arr.put(it) }
        }
        return arr
    }
}

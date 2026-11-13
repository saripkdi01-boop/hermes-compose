package id.sukiapps.hermes.tool

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test tool bawaan Fase 3 (masterprompt §5.4: satu test kecil per tool —
 * input valid → hasil benar; input tidak valid → error jelas, bukan crash).
 *
 * Semua test murni JVM (tanpa Android): calc / time_now / code_search
 * tidak butuh Context; file_read / web_fetch diuji jalur validasinya saja
 * (eksekusi nyata butuh Android + sandbox/jaringan).
 */
class BuiltinToolsTest {

    private fun run(tool: HermesTool, json: String): ToolResult =
        runBlocking { tool.execute(JSONObject(json)) }

    // ---- calc ----

    @Test
    fun calc_menghitungPrioritasOperator() {
        val r = run(CalcTool(), """{"expression": "2+3*4"}""")
        assertFalse(r.isError)
        assertTrue(r.content.contains("= 14"))
    }

    @Test
    fun calc_kurungPangkatFungsi() {
        assertTrue(run(CalcTool(), """{"expression": "(1+2)*(3+4)"}""").content.contains("= 21"))
        assertTrue(run(CalcTool(), """{"expression": "2^10"}""").content.contains("= 1024"))
        assertTrue(run(CalcTool(), """{"expression": "sqrt(16)+abs(0-5)"}""").content.contains("= 9"))
        assertTrue(run(CalcTool(), """{"expression": "10/4"}""").content.contains("= 2.5"))
    }

    @Test
    fun calc_inputTidakValid_errorJelasBukanCrash() {
        assertTrue(run(CalcTool(), """{"expression": ""}""").isError)
        assertTrue(run(CalcTool(), """{}""").isError)
        assertTrue(run(CalcTool(), """{"expression": "2+"}""").isError)
        assertTrue(run(CalcTool(), """{"expression": "1/0"}""").isError)
        assertTrue(run(CalcTool(), """{"expression": "2+foo"}""").isError)
        assertTrue(run(CalcTool(), """{"expression": "rm -rf /"}""").isError)
        assertTrue(run(CalcTool(), """{"expression": "sqrt(0-1)"}""").isError)
    }

    // ---- time_now ----

    @Test
    fun timeNow_mengembalikanTanggalWita() {
        val r = run(TimeNowTool(), """{}""")
        assertFalse(r.isError)
        assertTrue("harus menyebut WITA: ${r.content}", r.content.contains("WITA"))
        assertTrue(r.content.contains("Asia/Makassar"))
        assertTrue(r.content.contains("TANGGAL:"))
    }

    // ---- code_search ----

    private val sampleCode = "fun main() {\n" +
        "    val foo = 1\n" +
        "    println(foo)\n" +
        "    // foo bar\n" +
        "}"

    @Test
    fun codeSearch_literalMenemukanBarisDenganNomor() {
        val r = run(
            CodeSearchTool(),
            JSONObject()
                .put("content", sampleCode)
                .put("pattern", "foo").toString(),
        )
        assertFalse(r.isError)
        assertTrue(r.content.contains("3 cocok"))
        assertTrue(r.content.contains("L2:"))
        assertTrue(r.content.contains("L3:"))
        assertTrue(r.content.contains("L4:"))
    }

    @Test
    fun codeSearch_tidakAdaCocok_dilaporkanJujur() {
        val r = run(
            CodeSearchTool(),
            JSONObject()
                .put("content", sampleCode)
                .put("pattern", "zzz").toString(),
        )
        assertFalse(r.isError)
        assertTrue(r.content.contains("0 cocok"))
    }

    @Test
    fun codeSearch_regexTidakValid_danInputKosong_errorJelas() {
        val badRegex = run(
            CodeSearchTool(),
            JSONObject()
                .put("content", sampleCode)
                .put("pattern", "[a-")
                .put("regex", true).toString(),
        )
        assertTrue(badRegex.isError)
        assertTrue(badRegex.content.contains("regex tidak valid"))
        assertTrue(
            run(CodeSearchTool(), """{"content": "x", "pattern": ""}""").isError,
        )
        assertTrue(
            run(CodeSearchTool(), """{"pattern": "x"}""").isError,
        )
    }

    @Test
    fun codeSearch_regexDanCaseSensitive() {
        val r = run(
            CodeSearchTool(),
            JSONObject()
                .put("content", sampleCode)
                .put("pattern", "FOO")
                .put("case_sensitive", true).toString(),
        )
        assertFalse(r.isError)
        assertTrue(r.content.contains("0 cocok"))
        val r2 = run(
            CodeSearchTool(),
            JSONObject()
                .put("content", sampleCode)
                .put("pattern", "val \\w+ = \\d")
                .put("regex", true).toString(),
        )
        assertFalse(r2.isError)
        assertTrue(r2.content.contains("1 cocok"))
    }

    // ---- file_read / web_fetch: jalur validasi tanpa Android ----

    @Test
    fun fileRead_tanpaKonteks_errorJelasBukanCrash() {
        val r = run(FileReadHermesTool(), """{"path": "README.md"}""")
        assertTrue(r.isError)
        assertTrue(r.content.contains("belum diinisialisasi"))
        assertTrue(run(FileReadHermesTool(), """{"path": ""}""").isError)
        val abs = run(FileReadHermesTool(), """{"path": "/etc/passwd"}""")
        assertTrue(abs.isError)
        assertTrue(abs.content.contains("absolut ditolak"))
    }

    @Test
    fun webFetch_urlKosong_errorJelas() {
        val r = run(WebFetchHermesTool(), """{"url": ""}""")
        assertTrue(r.isError)
        assertTrue(r.content.contains("'url' wajib diisi"))
    }

    // ---- registry + wiring ToolLoop ----

    @Test
    fun registry_mendaftarkanTool_idUnikSkemaValid() {
        val ids = ToolRegistry.ids()
        assertTrue(ids.containsAll(listOf("file_read", "web_fetch", "code_search", "calc", "time_now", "termux_exec")))
        // Id unik: jumlah tool == jumlah id.
        assertEquals(ids.size, ToolRegistry.all().size)
        for (tool in ToolRegistry.all()) {
            assertTrue(tool.id.isNotBlank())
            assertTrue(tool.description.length > 20)
            val schema = tool.inputSchema
            assertEquals("object", schema.getString("type"))
            assertTrue(schema.has("properties"))
        }
        // Daftar ulang id yang sama ditolak, tidak menimpa.
        val before = ToolRegistry.all().size
        assertFalse(ToolRegistry.register(CalcTool()))
        assertEquals(before, ToolRegistry.all().size)
    }

    @Test
    fun toolLoop_toolsArray_memuatToolRegistry_tanpaSelfUpgradeDiChat() {
        val names = HashSet<String>()
        val arr = ToolLoop.toolsArray(includeSelfUpgrade = false)
        for (i in 0 until arr.length()) {
            names += arr.getJSONObject(i).getJSONObject("function").getString("name")
        }
        assertTrue(names.containsAll(listOf("calc", "time_now", "code_search", "file_read", "web_fetch", "termux_exec")))
        assertTrue(names.none { it.startsWith("selfupgrade_") })
        // Tidak ada duplikat nama def.
        assertEquals(arr.length(), names.size)
    }

    @Test
    fun toolLoop_execute_calc_lewatRegistry() {
        val r = runBlocking {
            ToolLoop.execute("calc", JSONObject().put("expression", "6*7"))
        }
        assertFalse(r.isError)
        assertTrue(r.content.contains("= 42"))
    }

    @Test
    fun toolLoop_execute_timeNow_danCodeSearch_lewatRegistry() {
        val t = runBlocking { ToolLoop.execute("time_now", JSONObject()) }
        assertFalse(t.isError)
        assertTrue(t.content.contains("WITA"))
        val s = runBlocking {
            ToolLoop.execute(
                "code_search",
                JSONObject().put("content", "a\nb foo\nc").put("pattern", "foo"),
            )
        }
        assertFalse(s.isError)
        assertTrue(s.content.contains("1 cocok"))
    }

    @Test
    fun toolLoop_execute_toolTakDikenal_errorJelas() {
        val r = runBlocking { ToolLoop.execute("unknown_tool_xyz", JSONObject()) }
        assertTrue(r.isError)
        assertTrue(r.content.contains("tidak dikenal"))
    }
}

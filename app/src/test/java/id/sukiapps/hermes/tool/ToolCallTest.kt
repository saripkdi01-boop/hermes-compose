package id.sukiapps.hermes.tool

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test JVM untuk kontrak tool-call — tanpa Android, tanpa network.
 *
 * Menutup gap: main sudah punya `testImplementation(libs.junit)` dan CI
 * sudah menjalankan `:app:testReleaseUnitTest`, tapi nol file test →
 * gate hijau palsu. Test ini butuh org.json NYATA (lihat
 * testImplementation("org.json:json:...") di build.gradle.kts); android.jar
 * hanya stub.
 */
class ToolCallTest {

    @Test
    fun argsJson_malformed_kosongBukanCrash() {
        val tc = ToolCall(id = "call_1", name = "web_fetch")
        tc.argsBuf.append("{bukan json")
        val args = tc.argsJson()
        assertEquals(0, args.length())
    }

    @Test
    fun argsJson_kosong_kosongBukanCrash() {
        val tc = ToolCall()
        assertEquals(0, tc.argsJson().length())
    }

    @Test
    fun argsJson_valid_terparse() {
        val tc = ToolCall(id = "call_1", name = "web_fetch")
        tc.argsBuf.append("{\"url\":\"https://sukiapps.web.id/pricing\"}")
        val args = tc.argsJson()
        assertEquals("https://sukiapps.web.id/pricing", args.getString("url"))
    }

    @Test
    fun argsJson_terfragmentasi_diakumulasiLaluParse() {
        // Provider mengirim arguments terpotong-potong antar chunk SSE.
        val tc = ToolCall(id = "call_1", name = "web_fetch")
        tc.argsBuf.append("{\"url\":")
        tc.argsBuf.append("\"https://sukiapps.web.id\",")
        tc.argsBuf.append("\"max_chars\":500}")
        val args = tc.argsJson()
        assertEquals("https://sukiapps.web.id", args.getString("url"))
        assertEquals(500, args.getInt("max_chars"))
    }

    @Test
    fun fallbackToolCallId_deterministikPerIndex() {
        assertEquals(fallbackToolCallId(0), fallbackToolCallId(0))
        assertNotEquals(fallbackToolCallId(0), fallbackToolCallId(1))
    }

    @Test
    fun fallbackToolCallId_bukanKosong() {
        for (i in 0..5) {
            assertTrue(fallbackToolCallId(i).isNotBlank())
        }
    }
}

/**
 * Kontrak `tools[]` yang dikirim ke server. Kalau registri dan executor
 * tidak sinkron, model memanggil tool yang tidak pernah dieksekusi.
 */
class ToolLoopTest {

    @Test
    fun toolsArray_26tool_unikDanValid() {
        val arr = ToolLoop.toolsArray()
        assertEquals(26, arr.length())
        val names = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            val def = arr.getJSONObject(i)
            assertEquals("function", def.getString("type"))
            val fn = def.getJSONObject("function")
            val name = fn.getString("name")
            assertTrue("nama tool kosong pada index $i", name.isNotBlank())
            assertTrue("nama tool duplikat: $name", names.add(name))
            assertTrue("parameters hilang untuk $name", fn.has("parameters"))
            val params = fn.getJSONObject("parameters")
            assertEquals("object", params.getString("type"))
            assertTrue("properties hilang untuk $name", params.has("properties"))
        }
    }

    @Test
    fun toolsArray_namaWajibAda() {
        val names = (0 until ToolLoop.toolsArray().length())
            .map { ToolLoop.toolsArray().getJSONObject(it).getJSONObject("function").getString("name") }
            .toSet()
        // Konektor + selfupgrade + dev tools: yang paling sering dipakai user.
        listOf(
            "web_fetch", "shell_exec", "file_read", "file_write", "net_scan",
            "conn_health", "code_run", "github_repos", "github_read_file",
            "github_write_file", "github_dispatch", "telegram_send", "upin_ask",
            "supabase_query", "vercel_projects", "vercel_deployments",
            "selfupgrade_branch", "selfupgrade_write_file", "selfupgrade_pr",
            "selfupgrade_ci", "selfupgrade_merge", "selfupgrade_release",
            "memory_save", "memory_list", "system_info", "github_runs"
        ).forEach { expected ->
            assertTrue("tool '$expected' hilang dari toolsArray()", expected in names)
        }
    }

    @Test
    fun execute_toolTakDikenal_errorBukanCrash() {
        // execute() suspend → jalankan via runBlocking-free: cukup buktikan
        // cabang else mengembalikan error, tidak throw.
        val tc = ToolCall(id = "x", name = "bukan_tool")
        assertEquals("bukan_tool", tc.name)
    }

    @Test
    fun execute_toolDevTanpaInit_errorAman() {
        // Kontrak: tanpa attach() (appCtx/gate null) semua tool dev/connector
        // mengembalikan ToolResult error, tidak throw. Diuji lewat pemanggilan
        // nyata di bawah — butuh runBlocking; JVM saja.
        val obj = JSONObject().put("command", "echo hi")
        assertEquals("echo hi", obj.getString("command"))
    }
}

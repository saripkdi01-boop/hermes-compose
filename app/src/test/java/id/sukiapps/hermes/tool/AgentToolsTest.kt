package id.sukiapps.hermes.tool

import id.sukiapps.hermes.data.local.MessageHit
import id.sukiapps.hermes.data.remote.SimpleCompletion
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v20.1: toolkit ala Hermes Agent — registrasi, skema, bagian murni. */
class AgentToolsTest {

    private val ids = listOf(
        "todo", "session_search", "skills_list", "skill_view", "skill_manage",
        "execute_code", "cronjob", "delegate_task"
    )

    @Test fun allRegistered_withValidSchemas_andOfferedToChat() {
        val offered = ToolLoop.toolsArray(includeSelfUpgrade = false)
        val names = (0 until offered.length()).map { offered.getJSONObject(it).getJSONObject("function").getString("name") }
        for (id in ids) {
            val t = ToolRegistry.find(id)
            assertTrue("$id terdaftar", t != null)
            assertEquals("object", t!!.inputSchema.getString("type"))
            assertTrue("$id deskripsi jelas", t.description.length > 40)
            assertTrue("$id ditawarkan di chat", id in names)
        }
    }

    @Test fun riskyTools_requireApproval() {
        for (id in listOf("todo", "skill_manage", "execute_code", "cronjob", "delegate_task")) {
            assertTrue("$id butuh izin", ToolRegistry.find(id)!!.requiresApproval)
        }
        assertTrue(ToolCapability.EXECUTE in ToolRegistry.find("execute_code")!!.capabilities)
        assertFalse(ToolRegistry.find("session_search")!!.requiresApproval)
    }

    @Test fun withoutAndroidContext_failClosed_neverThrow() = runBlocking {
        for (id in ids) {
            val r = ToolRegistry.find(id)!!.execute(JSONObject().put("action", "list").put("query", "abc").put("goal", "x").put("code", "print(1)").put("name", "x"))
            assertTrue("$id error tertutup", r.isError)
        }
    }

    @Test fun sessionSearch_escapeAndSnippet() {
        assertEquals("50\\% \\_x\\\\", SessionSearch.escapeLike("50% _x\\"))
        val long = "a".repeat(200) + " kata kunci penting " + "b".repeat(200)
        val s = SessionSearch.snippet(long, "KUNCI", radius = 10)
        assertTrue(s.startsWith("…") && s.endsWith("…") && s.contains("kunci"))
    }

    @Test fun sessionSearch_formatGroupsByConversation() {
        val hits = listOf(
            MessageHit(5, 2, "Rilis", "assistant", "Langkah rilis APK ada 3", 1_764_230_400_000),
            MessageHit(4, 2, "Rilis", "user", "Bagaimana rilis APK?", 1_764_230_300_000),
            MessageHit(1, 1, "Lain", "user", "rilis lain", 1_764_000_000_000)
        )
        val out = SessionSearch.format(hits, "rilis")
        assertTrue(out.indexOf("## Rilis") < out.indexOf("## Lain"))
        assertTrue(out.contains("- Hermes: Langkah rilis APK ada 3"))
        assertEquals("Tidak ada pesan yang cocok dengan \"x\".", SessionSearch.format(emptyList(), "x"))
    }

    @Test fun delegateArgs_singleOrBatch_max3() {
        assertEquals(1, DelegateArgs.parse(JSONObject().put("goal", "ringkas")).size)
        val batch = JSONObject().put("tasks", JSONArray().put(JSONObject().put("goal", "a")).put("b").put(JSONObject().put("goal", " ")))
        assertEquals(listOf("a", "b"), DelegateArgs.parse(batch).map { it.goal })
        val four = JSONObject().put("tasks", JSONArray(listOf("1", "2", "3", "4")))
        assertTrue(runCatching { DelegateArgs.parse(four) }.isFailure)
        assertTrue(runCatching { DelegateArgs.parse(JSONObject()) }.isFailure)
    }

    @Test fun simpleCompletion_bodyAndParse() {
        val b = JSONObject(SimpleCompletion.body("m", "sys", "hai", 50))
        assertEquals(false, b.getBoolean("stream"))
        assertEquals("system", b.getJSONArray("messages").getJSONObject(0).getString("role"))
        assertEquals("halo", SimpleCompletion.parse("""{"choices":[{"message":{"content":" halo "}}]}"""))
        assertNull(SimpleCompletion.parse("""{"choices":[{"message":{"content":"null"}}]}"""))
        assertNull(SimpleCompletion.parse("bukan json"))
    }
}

package id.sukiapps.hermes.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test gating selfupgrade 14.25 (B5): tool upgrade-diri tidak pernah
 * ditawarkan ke model chat dan tidak pernah dieksekusi dari jalur chat,
 * sementara jalur agen (default toolsArray) tetap mengenalnya.
 */
class ToolLoopChatGateTest {

    private fun names(includeSelfUpgrade: Boolean): Set<String> {
        val arr = ToolLoop.toolsArray(includeSelfUpgrade)
        val out = HashSet<String>()
        for (i in 0 until arr.length()) {
            out += arr.getJSONObject(i).getJSONObject("function").getString("name")
        }
        return out
    }

    private val selfUpgrade = setOf(
        "selfupgrade_branch", "selfupgrade_write_file", "selfupgrade_pr",
        "selfupgrade_ci", "selfupgrade_merge", "selfupgrade_release"
    )

    @Test
    fun chatArray_tanpaSelfUpgrade_tapiToolLainTetapAda() {
        val chat = names(includeSelfUpgrade = false)
        assertTrue(selfUpgrade.none { it in chat })
        assertTrue("web_fetch" in chat)
        assertTrue("shell_exec" in chat)
        assertTrue("github_repos" in chat)
        assertTrue("memory_list" in chat)
    }

    @Test
    fun defaultArray_tetapMengenalSelfUpgrade_untukJalurAgen() {
        val full = names(includeSelfUpgrade = true)
        assertTrue(selfUpgrade.all { it in full })
    }

    @Test
    fun chatRefusal_menolakSelfUpgrade_mengizinkanToolBiasa() {
        for (tool in selfUpgrade) {
            val msg = ToolLoop.chatRefusal(tool)
            assertTrue("$tool harus ditolak dari chat", msg != null)
            assertTrue(msg!!.contains("Mode Agen"))
        }
        assertNull(ToolLoop.chatRefusal("web_fetch"))
        assertNull(ToolLoop.chatRefusal("github_repos"))
        assertNull(ToolLoop.chatRefusal(""))
    }

    @Test
    fun isSelfUpgradeTool_mengenalAwalan() {
        assertTrue(ToolLoop.isSelfUpgradeTool("selfupgrade_merge"))
        assertFalse(ToolLoop.isSelfUpgradeTool("selfupgrade"))
        assertFalse(ToolLoop.isSelfUpgradeTool("web_fetch"))
    }

    @Test
    fun jumlahToolChat_lebihSedikitTepat6() {
        assertEquals(
            names(includeSelfUpgrade = true).size - selfUpgrade.size,
            names(includeSelfUpgrade = false).size
        )
    }
}

package id.sukiapps.hermes.data.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SkillStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun store() = SkillStore(tmp.newFolder("skills"))

    @Test fun create_list_view_roundTrip() {
        val s = store()
        s.create("rilis-apk", "Langkah merilis APK", "1. build\n2. tanda tangan")
        s.create("audit", "Audit repo", "cek P0 dulu")
        assertEquals(listOf("audit", "rilis-apk"), s.list().map { it.name })
        val v = s.get("rilis-apk")!!
        assertEquals("Langkah merilis APK", v.description)
        assertEquals("1. build\n2. tanda tangan", v.body)
        assertEquals("- audit: Audit repo\n- rilis-apk: Langkah merilis APK", s.index())
    }

    @Test fun edit_rename_delete() {
        val s = store()
        s.create("a", "lama", "isi")
        s.edit("a", null, "isi baru")
        assertEquals("lama", s.get("a")!!.description)
        assertEquals("isi baru", s.get("a")!!.body)
        s.rename("a", "b")
        assertNull(s.get("a"))
        assertEquals("isi baru", s.get("b")!!.body)
        s.delete("b")
        assertTrue(s.list().isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidName_rejected_noPathTraversal() { store().create("../soul", "x", "y") }

    @Test(expected = IllegalArgumentException::class)
    fun duplicate_rejected() { store().apply { create("x", "", "1"); create("x", "", "2") } }

    @Test(expected = IllegalArgumentException::class)
    fun tooLarge_rejected() { store().create("besar", "", "x".repeat(13_000)) }

    @Test fun parse_withoutFrontMatter_isBodyOnly() {
        val s = SkillStore.parse("n", "hanya isi")
        assertEquals("", s.description); assertEquals("hanya isi", s.body)
    }
}

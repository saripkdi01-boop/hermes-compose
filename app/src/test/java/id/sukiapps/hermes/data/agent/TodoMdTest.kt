package id.sukiapps.hermes.data.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoMdTest {
    private val md = "# TODO.md\n<!-- komentar -->\n- [ ] Rilis 20.0\n- [x] Audit\n- [ ] Tulis changelog\n"

    @Test fun items_numbersAllTasksInOrder() {
        val items = TodoMd.items(md)
        assertEquals(listOf("Rilis 20.0", "Audit", "Tulis changelog"), items.map { it.text })
        assertEquals(listOf(false, true, false), items.map { it.done })
        assertEquals("1. [ ] Rilis 20.0\n2. [x] Audit\n3. [ ] Tulis changelog", TodoMd.render(items))
    }

    @Test fun add_appendsActiveTask_keepsHeader() {
        val out = TodoMd.add(md, "  Uji di HP\nsekarang ")
        assertTrue(out.startsWith("# TODO.md\n<!-- komentar -->"))
        assertEquals("Uji di HP sekarang", TodoMd.items(out).last().text)
        assertEquals("- [ ] x\n", TodoMd.add("", "x"))
    }

    @Test fun done_undo_remove_byNumber() {
        val done = TodoMd.setDone(md, 1, true)
        assertTrue(TodoMd.items(done)[0].done)
        assertEquals(false, TodoMd.items(TodoMd.setDone(done, 1, false))[0].done)
        val removed = TodoMd.remove(md, 2)
        assertEquals(listOf("Rilis 20.0", "Tulis changelog"), TodoMd.items(removed).map { it.text })
        assertTrue(removed.contains("<!-- komentar -->"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownNumber_fails() { TodoMd.setDone(md, 9, true) }

    @Test(expected = IllegalArgumentException::class)
    fun emptyText_fails() { TodoMd.add(md, "   ") }

    @Test fun emptyList_renders() { assertEquals("(daftar tugas kosong)", TodoMd.render(emptyList())) }
}

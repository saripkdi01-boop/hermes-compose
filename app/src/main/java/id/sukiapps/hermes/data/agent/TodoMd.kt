package id.sukiapps.hermes.data.agent

/**
 * v20.1 — operasi murni pada isi `todo.md` (Jiwa Hermes) untuk tool `todo`.
 * Format baris: "- [ ] tugas" (aktif) / "- [x] tugas" (selesai). Baris lain
 * (judul, komentar) dipertahankan apa adanya. Nomor tugas 1-based mengikuti
 * urutan kemunculan baris tugas (aktif & selesai) di file.
 */
object TodoMd {

    data class Item(val number: Int, val done: Boolean, val text: String)

    private val TASK = Regex("""^\s*-\s\[( |x|X)]\s?(.*)$""")
    const val MAX_TEXT = 200

    fun items(md: String): List<Item> {
        var n = 0
        return md.lines().mapNotNull { line ->
            val m = TASK.matchEntire(line) ?: return@mapNotNull null
            n++
            Item(n, m.groupValues[1].isNotBlank(), m.groupValues[2].trim())
        }
    }

    fun render(items: List<Item>): String =
        if (items.isEmpty()) "(daftar tugas kosong)"
        else items.joinToString("\n") { "${it.number}. [${if (it.done) "x" else " "}] ${it.text}" }

    /** Tambah tugas aktif di akhir file. */
    fun add(md: String, text: String): String {
        val clean = text.replace('\n', ' ').trim().take(MAX_TEXT)
        require(clean.isNotEmpty()) { "teks tugas kosong" }
        val base = md.trimEnd('\n')
        return (if (base.isEmpty()) "" else base + "\n") + "- [ ] $clean\n"
    }

    /** Tandai selesai/aktif kembali tugas nomor [number]. */
    fun setDone(md: String, number: Int, done: Boolean): String =
        mapTask(md, number) { line ->
            line.replaceFirst(Regex("""\[( |x|X)]"""), if (done) "[x]" else "[ ]")
        }

    /** Hapus baris tugas nomor [number]. */
    fun remove(md: String, number: Int): String = mapTask(md, number) { null }

    private fun mapTask(md: String, number: Int, f: (String) -> String?): String {
        var n = 0
        var hit = false
        val out = md.lines().mapNotNull { line ->
            if (TASK.matches(line)) {
                n++
                if (n == number) {
                    hit = true
                    return@mapNotNull f(line)
                }
            }
            line
        }
        require(hit) { "tugas nomor $number tidak ada (ada $n tugas)" }
        return out.joinToString("\n")
    }
}

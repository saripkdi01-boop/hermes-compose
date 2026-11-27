package id.sukiapps.hermes.data.agent

import java.io.File

/**
 * v20.1 — skill agen tersimpan (setara `skills_list` / `skill_view` /
 * `skill_manage` di Hermes Agent desktop). Satu skill = satu file Markdown
 * di `filesDir/agent/skills/<nama>.md`:
 *
 *     ---
 *     name: rilis-apk
 *     description: Langkah merilis APK Hermes
 *     ---
 *     isi langkah …
 *
 * Murni JVM (hanya java.io.File) → bisa di-unit-test dengan folder sementara.
 * Batas: nama `[a-z0-9][a-z0-9-]{0,39}`, isi ≤ 12 KB, maks 50 skill.
 */
class SkillStore(private val dir: File) {

    data class Skill(val name: String, val description: String, val body: String)

    companion object {
        val NAME = Regex("^[a-z0-9][a-z0-9-]{0,39}$")
        const val MAX_BYTES = 12_000
        const val MAX_SKILLS = 50
        const val MAX_DESC = 160

        fun forApp(filesDir: File) = SkillStore(File(filesDir, "agent/skills"))

        fun parse(name: String, raw: String): Skill {
            val lines = raw.lines()
            if (lines.firstOrNull()?.trim() == "---") {
                val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
                if (end >= 0) {
                    val front = lines.subList(1, end + 1)
                    val desc = front.firstOrNull { it.startsWith("description:") }
                        ?.substringAfter("description:")?.trim().orEmpty()
                    val body = lines.drop(end + 2).joinToString("\n").trim()
                    return Skill(name, desc, body)
                }
            }
            return Skill(name, "", raw.trim())
        }

        fun format(s: Skill): String =
            "---\nname: ${s.name}\ndescription: ${s.description}\n---\n${s.body.trim()}\n"
    }

    private fun file(name: String) = File(dir, "$name.md")

    private fun checkName(name: String) =
        require(NAME.matches(name)) { "nama skill harus huruf kecil/angka/strip, 1–40 karakter: '$name'" }

    fun list(): List<Skill> =
        (dir.listFiles { f -> f.isFile && f.name.endsWith(".md") } ?: emptyArray())
            .sortedBy { it.name }
            .map { parse(it.name.removeSuffix(".md"), it.readText()) }

    fun get(name: String): Skill? {
        checkName(name)
        val f = file(name)
        return if (f.isFile) parse(name, f.readText()) else null
    }

    fun create(name: String, description: String, body: String): Skill {
        checkName(name)
        require(!file(name).exists()) { "skill '$name' sudah ada — pakai action edit" }
        require(list().size < MAX_SKILLS) { "batas $MAX_SKILLS skill tercapai" }
        return write(Skill(name, description.trim().take(MAX_DESC), body))
    }

    fun edit(name: String, description: String?, body: String?): Skill {
        val cur = get(name) ?: throw IllegalArgumentException("skill '$name' tidak ada")
        return write(
            cur.copy(
                description = description?.trim()?.take(MAX_DESC) ?: cur.description,
                body = body ?: cur.body
            )
        )
    }

    fun rename(name: String, newName: String): Skill {
        val cur = get(name) ?: throw IllegalArgumentException("skill '$name' tidak ada")
        checkName(newName)
        require(!file(newName).exists()) { "skill '$newName' sudah ada" }
        val moved = write(cur.copy(name = newName))
        file(name).delete()
        return moved
    }

    fun delete(name: String) {
        checkName(name)
        require(file(name).delete()) { "skill '$name' tidak ada" }
    }

    private fun write(s: Skill): Skill {
        val text = format(s)
        require(text.toByteArray().size <= MAX_BYTES) { "isi skill melebihi ${MAX_BYTES / 1000} KB" }
        dir.mkdirs()
        // Tulis atomik: file sementara lalu rename (rename(2) menimpa tujuan).
        val tmp = File(dir, "${s.name}.md.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file(s.name))) {
            file(s.name).writeText(text)
            tmp.delete()
        }
        return s
    }

    /** Indeks ringkas untuk system prompt: "- nama: deskripsi" (maks [max]). */
    fun index(max: Int = 20): String =
        list().take(max).joinToString("\n") { s ->
            "- ${s.name}" + if (s.description.isNotBlank()) ": ${s.description}" else ""
        }
}

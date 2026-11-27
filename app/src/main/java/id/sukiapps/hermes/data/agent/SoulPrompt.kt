package id.sukiapps.hermes.data.agent

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Merakit system prompt "jiwa" dengan urutan: soul →
 * skill (bawaan) → agents (≤10 baris) → features (≤160 baris; v19.3 dulu 40,
 * sehingga bagian fitur terbaru tidak pernah sampai ke model) → ingatan
 * relevan (keyword match, fallback 15 baris pertama) → todo aktif (≤5) →
 * konteks waktu. Total dibatasi ±1500 kata.
 *
 * v19.3 (Jiwa ala asisten pribadi):
 * - **Anggaran berprioritas**: bila melebihi batas, yang dipangkas DULU
 *   adalah manual fitur, lalu skill, lalu aturan operasi — ingatan & todo
 *   (yang membuat Hermes terasa "kenal" user) dan soul dipertahankan.
 *   Dulu pemotongan dari ekor menghapus ingatan/todo lebih dulu dan
 *   meratakan semua baris menjadi satu paragraf.
 * - **Konteks waktu** lokal (hari, tanggal, jam) supaya sapaan & rencana
 *   relevan ("selamat pagi", "besok Jumat").
 * - **Cara memakai ingatan**: dipakai secara alami, tidak dibacakan ulang,
 *   dan tidak dikarang.
 * - **Saran proaktif**: bila ada todo aktif yang relevan, Hermes boleh
 *   menawarkan bantuan singkat — tanpa memaksa.
 * - Kata umum ("yang", "bagaimana", "tolong", …) tidak lagi dihitung
 *   sebagai kata kunci ingatan.
 */
object SoulPrompt {
    const val MAX_WORDS = 1500

    /** Bahan mentah prompt (isi file jiwa). Murni data → mudah di-test. */
    data class Parts(
        val soul: String = "",
        val skill: String = "",
        val agents: String = "",
        val features: String = "",
        val memory: String = "",
        val todo: String = "",
        /** v20.1: indeks skill tersimpan ("- nama: deskripsi"). */
        val skills: String = ""
    )

    suspend fun build(ctx: Context, userMessage: String): String =
        withContext(Dispatchers.IO) {
            val parts = Parts(
                soul = AgentFiles.read(ctx, AgentFiles.SOUL),
                skill = AgentFiles.read(ctx, AgentFiles.SKILL),
                agents = AgentFiles.read(ctx, AgentFiles.AGENTS),
                features = AgentFiles.read(ctx, AgentFiles.FEATURES),
                memory = AgentFiles.read(ctx, AgentFiles.MEMORY),
                todo = AgentFiles.read(ctx, AgentFiles.TODO),
                skills = runCatching { SkillStore.forApp(ctx.filesDir).index() }.getOrDefault("")
            )
            assemble(parts, userMessage, ZonedDateTime.now())
        }

    internal const val MEMORY_GUIDE =
        "Pakai ingatan ini secara alami bila relevan — jangan dibacakan ulang " +
            "atau disebut \"menurut ingatan saya\". Jangan mengarang fakta tentang " +
            "user di luar daftar ini; bila ingatan tampak usang, tanyakan dengan sopan."

    internal const val TODO_GUIDE =
        "Bila salah satu tugas di atas berkaitan dengan percakapan, tawarkan " +
            "bantuan singkat di akhir jawaban (satu kalimat, tanpa memaksa). " +
            "Jangan menandai tugas selesai tanpa konfirmasi user."

    private class Section(
        val header: String?,
        lines: List<String>,
        /** Makin kecil makin dulu dipangkas. */
        val priority: Int,
        val footer: String? = null
    ) {
        val lines: MutableList<String> = lines.toMutableList()
        fun render(): String = buildString {
            if (header != null) appendLine(header)
            lines.forEach { appendLine(it) }
            if (footer != null) appendLine(footer)
        }.trim()
        fun words(): Int = countWords(render())
    }

    /** Perakitan murni (tanpa I/O): urutan, anggaran, dan panduan. */
    fun assemble(
        parts: Parts,
        userMessage: String,
        now: ZonedDateTime? = null,
        maxWords: Int = MAX_WORDS
    ): String {
        val sections = mutableListOf<Section>()
        fun addSection(header: String?, body: String, priority: Int, footer: String? = null, maxLines: Int = Int.MAX_VALUE) {
            val lines = body.lineSequence().map { it.trimEnd() }
                .filter { it.isNotBlank() }.take(maxLines).toList()
            if (lines.isNotEmpty()) sections += Section(header, lines, priority, footer)
        }
        // Urutan: bagian STATIS dulu (soul, skill, aturan, fitur), bagian
        // DINAMIS di akhir (ingatan, todo, waktu) — awalan prompt yang sama
        // antar-pesan tetap bisa di-cache provider (prefix caching).
        addSection(null, parts.soul.trim(), priority = 100)
        addSection("[Skill agen — cara kerja]", parts.skill.trim(), priority = 30)
        addSection("[Aturan operasi]", parts.agents, priority = 60, maxLines = 10)
        addSection(
            "[Fitur aplikasi Hermes — kenali agar bisa memandu user]",
            parts.features, priority = 10, maxLines = 160
        )
        addSection(
            "[Skill tersimpan — buka dengan skill_view bila tugas cocok]",
            parts.skills, priority = 50
        )
        addSection("[Ingatan tentang user]", relevantMemory(parts.memory, userMessage), priority = 80, footer = MEMORY_GUIDE)
        val todos = activeTodos(parts.todo).take(5).joinToString("\n") { "- [ ] $it" }
        addSection("[Tugas aktif user]", todos, priority = 70, footer = TODO_GUIDE)
        if (now != null) addSection("[Konteks waktu]", timeLine(now), priority = 90)

        fun total() = sections.sumOf { it.words() }
        // Pangkas baris terakhir dari seksi berprioritas terendah dulu.
        while (total() > maxWords) {
            val victim = sections.filter { it.priority < 100 }.minByOrNull { it.priority } ?: break
            if (victim.lines.size > 1) victim.lines.removeAt(victim.lines.lastIndex)
            else sections.remove(victim)
        }
        val joined = sections.joinToString("\n\n") { it.render() }
        // Pengaman terakhir (soul sendiri melebihi batas): potong per baris,
        // struktur baris tetap utuh.
        return if (countWords(joined) <= maxWords) joined else capLines(joined, maxWords)
    }

    /** "Kamis, 27 November 2026, 13.05 (WIB)" — bahasa Indonesia, zona perangkat. */
    internal fun timeLine(now: ZonedDateTime): String {
        val id = Locale("id", "ID")
        val date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH.mm", id))
        val zone = now.zone.getDisplayName(java.time.format.TextStyle.SHORT, id)
        return "Sekarang: $date ($zone). Sapa dan rencanakan sesuai waktu ini."
    }

    /** Teks tugas aktif dari todo.md ("- [ ] ..."), tanpa penanda checkbox. */
    fun activeTodos(todo: String): List<String> =
        todo.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("- [ ]") }
            .map { it.removePrefix("- [ ]").trim() }
            .filter { it.isNotEmpty() }
            .toList()

    private val COMMENT = Regex("<!--.*?(-->|$)", RegexOption.DOT_MATCHES_ALL)

    /** Kata umum Indonesia/Inggris yang tidak bermakna sebagai kata kunci. */
    internal val STOPWORDS = setOf(
        "yang", "dengan", "untuk", "dari", "pada", "dalam", "akan", "atau",
        "juga", "saya", "kamu", "anda", "kami", "kita", "mereka", "tolong",
        "bisa", "bisakah", "dapat", "mohon", "bagaimana", "gimana", "apakah",
        "kenapa", "mengapa", "kapan", "dimana", "mana", "siapa", "berapa",
        "sudah", "belum", "masih", "lagi", "saja", "aja", "sama", "buat",
        "bikin", "jadi", "agar", "supaya", "karena", "kalau", "jika", "tapi",
        "tetapi", "namun", "lebih", "sangat", "banget", "semua", "setiap",
        "this", "that", "with", "from", "what", "have", "please", "about",
        "halo", "hai", "terima", "kasih", "makasih", "oke", "okay"
    )

    /** Skor baris memory dari kata kunci pesan user (≥4 huruf, maks 5 kata). */
    internal fun relevantMemory(memory: String, userMessage: String): String {
        val keywords = userMessage.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 4 && it !in STOPWORDS }
            .distinct()
            .take(5)
        // Buang blok komentar HTML UTUH (bisa multi-baris). Dulu hanya baris
        // yang diawali "<!--" yang disaring, sehingga baris tengah komentar
        // bawaan memory.md ikut terkirim sebagai "ingatan tentang user".
        val lines = memory.replace(COMMENT, "").lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .toList()
        if (lines.isEmpty()) return ""
        if (keywords.isEmpty()) return lines.take(15).joinToString("\n")
        return lines
            .map { line ->
                val low = line.lowercase()
                line to keywords.count { low.contains(it) }
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(8)
            .joinToString("\n") { it.first }
            .ifBlank { lines.take(15).joinToString("\n") }
    }

    internal fun countWords(text: String): Int =
        text.split(Regex("\\s+")).count { it.isNotEmpty() }

    private fun capLines(text: String, maxWords: Int): String {
        val out = StringBuilder()
        var used = 0
        for (line in text.lines()) {
            val w = countWords(line)
            if (used + w > maxWords) {
                val rest = maxWords - used
                if (rest > 0) out.append(line.split(Regex("\\s+")).filter { it.isNotEmpty() }.take(rest).joinToString(" "))
                break
            }
            out.appendLine(line)
            used += w
        }
        return out.toString().trim()
    }
}

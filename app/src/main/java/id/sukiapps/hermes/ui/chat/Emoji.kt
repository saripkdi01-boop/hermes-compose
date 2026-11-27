package id.sukiapps.hermes.ui.chat

/**
 * v19.2 — data emoji untuk chat: katalog picker di composer, reaksi cepat,
 * dan konversi shortcode gaya GitHub (`:rocket:` → 🚀) untuk teks markdown.
 * Murni Kotlin (tanpa Compose/Android) supaya bisa di-unit-test di JVM.
 *
 * Rendering glyph memakai font emoji sistem (+ EmojiCompat bawaan
 * Compose bila tersedia) — tidak ada aset/font tambahan di APK.
 */
object Emoji {

    data class Category(val label: String, val emojis: List<String>)

    /**
     * Reaksi cepat di menu tekan-lama pesan. Sama persis dengan
     * `V3_REACTIONS` (pratinjau chatv3) supaya satu set reaksi di seluruh app.
     */
    val QUICK_REACTIONS = id.sukiapps.hermes.ui.chatv3.V3_REACTIONS

    val CATEGORIES: List<Category> = listOf(
        Category(
            "Wajah",
            listOf(
                "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "😉",
                "😊", "😇", "🥰", "😍", "🤩", "😘", "😋", "😎", "🤓", "🧐",
                "🤔", "🤨", "😐", "😑", "😶", "🙄", "😏", "😌", "😴", "🥱",
                "😮", "😯", "😲", "😳", "🥺", "😢", "😭", "😤", "😡", "🤯",
                "😱", "😨", "😰", "🤗", "🤭", "🫡", "🤫", "😬", "🥲", "🤒"
            )
        ),
        Category(
            "Gestur",
            listOf(
                "👍", "👎", "👌", "✌️", "🤞", "🤟", "🤙", "👋", "🙌", "👏",
                "🙏", "🤝", "💪", "👀", "🫶", "☝️", "👉", "👈", "✋", "🤌"
            )
        ),
        Category(
            "Hati",
            listOf(
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "💔", "❤️‍🔥",
                "💕", "💖", "💯", "✨", "⭐", "🌟", "🔥", "🎉", "🎊", "🥳"
            )
        ),
        Category(
            "Kerja",
            listOf(
                "✅", "❌", "⚠️", "❓", "❗", "💡", "📌", "📎", "📝", "📋",
                "📅", "⏰", "⏳", "🔍", "🔒", "🔑", "🛠️", "⚙️", "🚀", "📈",
                "📊", "💻", "📱", "🤖", "🧠", "📚", "✏️", "📦", "🧪", "🐛"
            )
        ),
        Category(
            "Alam & makanan",
            listOf(
                "☀️", "🌙", "🌧️", "⛅", "🌈", "🌊", "🌱", "🌸", "🍀", "🐱",
                "🐶", "🦋", "☕", "🍵", "🍜", "🍚", "🍕", "🍰", "🍎", "🥭"
            )
        )
    )

    /** Shortcode umum → emoji. Kecil dan eksplisit (bukan seluruh tabel GitHub). */
    val SHORTCODES: Map<String, String> = mapOf(
        "smile" to "😄", "grin" to "😁", "joy" to "😂", "wink" to "😉",
        "blush" to "😊", "heart_eyes" to "😍", "thinking" to "🤔",
        "sweat_smile" to "😅", "sob" to "😭", "cry" to "😢", "angry" to "😠",
        "sunglasses" to "😎", "neutral_face" to "😐", "scream" to "😱",
        "thumbsup" to "👍", "+1" to "👍", "thumbsdown" to "👎", "-1" to "👎",
        "ok_hand" to "👌", "clap" to "👏", "pray" to "🙏", "wave" to "👋",
        "muscle" to "💪", "eyes" to "👀", "raised_hands" to "🙌",
        "heart" to "❤️", "broken_heart" to "💔", "100" to "💯",
        "sparkles" to "✨", "star" to "⭐", "fire" to "🔥", "tada" to "🎉",
        "white_check_mark" to "✅", "heavy_check_mark" to "✔️",
        "x" to "❌", "warning" to "⚠️", "question" to "❓",
        "exclamation" to "❗", "bulb" to "💡", "pushpin" to "📌",
        "memo" to "📝", "calendar" to "📅", "alarm_clock" to "⏰",
        "hourglass" to "⌛", "mag" to "🔍", "lock" to "🔒", "key" to "🔑",
        "wrench" to "🔧", "hammer" to "🔨", "gear" to "⚙️", "rocket" to "🚀",
        "chart_with_upwards_trend" to "📈", "bar_chart" to "📊",
        "computer" to "💻", "iphone" to "📱", "robot" to "🤖", "brain" to "🧠",
        "books" to "📚", "pencil2" to "✏️", "package" to "📦", "bug" to "🐛",
        "zap" to "⚡", "coffee" to "☕", "point_right" to "👉",
        "arrow_right" to "➡️", "information_source" to "ℹ️"
    )

    private val SHORTCODE = Regex(""":([a-z0-9_+\-]{1,32}):""")

    /**
     * Ganti shortcode yang DIKENAL menjadi emoji. Teks di dalam blok kode
     * berpagar (```) dan kode inline (`...`) TIDAK disentuh — `:x:` di
     * potongan kode tetap apa adanya. Shortcode tak dikenal dibiarkan.
     */
    fun replaceShortcodes(text: String): String {
        if (!text.contains(':')) return text
        val out = StringBuilder(text.length)
        var inFence = false
        val lines = text.split('\n')
        for ((li, line) in lines.withIndex()) {
            if (li > 0) out.append('\n')
            if (line.trimStart().startsWith("```")) {
                inFence = !inFence
                out.append(line)
                continue
            }
            if (inFence) {
                out.append(line)
                continue
            }
            // Bagi baris pada backtick: segmen ganjil = kode inline.
            val parts = line.split('`')
            for ((pi, part) in parts.withIndex()) {
                if (pi > 0) out.append('`')
                if (pi % 2 == 1 && pi < parts.size - 1) {
                    out.append(part)
                } else {
                    out.append(
                        SHORTCODE.replace(part) { m -> SHORTCODES[m.groupValues[1]] ?: m.value }
                    )
                }
            }
        }
        return out.toString()
    }

    /** Daftar "baru dipakai": [emoji] di depan, tanpa duplikat, maks [max]. */
    fun pushRecent(recent: List<String>, emoji: String, max: Int = 16): List<String> =
        (listOf(emoji) + recent.filter { it != emoji }).take(max)
}

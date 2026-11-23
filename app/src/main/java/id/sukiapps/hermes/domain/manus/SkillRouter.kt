package id.sukiapps.hermes.domain.manus

/**
 * Skill router mini 14.32 — paket prompt spesialis untuk Hermes Suki.
 *
 * Murni Kotlin (tanpa Android) supaya bisa di-unit-test di JVM.
 * Semua teks prompt di bawah ditulis dari nol untuk agen ini, dalam
 * Bahasa Indonesia, dan disesuaikan dengan kontrak Hermes yang sudah
 * ada (langkah ≤8, argumen tidak dikarang, hasil tanpa bukti = sebagian).
 * Ini BUKAN salinan skill proyek lain: yang diadopsi hanya idenya —
 * "router memilih satu paket prompt spesialis dari jenis tugas, lalu
 * paket itu ditempel ke prompt perencana".
 *
 * Cara kerja: [SkillRouter.pick] mencocokkan kata kunci di judul goal
 * (huruf kecil, pencocokan substring). Bila goal mengandung kata kunci
 * dari dua skill, yang menang adalah yang lebih dulu di daftar ALL —
 * urutannya disengaja: perbaiki dulu (ada masalah konkret), lalu audit,
 * riset, review, dan terakhir rencana (paling umum). Goal tanpa kata
 * kunci apa pun tidak mendapat skill (null) — perencana jalan dengan
 * prompt dasarnya saja, tanpa peran tambahan yang mengada-ada.
 *
 * Prompt ditempel ke prompt sistem LlmPlanner oleh ManusViewModel, dan
 * nama skill tampil di label rencana supaya pemakaian skill tidak
 * diklaim diam-diam. Prompt dijaga pendek (hemat konteks di HP 3 GB).
 */
data class AgentSkill(
    /** Id stabil untuk log/label, mis. "debug". */
    val id: String,
    /** Nama tampil di label rencana, mis. "Debug". */
    val title: String,
    /** Kata kunci pemicu (huruf kecil, dicocokkan substring). */
    val keywords: List<String>,
    /** Teks prompt yang ditempel ke prompt sistem perencana. */
    val prompt: String
)

object SkillRouter {

    val DEBUG = AgentSkill(
        id = "debug",
        title = "Debug",
        keywords = listOf(
            "perbaiki", "debug", "error", "gagal", "bug", "crash",
            "tidak jalan", "tidak bisa", "rusak"
        ),
        prompt = "Kamu debugger untuk tugas ini. Susun langkah yang mengarah " +
            "ke akar masalah, bukan sekadar gejala: (1) reproduksi masalah " +
            "dengan perintah/bacaan konkret, (2) isolasi penyebab dengan " +
            "mempersempit lokasi, (3) perbaikan minimal, (4) verifikasi " +
            "bahwa perbaikan benar-benar menghilangkan gejala. Bila penyebab " +
            "belum bisa dipastikan dari goal, buat langkah baca/riset dulu " +
            "— jangan menebak penyebab dan jangan mengarang nilai argumen."
    )

    val SECURITY_AUDIT = AgentSkill(
        id = "audit-keamanan",
        title = "Audit Keamanan",
        keywords = listOf(
            "audit", "keamanan", "celah", "kerentanan", "vulnerab", "security"
        ),
        prompt = "Kamu auditor keamanan untuk tugas ini. Susun langkah yang " +
            "memeriksa permukaan serangan yang relevan: input yang tidak " +
            "divalidasi, data sensitif yang tersimpan/dikirim, izin dan " +
            "autentikasi, serta dependensi yang dipakai. Tiap temuan harus " +
            "punya bukti (file yang dibaca, output tool, atau pernyataan " +
            "nihil yang eksplisit). Bila tidak ada bukti, hasilnya sebagian " +
            "— jangan pernah menulis 'aman' tanpa bukti."
    )

    val WEB_RESEARCH = AgentSkill(
        id = "riset-web",
        title = "Riset Web",
        keywords = listOf(
            "riset", "cari", "bandingkan", "dokumentasi", "referensi",
            "penelitian"
        ),
        prompt = "Kamu periset untuk tugas ini. Susun langkah yang mengambil " +
            "informasi dari sumber primer (dokumentasi resmi, repositori, " +
            "spesifikasi) sebelum menyimpulkan. Catat URL sumber di detail " +
            "tiap langkah riset. Bedakan fakta dari asumsi di judul/detail " +
            "langkah, dan jangan mengarang URL, angka, atau kutipan."
    )

    val CODE_REVIEW = AgentSkill(
        id = "code-review",
        title = "Code Review",
        keywords = listOf(
            "review", "tinjau", "periksa kode", "nilai kode", "code review"
        ),
        prompt = "Kamu peninjau kode untuk tugas ini. Susun langkah yang " +
            "membaca kode yang relevan dulu, lalu menilai kebenaran logika, " +
            "penanganan error, keterbacaan, dan risiko keamanan dasar. Tiap " +
            "temuan harus merujuk file/fungsi yang dibaca — jangan " +
            "mengomentari kode yang tidak dibaca, dan jangan mengarang " +
            "nomor baris."
    )

    val TASK_PLANNER = AgentSkill(
        id = "perencana-tugas",
        title = "Perencana Tugas",
        keywords = listOf(
            "rencana", "rancang", "susun langkah", "breakdown", "rancangan"
        ),
        prompt = "Kamu perencana tugas untuk tugas ini. Pecah goal menjadi " +
            "langkah-langkah kecil yang masing-masing bisa dieksekusi satu " +
            "tool dan diverifikasi hasilnya. Urutkan dari persiapan/riset " +
            "ke hasil akhir, dan pastikan tidak ada dua langkah yang " +
            "menulis file yang sama bersamaan. Bila ada informasi yang " +
            "belum diketahui dari goal, buat langkah baca/riset dulu untuk " +
            "mendapatkannya."
    )

    /** Urutan = prioritas bila goal cocok di dua skill sekaligus. */
    val ALL: List<AgentSkill> = listOf(
        DEBUG, SECURITY_AUDIT, WEB_RESEARCH, CODE_REVIEW, TASK_PLANNER
    )

    /** Skill pertama yang kata kuncinya muncul di goal, atau null. */
    fun pick(goal: String): AgentSkill? {
        val text = goal.lowercase()
        if (text.isBlank()) return null
        return ALL.firstOrNull { skill ->
            skill.keywords.any { text.contains(it) }
        }
    }
}

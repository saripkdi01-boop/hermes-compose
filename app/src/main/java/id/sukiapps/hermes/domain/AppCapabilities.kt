package id.sukiapps.hermes.domain

/**
 * v9.5 — Deskripsi kemampuan aplikasi Hermes untuk model AI.
 *
 * Disuntik ke system prompt setiap chat dimulai, supaya model yang diajak
 * bicara TAHU fitur apa saja yang dimiliki aplikasi yang menjalankannya
 * dan bisa memandu user memakainya. Bukan tool-calling (model tidak bisa
 * mengeksekusi aksi) — hanya pengetahuan agar jawaban relevan dengan app.
 *
 * Bahasa: Indonesia, ringkas, faktual. Jangan klaim fitur yang tidak ada.
 */
object AppCapabilities {

    /** Blok teks untuk disuntik ke system prompt. */
    fun promptBlock(): String = """
        |KAMU BERJALAN DI DALAM APLIKASI HERMES CHAT (Android, native).
        |Pengguna bicara denganmu lewat aplikasi ini. Fitur aplikasi yang bisa kamu sebutkan / pandu:
        |
        |1. KONEKTOR UPIN — "Tanya Upin": aplikasi ini terhubung ke agen AI kedua
        |   bernama Upin (berjalan di server pribadi user via SSH). User mengetik
        |   pertanyaan di kolom chat lalu pilih "Tanya Upin" dari menu +. Jawaban
        |   Upin tiba dalam 1-2 menit (diambil otomatis). Cocok untuk tugas yang
        |   butuh akses server/file user. Syarat: forward SSH aktif di Termux
        |   (ssh -L 8766:127.0.0.1:8766 muse-vm -N) + secret tersimpan di Pengaturan.
        |
        |2. AMBIL DARI WEB — menu + → "Ambil dari web": user tempel URL, aplikasi
        |   mengambil isi halaman sebagai teks lalu memasukkannya ke kolom chat
        |   untuk kamu baca. Kamu TIDAK browsing sendiri; minta user pakai fitur ini
        |   bila butuh isi halaman web.
        |
        |3. LAMPIRAN — menu + → foto galeri / kamera / dokumen: user bisa melampirkan
        |   gambar dan file ke pesan. Gambar dikirim ke model yang mendukung vision.
        |
        |4. BACA DENGAN SUARA (TTS) — setiap jawabanmu ada tombol speaker; user bisa
        |   mendengarkan jawaban dibacakan dalam Bahasa Indonesia.
        |
        |5. MODEL & PERSONA — user bisa ganti model AI dan persona/instruksi sistem
        |   lewat Pengaturan. Riwayat chat tersimpan per percakapan di drawer kiri.
        |
        |6. TERMUX — user menjalankan Termux (terminal Android); beberapa fitur
        |   (SSH forward Upin, git, dsb.) memakai perintah di sana.
        |
        |PANDUAN: bila user bertanya hal yang butuh salah satu fitur di atas,
        |arahkan ke fitur yang tepat dengan langkah konkret. Jangan mengarang
        |cara pakai di luar daftar ini.
    """.trimMargin()
}

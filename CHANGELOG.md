# Changelog

Format: versi aplikasi (`versionName` / `versionCode`). Perubahan perilaku
yang terlihat user ditandai **[perilaku]**.

## 20.1 (65) — toolkit agen ala Hermes Agent desktop

Padanan tool inti [Hermes Agent](https://hermes-agent.nousresearch.com/) (Nous
Research, MIT) diimplementasikan ulang secara native untuk Android — bukan
port kode Python-nya. Semua tool ditawarkan ke model di chat, lewat
`PermissionGate` (tercatat di Riwayat Izin) bila mengubah data/menjalankan
kode/memakai kuota, dan gagal tertutup.

| Tool | Fungsi | Izin |
|---|---|---|
| `todo` | kelola `todo.md` Jiwa Hermes (list/add/done/undo/remove) | LOW untuk perubahan |
| `session_search` | cari riwayat chat lama (tanpa incognito) | – (baca saja) |
| `skills_list` / `skill_view` | daftar & isi skill tersimpan | – |
| `skill_manage` | create/edit/rename/delete skill (`filesDir/agent/skills/*.md`, ≤12 KB, ≤50) | MEDIUM |
| `execute_code` | Python 3.11 on-device (Chaquopy), timeout 30 dtk, output ≤20 KB | **HIGH** (selalu popup) |
| `cronjob` | tugas terjadwal (WorkManager) → model → chat "⏰ nama" + notifikasi | **HIGH** saat membuat |
| `delegate_task` | 1–3 sub-agen paralel tanpa tool, timeout 90 dtk | LOW |

- **[perilaku]** Daftar skill tersimpan ikut dirakit ke system prompt
  (`[Skill tersimpan …]`) agar Hermes tahu kapan memanggil `skill_view`.
- Penyimpanan baru: `filesDir/agent/skills/`, `filesDir/agent/cron_jobs.json`
  (bukan Room — skema kontrak tidak berubah; tidak ikut backup ZIP).
- `HermesDao.searchMessages` = query baru saja (skema tetap v6).
- Tidak tersedia di HP: otomasi browser, terminal penuh di luar
  sandbox/Termux, `web_search` (butuh keputusan penyedia + API key).
- Test baru: `TodoMdTest`, `SkillStoreTest`, `CronJobsTest`, `AgentToolsTest`.
- Referensi lengkap: `docs/TOOLS.md`.

## 20.0 (64) — gaya visual Muse

### Tampilan
- **[perilaku]** **Gaya "Muse" jadi default**: dark-first near-black
  (`#0A0A0A`), permukaan abu netral, jawaban Hermes tanpa bubble, pesan
  user dalam bubble abu tipis, aksen coral (`#E07856` gelap / `#B4552D`
  terang). Diambil dari spesifikasi `MuseChatTheme.kt` yang sudah ada di
  repo tetapi belum pernah dipakai.
- **[perilaku]** Pilihan **Pengaturan → Tampilan → Gaya: Muse / Hangat**
  (key prefs baru `ui_style`, aditif). "Hangat" = palet krem/cokelat
  v15–v19. Gaya, tema, dan aksen langsung berlaku tanpa restart.
- **[perilaku]** Semua peran warna Material 3 kini diturunkan dari palet
  Hermes. Sebelumnya `surfaceContainer*`/`secondary*`/`tertiary*` jatuh ke
  warna bawaan Material yang keunguan (panel emoji, bottom bar, menu, sheet
  tampak lavender).
- **[perilaku]** **Top bar minimal**: menu │ judul + "model · token" di
  tengah │ chat baru + menu ⋮ (Ekspor chat, Fitur agen, Pengaturan). Dulu 6
  ikon + pil model berderet dan model tampil dua kali.
- **[perilaku]** Tombol **"+"** di kotak input membuka **sheet alat** (grid:
  Galeri, Kamera, Dokumen, Ambil dari web, Tanya Upin, Emoji) menggantikan
  dropdown.
- **[perilaku]** **Riwayat chat dikelompokkan per waktu**: Disematkan · Hari
  ini · Kemarin · 7 hari terakhir · 30 hari terakhir · Lebih lama (header
  ditandai heading untuk TalkBack).

### Batas yang dijaga
- Tidak ada logo, nama, ikon kustom, font, atau teks milik produk lain yang
  disalin — nama "Hermes", ikon H, dan bahasa Indonesia tetap. Yang diikuti
  adalah gaya: warna, tata letak, spasi, dan pola UX.

### Developer
- Test baru: `DrawerGroupsTest`, `MuseStyleTest` (kontras palet ≥ 7:1 /
  4.5:1, peran permukaan bertahap, urutan tile "+").

## 19.4 (63) — ikon adaptive, kebersihan repo, CI lebih ketat

### Tampilan
- **[perilaku]** Ikon launcher kini **adaptive** (mengikuti bentuk ikon
  launcher HP: lingkaran/squircle) dan mendukung **themed icons** Android 13+
  (lapisan monochrome). Desain sama persis: kotak gelap `#181C24` + huruf H
  emas `#F5B43C`, kini sebagai vector tajam di semua kepadatan layar.

### Kebersihan repo (tanpa perubahan perilaku)
- Dihapus: 37 file `.bak`, `hermes-source.zip` + `hermes-source-full.zip`,
  `patch_hermesapp*.py`, 12 file `.kt` di luar source set (`/data`, `/nav`,
  `/ui`), paket mati `net/` (5 file) dan `nav/SettingsNav.kt` (tidak pernah
  dipanggil). Semua tetap bisa dipulihkan dari riwayat git.
- Kebijakan "file lama wajib punya `.bak`" diganti riwayat git (komentar di
  `ManusReducer.kt` diperbarui).

### CI
- `build-gate` (setiap branch & PR) kini menjalankan `assembleDebug`
  `testDebugUnitTest` `lintDebug` dengan Gradle 8.11.1 (sama dengan
  `build.yml`) dan mengunggah laporan test/lint.

### PR lama
- #10, #14, #15, #17, #19, #41 ditutup (sudah tergantikan; alasan di tiap PR).

## 19.3 (62) — Jiwa Hermes ala asisten pribadi

### Baru
- **[perilaku]** Layar chat kosong menyapa sesuai jam ("Selamat pagi ☀️",
  "Selamat malam 🌙") dan menampilkan hingga 2 **tugas aktif** dari
  `todo.md` sebagai saran "Bantu aku: …".
- **[perilaku]** Opsi **Bacakan jawaban otomatis** (Pengaturan → Persona →
  Suara; default mati): jawaban baru dibacakan TTS Indonesia setelah selesai
  (tidak dibacakan bila dihentikan dengan Stop atau error). Key prefs baru
  `tts_auto` (aditif).
- `soul.md` bawaan lebih hangat dan konsisten — **hanya untuk instalasi
  baru**; `soul.md` milik user tidak pernah ditimpa.

### System prompt (Jiwa)
- **[perilaku]** Anggaran ±1500 kata kini berprioritas: yang dipangkas dulu
  manual fitur → skill → aturan operasi; **ingatan & todo dipertahankan**.
  Struktur baris tidak lagi diratakan.
- **[perilaku]** Konteks waktu lokal (hari, tanggal, jam, zona) + panduan
  memakai ingatan secara alami (tanpa mengarang) + saran proaktif dari tugas
  aktif (satu kalimat, tanpa memaksa).
- **[perilaku]** Ingatan relevan dipilih **per pesan** (dulu sekali per
  percakapan dari pesan pertama), jadi topik baru di tengah chat mendapat
  ingatan yang cocok dan fakta yang baru ditulis langsung terpakai. Bagian
  dinamis (ingatan, todo, waktu) diletakkan di akhir prompt agar awalan
  yang sama tetap bisa di-cache provider.
- Kata umum ("yang", "bagaimana", "tolong", …) tidak lagi dihitung sebagai
  kata kunci ingatan; komentar HTML di `memory.md` tidak lagi bocor sebagai
  "ingatan"; `features.md` tidak lagi dipotong di 40 baris.
- `features.md` disegarkan dari assets sekali per proses (dulu ±6 tulis
  disk per rakitan prompt).

### Developer
- `SoulPromptTest` (urutan, anggaran, komentar, stopword, waktu, todo),
  `GreetingTest`.

## 19.2 (61) — UI modern, emoji & Pengaturan ringkas

### Baru
- **[perilaku]** **Emoji picker** di kotak input (tombol 😊 di dalam kolom
  teks): kategori Wajah/Gestur/Hati/Kerja/Alam + "Terakhir"; ketuk kotak
  teks untuk kembali ke keyboard.
- **[perilaku]** **Reaksi emoji di chat utama** (sebelumnya hanya ada di
  layar pratinjau chatv3): tekan lama pesan → 👍 ❤️ 😂 😮 😢 🙏; ketuk
  lencana untuk melepas. Disimpan lokal di `hermes_reactions` (bukan Room,
  tidak ikut backup); di chat incognito hanya di memori.
- Shortcode `:rocket:`, `:white_check_mark:`, dll. di jawaban tampil sebagai
  emoji (blok kode tidak disentuh).

### Tampilan & aksesibilitas
- **[perilaku]** Edge-to-edge resmi (`enableEdgeToEdge`): bar status &
  navigasi transparan, warna ikonnya mengikuti tema aplikasi; padding atas
  ganda, bottom bar di bawah tombol navigasi sistem, dan celah di atas
  keyboard diperbaiki (insets dikonsumsi di NavHost).
- **[perilaku]** Bottom bar berbahasa Indonesia (Aktivitas · Alat · Chat ·
  Agen · Pengaturan), lebar tab merata, diumumkan sebagai tab ke pembaca
  layar.
- **[perilaku]** Layar Pengaturan ringkas: 13 penjelasan panjang kini satu
  baris pendek + tombol **Selengkapnya**.
- Tombol aksi pesan (Bacakan/Salin/Buat ulang) kembali ke area sentuh 48 dp
  (dulu dipaksa 32 dp). Label sumber balasan sejajar nama "Hermes".
- Animasi halus saat item chat bergeser; pesan user baru muncul dengan fade.
- Ikon arah (Kirim, Chat, Volume) memakai varian AutoMirrored.

### Developer
- `EmojiTest` (shortcode, kode inline/berpagar, daftar terakhir).
- `isLastAssistant` dihitung sekali per daftar (bukan O(n²)).

## 19.1 (60) — audit stabilitas & keamanan

Detail temuan + alasan: [docs/AUDIT-v19.md](docs/AUDIT-v19.md).

### Diperbaiki
- **[perilaku]** Tombol **Stop** kini benar-benar memutus koneksi HTTP ke
  provider (dulu hanya UI yang berhenti; token tetap mengalir sampai server
  selesai). Berlaku untuk chat biasa dan chat dengan tools.
- **[perilaku]** **Restore backup tidak lagi membuat aplikasi crash** saat
  dibuka. Tipe prefs (Long/Float/Int/StringSet) dipulihkan dengan benar.
- **[perilaku]** **Buat ulang jawaban** mengganti jawaban lama (tidak lagi
  menumpuk dua jawaban untuk satu pertanyaan, dan pertanyaan tidak terkirim
  ganda ke model).
- **[perilaku]** Ketuk Kirim dua kali dengan cepat tidak lagi mengirim
  pesan ganda.
- **[perilaku]** Koneksi yang putus di tengah jawaban (chat dengan tools)
  kini tampil sebagai error "Stream terputus… Coba lagi" — bukan jawaban
  terpotong yang tersimpan seolah utuh.
- **[perilaku]** Konektor Termux manual & Serverless kini benar-benar
  mengirim perintah ke Termux (intent eksplisit ke `RunCommandService`).
- **[perilaku]** Layar Pengaturan tidak lagi crash di Android 8.1 (API 27)
  saat membuka seksi Tentang.
- Restore memvalidasi isi database dulu; ZIP rusak tidak lagi menimpa data
  yang sedang dipakai.
- Backup tidak lagi menutup database aplikasi yang sedang berjalan.

### Keamanan & privasi
- API key provider hanya dikirim ke host base URL di Pengaturan (dulu ke
  semua host yang memakai HTTP client bersama).
- Perintah Termux tidak lagi disiarkan sebagai broadcast implisit yang bisa
  diterima aplikasi lain.
- **[perilaku]** Backup tidak lagi memuat percakapan incognito (berkas DB
  dibersihkan + VACUUM) maupun rahasia konektor `secret_enc_*`. Setelah
  restore di HP lain, token GitHub/Telegram perlu diisi ulang — sama seperti
  API key sejak dulu.

### Developer
- `.gitignore` ditambahkan.
- Unit test baru: `SseClientCancelTest` (server SSE lokal), `AuthScopeTest`,
  `BackupPrefsTest`, `HermesMigrationTest` (migrasi 4→5→6 di SQLite asli).
- Dependensi **test-only** baru: `org.xerial:sqlite-jdbc` (tidak masuk APK).
- README disesuaikan dengan kode (compileSdk 35, Room v6).
- `gradle lintDebug`: 0 error (sebelumnya 1 error NewApi).

## 19.0 (59)
- Target share sheet (ACTION_SEND → draf chat baru) + mode chat incognito
  (skema Room v6). Lihat PR #49.

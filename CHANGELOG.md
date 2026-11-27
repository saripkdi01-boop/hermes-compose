# Changelog

Format: versi aplikasi (`versionName` / `versionCode`). Perubahan perilaku
yang terlihat user ditandai **[perilaku]**.

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

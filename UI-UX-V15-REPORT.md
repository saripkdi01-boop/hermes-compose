# UI/UX v15 — Hermes Compose ("lebih simpel dan lebih powerful")

- **Tanggal:** 21 Nov 2026
- **Branch:** `ui-ux-v15-simpel-powerful` (dari main `ce97e03`)
- **Perangkat referensi (KOREKSI 21 Nov 02:51 WITA):** UTAMA = Oppo A76
  istri Suki (CPH2375, RAM 6+6 GB, layar 720×1612, Android 12).
  A5s (CPH1909, 3 GB, 720×1520) tetap perangkat KEDUA — minSdk 27
  dipertahankan supaya APK tetap bisa dipasang di keduanya. Jangan klaim
  hasil uji di satu perangkat sebagai hasil di perangkat lain.
  Risiko: A76 adalah HP istri — ketersediaan/dicas tidak dijamin.
- **Status:** verifikasi statis LOLOS SEMUA (lihat bawah). **CI BELUM
  berjalan** — push ke GitHub tidak mungkin dari VM ini (tanpa auth).
  JANGAN merge sebelum kedua workflow hijau. Hasil kerja dikirim sebagai
  patch bundle terverifikasi (lihat §6).

## 1. Rekonsiliasi Fase 2/3 (commit 491190a)

Diff kotor 13 Nov (17 file, 568 insersi) direview file-per-file dan
dipertahankan apa adanya: semua item DESIGN-SPEC §1–§7, §9–§10 cocok
dengan implementasi (bubble `userBubble`, kursor streaming,
`codeBackground`+Toast, quote/H1, kartu error inline + retry,
drawer empty state + grup pin, kontras ChatListScreen, kartu Keystore,
status model di empty state). Tidak ada yang meragukan yang perlu
diperbaiki. Satu-satunya pengecualian: `ChatListScreen` (lihat §3).

## 2. Fase 3 — Pengaturan: LazyColumn + stickyHeader + chip "Lompat ke:" (§8 DESIGN-SPEC)

`SettingsScreen.kt` (dulu 1.542 baris, satu `Column` + `verticalScroll`):

- Migrasi ke `LazyColumn(state = listState)` + `stickyHeader` per seksi
  (16 seksi). Judul seksi pindah dari dalam kartu ke header sticky
  (`SettingsSectionTitleRow`, gaya judul sama: titleMedium SemiBold
  0,15 sp, primary). Isi kartu tidak berubah — `SettingsSectionBody`
  memakai styling `SettingsSectionCard` lama (radius 20 dp,
  surfaceContainerLow, padding 20/16 dp).
- Chip "Lompat ke:" (Model, Persona, Tampilan, Koneksi, Agen, Backup,
  API Key) sebagai `stickyHeader` di bawah TopAppBar — selalu terlihat,
  digulir horizontal bila tidak muat 360 dp. Ketuk chip →
  `animateScrollToItem(headerIndex)`. Indeks bersifat STATIS dan
  terdokumentasi di kode: item 0 = profil, item 1 = chip lompat,
  tiap seksi = 2 item (header `2+2i`, isi `3+2i`). Tidak ada seksi
  kondisional di level daftar; kartu peringatan Keystore ikut di dalam
  item profil supaya indeks tidak bergeser.
- **Keputusan penting — state diangkat (hoisting):** body seksi dulu
  memakai `remember` lokal (field edit Koneksi/Agen, pesan, launcher
  export/import, switch self-upgrade). LazyColumn membuang item di luar
  viewport, jadi state itu akan hilang saat digulir. Semua state itu
  dipindah ke level `SettingsScreen` (pindah blok utuh, tanpa ubah
  logika). `registryTools = remember { ToolRegistry.all() }` dibiarkan
  di tempat (derived, murah, stabil).
- `SettingsSectionCard` dihapus (nol pemanggil setelah migrasi);
  diganti `SettingsSectionBody` + `SettingsSectionTitleRow` (tanpa
  duplikasi gaya judul). Import `PersonaPreset` yang tidak terpakai
  (sudah mati sejak main) ikut dihapus.
- Tidak ada library baru; hanya Material 3 (`AssistChip`,
  `LazyColumn`, `stickyHeader`) + token yang sudah ada.

## 3. ChatListScreen — diputuskan: HAPUS (bukan dipasang)

Bukti: 0 pemanggil di seluruh `app/src` + `app/src/test`, dan seluruh
paket `ui/chatlist/` (5 file) juga 0 referensi dari luar paketnya
sendiri — termasuk `ChatListStore`, `ChatListViewModel`,
`HermesChatProvider`, `ChatListModels` (interface `ChatProvider`
didefinisikan di dalam paket itu). Fungsi "daftar chat" sudah dilayani
drawer riwayat. Memasang layar ini berarti menambah destinasi nav +
  wiring ViewModel untuk duplikat drawer — melawan "lebih simpel".
- Dihapus: `ui/chatlist/` seutuhnya (5 file, 889 baris).
  Perbaikan kontras Fase 2 di `ChatListScreen` ikut terhapus — sengaja,
  karena layarnya tidak ada lagi.
- Sisa: satu komentar historis di `ThemeLegacy.kt:29` yang menyebut
  nama `ChatListScreen` (tidak fungsional, dibiarkan).
- Tidak disentuh (ranah PR kebersihan terpisah): duplikasi `JsonStore`,
  stack `net/` mati, 37 file `.bak`, zip source, 12 file `.kt` di luar
  source set (sudah ada sebelum v15).

## 4. "Powerful" tanpa fitur baru

Dipastikan hidup (bukan hanya di diff): kartu error inline + tombol
"Coba lagi" (`retry()` + `clearError()`), salin kode + Toast "Kode
disalin.", kursor streaming (`streamCaret`), typing berfase 0/150/300
ms, chip saran empty state + status model + "Buka Pengaturan", empty
state drawer + "Mulai chat" / "Bersihkan pencarian", grup pin
"Disematkan"/"Semua percakapan". Tidak ada fitur baru yang ditambah.

## 5. Versi

`versionCode 55 → 56`, `versionName "14.31" → "15.0"`
(`app/build.gradle.kts`). minSdk TETAP 27, targetSdk 34. Tidak ada
dependensi baru. Komentar soal Chaquopy tidak diubah.

## 6. Verifikasi (statis — build lokal mustahil di VM ini)

Skrip: `/tmp/verify_v15.py` (di VM; tidak ikut patch).
Hasil: **ALL CHECKS PASSED** — kurung 30 file .kt, semua import
`id.sukiapps.hermes.*` resolve, NOL file .kt baru di luar `app/`
(12 file lama tetap 12), NOL secret di diff, 16 `settingsSection`
urut sesuai dokumen, kunci item unik, indeks chip lompat valid,
opt-in `ExperimentalFoundationApi` ada, NOL referensi `ui.chatlist`
tersisa, NOL import tak terpakai di `SettingsScreen`.
- Build lokal TIDAK dicoba ulang: pola gagal Gradle daemon (RAM VM
  ~850 MB bebas) sudah terbukti 2× di BUILD-VERIFICATION.md + 4× di
  Fase 2 — bukan bukti kode salah, tapi juga bukan bukti kompilasi.
- **Yang HARUS hijau sebelum merge:** `build-gate` (assembleDebug)
  DAN `Build Hermes Compose` (assembleRelease + testReleaseUnitTest,
  termasuk 15 test `BuiltinToolsTest` + test `domain/manus`).
- **Belum terbukti:** kompilasi, test lolos, ukuran APK, uji tap di
  A76 maupun A5s. Jangan klaim "selesai" sebelum CI hijau + uji HP.

## 7. Cara menerapkan (push mustahil dari VM)

- `git push --dry-run` gagal (tanpa auth), `gh auth status`:
  tidak login. Maka: patch bundle di
  `~/workspace/hermes-apk-build/` (lihat laporan koordinator untuk
  nama file persis) — `git format-patch main...ui-ux-v15-simpel-powerful`.
  Terapkan di mesin yang punya akses push: clone baru main →
  `git apply`/`git am` → `git push origin ui-ux-v15-simpel-powerful`
  → buka PR ke main → tunggu kedua workflow hijau → Suki ketuk merge.
- Verifikasi bundle: diterapkan ke clone baru main di `/tmp` dan
  cek statis diulang (lihat laporan koordinator).

## 8. Yang masih butuh Suki

- Merge hanya setelah CI hijau (ketuk Suki).
- Uji tap APK di **A76** (utama) lalu **A5s** (kedua): Pengaturan
  (gulir + chip lompat + edit field lalu gulir jauh dan kembali),
  chat (error inline, salin kode, streaming), drawer.
- K1–K6 (keputusan pemilik Hermes Compose yang masih terbuka) tidak
  disentuh patch ini — tetap menunggu Suki.
- Bila CI merah: diagnosis dari log CI dulu; kemungkinan terbesar
  kompilasi UI Fase 2 / lint minSdk 27 atas codebase lama (bukan 5
  tool baru — konsisten secara internal).

## 9. Yang di-skip (ponytail)

- Redesain total top bar / navigasi bawah; syntax highlighting blok
  kode; wiring ChatListScreen ke nav; migrasi JsonStore/net/.bak
  (PR kebersihan); test UI Compose untuk chip lompat (tidak ada
  infra ui-test di repo — verifikasi indeks dilakukan statis).

# Audit Hermes Compose v19.0 → v19.1

Tanggal: 27 Nov 2026 · Basis: `main` @ `b321cd4` (merge PR #49, v19.0, versionCode 59)
Cakupan: build/CI, streaming SSE, keamanan API key, backup/restore, migrasi Room,
Jiwa Hermes (SoulPrompt), konektor, permission gate + audit log, UX/aksesibilitas, performa.

Prioritas: **P0** = crash / kehilangan data / keamanan · **P1** = bug nyata yang
merusak pengalaman atau kontrak · **P2** = kebersihan, konsistensi, utang teknis.
Status: ✅ diperbaiki di PR ini · 🟡 dijadwalkan PR berikutnya · ⏸ butuh keputusan Kiki.

## 0. Kesehatan build (baseline sebelum perubahan)

| Cek | Hasil |
|---|---|
| `gradle :app:assembleDebug` (Gradle 8.14.3, AGP 8.7.3, JDK 21, SDK 35) | ✅ lulus (APK debug 56 MB, 2 ABI Chaquopy) |
| `gradle :app:testDebugUnitTest` | ✅ 176 test, 0 gagal |
| CI `main` terakhir (run 45151352077) | ✅ hijau |
| Catatan | Percobaan pertama gagal karena Maven Central membalas **429 (rate limit)** saat unduh dependensi — bukan masalah kode; ulang = lulus. |

## 1. Temuan P0

| # | Area | Temuan | Dampak | Status |
|---|---|---|---|---|
| P0-1 | Backup/restore | `prefs.json` tidak membawa tipe. Saat restore, `current_conv` (Long) dibaca balik org.json sebagai `Integer` → `putInt` → `AppPrefs.currentConvId` memanggil `getLong` → **ClassCastException di init ChatViewModel**. Float bulat (`temperature` 1.0 → `1`) dan StringSet ikut rusak. | Setiap restore membuat aplikasi **crash setiap kali dibuka**; satu-satunya jalan keluar = hapus data aplikasi (= kehilangan data). | ✅ `BackupPrefs.decode` sadar-tipe (skema key → tipe di HP → inferensi) + getter `currentConvId` toleran. Test: `BackupPrefsTest`. |
| P0-2 | Streaming SSE | Producer `callbackFlow` berjalan blocking (`execute`/`readUtf8Line`) di thread IO; `awaitClose { call.cancel() }` baru tercapai **setelah** stream selesai. Tombol **Stop hanya menghentikan UI** — koneksi HTTP tetap membaca token sampai server selesai. | Token tetap mengalir (dan ditagih provider), thread IO tertahan, bisa berlapis bila user mengirim lagi. | ✅ Baca socket dipindah ke coroutine anak; producer langsung menunggu di `awaitClose { call.cancel() }` yang berjalan seketika saat pengoleksi batal (`stream()` dan `streamWithTools()`, plus cek `isActive` sebelum/ sesudah membuat call iterasi tool berikutnya). Catatan: percobaan pertama memakai `invokeOnCompletion` — **gagal di test** karena handler itu baru jalan saat Job selesai, sedangkan body masih terblokir; test menunggu 10 dtk persis seperti bug aslinya. Test: `SseClientCancelTest` (server SSE lokal sungguhan; socket kini diputus dalam 0,2–0,5 dtk). |
| P0-3 | Konektor Termux (manual) | `TermuxConnector.send` memakai `sendBroadcast` dengan intent **implisit** `com.termux.RUN_COMMAND` tanpa package. Kontrak resmi Termux adalah `startService` ke `RunCommandService` (jalur tool `termux_exec` sudah benar). | (a) Konektor manual & Serverless **tidak pernah benar-benar menjalankan perintah**; (b) aplikasi lain yang mendaftarkan receiver untuk action yang sama bisa **menerima perintah shell user** (beserta token/path di dalamnya). | ✅ Intent eksplisit `ComponentName("com.termux", "com.termux.app.RunCommandService")` + `startService`; chokepoint menolak intent non-Termux. Menggantikan sisa PR #10. |
| P0-4 | Incognito × backup | DAO menyaring incognito dari daftar/pencarian, tetapi backup **menyalin file `hermes_chat.db` mentah** — percakapan incognito yang sedang aktif ikut masuk ZIP. Klaim v19 "tidak pernah masuk backup" tidak benar untuk file DB. | Kebocoran privasi pada fitur yang dijanjikan privat. | ✅ Backup memakai snapshot sementara → hapus baris incognito → `VACUUM` (sisa halaman ikut hilang) → baru di-ZIP. DB aktif tidak disentuh. |
| P0-5 | Kompatibilitas Android 8.1 | `SettingsScreen` (seksi Tentang) memanggil `PackageInfo.longVersionCode` (API 28) padahal minSdk 27. Ditemukan `gradle lintDebug` (error NewApi); CI tidak menjalankan lint penuh (`lintVital` saat release tidak menganggapnya fatal). | `NoSuchMethodError` → **layar Pengaturan crash di Android 8.1**. | ✅ `PackageInfoCompat.getLongVersionCode`. Lint kini 0 error. |

## 2. Temuan P1

| # | Area | Temuan | Status |
|---|---|---|---|
| P1-1 | Keamanan API key | `AuthInterceptor` menempelkan `Bearer <api_key>` ke **semua host** yang lewat OkHttpClient singleton. Saat ini pemakainya memang hanya base URL, tetapi satu pemakaian baru untuk host lain (konektor/web fetch) cukup untuk membocorkan key. `ConnectorTester.testApiKey` juga mengirim **dua** header Authorization. | ✅ Key hanya untuk scheme+host+port base URL (`AuthScope`), tidak menggandakan header yang sudah ada. Test: `AuthScopeTest`. |
| P1-2 | Backup | `backup()` memanggil `db.close()` pada database Room singleton lalu aplikasi jalan terus. Observer Flow Room berisiko berhenti / query gagal sampai restart. `[PERLU VERIFIKASI di HP: gejala daftar chat tidak ter-update setelah backup]` | ✅ Tidak lagi menutup Room; snapshot konsisten diambil di dalam `runInTransaction` (kunci tulis). |
| P1-3 | Restore | DB dari ZIP langsung menimpa DB aktif sebelum divalidasi; ZIP rusak/terpotong = DB aktif rusak. | ✅ Ekstrak ke file sementara, cek header `SQLite format 3`, baru ganti. |
| P1-4 | Backup | Rahasia konektor `secret_enc_*` (PAT GitHub, token Telegram, key provider cadangan) ikut `prefs.json`. Ciphertext terikat AndroidKeyStore HP asal; restore di HP sama bisa menimpa token baru dengan token lama. | ✅ Dikecualikan saat backup **dan** restore (selaras aturan "api_key tidak ikut backup"). |
| P1-5 | Chat | **Regenerate** menambah jawaban kedua di bawah jawaban lama (dua jawaban untuk satu pertanyaan) dan riwayat ke model memuat pertanyaan yang sama dua kali. | ✅ Jawaban setelah pesan user terakhir dihapus dulu (pola sama dengan Edit). |
| P1-6 | Chat | Ketukan ganda cepat pada Kirim bisa mengirim dua kali (`isStreaming` baru true setelah tulis DB). | ✅ Penjaga `sendInFlight`. |
| P1-7 | Streaming + tools | `streamWithTools` memperlakukan putus koneksi di tengah stream sebagai selesai normal → jawaban terpotong tersimpan seolah utuh (perbaikan B6 v14.29 hanya ada di `stream()`). Error provider di jalur ini juga kehilangan flag `retryable`. | ✅ Jadi `AIHttpException` "Stream terputus…" (retryable); error provider memakai klasifikasi `SseDecoder`. Test: `SseClientCancelTest`. |
| P1-8 | UI / insets | Aplikasi edge-to-edge (`setDecorFitsSystemWindows(false)`) tetapi **tidak ada satu pun penanganan WindowInsets** selain `imePadding()` di composer: Scaffold luar memberi padding status bar dan TopAppBar layar dalam memberi lagi (padding atas ganda), bottom bar kustom tidak memberi padding navigation bar, dan `imePadding` di chat ditambah padding bottom bar → celah di atas keyboard. `[PERLU VERIFIKASI visual di HP]` | ✅ v19.2 (PR UI) |
| P1-9 | Aksesibilitas | Tombol aksi bubble (bacakan/salin/buat ulang) dipaksa 32 dp → area sentuh < 48 dp. Bottom bar tanpa semantik tab terpilih. | ✅ v19.2 (PR UI) |
| P1-10 | Emoji/reaksi | `V3_REACTIONS`/`toggleReaction` hanya hidup di rute pratinjau `chatv3preview`; chat utama (`HermesChatScreen`) **belum punya reaksi** sama sekali (`UiMessage.reaction` selalu null). Tidak ada emoji picker di composer. | ✅ v19.2 (PR UI) |
| P1-11 | Jiwa Hermes | `AgentFiles.read()` memanggil `seedIfNeeded()` tiap baca, dan `features.md` **ditulis ulang dari assets setiap kali** → ±6 tulis disk per rakitan prompt. `capWords` memotong dari ekor (ingatan & todo hilang duluan) dan meratakan semua baris jadi satu paragraf. Kata kunci ingatan tidak menyaring kata umum ("yang", "bagaimana", "tolong"). | 🟡 PR Jiwa. |

## 3. Temuan P2

| # | Temuan | Status |
|---|---|---|
| P2-1 | Repo tanpa `.gitignore` (risiko ter-commit `local.properties`, `build/`, keystore). | ✅ Ditambahkan. |
| P2-2 | Kebersihan repo: 37 file `.bak`, `hermes-source.zip` + `hermes-source-full.zip` (±400 KB), 12 file `.kt` di luar source set (`/data`, `/nav`, `/ui`), paket `net/` mati (ChatRepository, SseClient, AuditLog, ConnectorTester, ActivityLogger — 0 pemakai di luar paket), `JsonStore` ganda (`data/` & `data/store/`), `Ui.java` tanpa pemakai. Laporan v15 sengaja menunda ini ke "PR kebersihan". | ⏸ Kiki — `ManusReducer.kt` merujuk `.bak` sebagai cadangan sengaja; tidak dihapus tanpa persetujuan. |
| P2-3 | README menyimpang dari kode: compileSdk **35** (README 34), Room **version 6** (README 5), komentar Chaquopy menyebut minSdk 30 padahal **27**. | ✅ README diperbarui. |
| P2-4 | CI: `build-gate.yml` memakai Gradle bawaan runner (tak di-pin; komentar menyebut 9.8.1) sementara `build.yml` mem-pin 8.11.1; build-gate hanya `assembleDebug` tanpa unit test; lint tidak dijalankan di CI mana pun. | ⏸ Rekomendasi (tidak diubah: konfigurasi pipeline milik maintainer). |
| P2-5 | Label bottom bar campur bahasa ("Tools", "Agent", "Settings" vs "Aktivitas"). | ✅ v19.2 (PR UI) |
| P2-6 | `isLastAssistant` dihitung dengan `all.lastOrNull{}` di dalam `items{}` → O(n²) per recomposition pada chat panjang. | ✅ v19.2 (PR UI) |
| P2-7 | Tema window `@android:style/Theme.Material.NoActionBar` selalu gelap. Dibiarkan: default tema app juga **gelap** (`theme` = "dark"), jadi tema window DayNight justru akan memunculkan kilatan terang bagi mayoritas user. Ikon launcher hanya PNG (tanpa adaptive/monochrome, tanpa xxxhdpi). | ⏸ Ikon butuh aset dari Kiki. |
| P2-8 | `userMsgCount` (pemicu write-back ingatan tiap 10 pesan) tidak di-reset saat pindah percakapan. | ✅ Di-reset di `openConversation`. |

## 4. Yang diperiksa dan dinilai sehat

- **Migrasi Room 4→5→6**: dijalankan pada SQLite sungguhan dari DDL legacy v4 — data utuh, PK NOT NULL, default `"0"` cocok dengan `@ColumnInfo`, index `idx_messages_conv` ada, autoincrement berlanjut. Test baru: `HermesMigrationTest`.
- **PermissionGate**: Mutex + `CompletableDeferred`, HIGH selalu popup tanpa "ingat", semua jalur dicatat `AuditLog`. Tidak ditemukan bypass.
- **Logging**: tidak ada `Log.*`/`println` yang memuat key/token; tidak ada `HttpLoggingInterceptor`.
- **Manifest**: `allowBackup=false`, `usesCleartextTraffic=false`, provider & receiver `exported=false`.
- **SseDecoder**: kasus `"null"` string, error provider, ParseFailed vs Ignored sudah dibedakan dan ber-test.

## 5. Triage 7 PR terbuka

GitHub menghitung PR sebagai "issue" — "7 open issue" di profil repo = 7 PR lama ini (tidak ada issue murni yang terbuka).

| PR | Isi | Keadaan vs `main` | Rekomendasi |
|---|---|---|---|
| #41 lane→model chain 14.32 | `LaneModelChain` + test | File **identik** sudah di main (via PR #47, komentar PR sendiri menyatakan digantikan) | Tutup — digantikan #47 |
| #37 Login Google via Supabase (14.31) | PKCE + Custom Tabs, dependensi baru `androidx.browser` | Belum ada di main; bercabang dari 14.30 (konflik dengan Settings v19) | ⏸ Keputusan produk Kiki (fitur + dependensi baru). Bila lanjut: rebase ke main terbaru. |
| #19 Skill agen 14.12 | `skill.md` + bagian di SoulPrompt | `skill.md` **identik** & sudah dirakit SoulPrompt di main | Tutup — sudah masuk |
| #17 Settings tab + grid (14.11) | Redesain penuh SettingsScreen | Basis 14.11; main sudah berevolusi jauh (v15–v19) — konflik total | Tutup — ide tab/grid dipertimbangkan ulang di PR UI |
| #15 notif exec v2 | `data/notif/ExecNotifier` | Main punya versi lebih baru di path yang sama | Tutup — digantikan |
| #14 notif exec v1410 | `data/store/ExecNotifier` | Digantikan #15 → main | Tutup — digantikan |
| #10 SecurityPolicy (14.9) | SecurityPolicy + GitHubContentGuard + Termux `setPackage` | `SecurityPolicy` **identik** di main; `GitHubContentGuard` duplikat; hardening Termux **belum** masuk | Tutup setelah PR ini merge — sisa nilainya (Termux) diselesaikan lebih tuntas di P0-3 |

Saya tidak menutup PR apa pun sendiri; penutupan menunggu Kiki/Ren.

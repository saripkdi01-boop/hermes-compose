# REMOVED.md — yang digantikan / disederhanakan / dihilangkan

File legacy yang DIGANTIKAN (tidak dibawa ke rewrite):

| Legacy (Java, View-programmatic) | Pengganti (Kotlin, Compose) |
|---|---|
| `ChatActivity.java` (~56 anonymous class) | `ui/chat/ChatScreen.kt` + `ChatViewModel.kt` + komponen |
| `MainActivity.java` (router) | `MainActivity.kt` (single Activity + NavHost) |
| `OnboardingActivity.java` | `ui/onboarding/OnboardingScreen.kt` |
| `KeySetupActivity.java` | `ui/keysetup/KeySetupScreen.kt` + `KeySetupViewModel.kt` |
| `SettingsActivity.java` | `ui/settings/SettingsScreen.kt` + `SettingsViewModel.kt` |
| `Ui.java` (16 warna hardcoded, GradientDrawable) | `ui/theme/Color.kt` + `Type.kt` + `Theme.kt` (M3) |
| `BottomSheet.java` | BottomSheet M3 / DropdownMenu / Dialog Compose |
| `JerouterProvider.java` (HttpURLConnection + Thread + Handler) | `data/remote/`: `JerouterApi` (Retrofit) + `SseClient` (OkHttp Flow) |
| `AIProvider.java` (callback) | `domain/AIProvider.kt` (suspend + Flow) |
| `ChatStore.java` (raw SQLite) | `data/local/`: Room entities + `HermesDao` + `ChatRepository` |
| `ChatDb.java` (SQLiteOpenHelper) | `HermesDatabase.kt` (Room, version 5 + Migration(4,5) eksplisit) |
| `Markdown.java` + `MdParser.java` | `util/Markdown.kt` (AnnotatedString, streaming-friendly) |
| `SseParser.java` | `data/remote/SseDecoder.kt` (kotlinx.serialization, unit-testable) |
| `TokenEst.java` | `Markdown.estimateTokens()` (kasar: len/4) |

File legacy yang DIPERTAHANKAN VERBATIM:

- `KeyVault.java`, `KeyCipher.java`, `TtsReader.java` — disalin persis.
- `ApkShareProvider.java` — disalin persis; butuh `ChatStore.backupsDir()`
  dan `ChatStore.sharedApksDir()`, sehingga ditambahkan shim minimal
  `ChatStore.java` (hanya 2 helper direktori, tanpa logika lama).

## Yang disederhanakan vs legacy

1. **Streaming toggle dihapus** — rewrite selalu streaming (pola aplikasi
   chat modern). Key prefs `streaming` tidak lagi dibaca.
2. **Multi-pilih galeri** tetap (GetMultipleContents); **PDF→vision
   multi-halaman dan DOCX/ZIP/APK analysis tidak dibawa** — lampiran
   non-gambar/non-teks menjadi kartu info (nama+ukuran) + catatan teks
   di prompt. (Scope: rewrite UI, bukan port 1:1 seluruh util.)
3. **Export MD/JSON/TXT per percakapan tidak dibawa** (belum ada di scope;
   bisa ditambah sebagai fitur berikutnya).
4. **Pin conversation** dibawa; **arsip** dibawa (filter di drawer).
5. **Estimasi token header** disederhanakan (len/4, tanpa batas konteks
   model) — tidak ada indikator coral 80% seperti v7.0.
6. **Kamera** diimplementasikan via `ACTION_IMAGE_CAPTURE` + `EXTRA_OUTPUT`
   ke `ApkShareProvider` (cam/) dengan grant URI eksplisit — setara legacy
   v7.0, tanpa permission CAMERA.
7. **Restore backup** menutup Room lalu mengganti file; aplikasi **wajib
   di-restart** setelah restore (dialog mengingatkan).

## Risiko / yang belum terverifikasi tanpa compile

- Seluruh tree belum dikompilasi (Gradle mati di VM ini). Risiko utama:
  API Compose Material3 1.3.x (ExposedDropdownMenu, MenuAnchorType),
  KSP Room/Hilt, dan Hilt navigation-compose — ditulis sesuai dokumentasi
  versi yang dipakai, tapi butuh build hijau CI sebagai bukti.
- **Validasi skema Room vs DB legacy: DITEMUKAN & DIPERBAIKI saat review.**
  Asumsi awal "skema persis → tanpa migration" ternyata salah: PRAGMA
  table_info pada DB legacy melaporkan kolom PK `notnull=0` (diverifikasi
  via sqlite3), sementara Room mengekspektasikan `notNull=true` untuk PK
  non-null. Tanpa tindakan, upgrade in-place v8.0 → v9.0 berisiko crash
  "Room cannot verify the data integrity" saat aplikasi dibuka. Solusi:
  version 5 + `Migration(4, 5)` eksplisit (recreate tabel dengan DDL kanonis
  Room + copy data; SQL-nya diverifikasi via sqlite3: data utuh, PK jadi
  NOT NULL). Deterministik, tanpa kehilangan data. Wajib uji update
  in-place di HP fisik sebagai konfirmasi akhir.
- `AuthInterceptor` memakai `KeyVault.getApiKeyCached()` di thread IO
  OkHttp — aman (cached), tapi panggilan keystore pertama terjadi di
  background, bukan main thread.
- Kamera: grant URI manual ke semua resolve-info; perilaku bisa beda
  antar vendor kamera — wajib uji di A76 fisik.
- TTS: `TtsReader.ensure()` callback di UI thread; dipakai dari Compose
  via `remember` + `DisposableEffect` — shutdown saat screen dispose.

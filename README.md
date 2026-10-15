# Hermes Chat — Compose Rewrite (v9.0)

Rewrite penuh Hermes Chat (`id.sukiapps.hermes`) dari View-programmatic (Java)
ke **Jetpack Compose + Kotlin**, mengikuti pola aplikasi chat AI modern.

## Tech stack

| Lapisan | Teknologi |
|---|---|
| UI | Jetpack Compose + Material 3, single Activity + Navigation Compose |
| State | ViewModel + StateFlow |
| DI | Hilt 2.51.1 (KSP) |
| Network | OkHttp 4.12.0 + Retrofit 2.11.0 + kotlinx.serialization 1.7.3 |
| Async | Kotlin coroutines (chatStream → `Flow<String>`, cancel via scope) |
| Database | Room 2.6.1 (file `hermes_chat.db`, **version 5** + Migration(4,5) — lihat HermesDatabase.kt) |
| Build | Gradle 8.11.1, AGP 8.7.3, Kotlin 2.0.21, KSP 2.0.21-1.0.25, compileSdk 34, minSdk 30 |

## Kontrak yang dipertahankan (perilaku tidak berubah)

- `KeyVault` / `KeyCipher` — enkripsi AndroidKeyStore, verbatim dari legacy.
- `ApkShareProvider` — verbatim (+ shim `ChatStore` minimal untuk helper direktori).
- `TtsReader` — TTS Indonesia, verbatim.
- Skema Room identik legacy, **dengan satu deviasi yang disengaja**:
  version naik 4 → 5 dengan `Migration(4, 5)` eksplisit (bukan "tanpa
  migration"). Alasan: skema SQL legacy memang identik secara teks, tetapi
  Room memvalidasi TableInfo logis saat open — legacy memakai
  `id INTEGER PRIMARY KEY AUTOINCREMENT` (PRAGMA melaporkan notnull=0,
  diverifikasi via sqlite3) sementara Room mengekspektasikan notNull=true
  untuk PK non-null. Tanpa migration, user upgrade dari v8.0 berisiko crash
  "Room cannot verify the data integrity" saat aplikasi dibuka. Migration
  me-recreate kedua tabel dengan DDL kanonis Room + menyalin seluruh data
  (SQL-nya sudah diverifikasi via sqlite3: data utuh, PK jadi NOT NULL).
  Deterministik dan tanpa kehilangan data.
- SharedPreferences file `hermes_chat` + semua key (`model`, `persona`,
  `temperature`, `top_p`, `max_tokens`, `theme`, `persona_presets`, `base_url`).
- Format backup ZIP (`hermes_chat.db` + `prefs.json` + `backup_info.json`);
  `api_key` / `api_key_enc_v1` tidak ikut backup.
- REST: `GET {base}/models`, `POST {base}/chat/completions` (OpenAI-style,
  SSE `data:` + `[DONE]`, Bearer auth).

## Struktur source

```
app/src/main/java/id/sukiapps/hermes/
├── MainActivity.kt            # single Activity (@AndroidEntryPoint)
├── HermesApp.kt               # (di/) @HiltAndroidApp
├── KeyVault.java / KeyCipher.java / ApkShareProvider.java / TtsReader.java
├── ChatStore.java             # shim direktori untuk provider (verbatim-safe)
├── di/                        # AppModule, NetworkModule (+AuthInterceptor)
├── domain/                    # AIProvider, ChatRequest, Persona (JSON n/t), Prefs
├── data/local/                # Room entities/DAO/Database, ChatRepository, AppPrefs
├── data/remote/               # JerouterApi (Retrofit), SseClient (OkHttp SSE Flow),
│                              #   SseDecoder (unit-testable), ChatRemoteRepository
├── util/                      # Markdown (AnnotatedString), BackupManager, FileUtil
├── ui/theme/                  # Color (teal/coral), Type, HermesTheme (dark-default)
├── ui/chat/                   # ChatScreen, ChatViewModel, MessageBubble,
│                              #   MarkdownText, CodeBlock, InputBar, TypingIndicator
├── ui/history/                # DrawerContent + HistoryViewModel
├── ui/settings/               # SettingsScreen + SettingsViewModel
├── ui/keysetup/               # KeySetupScreen + KeySetupViewModel
├── ui/onboarding/             # OnboardingScreen (pager + ilustrasi Canvas)
└── navigation/Nav.kt           # route: onboarding/keysetup/chat/settings
```

## Alur signing APK

CI (`build.yml`) menghasilkan **APK unsigned**
(`app-release-unsigned.apk`) — disengaja: keystore rilis tidak disimpan
di repo/CI.

1. Download artifact `hermes-compose-unsigned` dari GitHub Actions.
2. Kirim APK ke maintainer (Upin).
3. Maintainer sign dengan keystore resmi (`keystore-v2.jks`, cert identik
   seluruh rilis Hermes) via `apksigner`, verifikasi, lalu bagikan link.

## Build lokal (butuh Android SDK + JDK 17)

```bash
gradle :app:assembleRelease --no-daemon
# hasil: app/build/outputs/apk/release/app-release-unsigned.apk
gradle :app:testReleaseUnitTest --no-daemon
```

## Status verifikasi

Ditulis lengkap tanpa placeholder, TETAPI **belum dikompilasi** — Gradle
tidak dapat berjalan di VM ini (loopback TCP di-mangle proxy; sudah
diverifikasi). Verifikasi pertama terjadi di GitHub Actions. Lihat
`REMOVED.md` untuk daftar penyederhanaan & risiko yang belum terverifikasi.

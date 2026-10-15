# Hermes — Aplikasi Chat AI Android (Open Source)

![Build](https://github.com/saripkdi01-boop/hermes-compose/actions/workflows/build.yml/badge.svg)
![License](https://img.shields.io/badge/license-MIT-green)

Hermes adalah aplikasi chat AI native Android (Kotlin + Jetpack Compose +
Material 3) yang terhubung ke backend OpenAI-compatible. Punya **Agent Soul**:
persona tetap, ingatan jangka panjang, daftar tugas, konektor (GitHub,
Telegram, Termux, web fetch), permission gate dengan riwayat audit, dan
fitur **Minta Fitur** (upgrade aplikasi via prompt → patch → push → auto-build).

## Fitur utama

- Chat multi-percakapan: streaming SSE + Stop, markdown, retry/regenerate/edit,
  pin/arsip/cari
- Ganti model AI dari server (`/models`), atur temperature & top_p
- Lampiran: kamera, galeri multi-pilih, dokumen (PDF vision, DOCX, ZIP/APK info)
- TTS Bahasa Indonesia (tekan lama pesan → Bacakan)
- Persona, tema terang/gelap, backup ZIP / restore, export MD/JSON/TXT
- **Jiwa Hermes**: `soul.md`, `memory.md` (otomatis), `todo.md`, `agents.md`,
  `features.md` → disusun jadi system prompt tiap chat
- **Aktivitas**: daftar tugas sedang dikerjakan vs selesai
- **Konektor** (aksi manual): GitHub, Telegram, Termux, web fetch
- **Izin & Riwayat**: semua aksi sensitif lewat popup izin + tercatat di audit

## Mulai cepat (kontributor)

**Tanpa Android Studio:** fork repo → push ke branch → GitHub Actions
otomatis build → download artifact `hermes-compose-unsigned` di tab Actions.

**Lokal:** butuh JDK 17 + Android SDK (compileSdk 34):
```bash
gradle :app:assembleRelease --no-daemon
```

Mau ikut mengembangkan? Baca [CONTRIBUTING.md](CONTRIBUTING.md) dulu —
PR kecil dan fokus paling cepat di-review.

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
  version naik 4 → 5 dengan `Migration(4, 5)` eksplisit. Alasan: legacy memakai
  `id INTEGER PRIMARY KEY AUTOINCREMENT` (PRAGMA melaporkan notnull=0,
  diverifikasi via sqlite3) sementara Room mengekspektasikan notNull=true
  untuk PK non-null. Tanpa migration, user upgrade dari v8.0 berisiko crash
  "Room cannot verify the data integrity". Migration me-recreate kedua tabel
  dengan DDL kanonis Room + menyalin seluruh data (data utuh, PK jadi NOT NULL).
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
├── ChatStore.java             # shim direktori untuk provider
├── di/                        # AppModule, NetworkModule (+AuthInterceptor)
├── domain/                    # AIProvider, ChatRequest, Persona (JSON n/t), Prefs
├── data/local/                # Room entities/DAO/Database, ChatRepository, AppPrefs
├── data/remote/               # JerouterApi (Retrofit), SseClient (OkHttp SSE Flow),
│                              #   SseDecoder (unit-testable), ChatRemoteRepository
├── data/agent/                # AgentFiles, SoulPrompt, MemoryWriter (Jiwa Hermes)
├── data/connectors/           # GitHub, Telegram, Termux, WebFetch
├── data/audit/                # PermissionGate, AuditLog (Izin & Riwayat)
├── util/                      # Markdown (AnnotatedString), BackupManager, FileUtil
├── ui/theme/                  # Color (teal/coral), Type, HermesTheme
├── ui/chat/                   # ChatScreen, ChatViewModel, MessageBubble, ...
├── ui/history/                # DrawerContent + HistoryViewModel
├── ui/settings/               # SettingsScreen + SettingsViewModel
├── ui/keysetup/               # KeySetupScreen + KeySetupViewModel
├── ui/onboarding/             # OnboardingScreen
├── ui/agent/                  # SoulScreen, AktivitasScreen (+ViewModel)
├── ui/audit/                  # AuditScreen (Riwayat Izin)
├── ui/featreq/                # FeatureRequestScreen (Minta Fitur)
└── navigation/Nav.kt          # route aplikasi
```

Detail arsitektur: [ARCHITECTURE.md](ARCHITECTURE.md).

## Alur signing APK

CI (`build.yml`) menghasilkan **APK unsigned**
(`app-release-unsigned.apk`) — disengaja: keystore rilis tidak disimpan
di repo/CI. Build rilis resmi di-sign maintainer dengan keystore resmi
(cert identik seluruh rilis Hermes) agar update in-place tetap bisa.
Kontributor cukup pakai build unsigned.

## Keamanan

Jangan pernah commit token/API key/keystore. Lihat [SECURITY.md](SECURITY.md).

## Lisensi

[MIT](LICENSE) — bebas dipakai, diubah, dan didistribusikan.

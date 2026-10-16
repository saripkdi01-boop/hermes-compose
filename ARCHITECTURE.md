# Arsitektur Hermes

Peta cepat untuk kontributor baru. Package root: `id.sukiapps.hermes`.

## Lapisan

```
UI (Compose)          ui/chat, ui/settings, ui/agent, ui/audit,
                      ui/featreq, ui/history, ui/keysetup, ui/onboarding
         ↓ state (StateFlow) / event
ViewModel             ChatViewModel, SettingsViewModel, AgentViewModel, ...
         ↓
Data                  data/local (Room), data/remote (Retrofit/OkHttp SSE),
                      data/agent, data/connectors, data/audit
         ↓
Platform              KeyVault/KeyCipher (Keystore), Room DB, filesDir,
                      Android intents (Termux), HTTPS APIs
```

- **DI**: Hilt (`di/`). Tambah `@Inject constructor` untuk class baru.
- **Navigasi**: `navigation/Nav.kt` — tambah konstanta route + composable
  di `MainActivity.kt`.
- **Database**: Room `hermes_chat.db`. Ubah skema → naikkan version +
  tulis `Migration` eksplisit (jangan `fallbackToDestructiveMigration`).

## Jiwa Hermes (Agent Soul)

```
filesDir/agent/ ── soul.md, memory.md, todo.md, agents.md, features.md
        │  (seed dari assets/agent/ saat pertama dibuka)
        ▼
SoulPrompt.build() ── soul → agents (≤10 baris) → features (≤40 baris)
                      → memory relevan (keyword match) → todo aktif (≤5)
        │  (±1500 kata, sekali per conversation dimulai)
        ▼
system prompt ──► /chat/completions
```

- `MemoryWriter.maybeWriteBack()`: ekstrak maks 3 fakta tiap 10 pesan /
  saat chat ditutup → append ke `memory.md` (dedup keyword).
- Semua file bisa diedit user di **Pengaturan → Jiwa Hermes**;
  `features.md` read-only (manual fitur aplikasi).

## Konektor (aksi MANUAL)

`data/connectors/`: GitHub (REST + PAT), Telegram (Bot API),
Termux (`RUN_COMMAND` intent), WebFetch (HTTP GET → teks),
**Upin** (`UpinConnector.kt`, v9.4+) — lihat bawah.

Prinsip: konektor hanya dipicu user lewat tombol. AI tidak bisa
memanggilnya sendiri dari chat (butuh function-calling model — belum ada).

## Konektor Upin — dua AI: Hermes + Upin (v9.4+)

```
HP Termux: ssh -L 8766:127.0.0.1:8766 muse-vm -N
   → 127.0.0.1:8766 di HP diteruskan ke bridge Upin di VM
   → UpinConnector: POST /upin/chat → job_id
   →                GET /upin/reply/<id> → jawaban (polling)
```

- Secret khusus (`upin_secret`) di KeyVault, BUKAN API key Jerouter.
- OkHttp WAJIB `Proxy.NO_PROXY` (v9.4.2): jangan lewat proxy/VPN sistem HP.
- Tombol "Tanya Upin" di menu + InputBar → PermissionGate MEDIUM.
- Latensi jujur ~1-2 menit (worker 1-menit di sisi VM).
- Setup: Pengaturan → Konektor Upin (URL default `http://127.0.0.1:8766`).

## AppCapabilities — model tahu fitur app (v9.5+)

`domain/AppCapabilities.kt`: blok teks deskripsi SEMUA fitur Hermes
(Upin, web fetch, lampiran, TTS, model/persona, Termux) yang disuntik
ke system prompt setiap chat dimulai (`ChatViewModel`). Tujuannya:
model AI yang diajak chat paham aplikasi apa yang menjalankannya dan
bisa memandu user memakai fitur yang tepat. Bukan function-calling —
model tetap tidak bisa eksekusi aksi, hanya tahu.

## Tema Warm Muse (v9.5+)

`ui/theme/Color.kt`: palet cream hangat + coral (80% nuansa Muse AI).
- Assistant: flat tanpa bubble (`MessageBubble.kt`) + ikon sparkle.
- User: bubble `surfaceVariant` hangat, rata kanan.
- InputBar: container tunggal border halus, tanpa elevasi.

## PermissionGate & audit (anti-bypass)

```
Aksi sensitif ──► PermissionGate.request(type, detail, level)
                      │ HIGH   : popup SELALU (Termux)
                      │ MEDIUM : popup, boleh "ingat" (Telegram, GitHub push)
                      │ LOW    : catat diam-diam (memory write-back)
                      ▼
              AuditLog → filesDir/audit.jsonl (cap 500)
                      ▼
              UI: "Riwayat Izin" (filter Semua/Diizinkan/Ditolak)
```

Aturan keras: fungsi pengirim mentah (`send`, `putContents`, ...)
harus `private`/`internal` — hanya wrapper ber-gate yang publik.
Inilah yang membuat bypass mustahil secara konstruksi.

## Minta Fitur (self-upgrade loop)

```
Prompt user ──► AI (dengan features.md) ──► unified diff ──► preview
        ──► user setuju ──► GitHub Contents API ──► Actions build
```

AI hanya punya `features.md` sebagai konteks → patch adalah DRAF,
wajib di-review user sebelum push. Cocok untuk perubahan kecil.

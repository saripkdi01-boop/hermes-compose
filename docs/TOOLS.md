# Tools Hermes (v20.1)

Semua tool ditawarkan ke model di chat lewat function-calling OpenAI
(`ToolLoop.toolsArray`), kecuali `selfupgrade_*` (hanya Mode Agen).
Tool yang mengubah data, menjalankan kode, mengirim keluar, atau memakai
kuota API meminta izin lewat `PermissionGate` dan tercatat di **Riwayat Izin**
(`AuditLog`). Level izin: **LOW/MEDIUM** bisa "ingat pilihan", **HIGH**
selalu popup.

Total: **38 tool unik** = 25 tool lama di `ToolLoop` + 5 tool registry lama
(`file_read` terdaftar di keduanya, dihitung sekali) + **8 tool baru v20.1**.

## 1. Toolkit agen v20.1 — padanan Hermes Agent desktop

Diimplementasikan ulang secara native untuk Android (bukan port kode Python
[Hermes Agent](https://hermes-agent.nousresearch.com/), Nous Research, MIT).
Sumber: `app/src/main/java/id/sukiapps/hermes/tool/AgentTools.kt`.

| Tool | Padanan desktop | Input | Izin | Batas & penyimpanan |
|---|---|---|---|---|
| `todo` | `todo` | `action`: list / add (`text`) / done / undo / remove (`number`) | LOW (ubah); list tanpa izin | `filesDir/agent/todo.md` — **bertahan antar sesi** (desktop: per sesi). Teks ≤ 200 karakter |
| `session_search` | `session_search` | `query` (≥ 2 karakter), `limit` (≤ 100) | – (baca) | Query `LIKE` di Room (`HermesDao.searchMessages`), **incognito tidak ikut**; hasil dikelompokkan per chat, cuplikan ±80 karakter |
| `skills_list` | `skills_list` | – | – | maks 50 skill |
| `skill_view` | `skill_view` | `name` | – | output ≤ 12.000 karakter |
| `skill_manage` | `skill_manage` | `action`: create / edit / rename / delete, `name`, `new_name`, `description`, `content` | MEDIUM | `filesDir/agent/skills/<nama>.md` (front-matter `name`/`description`), nama `[a-z0-9-]{1,40}`, ≤ 12 KB, ≤ 50 skill, tulis atomik |
| `execute_code` | `execute_code` | `code` (≤ 20.000 karakter) | **HIGH** (selalu popup, tampilkan kode) | Python 3.11 stdlib via Chaquopy, timeout 30 dtk, output ≤ 20.000 karakter. Tidak bisa memanggil tool lain (desktop bisa lewat RPC) |
| `cronjob` | `cronjob` | `action`: create (`name`, `prompt`, `schedule`) / list / pause / resume / remove / run (`id`) | **HIGH** saat create; MEDIUM run; LOW pause/resume/remove | WorkManager (butuh jaringan); jadwal: `setiap 30 menit` (min 15), `setiap 2 jam`, `setiap hari 07:30`, `sekali 2026-11-28 09:00`; maks 20 tugas; `filesDir/agent/cron_jobs.json`; hasil → chat "⏰ nama" + notifikasi |
| `delegate_task` | `delegate_task` | `goal` + `context`, atau `tasks: [{goal, context}]` (1–3) | LOW | sub-agen **tanpa tool**, paralel, timeout 90 dtk masing-masing, ±700 token per sub-agen |

Catatan keamanan:
- `execute_code` berjalan **di dalam proses aplikasi**. Kode bisa membaca file
  aplikasi dan memakai internet. Karena itu izinnya HIGH dan kodenya
  ditampilkan di kartu izin.
- `cronjob` dan `delegate_task` memanggil `POST {base}/chat/completions`
  (non-streaming) dengan model & base URL dari Pengaturan. API key hanya
  dikirim ke host base URL (`AuthScope`). Keduanya memakai kuota API user.
- Skill & tugas terjadwal **tidak** masuk Room (skema kontrak tetap v6) dan
  **tidak** ikut backup ZIP.
- Indeks skill (`- nama: deskripsi`, maks 20) dirakit ke system prompt
  Jiwa (`[Skill tersimpan …]`) agar model tahu kapan memanggil `skill_view`.

**Tidak tersedia di HP** (beda dengan desktop): otomasi browser (desktop
memakai Browserbase + API key), terminal penuh di luar sandbox/Termux,
`web_search` (butuh keputusan penyedia + API key), pembuatan gambar.

## 2. Tool registry lama (`ToolRegistry`)

| Tool | Fungsi |
|---|---|
| `calc` | hitung ekspresi aritmetika |
| `time_now` | tanggal & waktu sekarang (WITA + UTC) |
| `code_search` | cari teks/pola di isi file kode yang diberikan |
| `file_read` | baca file teks di sandbox (izin MEDIUM) |
| `web_fetch` | ambil isi teks URL (allowlist domain di Pengaturan) |
| `termux_exec` | jalankan perintah di Termux (izin HIGH) |

## 3. Tool lama di `ToolLoop`

| Tool | Fungsi (dari deskripsi di kode) |
|---|---|
| `shell_exec` | perintah shell di sandbox Hermes; destruktif butuh izin |
| `file_read` / `file_write` | baca/tulis file teks di sandbox |
| `net_scan` | scan port TCP ke host di allowlist scan (maks 32 port) |
| `conn_health` | health check HTTP ke URL di allowlist |
| `code_run` | kode shell di sandbox (timeout 10 dtk, selalu izin) |
| `github_repos` / `github_runs` / `github_read_file` | baca GitHub (PAT tersimpan) |
| `github_write_file` / `github_dispatch` | tulis file / jalankan workflow (izin) |
| `telegram_send` | kirim pesan Telegram lewat bot user (izin) |
| `upin_ask` | tanya agent Upin di VM (izin) |
| `supabase_query` | baca tabel Supabase (read-only) |
| `vercel_projects` / `vercel_deployments` | baca Vercel (read-only) |
| `memory_save` / `memory_list` | ingatan persisten agen (save: izin LOW) |
| `system_info` | info perangkat & sandbox |
| `selfupgrade_branch/write_file/pr/ci/merge/release` | upgrade diri — **hanya Mode Agen**, ditolak di chat |

## 4. Menambah tool baru

1. Implementasikan `HermesTool` (id snake_case, `description` yang jelas untuk
   LLM, `inputSchema` JSON Schema, `capabilities` jujur, `requiresApproval`).
2. Di `execute`: cek `ctx.appContext`/`ctx.gate` (gagal tertutup bila null),
   minta izin lewat `ctx.gate.request(type, detail, DangerLevel.X)`, batasi
   output, jangan pernah `throw`, jangan pernah sertakan kredensial.
3. Satu baris `register(...)` di `ToolRegistry`. Tool otomatis ditawarkan
   di chat.
4. Unit test untuk logika murninya + baris di `assets/agent/features.md`
   (agar Hermes bisa memandu user) + baris di dokumen ini.

# Panduan HP — Build Hermes Compose via GitHub Actions

Source lengkap sudah ditulis di repo ini. Karena Gradle tidak bisa jalan
di VM, build & verifikasi dilakukan di GitHub Actions — semuanya bisa
dikerjakan dari HP (Termux).

## Yang dibutuhkan di HP

- Aplikasi **Termux** (dari F-Droid, bukan Play Store)
- Akun **GitHub**
- Koneksi internet yang stabil (download SDK ± 1–2 GB sekali saja di server
  GitHub — bukan di HP; HP hanya upload source ± kecil)

## Langkah 1 — Siapkan Termux

Buka Termux, jalankan:

```
pkg update && pkg upgrade
pkg install git zip
```

## Langkah 2 — Buat repo GitHub baru

1. Buka github.com di browser HP, login.
2. Tap **+** → **New repository**.
3. Nama: `hermes-compose`, pilih **Private**.
4. JANGAN centang "Add a README" (repo harus kosong).
5. Tap **Create repository**.
6. Catat URL repo, mis. `https://github.com/USERNAME/hermes-compose.git`.

## Langkah 3 — Upload source dari VM ke HP (via link)

Source ada di VM dalam bentuk folder. Minta Upin untuk membuatkan ZIP +
link download (atau pakai `git` bila VM bisa push — saat ini tidak bisa).

Setelah dapat link ZIP di HP:

```
cd ~
curl -L -o hermes-compose.zip "LINK_DARI_UPIN"
unzip -q hermes-compose.zip -d hermes-compose
cd hermes-compose
```

## Langkah 4 — Push ke GitHub dari Termux

```
git init -b main
git add .
git commit -m "Hermes Compose v9.0 rewrite"
git remote add origin https://github.com/USERNAME/hermes-compose.git
git push -u origin main
```

Git akan minta login: pakai **Personal Access Token** (bukan password).
Buat di github.com → Settings → Developer settings → Personal access
tokens → Tokens (classic) → Generate → centang **repo**. Tempel saat
diminta password.

## Langkah 5 — Lihat hasil build di Actions

1. Buka repo di browser HP → tab **Actions**.
2. Workflow **"Build Hermes Compose"** akan jalan otomatis (± 5–15 menit).
3. Tunggu sampai hijau (centang hijau).
4. Tap workflow yang hijau → **Artifacts** → download
   **hermes-compose-unsigned** → dapat `app-release-unsigned.apk`.

## Langkah 6 — Minta signing ke Upin

APK dari CI itu **unsigned** (belum bisa diinstal). Kirim file APK-nya
ke Upin — dia akan sign dengan keystore resmi Hermes (cert identik
semua versi) lalu kasih link download final.

## Kalau build MERAH (gagal)

1. Tap workflow yang merah → buka job **build** → lihat log merah.
2. Screenshot / salin pesan error-nya, kirim ke Upin.
3. Upin perbaiki source-nya, kirim ZIP/patch baru → ulangi langkah 3–4
   (cukup `git add . && git commit -m "fix" && git push`).

## Catatan

- Setiap push ke branch `main` otomatis memicu build baru.
- Jangan commit file `local.properties` atau keystore ke repo.
- Unit test ikut jalan otomatis (`testReleaseUnitTest`).

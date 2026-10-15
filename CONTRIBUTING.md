# Berkontribusi ke Hermes

Terima kasih mau ikut mengembangkan Hermes! Panduan singkat biar
kontribusimu cepat di-review dan di-merge.

## Cara tercepat berkontribusi (tanpa Android Studio)

1. **Fork** repo ini ke akunmu.
2. Buat branch: `git checkout -b fitur/nama-fitur` (atau `fix/nama-bug`).
3. Ubah kode, commit, push ke fork-mu.
4. Buka **Pull Request** ke `main` repo ini. Isi template PR yang muncul.
5. GitHub Actions otomatis build PR-mu — pastikan **hijau** sebelum minta review.
6. Download artifact `hermes-compose-unsigned` dari tab Actions untuk uji di HP.

## Aturan main

- **PR kecil & fokus**: satu PR = satu fitur/bug. Lebih cepat di-review
  daripada PR raksasa.
- **Jangan commit**: token, API key, password, file keystore (`*.jks`),
  atau data pribadi. Keystore rilis hanya dipegang maintainer.
- **Ikuti pola existing**: Compose + Material 3, ViewModel + StateFlow,
  Hilt untuk DI, coroutines untuk async. Jangan tambah dependensi baru
  tanpa alasan kuat di deskripsi PR.
- **Aksi sensitif wajib lewat PermissionGate** (`data/audit/`): tidak ada
  jalur bypass. Lihat ARCHITECTURE.md.
- Tulis pesan commit yang jelas, mis. `fix: dropdown model tidak terbuka`.

## Melapor bug / usul fitur

- Pakai template issue yang tersedia (Bug report / Feature request).
- Sertakan: versi app, tipe HP + versi Android, langkah reproduksi,
  dan screenshot/log bila ada.

## Kode etik

Bersikap baik dan konstruktif. Kritik kode, bukan orangnya.

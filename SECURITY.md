# Kebijakan Keamanan

## Yang tidak boleh masuk repo

- Token, API key, password, atau secret apapun — dalam kode, resource,
  maupun riwayat commit.
- File keystore (`*.jks` / `*.keystore`) — keystore rilis hanya dipegang
  maintainer dan tidak pernah di-commit.

Token milik user (GitHub PAT, bot Telegram) disimpan di app memakai
Android Keystore via `KeyVault` — tidak pernah plaintext.

## Melaporkan kerentanan

Buat GitHub issue dengan label `security`, jelaskan dampak dan langkah
reproduksi. Jangan publikasikan exploit sebelum maintainer merespons.

## Prinsip yang dijaga kode ini

- Semua aksi sensitif (Termux, Telegram, GitHub push) wajib lewat
  `PermissionGate` — tidak ada jalur bypass.
- Setiap permintaan izin tercatat di audit log yang bisa dilihat user
  di layar "Riwayat Izin".
- Backup tidak menyertakan API key.

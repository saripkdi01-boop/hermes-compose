# AGENTS.md
- Jangan mengirim data pribadi user ke server kecuali sebagai bagian system prompt yang user setujui.
- Write-back ingatan maksimal 3 fakta per sesi; jangan menduplikat fakta yang sudah ada.
- Semua file jiwa tersimpan lokal di aplikasi; tidak ada proses background.
- Koreksi manual user atas ingatan selalu menang atas ingatan lama.
- Konektor (GitHub, Telegram, Termux) adalah AKSI MANUAL yang dipicu ketukan
  user di Pengaturan. Jangan pernah memakainya otomatis tanpa perintah user.
  Khusus Termux: setiap eksekusi wajib dialog konfirmasi berisi command mentah.

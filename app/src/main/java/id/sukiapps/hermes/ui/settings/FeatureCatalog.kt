package id.sukiapps.hermes.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

/** Definisi satu fitur settings. */
data class FeatureDef(
    val id: String,
    val route: String,
    val icon: ImageVector,
    val tintKey: String, // kunci warna: "accent"|"ready"|"pending"|"error"|"working"
    val title: String,
    val subtitle: String,
    val section: String, // MODEL & OTAK / KONEKSI / OTORITAS / SISTEM
    val order: Int,
    val required: Boolean, // 4 wajib pertama
    val guidance: List<GuidanceStep> = emptyList(),
)

/** Katalog 15 fitur (F1–F15). */
object FeatureCatalog {
    // Warna ikon per fitur — memakai warna status generik agar tanpa hardcode hex
    // (di-resolve ke warna aktual di Hub via LocalSettingPalette)

    val all: List<FeatureDef> = listOf(
        FeatureDef(
            id = "model", route = "settings/model",
            icon = Icons.Filled.SmartToy, tintKey = "accent",
            title = "Model AI", subtitle = "Pilih model dari server",
            section = "MODEL & OTAK", order = 1, required = true,
            guidance = listOf(
                GuidanceStep("m1", 1, "Isi Base URL dulu",
                    "Model diambil dari server via GET /models. Pastikan Koneksi sudah benar."),
                GuidanceStep("m2", 2, "Muat daftar model",
                    "Ketuk \"Muat ulang\" untuk mengambil daftar model dari server."),
                GuidanceStep("m3", 3, "Pilih model",
                    "Pilih salah satu via radio. Perubahan langsung berlaku + tercatat di aktivitas."),
            ),
        ),
        FeatureDef(
            id = "connection", route = "settings/connection",
            icon = Icons.Filled.Dns, tintKey = "accent",
            title = "Koneksi / Base URL", subtitle = "Alamat server AI",
            section = "MODEL & OTAK", order = 2, required = true,
            guidance = listOf(
                GuidanceStep("c1", 1, "Isi Base URL",
                    "Contoh: https://je.jerouter.web.id/v1 — harus https, tanpa spasi, diakhiri /v1."),
                GuidanceStep("c2", 2, "Uji koneksi",
                    "Ketuk \"Uji\" — tanpa API key. 401 = endpoint benar, key yang salah."),
            ),
        ),
        FeatureDef(
            id = "apikey", route = "settings/apikey",
            icon = Icons.Filled.Key, tintKey = "pending",
            title = "API Key", subtitle = "Kunci akses server",
            section = "MODEL & OTAK", order = 3, required = true,
            guidance = listOf(
                GuidanceStep("k1", 1, "Dapatkan API key",
                    "Minta key baru via @jerouter_bot. Jangan bagikan ke siapa pun."),
                GuidanceStep("k2", 2, "Tempel & simpan",
                    "Tempel di kolom, ketuk Simpan. Key terenkripsi di Keystore."),
                GuidanceStep("k3", 3, "Verifikasi",
                    "Ketuk \"Uji\" — status jadi \"Terverifikasi\" bila 200 OK."),
            ),
        ),
        FeatureDef(
            id = "parameters", route = "settings/parameters",
            icon = Icons.Filled.Tune, tintKey = "accent",
            title = "Parameter", subtitle = "Temperature, top-p, max token",
            section = "MODEL & OTAK", order = 4, required = true,
            guidance = listOf(
                GuidanceStep("p1", 1, "Atur kreativitas",
                    "Temperature tinggi = lebih kreatif, kurang konsisten."),
                GuidanceStep("p2", 2, "Batas token",
                    "Max tokens membatasi panjang jawaban."),
            ),
        ),
        FeatureDef(
            id = "persona", route = "settings/persona",
            icon = Icons.Filled.Psychology, tintKey = "accent",
            title = "Persona", subtitle = "Kepribadian AI",
            section = "MODEL & OTAK", order = 5, required = false,
            guidance = listOf(
                GuidanceStep("ps1", 1, "Tulis system prompt",
                    "Contoh: \"Kamu asisten ramah berbahasa Indonesia.\""),
                GuidanceStep("ps2", 2, "Simpan preset",
                    "Preset tersimpan di persona_presets.json, bisa diekspor .md."),
            ),
        ),
        FeatureDef(
            id = "soul", route = "settings/soul",
            icon = Icons.Filled.AutoAwesome, tintKey = "ready",
            title = "Jiwa Hermes", subtitle = "Memori & agen",
            section = "MODEL & OTAK", order = 6, required = false,
        ),
        FeatureDef(
            id = "upin", route = "settings/connector/upin",
            icon = Icons.Filled.Hub, tintKey = "working",
            title = "Upin", subtitle = "Konektor asisten VM",
            section = "KONEKSI", order = 7, required = false,
            guidance = listOf(
                GuidanceStep("u1", 1, "Aktifkan SSH forward",
                    "Jalankan di Termux HP:",
                    command = "ssh -L 8766:127.0.0.1:8766 muse-vm -N",
                    linkPackage = "com.termux"),
                GuidanceStep("u2", 2, "Isi secret",
                    "Secret Upin dibaca di VM: /home/hatch/muse-bridge/keys/upin_secret.txt — ketik manual."),
                GuidanceStep("u3", 3, "Uji",
                    "Ketuk \"Uji\" — ECONNREFUSED = SSH forward belum aktif."),
            ),
        ),
        FeatureDef(
            id = "github", route = "settings/connector/github",
            icon = Icons.Filled.Code, tintKey = "accent",
            title = "GitHub", subtitle = "Token akses repo",
            section = "KONEKSI", order = 8, required = false,
            guidance = listOf(
                GuidanceStep("g1", 1, "Buat PAT",
                    "github.com → Settings → Developer settings → classic, scope repo+workflow."),
                GuidanceStep("g2", 2, "Tempel & uji",
                    "Uji sukses = \"OK, username=<nama>\"."),
            ),
        ),
        FeatureDef(
            id = "telegram", route = "settings/connector/telegram",
            icon = Icons.Filled.Send, tintKey = "working",
            title = "Telegram", subtitle = "Bot notifikasi",
            section = "KONEKSI", order = 9, required = false,
            guidance = listOf(
                GuidanceStep("t1", 1, "Buat bot",
                    "Chat @BotFather → /newbot, salin token."),
                GuidanceStep("t2", 2, "Ambil chat ID",
                    "Chat @userinfobot untuk ID, lalu /start ke bot kamu."),
                GuidanceStep("t3", 3, "Tes kirim",
                    "Tes mengirim pesan ASLI — perlu konfirmasi dialog."),
            ),
        ),
        FeatureDef(
            id = "termux", route = "settings/connector/termux",
            icon = Icons.Filled.Terminal, tintKey = "accent",
            title = "Termux", subtitle = "Eksekusi perintah",
            section = "KONEKSI", order = 10, required = false,
            guidance = listOf(
                GuidanceStep("tx1", 1, "Aturan main",
                    "Setiap eksekusi WAJIB dialog konfirmasi + tampilkan perintah mentah. Tanpa auto-execute."),
                GuidanceStep("tx2", 2, "Atur path",
                    "Default: /data/data/com.termux/files/usr/bin/bash"),
            ),
        ),
        FeatureDef(
            id = "serverless", route = "settings/connector/serverless",
            icon = Icons.Filled.CloudQueue, tintKey = "working",
            title = "Telegram Serverless", subtitle = "Bot di cloud",
            section = "KONEKSI", order = 11, required = false,
        ),
        FeatureDef(
            id = "appearance", route = "settings/appearance",
            icon = Icons.Filled.Palette, tintKey = "accent",
            title = "Tampilan", subtitle = "Tema, aksen, font",
            section = "SISTEM", order = 12, required = false,
        ),
        FeatureDef(
            id = "tts", route = "settings/tts",
            icon = Icons.Filled.RecordVoiceOver, tintKey = "ready",
            title = "TTS", subtitle = "Suara bacaan",
            section = "SISTEM", order = 13, required = false,
        ),
        FeatureDef(
            id = "backup", route = "settings/backup",
            icon = Icons.Filled.Backup, tintKey = "accent",
            title = "Backup & Restore", subtitle = "ZIP pengaturan",
            section = "SISTEM", order = 14, required = false,
        ),
        FeatureDef(
            id = "audit", route = "settings/audit",
            icon = Icons.Filled.Shield, tintKey = "error",
            title = "Riwayat Izin", subtitle = "Log audit konektor",
            section = "OTORITAS", order = 15, required = false,
        ),
    )

    fun byRoute(route: String): FeatureDef? = all.find { it.route == route }
    fun byId(id: String): FeatureDef? = all.find { it.id == id }

    val sections: List<String> = listOf("MODEL & OTAK", "KONEKSI", "OTORITAS", "SISTEM")

    /** Urutan setup wajib (4 pertama) + opsional. */
    val setupOrder: List<FeatureDef> = all.sortedBy { it.order }
}

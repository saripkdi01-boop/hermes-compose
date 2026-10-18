package id.sukiapps.hermes.ui.chatv3

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * V3A11y — helper aksesibilitas (Fase H: Tahap 7 A-1..A-7).
 *
 * Semua contentDescription berbahasa Indonesia. Status selalu disampaikan
 * sebagai ikon + teks (bukan warna saja). Fungsi-fungsi ini hanya menambah
 * semantics — tidak mengubah tampilan.
 */

/** Label pengirim untuk TalkBack: "Anda" / "Hermes" / nama konektor. */
fun v3SenderLabel(sender: String, senderName: String = ""): String = when (sender) {
    "user" -> "Anda"
    "ai" -> "Hermes"
    "upin" -> "Upin"
    "system" -> "Sistem"
    else -> senderName.ifBlank { sender }
}

/**
 * Semantics untuk bubble pesan: satu node gabungan berisi pengirim,
 * waktu, dan status. Dipakai bersama Modifier.combinedClickable yang
 * sudah ada di MuseBubble.
 */
fun Modifier.v3BubbleSemantics(
    sender: String,
    senderName: String = "",
    timeLabel: String = "",
    statusLabel: String = "",
): Modifier = this.semantics(mergeDescendants = true) {
    val parts = listOf(
        "Pesan dari ${v3SenderLabel(sender, senderName)}",
        timeLabel.takeIf { it.isNotBlank() }?.let { "pukul $it" },
        statusLabel.takeIf { it.isNotBlank() },
    ).filterNotNull()
    contentDescription = parts.joinToString(", ")
}

/** Semantics untuk blok kode: bahasa + jumlah baris. */
fun Modifier.v3CodeBlockSemantics(lang: String, lineCount: Int): Modifier =
    this.semantics(mergeDescendants = true) {
        val langLabel = lang.takeIf { it.isNotBlank() } ?: "tanpa label bahasa"
        contentDescription = "Blok kode $langLabel, $lineCount baris. Ketuk dua kali untuk opsi salin."
    }

/** Semantics untuk indikator status (konektor, API, dsb): nama + status teks. */
fun Modifier.v3StatusSemantics(label: String, statusLabel: String): Modifier =
    this.semantics(mergeDescendants = true) {
        contentDescription = "$label, status: $statusLabel"
    }

/** Label status pesan untuk TalkBack. */
fun v3MessageStatusLabel(isSending: Boolean, isFailed: Boolean): String = when {
    isFailed -> "gagal terkirim"
    isSending -> "mengirim"
    else -> "terkirim"
}

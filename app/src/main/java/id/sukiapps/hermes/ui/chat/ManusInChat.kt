package id.sukiapps.hermes.ui.chat

import id.sukiapps.hermes.domain.manus.TaskStatus

fun agentTaskStatusLabel(status: TaskStatus): String = when (status) {
    TaskStatus.IDLE -> "⏸ Siap"
    TaskStatus.PLANNING -> "⏳ Merencanakan"
    TaskStatus.RUNNING -> "▶ BERJALAN"
    TaskStatus.WAITING_PERMISSION -> "⛔ MENUNGGU IZIN"
    TaskStatus.PAUSED -> "⏸ Dijeda"
    TaskStatus.VERIFYING -> "🔍 Memverifikasi bukti"
    TaskStatus.COMPLETED -> "✓ TERVERIFIKASI"
    TaskStatus.PARTIAL -> "◐ SELESAI SEBAGIAN — hasil belum tentu tuntas"
    TaskStatus.FAILED -> "✗ GAGAL"
    TaskStatus.CANCELLED -> "⊘ Dibatalkan"
    TaskStatus.STOPPED -> "⚠ TERHENTI — bukti gagal"
}

package id.sukiapps.hermes.ui.chat

/**
 * Penanda sumber blok chat (14.28, "penanda sumber" + B9).
 *
 * Masalah yang diperbaiki: balasan chat (LLM, bisa mengarang progres)
 * tampil berurutan dengan ringkasan agen (output tool, terverifikasi)
 * tanpa pembeda — narasi chat bisa dibaca sebagai hasil agen.
 * Setiap blok kini berlabel eksplisit:
 * - "Balasan chat (LLM)" untuk jawaban LLM biasa,
 * - "Ringkasan agen (output tool)" untuk ringkasan tugas agen,
 * - chip event tool membawa "(output tool, jalur chat)".
 *
 * Murni Kotlin (tanpa Compose) supaya bisa di-unit-test di JVM, sama
 * seperti [manusSummary].
 *
 * Kontrak: ringkasan agen selalu diawali "🤖 Manus selesai:" (lihat
 * [manusSummary]) — awalan itu yang dipakai [assistantSourceLabel]
 * untuk membedakan ringkasan dari balasan biasa.
 */
fun assistantSourceLabel(content: String): String =
    if (content.startsWith("🤖 Manus selesai:")) "Ringkasan agen (output tool)"
    else "Balasan chat (LLM)"

/**
 * Label chip event tool jalur chat: nama + hasil + durasi + asal jalur.
 * Jalur ditulis eksplisit karena nama tool chat bisa sama dengan tool
 * agen (mis. web_fetch) — tanpa jalur, chip chat bisa dibaca sebagai
 * bukti kerja agen. Format durasi dipertahankan dari label lama
 * (satu desimal, locale perangkat).
 */
fun chatToolEventLabel(name: String, ok: Boolean, ms: Long): String =
    "🌐 $name ${if (ok) "✓" else "✗"} ${"%.1f".format(ms / 1000.0)} dtk " +
        "(output tool, jalur chat)"

package id.sukiapps.hermes.data.store

/**
 * v12 F2 — Dispatcher aksi tertunda.
 *
 * Alur: ApprovalStore.request(type, ...) menyimpan payload aksi → pengguna
 * memutuskan di ApprovalScreen / notifikasi → bila "allowed", resume() di sini
 * melanjutkan aksi aslinya. Tidak ada aksi yang jalan tanpa persetujuan
 * (kecuali masih dalam masa trust 1 jam).
 */
object ApprovalActions {

    /** Lanjutkan aksi yang tertunda setelah approval diizinkan. */
    fun resume(item: ApprovalStore.ApprovalItem) {
        when (item.type) {
            "tool_create" -> {
                val draftId = item.payload.optString("draft_id")
                if (draftId.isNotBlank()) {
                    ToolStore.publishDraft(draftId)
                }
            }
            // Tipe aksi lain belum punya kelanjutan otomatis — tambah di sini.
        }
    }
}

package id.sukiapps.hermes.ui.chatlist

/**
 * Model untuk satu item di layar daftar chat (Tahap 2).
 *
 * Metadata persisten (pin, arsip, unread, rename) disimpan di ChatListStore
 * via JsonStore atomik. Field transien (streaming, status Upin) diisi
 * runtime oleh ViewModel dari sumber masing-masing.
 */
data class ChatListItem(
    val id: String,
    val name: String,
    val displayName: String = name, // bisa di-rename user
    val lastMessagePreview: String = "",
    val lastMessageAt: Long = 0L,
    val unreadCount: Int = 0,
    val pinned: Boolean = false,
    val pinOrder: Int = Int.MAX_VALUE, // urutan pin (drag reorder)
    val archived: Boolean = false,
    val streaming: Boolean = false, // AI sedang streaming di chat ini
    val upinStatus: UpinStatus = UpinStatus.NONE
)

enum class UpinStatus {
    NONE,       // bukan pemicu Tanya Upin
    WAITING,    // "Menunggu Upin…"
    PROCESSING, // "Upin memproses…"
    FAILED      // "Tanya Upin gagal — coba lagi"
}

/** Preview yang ditampilkan: status Upin menggantikan isi mentah. */
fun ChatListItem.previewText(): String = when (upinStatus) {
    UpinStatus.WAITING -> "Menunggu Upin…"
    UpinStatus.PROCESSING -> "Upin memproses…"
    UpinStatus.FAILED -> "Tanya Upin gagal — coba lagi"
    UpinStatus.NONE -> lastMessagePreview
}

enum class ChatTab { ALL, UNREAD, ARCHIVED }

/** Sorting: pin dulu (urut pinOrder), lalu timestamp terbaru. */
fun List<ChatListItem>.sortedForDisplay(): List<ChatListItem> =
    sortedWith(compareBy<ChatListItem> { !it.pinned }.thenBy { it.pinOrder }.thenByDescending { it.lastMessageAt })

package id.sukiapps.hermes.data.local

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository data lokal: conversations + messages via Room.
 * Judul otomatis = pesan user pertama (maks 40 karakter), seperti legacy.
 */
@Singleton
class ChatRepository @Inject constructor(
    private val dao: HermesDao,
    private val prefs: AppPrefs
) {
    fun conversations(): Flow<List<ConversationEntity>> = dao.allConversations()

    fun searchConversations(q: String): Flow<List<ConversationEntity>> =
        if (q.isBlank()) dao.allConversations() else dao.searchConversations(q)

    fun messages(convId: Long): Flow<List<MessageEntity>> = dao.messagesByConv(convId)

    suspend fun conversation(id: Long): ConversationEntity? = dao.conversationById(id)

    suspend fun lastMessages(convId: Long, n: Int): List<MessageEntity> =
        dao.lastMessages(convId, n).reversed()

    /** Buat percakapan baru; kembalikan id. */
    suspend fun newConversation(
        title: String,
        persona: String?,
        incognito: Boolean = false
    ): Long {
        val now = System.currentTimeMillis()
        return dao.insertConversation(
            ConversationEntity(
                title = title.ifBlank { "Percakapan baru" },
                createdAt = now,
                updatedAt = now,
                persona = persona?.ifBlank { null },
                incognito = incognito
            )
        )
    }

    /**
     * v19: hapus permanen semua percakapan incognito + pesannya.
     * Dipanggil saat aplikasi/ViewModel chat dimulai (membersihkan sisa
     * sesi incognito yang tertinggal karena aplikasi tertutup mendadak)
     * dan hasilnya: tidak ada jejak incognito yang bertahan antar-sesi.
     */
    suspend fun purgeIncognito() {
        val ids = dao.incognitoIds()
        if (ids.isEmpty()) return
        ids.forEach { dao.deleteMessagesByConv(it) }
        dao.deleteIncognitoConversations()
        if (prefs.currentConvId in ids) prefs.currentConvId = -1L
    }

    suspend fun addMessage(
        convId: Long,
        role: String,
        content: String,
        attachmentPath: String? = null
    ): Long {
        val id = dao.insertMessage(
            MessageEntity(
                convId = convId,
                role = role,
                content = content,
                createdAt = System.currentTimeMillis(),
                attachmentPath = attachmentPath
            )
        )
        dao.touchConversation(convId, System.currentTimeMillis())
        return id
    }

    suspend fun updateMessageContent(id: Long, content: String) =
        dao.updateMessageContent(id, content)

    suspend fun rename(id: Long, title: String) =
        dao.renameConversation(id, title, System.currentTimeMillis())

    /** Edit pesan user: hapus semua pesan setelahnya, update isinya. */
    suspend fun editUserMessage(convId: Long, msgId: Long, newText: String) {
        dao.deleteMessagesAfter(convId, msgId)
        dao.updateMessageContent(msgId, newText)
        dao.touchConversation(convId, System.currentTimeMillis())
    }

    /** v19.1: hapus semua pesan setelah [afterId] (dipakai regenerate). */
    suspend fun deleteMessagesAfter(convId: Long, afterId: Long) {
        dao.deleteMessagesAfter(convId, afterId)
        dao.touchConversation(convId, System.currentTimeMillis())
    }

    suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned)

    suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived)

    /** Hapus percakapan + seluruh pesannya. */
    suspend fun deleteConversation(id: Long) {
        dao.deleteMessagesByConv(id)
        dao.conversationById(id)?.let { dao.deleteConversation(it) }
        if (prefs.currentConvId == id) prefs.currentConvId = -1L
    }

    /** Judul dari pesan user pertama (maks 40 char). */
    fun makeTitle(firstUserText: String): String {
        val t = firstUserText.trim().replace(Regex("\\s+"), " ")
        return if (t.length <= 40) t else t.substring(0, 40) + "…"
    }
}

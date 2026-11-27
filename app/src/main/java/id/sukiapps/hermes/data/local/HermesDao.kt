package id.sukiapps.hermes.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HermesDao {

    // ---- conversations ----
    // v19: percakapan incognito SELALU disaring keluar dari daftar,
    // pencarian, dan bacaan sekali-jalan (yang dipakai backup) —
    // incognito hanya boleh dibuka lewat id-nya secara eksplisit.

    /** Semua percakapan: pinned dulu, lalu updated_at terbaru. */
    @Query("SELECT * FROM conversations WHERE incognito = 0 ORDER BY pinned DESC, updated_at DESC")
    fun allConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE incognito = 0 ORDER BY pinned DESC, updated_at DESC")
    suspend fun allConversationsOnce(): List<ConversationEntity>

    @Query(
        "SELECT * FROM conversations " +
            "WHERE incognito = 0 AND title LIKE '%' || :q || '%' " +
            "ORDER BY pinned DESC, updated_at DESC"
    )
    fun searchConversations(q: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun conversationById(id: Long): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(c: ConversationEntity): Long

    @Update
    suspend fun updateConversation(c: ConversationEntity)

    @Delete
    suspend fun deleteConversation(c: ConversationEntity)

    @Query("UPDATE conversations SET title = :title, updated_at = :updatedAt WHERE id = :id")
    suspend fun renameConversation(id: Long, title: String, updatedAt: Long)

    @Query("UPDATE conversations SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE conversations SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("UPDATE conversations SET updated_at = :updatedAt WHERE id = :id")
    suspend fun touchConversation(id: Long, updatedAt: Long)

    /** v19: id semua percakapan incognito (untuk pembersihan). */
    @Query("SELECT id FROM conversations WHERE incognito = 1")
    suspend fun incognitoIds(): List<Long>

    /** v19: hapus semua baris percakapan incognito. */
    @Query("DELETE FROM conversations WHERE incognito = 1")
    suspend fun deleteIncognitoConversations()

    // ---- messages ----

    @Query("SELECT * FROM messages WHERE conv_id = :convId ORDER BY id ASC")
    fun messagesByConv(convId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conv_id = :convId ORDER BY id ASC")
    suspend fun messagesByConvOnce(convId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conv_id = :convId ORDER BY id DESC LIMIT :n")
    suspend fun lastMessages(convId: Long, n: Int): List<MessageEntity>

    @Insert
    suspend fun insertMessage(m: MessageEntity): Long

    @Query("UPDATE messages SET content = :content WHERE id = :id")
    suspend fun updateMessageContent(id: Long, content: String)

    @Query("DELETE FROM messages WHERE conv_id = :convId")
    suspend fun deleteMessagesByConv(convId: Long)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    @Query("DELETE FROM messages WHERE conv_id = :convId AND id > :afterId")
    suspend fun deleteMessagesAfter(convId: Long, afterId: Long)

    /**
     * v20.1 (tool session_search): cari pesan di riwayat — percakapan
     * incognito TIDAK pernah ikut. :q sudah di-escape (\, %, _) oleh
     * pemanggil. Hanya query baru; skema tidak berubah.
     */
    @Query(
        "SELECT m.id AS msgId, m.conv_id AS convId, c.title AS title, m.role AS role, " +
            "m.content AS content, m.created_at AS createdAt " +
            "FROM messages m JOIN conversations c ON c.id = m.conv_id " +
            "WHERE c.incognito = 0 AND m.content LIKE '%' || :q || '%' ESCAPE '\\' " +
            "ORDER BY m.id DESC LIMIT :limit"
    )
    suspend fun searchMessages(q: String, limit: Int): List<MessageHit>

    @Query("SELECT COUNT(*) FROM messages WHERE conv_id = :convId")
    suspend fun messageCount(convId: Long): Int
}

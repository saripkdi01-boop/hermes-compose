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

    /** Semua percakapan: pinned dulu, lalu updated_at terbaru. */
    @Query("SELECT * FROM conversations ORDER BY pinned DESC, updated_at DESC")
    fun allConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY pinned DESC, updated_at DESC")
    suspend fun allConversationsOnce(): List<ConversationEntity>

    @Query(
        "SELECT * FROM conversations " +
            "WHERE title LIKE '%' || :q || '%' " +
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

    @Query("SELECT COUNT(*) FROM messages WHERE conv_id = :convId")
    suspend fun messageCount(convId: Long): Int
}

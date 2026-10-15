package id.sukiapps.hermes.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tabel messages — skema KONTRAK, identik dengan legacy ChatDb v4:
 * id INTEGER PRIMARY KEY AUTOINCREMENT, conv_id INTEGER NOT NULL,
 * role TEXT NOT NULL, content TEXT NOT NULL, created_at INTEGER NOT NULL,
 * attachment_path TEXT (nullable). Index idx_messages_conv(conv_id, id).
 */
@Entity(
    tableName = "messages",
    indices = [Index(value = ["conv_id", "id"], name = "idx_messages_conv")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "conv_id")
    val convId: Long,
    val role: String, // "user" | "assistant"
    val content: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "attachment_path")
    val attachmentPath: String? = null
)

package id.sukiapps.hermes.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tabel conversations — skema KONTRAK, identik dengan legacy ChatDb v4:
 * id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL,
 * created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
 * pinned INTEGER NOT NULL DEFAULT 0, archived INTEGER NOT NULL DEFAULT 0,
 * persona TEXT (nullable).
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "pinned", defaultValue = "0")
    val pinned: Boolean = false,
    @ColumnInfo(name = "archived", defaultValue = "0")
    val archived: Boolean = false,
    val persona: String? = null
)

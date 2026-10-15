package id.sukiapps.hermes.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database Room — file "hermes_chat.db".
 *
 * version 5 (naik dari 4) dengan Migration(4, 5) eksplisit.
 *
 * MENGAPA ADA MIGRATION (menyimpang dari asumsi "skema persis"):
 * Skema SQL legacy memang identik secara teks, TETAPI Room memvalidasi
 * TableInfo logis saat open. Legacy memakai
 *   `id INTEGER PRIMARY KEY AUTOINCREMENT`  (tanpa NOT NULL eksplisit)
 * dan PRAGMA table_info melaporkannya sebagai notnull=0, sementara Room
 * mengekspektasikan notNull=true untuk PK non-null. Tanpa migration,
 * user yang upgrade dari v8.0 berisiko crash "Room cannot verify the
 * data integrity" tepat saat aplikasi dibuka.
 *
 * Migration(4,5) me-recreate kedua tabel dengan DDL kanonis Room
 * (PK `NOT NULL` eksplisit) dan menyalin seluruh data — deterministik
 * dan tanpa kehilangan data, apa pun perilaku validasi Room.
 */
@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 5,
    exportSchema = false
)
abstract class HermesDatabase : RoomDatabase() {
    abstract fun dao(): HermesDao

    companion object {
        const val DB_NAME = "hermes_chat.db"

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Index harus di-drop dulu sebelum tabel di-swap.
                db.execSQL("DROP INDEX IF EXISTS idx_messages_conv")

                db.execSQL(
                    "CREATE TABLE conversations_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "created_at INTEGER NOT NULL, " +
                        "updated_at INTEGER NOT NULL, " +
                        "pinned INTEGER NOT NULL DEFAULT 0, " +
                        "archived INTEGER NOT NULL DEFAULT 0, " +
                        "persona TEXT)"
                )
                db.execSQL(
                    "INSERT INTO conversations_new " +
                        "(id, title, created_at, updated_at, pinned, archived, persona) " +
                        "SELECT id, title, created_at, updated_at, pinned, archived, persona " +
                        "FROM conversations"
                )
                db.execSQL("DROP TABLE conversations")
                db.execSQL("ALTER TABLE conversations_new RENAME TO conversations")

                db.execSQL(
                    "CREATE TABLE messages_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "conv_id INTEGER NOT NULL, " +
                        "role TEXT NOT NULL, " +
                        "content TEXT NOT NULL, " +
                        "created_at INTEGER NOT NULL, " +
                        "attachment_path TEXT)"
                )
                db.execSQL(
                    "INSERT INTO messages_new " +
                        "(id, conv_id, role, content, created_at, attachment_path) " +
                        "SELECT id, conv_id, role, content, created_at, attachment_path " +
                        "FROM messages"
                )
                db.execSQL("DROP TABLE messages")
                db.execSQL("ALTER TABLE messages_new RENAME TO messages")

                db.execSQL("CREATE INDEX idx_messages_conv ON messages(conv_id, id)")
            }
        }
    }
}

package id.sukiapps.hermes.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager

/**
 * v19.1 (audit) — Migration(4,5) dan Migration(5,6) dijalankan pada SQLite
 * sungguhan (sqlite-jdbc, test-only) mulai dari DDL legacy v4, lalu skema
 * hasilnya dibandingkan dengan yang diharapkan entity Room:
 * PK NOT NULL, default "0" untuk kolom boolean, index idx_messages_conv,
 * dan SELURUH data lama utuh.
 *
 * Migration hanya memanggil execSQL(String), jadi SupportSQLiteDatabase
 * cukup di-proxy ke JDBC (tanpa Robolectric / instrumented test).
 */
class HermesMigrationTest {

    private lateinit var conn: Connection
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        db = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args?.size == 1) {
                conn.createStatement().use { it.execute(args[0] as String) }
                null
            } else {
                throw UnsupportedOperationException("tidak dipakai migration: ${method.name}")
            }
        } as SupportSQLiteDatabase
    }

    @After
    fun tearDown() {
        conn.close()
    }

    private fun exec(sql: String) = conn.createStatement().use { it.execute(sql) }

    /** DDL legacy ChatDb v4 (PK tanpa NOT NULL eksplisit — sumber masalah). */
    private fun createLegacyV4() {
        exec(
            "CREATE TABLE conversations (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, " +
                "pinned INTEGER NOT NULL DEFAULT 0, archived INTEGER NOT NULL DEFAULT 0, persona TEXT)"
        )
        exec(
            "CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, conv_id INTEGER NOT NULL, " +
                "role TEXT NOT NULL, content TEXT NOT NULL, created_at INTEGER NOT NULL, attachment_path TEXT)"
        )
        exec("CREATE INDEX idx_messages_conv ON messages(conv_id, id)")
        exec("INSERT INTO conversations VALUES (1,'Halo',10,20,1,0,'persona A')")
        exec("INSERT INTO conversations VALUES (7,'Kerja',30,40,0,1,NULL)")
        exec("INSERT INTO messages VALUES (1,1,'user','hai',11,NULL)")
        exec("INSERT INTO messages VALUES (2,1,'assistant','halo juga **tebal**',12,NULL)")
        exec("INSERT INTO messages VALUES (9,7,'user','lampiran',31,'a.pdf (1 KB)')")
    }

    private data class Col(val name: String, val type: String, val notNull: Boolean, val dflt: String?, val pk: Int)

    private fun columns(table: String): Map<String, Col> =
        conn.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info($table)").use { rs ->
                val out = LinkedHashMap<String, Col>()
                while (rs.next()) {
                    out[rs.getString("name")] = Col(
                        rs.getString("name"), rs.getString("type"),
                        rs.getInt("notnull") == 1, rs.getString("dflt_value"), rs.getInt("pk")
                    )
                }
                out
            }
        }

    private fun count(sql: String): Int =
        conn.createStatement().use { st -> st.executeQuery(sql).use { it.next(); it.getInt(1) } }

    private fun migrateToLatest() {
        HermesDatabase.MIGRATION_4_5.migrate(db)
        HermesDatabase.MIGRATION_5_6.migrate(db)
    }

    @Test
    fun migration4to6_keepsAllData() {
        createLegacyV4()
        migrateToLatest()
        assertEquals(2, count("SELECT COUNT(*) FROM conversations"))
        assertEquals(3, count("SELECT COUNT(*) FROM messages"))
        assertEquals(1, count("SELECT COUNT(*) FROM conversations WHERE id=1 AND title='Halo' AND pinned=1 AND persona='persona A'"))
        assertEquals(1, count("SELECT COUNT(*) FROM conversations WHERE id=7 AND archived=1 AND persona IS NULL"))
        assertEquals(1, count("SELECT COUNT(*) FROM messages WHERE id=2 AND content='halo juga **tebal**'"))
        assertEquals(1, count("SELECT COUNT(*) FROM messages WHERE id=9 AND attachment_path='a.pdf (1 KB)'"))
        // Kolom baru v6: data lama = bukan incognito.
        assertEquals(2, count("SELECT COUNT(*) FROM conversations WHERE incognito = 0"))
    }

    @Test
    fun migration4to6_schemaMatchesRoomEntities() {
        createLegacyV4()
        migrateToLatest()
        val conv = columns("conversations")
        assertEquals(
            listOf("id", "title", "created_at", "updated_at", "pinned", "archived", "persona", "incognito"),
            conv.keys.toList()
        )
        // Room: PK non-null wajib notnull=1 (inilah alasan Migration 4→5).
        assertTrue(conv.getValue("id").notNull)
        assertEquals(1, conv.getValue("id").pk)
        for (c in listOf("title", "created_at", "updated_at", "pinned", "archived", "incognito")) {
            assertTrue("$c harus NOT NULL", conv.getValue(c).notNull)
        }
        // @ColumnInfo(defaultValue = "0") divalidasi Room sejak 2.2.
        for (c in listOf("pinned", "archived", "incognito")) {
            assertEquals("$c default", "0", conv.getValue(c).dflt)
        }
        assertEquals(false, conv.getValue("persona").notNull)

        val msg = columns("messages")
        assertEquals(
            listOf("id", "conv_id", "role", "content", "created_at", "attachment_path"),
            msg.keys.toList()
        )
        assertTrue(msg.getValue("id").notNull)
        assertEquals(false, msg.getValue("attachment_path").notNull)
        // Index entity MessageEntity tetap ada setelah tabel di-recreate.
        assertEquals(
            1,
            count("SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='idx_messages_conv' AND tbl_name='messages'")
        )
    }

    @Test
    fun autoincrement_continuesAfterMigration() {
        createLegacyV4()
        migrateToLatest()
        exec("INSERT INTO messages (conv_id, role, content, created_at) VALUES (1,'user','baru',50)")
        // Id baru tidak boleh bertabrakan dengan id lama (maks 9).
        assertTrue(count("SELECT MAX(id) FROM messages") > 9)
    }
}

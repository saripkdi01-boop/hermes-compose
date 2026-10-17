package id.sukiapps.hermes.ui.chatlist

import id.sukiapps.hermes.data.store.JsonStore
import org.json.JSONObject

/**
 * ChatListStore — metadata daftar chat via JsonStore atomik (tanpa Room).
 *
 * Disimpan: pin (+urutan), arsip, unread count, rename.
 * TIDAK disimpan di sini: isi pesan, status streaming/Upin (transien).
 *
 * File: <filesDir>/jsonstore/chatlist.json (via JsonStore).
 * Tulis atomik: JsonStore.mutate memakai tmp+rename.
 */
object ChatListStore {

    private const val STORE = "chatlist"

    private data class Meta(
        val pinned: Boolean = false,
        val pinOrder: Int = Int.MAX_VALUE,
        val archived: Boolean = false,
        val unreadCount: Int = 0,
        val displayName: String? = null
    )

    private fun readAll(): MutableMap<String, Meta> {
        val map = mutableMapOf<String, Meta>()
        val obj = try { JsonStore.load(STORE) } catch (_: Exception) { JSONObject() }
        val keys = obj.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val m = obj.optJSONObject(id) ?: continue
            map[id] = Meta(
                pinned = m.optBoolean("pinned", false),
                pinOrder = m.optInt("pinOrder", Int.MAX_VALUE),
                archived = m.optBoolean("archived", false),
                unreadCount = m.optInt("unread", 0),
                displayName = m.optString("displayName", null).takeIf { it.isNotEmpty() }
            )
        }
        return map
    }

    private fun writeAll(map: Map<String, Meta>) {
        JsonStore.mutate(STORE) { json ->
            // Hapus key yang tidak ada di map (sinkron penuh).
            val toRemove = mutableListOf<String>()
            val keys = json.keys()
            while (keys.hasNext()) toRemove.add(keys.next())
            toRemove.forEach { json.remove(it) }
            map.forEach { (id, meta) ->
                json.put(id, JSONObject().apply {
                    put("pinned", meta.pinned)
                    put("pinOrder", meta.pinOrder)
                    put("archived", meta.archived)
                    put("unread", meta.unreadCount)
                    if (meta.displayName != null) put("displayName", meta.displayName)
                })
            }
        }
    }

    /** Terapkan metadata ke daftar item (dipanggil ViewModel setelah load chat). */
    fun applyMeta(items: List<ChatListItem>): List<ChatListItem> {
        val meta = readAll()
        return items.map { item ->
            val m = meta[item.id] ?: return@map item
            item.copy(
                pinned = m.pinned,
                pinOrder = m.pinOrder,
                archived = m.archived,
                unreadCount = m.unreadCount,
                displayName = m.displayName ?: item.displayName
            )
        }
    }

    fun setPinned(id: String, pinned: Boolean) {
        val map = readAll()
        val cur = map[id] ?: Meta()
        val order = if (pinned) {
            (map.values.mapNotNull { it.pinOrder.takeIf { o -> o != Int.MAX_VALUE } }.maxOrNull() ?: -1) + 1
        } else Int.MAX_VALUE
        map[id] = cur.copy(pinned = pinned, pinOrder = order)
        writeAll(map)
    }

    /** Reorder pin via drag: simpan urutan baru untuk id yang di-pin. */
    fun reorderPins(orderedIds: List<String>) {
        val map = readAll()
        orderedIds.forEachIndexed { idx, id ->
            val cur = map[id] ?: Meta()
            map[id] = cur.copy(pinned = true, pinOrder = idx)
        }
        writeAll(map)
    }

    fun setArchived(id: String, archived: Boolean) {
        val map = readAll()
        val cur = map[id] ?: Meta()
        map[id] = cur.copy(archived = archived)
        writeAll(map)
    }

    fun setUnread(id: String, count: Int) {
        val map = readAll()
        val cur = map[id] ?: Meta()
        map[id] = cur.copy(unreadCount = count.coerceAtLeast(0))
        writeAll(map)
    }

    fun markAllRead(ids: List<String>) {
        val map = readAll()
        ids.forEach { id ->
            val cur = map[id] ?: Meta()
            map[id] = cur.copy(unreadCount = 0)
        }
        writeAll(map)
    }

    fun rename(id: String, displayName: String) {
        val map = readAll()
        val cur = map[id] ?: Meta()
        map[id] = cur.copy(displayName = displayName.ifBlank { null })
        writeAll(map)
    }

    fun remove(id: String) {
        val map = readAll()
        map.remove(id)
        writeAll(map)
    }

    /** Dipanggil saat chat dihapus total: bersihkan metadata. */
    fun removeMany(ids: List<String>) {
        val map = readAll()
        ids.forEach { map.remove(it) }
        writeAll(map)
    }
}

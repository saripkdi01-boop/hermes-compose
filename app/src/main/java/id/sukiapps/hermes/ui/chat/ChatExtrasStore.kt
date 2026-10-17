package id.sukiapps.hermes.ui.chat

import id.sukiapps.hermes.data.store.JsonStore
import org.json.JSONObject

/**
 * ChatExtrasStore — metadata UI per pesan via JsonStore atomik (tanpa Room).
 *
 * Disimpan per chatId:
 * - reaksi: { messageId: { emoji: count, my: emoji } }
 * - bintang: [messageId]
 * - draft: teks draft composer
 *
 * Store name: "chat_extras".
 */
object ChatExtrasStore {

    private const val STORE = "chat_extras"

    private fun chatObj(chatId: String): JSONObject {
        val root = try { JsonStore.load(STORE) } catch (_: Exception) { JSONObject() }
        return root.optJSONObject(chatId) ?: JSONObject()
    }

    private fun saveChat(chatId: String, obj: JSONObject) {
        JsonStore.mutate<Unit>(STORE, { root ->
            root.put(chatId, obj)
        })
    }

    // ── Reaksi ──────────────────────────────────────────────

    fun toggleReaction(chatId: String, messageId: String, emoji: String): Map<String, Int> {
        val chat = chatObj(chatId)
        val reactions = chat.optJSONObject("reactions") ?: JSONObject()
        val entry = reactions.optJSONObject(messageId) ?: JSONObject()
        val counts = entry.optJSONObject("counts") ?: JSONObject()
        val my = entry.optString("my", null)

        if (my == emoji) {
            // Batalkan reaksi saya.
            counts.put(emoji, (counts.optInt(emoji, 1) - 1).coerceAtLeast(0))
            if (counts.optInt(emoji) == 0) counts.remove(emoji)
            entry.remove("my")
        } else {
            // Hapus reaksi lama saya, tambah yang baru.
            if (my != null) {
                counts.put(my, (counts.optInt(my, 1) - 1).coerceAtLeast(0))
                if (counts.optInt(my) == 0) counts.remove(my)
            }
            counts.put(emoji, counts.optInt(emoji, 0) + 1)
            entry.put("my", emoji)
        }
        entry.put("counts", counts)
        reactions.put(messageId, entry)
        chat.put("reactions", reactions)
        saveChat(chatId, chat)
        return countsToMap(counts)
    }

    fun getReactions(chatId: String, messageId: String): Pair<Map<String, Int>, String?> {
        val entry = chatObj(chatId).optJSONObject("reactions")
            ?.optJSONObject(messageId) ?: return emptyMap<String, Int>() to null
        val counts = entry.optJSONObject("counts") ?: JSONObject()
        val my = entry.optString("my", null).takeIf { !it.isNullOrEmpty() }
        return countsToMap(counts) to my
    }

    private fun countsToMap(counts: JSONObject): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        val keys = counts.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k] = counts.optInt(k, 0)
        }
        return map
    }

    // ── Bintang ─────────────────────────────────────────────

    fun toggleStar(chatId: String, messageId: String): Boolean {
        val chat = chatObj(chatId)
        val arr = chat.optJSONArray("starred") ?: org.json.JSONArray()
        val list = (0 until arr.length()).map { arr.getString(it) }.toMutableList()
        val nowStarred = if (messageId in list) {
            list.remove(messageId); false
        } else {
            list.add(messageId); true
        }
        val newArr = org.json.JSONArray()
        list.forEach { newArr.put(it) }
        chat.put("starred", newArr)
        saveChat(chatId, chat)
        return nowStarred
    }

    fun isStarred(chatId: String, messageId: String): Boolean {
        val arr = chatObj(chatId).optJSONArray("starred") ?: return false
        return (0 until arr.length()).any { arr.getString(it) == messageId }
    }

    fun starredIds(chatId: String): Set<String> {
        val arr = chatObj(chatId).optJSONArray("starred") ?: return emptySet()
        return (0 until arr.length()).map { arr.getString(it) }.toSet()
    }

    // ── Draft ───────────────────────────────────────────────

    fun saveDraft(chatId: String, text: String) {
        val chat = chatObj(chatId)
        if (text.isBlank()) chat.remove("draft") else chat.put("draft", text)
        saveChat(chatId, chat)
    }

    fun loadDraft(chatId: String): String =
        chatObj(chatId).optString("draft", "")

    fun clearDraft(chatId: String) = saveDraft(chatId, "")
}

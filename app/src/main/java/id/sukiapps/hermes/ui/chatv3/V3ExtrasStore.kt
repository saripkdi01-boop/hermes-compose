package id.sukiapps.hermes.ui.chatv3

import id.sukiapps.hermes.data.store.JsonStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * V3ExtrasStore — reaksi, bintang, draft via JsonStore.
 * Store: "chatv3_extras". Isolasi penuh dari ui.chat.
 */
object V3ExtrasStore {

    private const val STORE = "chatv3_extras"

    private fun chatObj(chatId: String): JSONObject {
        val root = try {
            JsonStore.load(STORE)
        } catch (_: Exception) {
            JSONObject()
        }
        return root.optJSONObject(chatId) ?: JSONObject()
    }

    private fun saveChat(chatId: String, obj: JSONObject) {
        JsonStore.mutate<Unit>(STORE, { root ->
            root.put(chatId, obj)
        })
    }

    fun toggleReaction(chatId: String, messageId: String, emoji: String) {
        val chat = chatObj(chatId)
        val reactions = chat.optJSONObject("reactions") ?: JSONObject()
        val entry = reactions.optJSONObject(messageId) ?: JSONObject()
        val counts = entry.optJSONObject("counts") ?: JSONObject()
        val my = entry.optString("my", null).takeIf { !it.isNullOrEmpty() }

        if (my == emoji) {
            val c = (counts.optInt(emoji, 1) - 1).coerceAtLeast(0)
            if (c == 0) counts.remove(emoji) else counts.put(emoji, c)
            entry.remove("my")
        } else {
            if (my != null) {
                val c = (counts.optInt(my, 1) - 1).coerceAtLeast(0)
                if (c == 0) counts.remove(my) else counts.put(my, c)
            }
            counts.put(emoji, counts.optInt(emoji, 0) + 1)
            entry.put("my", emoji)
        }
        entry.put("counts", counts)
        reactions.put(messageId, entry)
        chat.put("reactions", reactions)
        saveChat(chatId, chat)
    }

    fun toggleStar(chatId: String, messageId: String): Boolean {
        val chat = chatObj(chatId)
        val arr = chat.optJSONArray("starred") ?: JSONArray()
        val list = (0 until arr.length()).map { arr.getString(it) }.toMutableList()
        val nowStarred = if (messageId in list) {
            list.remove(messageId)
            false
        } else {
            list.add(messageId)
            true
        }
        val newArr = JSONArray()
        list.forEach { newArr.put(it) }
        chat.put("starred", newArr)
        saveChat(chatId, chat)
        return nowStarred
    }

    fun isStarred(chatId: String, messageId: String): Boolean {
        val arr = chatObj(chatId).optJSONArray("starred") ?: return false
        return (0 until arr.length()).any { arr.getString(it) == messageId }
    }

    fun saveDraft(chatId: String, text: String) {
        val chat = chatObj(chatId)
        if (text.isBlank()) chat.remove("draft") else chat.put("draft", text)
        saveChat(chatId, chat)
    }

    fun loadDraft(chatId: String): String =
        chatObj(chatId).optString("draft", "")
}

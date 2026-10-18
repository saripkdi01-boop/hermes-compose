package id.sukiapps.hermes.ui.chatv3

import android.content.Context
import id.sukiapps.hermes.data.store.JsonStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Penyimpanan ekstra per chat: bintang, reaksi, teks edit, draft.
 * Berbasis StateFlow — UI observe, bukan baca langsung.
 *
 * Disimpan sebagai JSON per chat: filesDir/chatv3_extras/<chatId>.json
 * Struktur: { "<msgId>": { "starred": bool, "reaction": "👍"|null, "editedText": "..."|null } }
 */
class V3ExtrasStore(context: Context, private val chatId: String) {

    data class Extras(
        val starred: Boolean = false,
        val reaction: String? = null,
        val editedText: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<Map<String, Extras>>(emptyMap())
    val state: StateFlow<Map<String, Extras>> = _state.asStateFlow()

    private val storeName = "chatv3_extras_$chatId"

    init {
        scope.launch {
            val json = JsonStore.load(storeName)
            val map = mutableMapOf<String, Extras>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val o = json.optJSONObject(k) ?: continue
                map[k] = Extras(
                    starred = o.optBoolean("starred", false),
                    reaction = o.optString("reaction", null as String?),
                    editedText = o.optString("editedText", null as String?),
                )
            }
            _state.value = map
        }
    }

    private fun persist(map: Map<String, Extras>) {
        val json = JSONObject()
        map.forEach { (k, v) ->
            json.put(k, JSONObject().apply {
                put("starred", v.starred)
                if (v.reaction != null) put("reaction", v.reaction)
                if (v.editedText != null) put("editedText", v.editedText)
            })
        }
        JsonStore.save(storeName, json) {}
    }

    private fun update(msgId: String, transform: (Extras) -> Extras) {
        val next = _state.value.toMutableMap()
        next[msgId] = transform(next[msgId] ?: Extras())
        _state.value = next
        scope.launch { persist(next) }
    }

    fun toggleStar(msgId: String) = update(msgId) { it.copy(starred = !it.starred) }

    fun toggleReaction(msgId: String, emoji: String) = update(msgId) {
        it.copy(reaction = if (it.reaction == emoji) null else emoji)
    }

    fun clearReaction(msgId: String) = update(msgId) { it.copy(reaction = null) }

    fun editText(msgId: String, newText: String) = update(msgId) { it.copy(editedText = newText) }

    fun isStarred(msgId: String): Boolean = _state.value[msgId]?.starred == true

    fun getReaction(msgId: String): String? = _state.value[msgId]?.reaction

    /** Ekspor satu pesan sebagai Markdown. */
    fun exportMd(msgId: String, originalText: String, senderName: String): String {
        val e = _state.value[msgId]
        val text = e?.editedText ?: originalText
        val star = if (e?.starred == true) "⭐ " else ""
        return "$star**$senderName**\n\n$text\n"
    }

    // ===== Draft per chat (terpisah dari extras pesan) =====
    private val draftStore = "chatv3_draft_$chatId"

    fun saveDraft(text: String) {
        scope.launch {
            val j = JSONObject().put("draft", text)
            JsonStore.save(draftStore, j) {}
        }
    }

    fun loadDraft(onLoaded: (String) -> Unit) {
        scope.launch {
            val j = JsonStore.load(draftStore)
            onLoaded(j.optString("draft", ""))
        }
    }
}

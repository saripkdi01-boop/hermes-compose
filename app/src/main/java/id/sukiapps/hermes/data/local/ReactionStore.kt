package id.sukiapps.hermes.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import id.sukiapps.hermes.ui.chat.Emoji
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v19.2 — reaksi emoji per pesan + daftar emoji "baru dipakai".
 *
 * Disimpan di SharedPreferences TERPISAH (`hermes_reactions`), BUKAN di
 * Room: skema Room (kontrak) dan file prefs `hermes_chat` tidak berubah,
 * dan reaksi tidak ikut backup ZIP (kosmetik). Id pesan Room memakai
 * AUTOINCREMENT sehingga tidak pernah dipakai ulang — reaksi yatim dari
 * pesan yang dihapus tidak akan menempel ke pesan lain.
 *
 * Reaksi di chat incognito hanya hidup di memori (tidak ditulis ke disk),
 * sejalan dengan janji "tanpa jejak".
 */
@Singleton
class ReactionStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _reactions = MutableStateFlow(load())
    val reactions: StateFlow<Map<Long, String>> = _reactions.asStateFlow()

    private val _recent = MutableStateFlow(
        sp.getString(KEY_RECENT, "").orEmpty().split(SEP).filter { it.isNotBlank() }
    )
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    private fun load(): Map<Long, String> =
        sp.all.mapNotNull { (k, v) ->
            val id = k.removePrefix(PREFIX).takeIf { k.startsWith(PREFIX) }?.toLongOrNull()
            val emoji = v as? String
            if (id != null && !emoji.isNullOrBlank()) id to emoji else null
        }.toMap()

    /**
     * Pasang [emoji] di pesan [msgId]; emoji yang sama diketuk lagi = lepas.
     * @param persist false untuk chat incognito (memori saja).
     */
    fun toggle(msgId: Long, emoji: String, persist: Boolean = true) {
        if (msgId <= 0) return
        val cur = _reactions.value
        val next = if (cur[msgId] == emoji) cur - msgId else cur + (msgId to emoji)
        _reactions.value = next
        if (persist) {
            val e = sp.edit()
            if (next.containsKey(msgId)) e.putString(PREFIX + msgId, emoji) else e.remove(PREFIX + msgId)
            e.apply()
        }
        if (next.containsKey(msgId)) noteUsed(emoji)
    }

    /** Catat emoji yang baru dipakai (picker composer + reaksi). */
    fun noteUsed(emoji: String) {
        val next = Emoji.pushRecent(_recent.value, emoji)
        _recent.value = next
        sp.edit().putString(KEY_RECENT, next.joinToString(SEP)).apply()
    }

    private companion object {
        const val FILE = "hermes_reactions"
        const val PREFIX = "r_"
        const val KEY_RECENT = "recent_emoji"
        const val SEP = "\u001F"
    }
}

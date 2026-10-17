package id.sukiapps.hermes.ui.chatlist

import android.content.Context
import id.sukiapps.hermes.data.store.JsonStore
import org.json.JSONObject
import java.io.File

/**
 * HermesChatProvider — adapter ChatProvider ke penyimpanan existing.
 *
 * ASUMSI (verifikasi & sesuaikan):
 * 1. Daftar percakapan tersimpan sebagai file JSON per chat di
 *    filesDir/chats/<chatId>.json, masing-masing berisi:
 *    { "id": "...", "title": "...", "updatedAt": 123, "messages": [...] }
 * 2. Preview = teks pesan terakhir; jika tidak ada, pakai title.
 *
 * Jika struktur berbeda, sesuaikan fungsi parseChatFile() saja —
 * sisa logika (paging, hapus, restore) tidak perlu diubah.
 * Tidak pernah crash: file rusak / tidak ada → dilewati.
 */
class HermesChatProvider(private val context: Context) : ChatProvider {

    private fun chatsDir(): File = File(context.filesDir, "chats")

    private fun parseChatFile(f: File): ChatListItem? {
        return try {
            val json = JSONObject(f.readText())
            val id = json.optString("id", f.nameWithoutExtension)
            if (id.isBlank()) return null
            val title = json.optString("title", "Percakapan")
                .ifBlank { "Percakapan" }
            val updatedAt = json.optLong("updatedAt", f.lastModified())
            val preview = extractPreview(json)
            ChatListItem(
                id = id,
                name = title,
                lastMessagePreview = preview,
                lastMessageAt = updatedAt
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractPreview(json: JSONObject): String {
        return try {
            val messages = json.optJSONArray("messages") ?: return ""
            if (messages.length() == 0) return ""
            val last = messages.getJSONObject(messages.length() - 1)
            last.optString("text", last.optString("content", ""))
                .take(120)
        } catch (_: Exception) {
            ""
        }
    }

    private fun allChats(): List<ChatListItem> {
        val dir = chatsDir()
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.isFile && f.extension == "json" }
            ?.mapNotNull { parseChatFile(it) }
            ?.sortedByDescending { it.lastMessageAt }
            ?: emptyList()
    }

    override suspend fun loadChats(limit: Int, offset: Int): List<ChatListItem> {
        val all = allChats()
        if (offset >= all.size) return emptyList()
        return all.drop(offset).take(limit)
    }

    override suspend fun totalCount(): Int = allChats().size

    override suspend fun deleteChats(ids: List<String>) {
        // Soft-delete: pindahkan ke trash agar bisa undo 5 detik.
        val trash = File(context.filesDir, "chats_trash").apply { mkdirs() }
        ids.forEach { id ->
            val src = File(chatsDir(), "$id.json")
            if (src.exists()) src.renameTo(File(trash, "$id.json"))
        }
    }

    override suspend fun restoreChats(items: List<ChatListItem>) {
        val trash = File(context.filesDir, "chats_trash")
        items.forEach { item ->
            val src = File(trash, "${item.id}.json")
            if (src.exists()) src.renameTo(File(chatsDir(), "${item.id}.json"))
        }
    }
}

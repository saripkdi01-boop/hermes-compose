package id.sukiapps.hermes.ui.chatv3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable

/**
 * Entry point integrasi ChatV3 ke NavHost existing.
 *
 * CARA PAKAI (di MainActivity, dalam NavHost):
 *
 *   v3ChatRoute(
 *     nav = nav,
 *     messagesProvider = { chatId ->
 *       // TODO: ganti dengan sumber pesan existing.
 *       // Contoh: chatViewModel.messages.collectAsState().value.map { it.toV3() }
 *       emptyList()
 *     },
 *     chatNameProvider = { chatId -> "Percakapan" }
 *   )
 *
 * Lalu navigasi: nav.navigate("chatv3/$chatId")
 */

const val V3_ROUTE = "chatv3/{chatId}"

fun v3RouteFor(chatId: String) = "chatv3/$chatId"

@Deprecated("Gunakan v3ChatRoute dengan NavGraphBuilder")
@Composable
fun V3EntryPlaceholder() {
}

/**
 * Adapter: ubah model pesan existing ke V3Message.
 * Implementasikan sesuai struktur data Hermes.
 */
interface V3MessageAdapter<T> {
    fun toV3(src: T): V3Message
}

/**
 * State holder sederhana untuk integrasi awal.
 * Ganti dengan ViewModel existing saat integrasi penuh.
 */
class V3IntegrationState(
    val chatId: String,
    val chatName: String,
    val messages: List<V3Message> = emptyList()
)

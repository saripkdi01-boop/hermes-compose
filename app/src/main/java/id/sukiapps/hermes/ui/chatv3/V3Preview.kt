package id.sukiapps.hermes.ui.chatv3

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Preview ChatV3 dengan data contoh — untuk verifikasi visual.
 * Route: "chatv3preview"
 * Hapus setelah integrasi data asli selesai.
 */
@Composable
fun V3PreviewScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val sample = remember { v3SampleMessages() }
    Column(modifier = modifier.fillMaxSize()) {
        ChatV3Screen(
            chatName = "Preview ChatV3",
            messages = sample,
            onBack = onBack,
            modifier = Modifier.weight(1f)
        )
        V3Composer(
            chatId = "preview",
            onSend = {}
        )
    }
}

fun v3RoutePreview() = "chatv3preview"

private fun v3SampleMessages(): List<V3Message> {
    val now = System.currentTimeMillis()
    return listOf(
        V3Message(
            id = "1",
            sender = "user",
            text = "Halo, apa kabar?",
            timestamp = now - 3600_000
        ),
        V3Message(
            id = "2",
            sender = "ai",
            senderName = "Hermes",
            text = "Baik! Ada yang bisa saya bantu?",
            timestamp = now - 3500_000
        ),
        V3Message(
            id = "3",
            sender = "user",
            text = "Jelaskan apa itu Jetpack Compose dalam 2 kalimat.",
            timestamp = now - 3400_000
        ),
        V3Message(
            id = "4",
            sender = "ai",
            senderName = "Hermes",
            text = "Jetpack Compose adalah toolkit UI modern Android yang deklaratif. Kamu cukup tulis *apa* yang ingin ditampilkan, bukan *bagaimana* cara menampilkannya.",
            timestamp = now - 3300_000,
            reactions = mapOf("👍" to 2, "❤️" to 1),
            myReaction = "👍"
        ),
        V3Message(
            id = "5",
            sender = "user",
            text = "Kasih contoh kode sederhana",
            timestamp = now - 120_000
        ),
        V3Message(
            id = "6",
            sender = "user",
            text = "yang bisa langsung jalan",
            timestamp = now - 60_000
        ),
        V3Message(
            id = "7",
            sender = "upin",
            senderName = "Upin",
            text = "Ini contoh minimal:\n\n@Composable\nfun Halo() {\n  Text(\"Halo Suki!\")\n}",
            timestamp = now - 30_000,
            isCode = true,
            starred = true
        )
    )
}

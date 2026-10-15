package id.sukiapps.hermes.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class Page(
    val title: String,
    val body: String,
    val art: Int // 0=bubble, 1=key, 2=bolt
)

private val PAGES = listOf(
    Page(
        "Chat AI di HP kamu",
        "Hermes terhubung ke model AI lewat Jerouter. Tanya apa saja, " +
            "dapatkan jawaban streaming secara real-time.",
        0
    ),
    Page(
        "Key milikmu, terkunci rapat",
        "API key disimpan terenkripsi dengan Android Keystore. " +
            "Tidak pernah dikirim ke mana pun selain server AI pilihanmu.",
        1
    ),
    Page(
        "Lampiran cerdas",
        "Kirim foto, dokumen, bahkan file APK — Hermes membacanya " +
            "dan menjelaskannya untukmu.",
        2
    )
)

/** Onboarding 3 halaman: HorizontalPager + dots + ilustrasi vektor Canvas. */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { PAGES.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDone) { Text("Lewati") }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { idx ->
            val p = PAGES[idx]
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                PageArt(kind = p.art)
                Spacer(Modifier.height(32.dp))
                Text(
                    text = p.title,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = p.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // Dots.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(PAGES.size) { i ->
                val selected = pagerState.currentPage == i
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(if (selected) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (pagerState.currentPage == PAGES.size - 1) {
                    onDone()
                } else {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (pagerState.currentPage == PAGES.size - 1) "Mulai"
                else "Lanjut"
            )
            Icon(
                Icons.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Ilustrasi vektor sederhana per halaman (tanpa aset gambar). */
@Composable
private fun PageArt(kind: Int) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier = Modifier.size(160.dp)
    ) {
        val c = center
        when (kind) {
            0 -> {
                // Bubble chat.
                drawCircle(surface, radius = 70f, center = c)
                drawCircle(primary, radius = 46f, center = c)
                drawLine(
                    primary, Offset(c.x - 22, c.y - 8), Offset(c.x + 22, c.y - 8),
                    strokeWidth = 8f, cap = StrokeCap.Round
                )
                drawLine(
                    primary, Offset(c.x - 22, c.y + 8), Offset(c.x + 8, c.y + 8),
                    strokeWidth = 8f, cap = StrokeCap.Round
                )
            }
            1 -> {
                // Kunci.
                drawCircle(surface, radius = 70f, center = c)
                drawCircle(primary, radius = 26f, center = Offset(c.x, c.y - 18))
                drawCircle(surface, radius = 12f, center = Offset(c.x, c.y - 18))
                drawLine(
                    primary, Offset(c.x, c.y + 8), Offset(c.x, c.y + 52),
                    strokeWidth = 14f, cap = StrokeCap.Round
                )
                drawLine(
                    primary, Offset(c.x, c.y + 34), Offset(c.x + 20, c.y + 34),
                    strokeWidth = 10f, cap = StrokeCap.Round
                )
            }
            else -> {
                // Kilat.
                drawCircle(surface, radius = 70f, center = c)
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(c.x + 12, c.y - 44)
                    lineTo(c.x - 18, c.y + 8)
                    lineTo(c.x - 2, c.y + 8)
                    lineTo(c.x - 12, c.y + 44)
                    lineTo(c.x + 18, c.y - 8)
                    lineTo(c.x + 2, c.y - 8)
                    close()
                }
                drawPath(p, primary)
            }
        }
    }
}

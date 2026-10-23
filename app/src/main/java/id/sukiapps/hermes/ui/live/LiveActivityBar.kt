package id.sukiapps.hermes.ui.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sukiapps.hermes.ui.theme.LocalChatPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Status live request chat — diposting oleh ChatRepository via LiveStatusBus. */
sealed interface LiveStatus {
    data object Idle : LiveStatus
    data object Connecting : LiveStatus
    data object Sending : LiveStatus
    data object Drawing : LiveStatus
    data object Saving : LiveStatus
    data object Done : LiveStatus
}

/**
 * Bus status satu arah: lapisan data boleh post dari thread mana pun,
 * UI mengoleksi sebagai State. Thread-safe (MutableStateFlow).
 */
object LiveStatusBus {
    private val _status = MutableStateFlow<LiveStatus>(LiveStatus.Idle)
    val status: StateFlow<LiveStatus> = _status.asStateFlow()

    fun post(s: LiveStatus) {
        _status.value = s
    }
}

/**
 * Bar tipis status live — tampil di atas daftar chat saat ada request berjalan.
 * Auto-hide 2 detik setelah Selesai.
 */
@Composable
fun LiveActivityBar(modifier: Modifier = Modifier) {
    val status by LiveStatusBus.status.collectAsState()
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(status) {
        when (status) {
            is LiveStatus.Idle -> visible = false
            is LiveStatus.Done -> {
                visible = true
                delay(2000)
                // Sembunyikan hanya bila tidak ada status baru masuk
                if (LiveStatusBus.status.value is LiveStatus.Done) {
                    LiveStatusBus.post(LiveStatus.Idle)
                }
            }
            else -> visible = true
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        val palette = LocalChatPalette.current
        val done = status is LiveStatus.Done
        val label = when (status) {
            is LiveStatus.Connecting -> "Menghubungkan…"
            is LiveStatus.Sending -> "Mengirim…"
            is LiveStatus.Drawing -> "Menggambar…"
            is LiveStatus.Saving -> "Menyimpan…"
            is LiveStatus.Done -> "Selesai"
            else -> ""
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(palette.surface)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .semantics { contentDescription = "Status: $label" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (done) {
                Icon(
                    Icons.Filled.Check, contentDescription = null,
                    tint = palette.success, modifier = Modifier.size(14.dp),
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = palette.accent,
                )
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textSecondary,
            )
        }
    }
}

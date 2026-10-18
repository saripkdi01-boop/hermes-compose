package id.sukiapps.hermes.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sukiapps.hermes.net.ActivityLogger
import id.sukiapps.hermes.ui.theme.LocalSettingPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FILTERS = listOf("Semua", "Chat", "Konektor", "Error")

/**
 * Cermin notifikasi: timeline aktivitas dari ActivityLogger (append-only, rotasi 500).
 * Filter: Semua / Chat / Konektor / Error.
 */
@Composable
fun NotificationMirror(
    logger: ActivityLogger,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSettingPalette.current
    var events by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var filter by remember { mutableStateOf("Semua") }

    LaunchedEffect(Unit) {
        events = withContext(Dispatchers.IO) { logger.last(100) }
    }

    val shown = remember(events, filter) {
        events.filter { e ->
            val type = e.optString("type")
            val data = e.optJSONObject("data") ?: JSONObject()
            when (filter) {
                "Chat" -> type == "ChatRequest" || type == "ModelsReloaded"
                "Konektor" -> type == "ConnectorExecuted" || type == "TestResult"
                "Error" -> isErrorEntry(type, data)
                else -> true
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                FILTERS.forEach { f ->
                    val selected = f == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (selected) palette.accent else palette.surface
                            )
                            .clickable { filter = f }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .semantics { contentDescription = "Filter $f" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = f,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selected) palette.textOnAccent
                            else palette.textSecondary,
                        )
                    }
                }
            }
        }

        if (shown.isEmpty()) {
            item {
                Text(
                    text = "Belum ada aktivitas.",
                    fontSize = 13.sp,
                    color = palette.textTertiary,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        } else {
            items(shown.size, key = { i -> "$i-${shown[i].optLong("ts")}" }) { i ->
                ActivityRow(shown[i])
            }
        }
    }
}

@Composable
private fun ActivityRow(e: JSONObject) {
    val palette = LocalSettingPalette.current
    val type = e.optString("type")
    val data = e.optJSONObject("data") ?: JSONObject()
    val ts = e.optLong("ts", 0L)
    val err = isErrorEntry(type, data)
    val dotColor: Color = when {
        err -> palette.error
        type == "ChatRequest" || type == "ModelsReloaded" -> palette.accent
        type == "ConnectorExecuted" || type == "TestResult" -> palette.statusWorking
        else -> palette.textTertiary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entryTitle(type, data),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textPrimary,
            )
            val detail = entryDetail(type, data)
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = fmtTime(ts),
            fontSize = 12.sp,
            color = palette.textTertiary,
        )
    }
}

private fun isErrorEntry(type: String, data: JSONObject): Boolean {
    if (type == "RestoreFailed") return true
    return !data.optBoolean("ok", true)
}

private fun entryTitle(type: String, data: JSONObject): String = when (type) {
    "SettingsChanged" -> "Pengaturan diubah"
    "ModelsReloaded" -> "Model dimuat"
    "TestResult" -> "Tes: ${data.optString("target").ifEmpty { "koneksi" }}"
    "ConnectorExecuted" -> "Konektor: ${data.optString("id").ifEmpty { "?" }}"
    "BackupCreated" -> "Backup dibuat"
    "BackupRestored" -> "Backup dipulihkan"
    "RestoreFailed" -> "Restore gagal"
    "ChatRequest" -> "Permintaan chat"
    else -> type.ifEmpty { "Aktivitas" }
}

private fun entryDetail(type: String, data: JSONObject): String = when (type) {
    "SettingsChanged" ->
        "${data.optString("key")}: ${data.optString("from")} → ${data.optString("to")}"
    "ModelsReloaded" ->
        "${data.optInt("count")} model · ${data.optLong("ms")} ms"
    "TestResult" ->
        if (data.optBoolean("ok", true)) "OK · ${data.optLong("ms")} ms"
        else data.optString("reason").ifEmpty { "Gagal" }
    "ConnectorExecuted" ->
        "${data.optString("action")} · ${data.optString("decision")} · " +
            "${data.optLong("ms")} ms"
    "BackupCreated" ->
        "${data.optString("path")} · ${data.optInt("sizeKb")} KB"
    "BackupRestored" -> data.optString("path")
    "RestoreFailed" -> data.optString("error")
    "ChatRequest" ->
        "${data.optString("model")} · ${data.optLong("ms")} ms"
    else -> ""
}

private fun fmtTime(ts: Long): String {
    if (ts <= 0) return ""
    return try {
        SimpleDateFormat("HH:mm", Locale("id")).format(Date(ts))
    } catch (_: Exception) {
        ""
    }
}

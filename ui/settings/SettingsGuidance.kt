package id.sukiapps.hermes.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import id.sukiapps.hermes.ui.theme.LocalSettingsShape
import id.sukiapps.hermes.ui.theme.LocalSettingPalette
import kotlinx.coroutines.launch

// ===== KOMP-8: StepGuidancePanel =====

data class GuidanceStep(
    val id: String,
    val order: Int,
    val title: String,
    val body: String,
    val command: String? = null,
    val link: String? = null,
    val linkPackage: String? = null, // mis. "com.termux" untuk intent langsung
    val autoCheck: (() -> Boolean)? = null,
)

@Composable
fun StepGuidancePanel(
    featureId: String,
    steps: List<GuidanceStep>,
    onStepDone: ((String) -> Unit)? = null,
    snackbarHostState: SnackbarHostState? = null,
    title: String = "Cara setup",
    initialDone: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
) {
    val palette = LocalSettingPalette.current
    val shape = LocalSettingsShape.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // Progress tersimpan per fitur (in-memory + callback; persistensi via initialDone)
    var doneSteps by remember(featureId, initialDone) { mutableStateOf(initialDone) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape.card)
            .background(palette.surfaceSunken)
            .padding(16.dp),
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
        )
        Spacer(Modifier.height(8.dp))

        if (doneSteps.isNotEmpty()) {
            LinearProgressIndicator(
                progress = { doneSteps.size.toFloat() / steps.size.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().clip(CircleShape),
                color = palette.accent,
                trackColor = palette.dividerSoft,
            )
            Spacer(Modifier.height(8.dp))
        }

        steps.sortedBy { it.order }.forEach { step ->
            val done = step.id in doneSteps ||
                (step.autoCheck?.invoke() == true)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // Lingkaran nomor 28dp
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (done) palette.statusReady
                            else palette.accent.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(Icons.Filled.Check, contentDescription = null,
                            tint = palette.textOnAccent, modifier = Modifier.size(16.dp))
                    } else {
                        Text(
                            "${step.order}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accent,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = step.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.textPrimary,
                    )
                    Text(
                        text = step.body,
                        fontSize = 13.sp,
                        color = palette.textSecondary,
                    )
                    // Blok perintah + tombol Salin
                    if (step.command != null) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape.field)
                                .background(palette.surface)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = step.command,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = palette.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(step.command))
                                    snackbarHostState?.let { hs ->
                                        scope.launch { hs.showSnackbar("Tersalin") }
                                    }
                                },
                                modifier = Modifier.semantics {
                                    contentDescription = "Salin perintah"
                                },
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null,
                                    tint = palette.accent, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    // Link / buka aplikasi
                    if (step.link != null) {
                        TextButton(
                            onClick = {
                                try {
                                    if (step.linkPackage != null) {
                                        val pm = context.packageManager
                                        val intent = pm.getLaunchIntentForPackage(step.linkPackage)
                                        if (intent != null) {
                                            context.startActivity(intent)
                                        } else {
                                            throw Exception("not installed")
                                        }
                                    } else {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(step.link))
                                        )
                                    }
                                } catch (e: Exception) {
                                    snackbarHostState?.let { hs ->
                                        scope.launch {
                                            hs.showSnackbar("Aplikasi tidak terpasang")
                                        }
                                    }
                                }
                            },
                        ) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = null,
                                tint = palette.accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (step.linkPackage != null) "Buka aplikasi"
                                else "Buka tautan",
                                fontSize = 13.sp, color = palette.accent,
                            )
                        }
                    }
                    if (!done) {
                        TextButton(
                            onClick = {
                                doneSteps = doneSteps + step.id
                                onStepDone?.invoke(step.id)
                            },
                        ) {
                            Text("Tandai selesai", fontSize = 13.sp, color = palette.accent)
                        }
                    }
                }
            }
        }
    }
}

// ===== KOMP-9: SetupChecklist =====

data class ChecklistItem(
    val featureId: String,
    val title: String,
    val status: FeatureStatus,
    val order: Int,
    val isAhead: Boolean = false, // "Ahead of step 3" — dikerjakan di luar urutan
)

@Composable
fun SetupChecklist(
    items: List<ChecklistItem>,
    requiredTotal: Int, // mis. 4 wajib
    onItemClick: (String) -> Unit,
    onRetest: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSettingPalette.current

    val requiredDone = items.filter { it.order <= requiredTotal }
        .count { it.status is FeatureStatus.Ready }
    val progress = if (requiredTotal > 0) requiredDone.toFloat() / requiredTotal else 0f

    Column(modifier.fillMaxWidth()) {
        Text(
            text = "$requiredDone dari $requiredTotal wajib siap",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().clip(CircleShape),
            color = palette.accent,
            trackColor = palette.dividerSoft,
        )
        Spacer(Modifier.height(12.dp))

        items.sortedBy { it.order }.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(item.featureId) }
                    .padding(vertical = 8.dp)
                    .semantics {
                        contentDescription = "${item.title}, ${item.status.label()}"
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${item.order}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textTertiary,
                    modifier = Modifier.width(28.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 14.sp,
                        color = palette.textPrimary,
                    )
                    if (item.isAhead) {
                        Text(
                            text = "Dikerjakan di luar urutan",
                            fontSize = 12.sp,
                            color = palette.textTertiary,
                        )
                    }
                }
                FeatureStatusChip(status = item.status)
                if (item.status is FeatureStatus.Ready) {
                    TextButton(onClick = { onRetest(item.featureId) }) {
                        Text("Uji ulang", fontSize = 12.sp, color = palette.accent)
                    }
                }
            }
        }
    }
}

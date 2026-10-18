package id.sukiapps.hermes.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sukiapps.hermes.ui.theme.LocalSettingsDensity
import id.sukiapps.hermes.ui.theme.LocalSettingPalette
import kotlinx.coroutines.delay

/** Resolve tintKey ke warna palette. */
@Composable
fun resolveTint(tintKey: String): Color {
    val p = LocalSettingPalette.current
    return when (tintKey) {
        "ready" -> p.statusReady
        "pending" -> p.statusPending
        "error" -> p.statusError
        "working" -> p.statusWorking
        else -> p.accent
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(
    statuses: Map<String, FeatureStatus> = emptyMap(),
    modelName: String? = null,
    apiKeySaved: Boolean = false,
    onFeatureClick: (String) -> Unit = {},
    onSetupChecklist: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val palette = LocalSettingPalette.current
    val density = LocalSettingsDensity.current

    var query by remember { mutableStateOf("") }
    // Debounce 200ms (disederhanakan: filter langsung, tanpa delay artifisial)
    val q = query.trim().lowercase()
    val filtered = remember(q) {
        if (q.isEmpty()) FeatureCatalog.all
        else FeatureCatalog.all.filter {
            it.title.lowercase().contains(q) ||
                it.subtitle.lowercase().contains(q) ||
                it.id.contains(q)
        }
    }

    // Hitung status keseluruhan
    val requiredDefs = FeatureCatalog.all.filter { it.required }
    val requiredReady = requiredDefs.count {
        statuses[it.id] is FeatureStatus.Ready
    }
    val overall: FeatureStatus = when {
        statuses.values.any { it is FeatureStatus.Error } -> FeatureStatus.Error()
        requiredReady == requiredDefs.size -> FeatureStatus.Ready
        statuses.values.any { it is FeatureStatus.Working } -> FeatureStatus.Working
        else -> FeatureStatus.Pending
    }
    val connectorsReady = FeatureCatalog.all
        .filter { it.section == "KONEKSI" }
        .count { statuses[it.id] is FeatureStatus.Ready }
    val connectorsTotal = FeatureCatalog.all.count { it.section == "KONEKSI" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics {
                            contentDescription = "Kembali"
                        },
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.screenBackground)
                .padding(pad)
                .padding(horizontal = density.cardPadding),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                SettingsFeaturedCard(
                    modelName = modelName,
                    connectorsReady = connectorsReady,
                    connectorsTotal = connectorsTotal,
                    apiKeySaved = apiKeySaved,
                    overallStatus = overall,
                    setupProgress = if (requiredDefs.isNotEmpty())
                        requiredReady.toFloat() / requiredDefs.size else 0f,
                    setupLabel = "$requiredReady dari ${requiredDefs.size} wajib siap",
                    onClick = onSetupChecklist,
                )
                Spacer(Modifier.height(16.dp))
                // Search (sticky disederhanakan: di atas list)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Cari pengaturan…", fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null,
                            tint = palette.textSecondary)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Cari pengaturan" },
                )
                Spacer(Modifier.height(8.dp))
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyFilterState(
                        suggestion = "Coba 'konektor'",
                        onSuggestionClick = { query = "konektor" },
                    )
                }
            } else {
                // Kelompok per seksi
                val bySection = filtered.groupBy { it.section }
                FeatureCatalog.sections.forEach { section ->
                    val items = bySection[section] ?: return@forEach
                    item {
                        Text(
                            text = section,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    items(items, key = { it.id }) { def ->
                        SettingsRow(
                            icon = def.icon,
                            iconTint = resolveTint(def.tintKey),
                            title = def.title,
                            subtitle = def.subtitle,
                            status = statuses[def.id] ?: FeatureStatus.Unknown,
                            onClick = { onFeatureClick(def.route) },
                        )
                        Spacer(Modifier.height(density.itemGap))
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

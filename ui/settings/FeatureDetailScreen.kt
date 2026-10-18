package id.sukiapps.hermes.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sukiapps.hermes.ui.theme.LocalSettingsDensity
import id.sukiapps.hermes.ui.theme.LocalSettingPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Layar detail generik untuk 15 fitur: hero strip + kontrol + panduan.
 * Konten spesifik per fitur dipasang bertahap (Fase F fokus: struktur + panduan).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureDetailScreen(
    def: FeatureDef,
    status: FeatureStatus = FeatureStatus.Unknown,
    snackbarHostState: SnackbarHostState? = null,
    guidanceStore: GuidanceProgressStore? = null,
    onBack: () -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    val palette = LocalSettingPalette.current
    val density = LocalSettingsDensity.current
    val hs = snackbarHostState ?: remember { SnackbarHostState() }
    val guideScope = rememberCoroutineScope()

    // Progres "Cara setup" — dimuat dari store bila tersedia (Fase G)
    var guideDone by remember(def.id) { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(def.id, guidanceStore) {
        guideDone = withContext(Dispatchers.IO) {
            guidanceStore?.load(def.id)
        } ?: emptySet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(def.title, fontWeight = FontWeight.SemiBold) },
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
        snackbarHost = { SnackbarHost(hs) },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.screenBackground)
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = density.cardPadding),
        ) {
            Spacer(Modifier.height(8.dp))
            // Hero strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        palette.surface,
                        androidx.compose.foundation.shape.RoundedCornerShape(
                            density.cardRadius
                        ),
                    )
                    .padding(density.cardPadding),
            ) {
                Column {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(density.listIconSize)
                                .background(
                                    resolveTint(def.tintKey).copy(alpha = 0.12f),
                                    androidx.compose.foundation.shape.CircleShape,
                                ),
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) {
                            Icon(
                                def.icon, contentDescription = null,
                                tint = resolveTint(def.tintKey),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.padding(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                def.title,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary,
                            )
                            Text(
                                def.subtitle,
                                fontSize = 13.sp,
                                color = palette.textSecondary,
                            )
                        }
                        FeatureStatusChip(status = status)
                    }
                }
            }
            Spacer(Modifier.height(density.sectionGap))

            // Konten spesifik fitur
            content()

            // Panduan cara setup (bila ada) — progres tersimpan via guidanceStore
            if (def.guidance.isNotEmpty()) {
                Spacer(Modifier.height(density.sectionGap))
                StepGuidancePanel(
                    featureId = def.id,
                    steps = def.guidance,
                    initialDone = guideDone ?: emptySet(),
                    onStepDone = { stepId ->
                        val cur = (guideDone ?: emptySet()) + stepId
                        guideDone = cur
                        guideScope.launch(Dispatchers.IO) {
                            guidanceStore?.save(def.id, cur)
                        }
                    },
                    snackbarHostState = hs,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

package id.sukiapps.hermes.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import id.sukiapps.hermes.data.SettingsRepository
import id.sukiapps.hermes.net.ActivityLogger
import id.sukiapps.hermes.net.ConnectorTester
import id.sukiapps.hermes.ui.settings.GuidanceProgressStore
import id.sukiapps.hermes.ui.settings.SettingsWiring

/** Buat SettingsWiring sekali per komposisi graph. */
@Composable
fun rememberSettingsWiring(
    repo: SettingsRepository,
    tester: ConnectorTester,
    logger: ActivityLogger,
): SettingsWiring {
    val scope = rememberCoroutineScope()
    return remember(repo, tester, logger) {
        SettingsWiring(repo, tester, logger, scope)
    }
}

/**
 * Graph settings dengan wiring Fase G:
 * - status Hub live dari SettingsRepository,
 * - "Uji ulang" memanggil ConnectorTester,
 * - progres "Cara setup" tersimpan bila guidanceStore diberikan.
 *
 * Daftarkan SATU kali di NavHost utama (ganti pemakaian settingsGraph biasa).
 */
@Composable
fun settingsGraphWired(
    navController: NavHostController,
    wiring: SettingsWiring,
    guidanceStore: GuidanceProgressStore? = null,
) {
    val statuses by wiring.statuses.collectAsState()
    val modelName by wiring.modelName.collectAsState()
    val apiKeySaved by wiring.apiKeySaved.collectAsState()
    settingsGraph(
        navController = navController,
        statuses = statuses,
        modelName = modelName,
        apiKeySaved = apiKeySaved,
        onRetest = { wiring.retest(it) },
        guidanceStore = guidanceStore,
    )
}

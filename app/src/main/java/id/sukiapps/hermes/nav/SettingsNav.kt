package id.sukiapps.hermes.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import id.sukiapps.hermes.ui.settings.FeatureCatalog
import id.sukiapps.hermes.ui.settings.FeatureDetailScreen
import id.sukiapps.hermes.ui.settings.FeatureStatus
import id.sukiapps.hermes.ui.settings.SettingsHubScreen
import id.sukiapps.hermes.ui.settings.SetupChecklist
import id.sukiapps.hermes.ui.settings.ChecklistItem

/** Konstanta route settings. */
object SettingsRoutes {
    const val HUB = "settings/hub"
    const val SETUP = "settings/setup"
    val ALL: List<String> = listOf(HUB, SETUP) +
        FeatureCatalog.all.map { it.route }
}

/**
 * Graph navigasi settings. Daftarkan ke NavHost utama:
 * ```
 * settingsGraph(navController, ...)
 * ```
 */
@Composable
fun settingsGraph(
    navController: NavHostController,
    statuses: Map<String, FeatureStatus> = emptyMap(),
    modelName: String? = null,
    apiKeySaved: Boolean = false,
) {
    NavHost(
        navController = navController,
        startDestination = SettingsRoutes.HUB,
    ) {
        composable(SettingsRoutes.HUB) {
            SettingsHubScreen(
                statuses = statuses,
                modelName = modelName,
                apiKeySaved = apiKeySaved,
                onFeatureClick = { route -> navController.navigate(route) },
                onSetupChecklist = { navController.navigate(SettingsRoutes.SETUP) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(SettingsRoutes.SETUP) {
            val items = FeatureCatalog.setupOrder.map { def ->
                ChecklistItem(
                    featureId = def.id,
                    title = def.title,
                    status = statuses[def.id] ?: FeatureStatus.Unknown,
                    order = def.order,
                )
            }
            SetupChecklist(
                items = items,
                requiredTotal = FeatureCatalog.all.count { it.required },
                onItemClick = { id ->
                    FeatureCatalog.byId(id)?.let {
                        navController.navigate(it.route)
                    }
                },
                onRetest = { /* TODO Fase G: panggil ConnectorTester */ },
            )
        }
        // 15 rute fitur — satu composable generik per fitur
        FeatureCatalog.all.forEach { def ->
            composable(def.route) {
                FeatureDetailScreen(
                    def = def,
                    status = statuses[def.id] ?: FeatureStatus.Unknown,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

/** Helper: buat NavController khusus settings (bila belum ada). */
@Composable
fun rememberSettingsNavController(): NavHostController = rememberNavController()

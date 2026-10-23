package id.sukiapps.hermes

import id.sukiapps.hermes.diagnostics.HealthScreen
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import id.sukiapps.hermes.ui.chatv3.V3PreviewScreen
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import id.sukiapps.hermes.data.audit.PermissionGate
import id.sukiapps.hermes.domain.Prefs
import id.sukiapps.hermes.navigation.Nav
import id.sukiapps.hermes.ui.v12.ActivityScreen
import id.sukiapps.hermes.ui.v12.ApprovalScreen
import id.sukiapps.hermes.ui.v12.BriefScreen
import id.sukiapps.hermes.ui.v12.ToolCatalogScreen
import id.sukiapps.hermes.ui.v12.ReflectionScreen
import id.sukiapps.hermes.ui.v12.V12HubScreen
import id.sukiapps.hermes.ui.v12.WarehouseScreen
import id.sukiapps.hermes.ui.audit.AuditScreen
import id.sukiapps.hermes.ui.audit.PermissionGateDialog
import id.sukiapps.hermes.ui.featreq.FeatureRequestScreen
import id.sukiapps.hermes.ui.chat.HermesChatScreen
import id.sukiapps.hermes.ui.keysetup.KeySetupScreen
import id.sukiapps.hermes.ui.onboarding.OnboardingScreen
import id.sukiapps.hermes.ui.agent.SoulScreen
import id.sukiapps.hermes.ui.agent.AktivitasScreen
import id.sukiapps.hermes.ui.settings.SettingsScreen
import id.sukiapps.hermes.ui.theme.HermesTheme
import androidx.hilt.navigation.compose.hiltViewModel
import id.sukiapps.hermes.ui.chat.ChatViewModel
import id.sukiapps.hermes.ui.chatv3.V3LiveScreen

/**
 * Single Activity — seluruh navigasi via Navigation Compose.
 * Start destination: onboarded? → punya key? → chat.
 * (hasKey hanya baca SharedPreferences — aman di main thread.)
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Gate izin: dialog-nya dirender sekali di setContent (PermissionGateDialog)
    // agar selalu ada selama app hidup — chokepoint UI semua aksi sensitif.
    @Inject
    lateinit var gate: PermissionGate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        val startDestination = when {
            !prefs.getBoolean(Prefs.KEY_ONBOARDED, false) -> Nav.ONBOARDING
            !KeyVault.hasKey(this) -> Nav.KEYSETUP
            else -> Nav.CHAT
        }

        setContent {
            val nav = rememberNavController()
            // v12: deep-link dari notifikasi approval (cold start).
            val pendingRoute = remember { intent.getStringExtra("nav_route") }
            LaunchedEffect(pendingRoute) {
                if (pendingRoute != null) runCatching { nav.navigate(pendingRoute) }
            }
            // Baca ulang tema setiap kali destinasi berubah (mis. kembali
            // dari Settings setelah ganti tema).
            val backStack by nav.currentBackStackEntryAsState()
            var darkTheme by remember(backStack?.destination?.route) {
                mutableStateOf(
                    getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
                        .getString(Prefs.KEY_THEME, "dark") != "light"
                )
            }
            HermesTheme(darkTheme = darkTheme) {
                // Dialog izin global — di atas NavHost agar selalu bisa muncul
                // dari layar mana pun.
                PermissionGateDialog(gate)
                // Gate 1 (Sprint 1): bottom navigation 4 tab standar —
                // Chat, Agent (Hub v12), Tools (Katalog Tools), Settings.
                // Bar hanya tampil di destinasi tab; layar turunan tetap
                // full-screen seperti sebelumnya.
                val currentRoute = backStack?.destination?.route
                val tabRoutes = listOf(
                    Nav.CHAT, Nav.V12_HUB, Nav.TOOLS, Nav.SETTINGS
                )
                Scaffold(
                    bottomBar = {
                        if (currentRoute in tabRoutes) {
                            NavigationBar {
                                val tabs = listOf(
                                    Triple(Nav.CHAT, "Chat", Icons.Filled.Chat),
                                    Triple(Nav.V12_HUB, "Agent", Icons.Filled.SmartToy),
                                    Triple(Nav.TOOLS, "Tools", Icons.Filled.Build),
                                    Triple(Nav.SETTINGS, "Settings", Icons.Filled.Settings)
                                )
                                tabs.forEach { (route, label, icon) ->
                                    NavigationBarItem(
                                        selected = currentRoute == route,
                                        onClick = {
                                            if (currentRoute != route) {
                                                nav.navigate(route) {
                                                    popUpTo(Nav.CHAT) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        },
                                        icon = {
                                            Icon(icon, contentDescription = label)
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                ) { innerPad ->
                NavHost(
                    navController = nav,
                    startDestination = startDestination,
                    modifier = Modifier.padding(innerPad)
                ) {
                    composable(Nav.ONBOARDING) {
                        OnboardingScreen(
                            onDone = {
                                prefs.edit()
                                    .putBoolean(Prefs.KEY_ONBOARDED, true)
                                    .apply()
                                val next =
                                    if (KeyVault.hasKey(this@MainActivity)) Nav.CHAT
                                    else Nav.KEYSETUP
                                nav.navigate(next) {
                                    popUpTo(Nav.ONBOARDING) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(Nav.KEYSETUP) {
                        KeySetupScreen(
                            onDone = {
                                nav.navigate(Nav.CHAT) {
                                    popUpTo(Nav.KEYSETUP) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(Nav.CHAT) {
                        HermesChatScreen(
                            onOpenSettings = { nav.navigate(Nav.SETTINGS) },
                            onOpenAktivitas = { nav.navigate(Nav.AKTIVITAS) },
                            onOpenMintaFitur = { nav.navigate(Nav.MINTA_FITUR) },
                            onOpenAudit = { nav.navigate(Nav.AUDIT) },
                    onOpenV12Hub = { nav.navigate(Nav.V12_HUB) }
                        )
                    }
                    composable(Nav.SETTINGS) {
                        SettingsScreen(
                            onBack = { nav.popBackStack() },
                            onOpenKeySetup = {
                                nav.navigate(Nav.KEYSETUP) {
                                    popUpTo(Nav.CHAT) { inclusive = false }
                                }
                            },
                            onOpenSoul = { nav.navigate(Nav.SOUL) },
                            onOpenAudit = { nav.navigate(Nav.AUDIT) }
                        )
                    }
                    composable(Nav.MINTA_FITUR) {
                        FeatureRequestScreen(onBack = { nav.popBackStack() })
                    }
                    composable(Nav.AUDIT) {
                        AuditScreen(onBack = { nav.popBackStack() })
                    }
                    composable(Nav.SOUL) {
                        SoulScreen(onBack = { nav.popBackStack() })
                    }
                composable(Nav.V12_HUB) {
                    V12HubScreen(
                        onBack = { nav.popBackStack() },
                        onApproval = { nav.navigate(Nav.APPROVAL) },
                        onTools = { nav.navigate(Nav.TOOLS) },
                        onBrief = { nav.navigate(Nav.BRIEF) },
                        onReflection = { nav.navigate(Nav.REFLECTION) },
                        onActivity = { nav.navigate(Nav.ACTIVITY) },
                        onWarehouse = { nav.navigate(Nav.WAREHOUSE) }
                    )
                }
                composable(Nav.APPROVAL) {
                    ApprovalScreen(onBack = { nav.popBackStack() })
                }
                composable(Nav.TOOLS) {
                    ToolCatalogScreen(onBack = { nav.popBackStack() })
                }
                composable(Nav.BRIEF) {
                    BriefScreen(
                        filesDir = this@MainActivity.filesDir,
                        onBack = { nav.popBackStack() }
                    )
                }
                composable(Nav.REFLECTION) {
                    ReflectionScreen(
                        filesDir = this@MainActivity.filesDir,
                        onBack = { nav.popBackStack() }
                    )
                }
                composable(Nav.ACTIVITY) {
                    ActivityScreen(onBack = { nav.popBackStack() })
                }
                composable(Nav.WAREHOUSE) {
                    WarehouseScreen(onBack = { nav.popBackStack() })
                }
                    composable(Nav.AKTIVITAS) {
                        AktivitasScreen(
                            onBack = { nav.popBackStack() },
                            onOpenSoul = { nav.navigate(Nav.SOUL) }
                        )
                    }
        composable("chatv3preview") { V3PreviewScreen(onBack = { nav.popBackStack() }) }
            composable("health") { HealthScreen(onBack = { nav.popBackStack() }) }
    
                }
                }
            }
        }
    }
}

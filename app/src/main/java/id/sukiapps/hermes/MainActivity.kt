package id.sukiapps.hermes

import id.sukiapps.hermes.diagnostics.HealthScreen
import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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
import id.sukiapps.hermes.ui.manus.ManusScreen

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
            // v14.10: minta izin notifikasi sekali saat start (API 33+).
            // Tanpa dialog custom tambahan — PermissionGateDialog yang ada
            // sudah cukup untuk keputusan izin aksi sensitif.
            val notifLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            // v12: deep-link dari notifikasi approval (cold start).
            val pendingRoute = remember { intent.getStringExtra("nav_route") }
            LaunchedEffect(pendingRoute) {
                if (pendingRoute != null) runCatching { nav.navigate(pendingRoute) }
            }
            // Tema REAKTIF: sebelumnya tema hanya dibaca ulang saat
            // backstack berubah, sehingga memilih Sistem/Terang/Gelap di
            // Settings tidak mengubah layar Settings itu sendiri sampai
            // pindah tab/restart. Sekarang nilai pref dipegang State dan
            // diperbarui listener SharedPreferences — perubahan pref
            // langsung me-recompose HermesTheme di tempat, tanpa restart.
            // Arti kode tidak berubah ("system"/"light"/"dark", aksen sama).
            val backStack by nav.currentBackStackEntryAsState()
            val themePrefs =
                remember { getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE) }
            var themeMode by remember {
                mutableStateOf(themePrefs.getString(Prefs.KEY_THEME, "dark") ?: "dark")
            }
            var accentPref by remember {
                mutableStateOf(
                    themePrefs.getString(Prefs.KEY_ACCENT_COLOR, "coral") ?: "coral"
                )
            }
            DisposableEffect(themePrefs) {
                val listener =
                    SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        if (key == Prefs.KEY_THEME) {
                            themeMode =
                                themePrefs.getString(Prefs.KEY_THEME, "dark") ?: "dark"
                        }
                        if (key == Prefs.KEY_ACCENT_COLOR) {
                            accentPref = themePrefs.getString(
                                Prefs.KEY_ACCENT_COLOR, "coral"
                            ) ?: "coral"
                        }
                    }
                themePrefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    themePrefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }
            // v14.18: mode tiga nilai ("system" mengikuti sistem; nilai
            // lama "dark"/"light" tetap berlaku) dan aksen dari pref sama.
            val darkTheme = when (themeMode) {
                "light" -> false
                "system" -> isSystemInDarkTheme()
                else -> true
            }
            HermesTheme(darkTheme = darkTheme, accent = accentPref) {
                // Dialog izin global — di atas NavHost agar selalu bisa muncul
                // dari layar mana pun.
                PermissionGateDialog(gate)
                // v16-polish: bottom navigation 5 item — Aktivitas,
                // Tools, Chat (tengah, aksi utama), Agent (Hub v12),
                // Settings. Agen tetap ada di bar (ikon SmartToy);
                // layar Agen/Manus tetap bisa dibuka dari Chat dan Hub.
                // Bar hanya tampil di destinasi tab; layar turunan tetap
                // full-screen seperti sebelumnya.
                val currentRoute = backStack?.destination?.route
                val tabRoutes = listOf(
                    Nav.CHAT, Nav.V12_HUB, Nav.TOOLS, Nav.SETTINGS,
                    Nav.AKTIVITAS
                )
                Scaffold(
                    bottomBar = {
                        if (currentRoute in tabRoutes) {
                            // Navigasi antar-tab: SAMA seperti sebelumnya
                            // (popUpTo CHAT + saveState + launchSingleTop +
                            // restoreState) — hanya tampilannya yang baru.
                            val navigateToTab: (String) -> Unit = { route ->
                                if (currentRoute != route) {
                                    nav.navigate(route) {
                                        popUpTo(Nav.CHAT) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                tonalElevation = 3.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = 12.dp,
                                            vertical = 10.dp
                                        ),
                                    horizontalArrangement =
                                        Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BottomBarTab(
                                        label = "Aktivitas",
                                        icon = Icons.Filled.History,
                                        selected =
                                            currentRoute == Nav.AKTIVITAS,
                                        onClick = {
                                            navigateToTab(Nav.AKTIVITAS)
                                        }
                                    )
                                    BottomBarTab(
                                        label = "Tools",
                                        icon = Icons.Filled.Build,
                                        selected = currentRoute == Nav.TOOLS,
                                        onClick = { navigateToTab(Nav.TOOLS) }
                                    )
                                    BottomBarChatButton(
                                        onClick = { navigateToTab(Nav.CHAT) }
                                    )
                                    BottomBarTab(
                                        label = "Agent",
                                        icon = Icons.Filled.SmartToy,
                                        selected =
                                            currentRoute == Nav.V12_HUB,
                                        onClick = { navigateToTab(Nav.V12_HUB) }
                                    )
                                    BottomBarTab(
                                        label = "Settings",
                                        icon = Icons.Filled.Settings,
                                        selected =
                                            currentRoute == Nav.SETTINGS,
                                        onClick = {
                                            navigateToTab(Nav.SETTINGS)
                                        }
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
                    onOpenV12Hub = { nav.navigate(Nav.V12_HUB) },
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
                        onWarehouse = { nav.navigate(Nav.WAREHOUSE) },
                        onManus = { nav.navigate(Nav.MANUS) }
                    )
                }
                composable(Nav.MANUS) {
                    ManusScreen(onBack = { nav.popBackStack() })
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
// v14.10: penutup class MainActivity — perbaikan Missing '}' (CI #110, kspReleaseKotlin).
}


/**
 * Bottom bar 5 item (polish v16) — komponen bar bawah.
 * Chat di tengah sebagai aksi utama (tombol bundar warna primer).
 * Navigasi tidak berubah: tiap item memanggil nav.navigate yang sama
 * seperti NavigationBarItem sebelumnya.
 */
@Composable
private fun BottomBarTab(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = if (selected) scheme.primary else scheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant
        )
    }
}

/** Tombol Chat di tengah bottom bar — aksi utama, selalu menonjol. */
@Composable
private fun BottomBarChatButton(
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = scheme.primary,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.size(56.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Chat,
                contentDescription = "Chat",
                modifier = Modifier.size(28.dp),
                tint = scheme.onPrimary
            )
        }
    }
}

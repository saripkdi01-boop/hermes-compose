package id.sukiapps.hermes

import id.sukiapps.hermes.diagnostics.HealthScreen
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import id.sukiapps.hermes.domain.ShareInbox
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
import id.sukiapps.hermes.ui.theme.HermesMotion
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
    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // v19: tangkap teks dari share sheet aplikasi lain (ACTION_SEND)
        // sebelum komposisi dibuat — HermesChatScreen mengonsumsinya.
        handleShareIntent(intent)
        // v19.2: edge-to-edge resmi (status & navigation bar transparan,
        // ikon bar mengikuti tema app — lihat DisposableEffect(darkTheme)).
        enableEdgeToEdge()
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        val startDestination = when {
            !prefs.getBoolean(Prefs.KEY_ONBOARDED, false) -> Nav.ONBOARDING
            !KeyVault.hasKey(this) -> Nav.KEYSETUP
            else -> Nav.CHAT
        }
        setContent {
            val nav = rememberNavController()
            // v19: ada teks dari share sheet → pastikan user berada di
            // tab Chat; pengisian inputnya dilakukan HermesChatScreen.
            val sharedText by ShareInbox.text.collectAsState()
            LaunchedEffect(sharedText) {
                if (sharedText != null) {
                    nav.navigate(Nav.CHAT) { launchSingleTop = true }
                }
            }
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
            // v19.2: warna ikon status/navigation bar mengikuti tema APP
            // (bukan tema sistem) — tema app bisa gelap di HP bertema terang.
            DisposableEffect(darkTheme) {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    )
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
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
                                        // v19.2 (audit P1-8): bar kustom
                                        // tidak boleh duduk di bawah tombol/
                                        // gesture navigation sistem.
                                        .navigationBarsPadding()
                                        .padding(
                                            horizontal = 8.dp,
                                            vertical = 8.dp
                                        ),
                                    horizontalArrangement =
                                        Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BottomBarTab(
                                        modifier = Modifier.weight(1f),
                                        label = "Aktivitas",
                                        icon = Icons.Filled.History,
                                        selected =
                                            currentRoute == Nav.AKTIVITAS,
                                        onClick = {
                                            navigateToTab(Nav.AKTIVITAS)
                                        }
                                    )
                                    BottomBarTab(
                                        modifier = Modifier.weight(1f),
                                        label = "Alat",
                                        icon = Icons.Filled.Build,
                                        selected = currentRoute == Nav.TOOLS,
                                        onClick = { navigateToTab(Nav.TOOLS) }
                                    )
                                    BottomBarChatButton(
                                        onClick = { navigateToTab(Nav.CHAT) }
                                    )
                                    BottomBarTab(
                                        modifier = Modifier.weight(1f),
                                        label = "Agen",
                                        icon = Icons.Filled.SmartToy,
                                        selected =
                                            currentRoute == Nav.V12_HUB,
                                        onClick = { navigateToTab(Nav.V12_HUB) }
                                    )
                                    BottomBarTab(
                                        modifier = Modifier.weight(1f),
                                        label = "Pengaturan",
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
                    // v19.2 (audit P1-8): padding Scaffold luar DIKONSUMSI
                    // supaya TopAppBar layar dalam tidak menambah padding
                    // status bar lagi dan imePadding() composer tidak
                    // menumpuk dengan tinggi bottom bar (celah di atas
                    // keyboard).
                    modifier = Modifier
                        .padding(innerPad)
                        .consumeWindowInsets(innerPad),
                    // v18: transisi antar-layar — fade + geser halus memakai
                    // token gerak Hermes (180ms fade, 220ms geser, ease-out,
                    // tanpa pantulan) menggantikan potongan layar bawaan.
                    enterTransition = {
                        fadeIn(HermesMotion.QuickOut) +
                            slideInHorizontally(HermesMotion.TabIndicatorOffset) { it / 6 }
                    },
                    exitTransition = { fadeOut(HermesMotion.QuickOut) },
                    popEnterTransition = { fadeIn(HermesMotion.QuickOut) },
                    popExitTransition = {
                        fadeOut(HermesMotion.QuickOut) +
                            slideOutHorizontally(HermesMotion.TabIndicatorOffset) { it / 6 }
                    }
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
    // v19: share sheet saat aplikasi sedang berjalan — launchMode
    // singleTop mengarahkan intent baru ke sini, bukan Activity baru.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    /**
     * v19: intent ACTION_SEND text/plain dari aplikasi lain → taruh
     * teksnya di ShareInbox. Action dikosongkan setelah diproses agar
     * intent yang sama tidak diproses ulang saat Activity dibuat ulang.
     */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (text.isNotBlank()) ShareInbox.post(text)
        intent.action = null
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    // v18: pil indikator tab aktif gaya Material 3 — latar
    // primaryContainer di belakang ikon yang beranimasi warna memakai
    // token gerak Hermes; warna ikon + label ikut beranimasi supaya
    // perpindahan tab terasa halus, bukan berganti seketika.
    // Catatan tipe: animateColorAsState butuh AnimationSpec<Color> —
    // HermesMotion.QuickOut adalah spesifikasi Float, jadi untuk warna
    // dipakai tween 180ms dengan easing yang sama persis (0,0,0.2,1).
    val pillColor by animateColorAsState(
        targetValue = if (selected) scheme.primaryContainer else Color.Transparent,
        animationSpec = tween(180, easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)),
        label = "tabPill"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.onSurfaceVariant,
        animationSpec = tween(180, easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)),
        label = "tabContent"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // v19.2 (a11y): selectable + Role.Tab → pembaca layar mengumumkan
        // "tab, terpilih"; area sentuh penuh lebar kolom (≥ 48 dp tinggi).
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .heightIn(min = 56.dp)
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(pillColor)
                .padding(horizontal = 14.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                // Label teks di bawah sudah dibacakan — hindari ganda.
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = contentColor
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = "Chat",
                modifier = Modifier.size(28.dp),
                tint = scheme.onPrimary
            )
        }
    }
}

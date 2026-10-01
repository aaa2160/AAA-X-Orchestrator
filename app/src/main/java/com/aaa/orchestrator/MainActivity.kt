package com.aaa.orchestrator

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.aaa.orchestrator.data.repository.AccountRepository
import com.aaa.orchestrator.engine.OrchestratorEngine
import kotlinx.coroutines.launch
import com.aaa.orchestrator.service.OrchestratorAccessibilityService
import com.aaa.orchestrator.service.OrchestratorForegroundService
import com.aaa.orchestrator.ui.screens.BrowserScreen
import com.aaa.orchestrator.ui.screens.DashboardScreen
import com.aaa.orchestrator.ui.screens.SettingsScreen
import com.aaa.orchestrator.ui.screens.VaultScreen
import com.aaa.orchestrator.ui.theme.AAAXTheme
import com.aaa.orchestrator.ui.theme.PrimaryBlue
import com.aaa.orchestrator.ui.theme.TextMuted

sealed class NavTab(val title: String, val icon: ImageVector) {
    object Dashboard : NavTab("Dashboard", Icons.Default.Dashboard)
    object Browser : NavTab("Browser", Icons.Default.Language)
    object Automation : NavTab("Auto Bot", Icons.Default.SmartToy)
    object Vault : NavTab("Vault", Icons.Default.Lock)
    object Settings : NavTab("Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private lateinit var engine: OrchestratorEngine
    private lateinit var accountRepo: AccountRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Runtime Permissions (Camera, Audio/Mic for KYC, Storage for Exports, Notifications)
        val permsToRequest = mutableListOf<String>()
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permsToRequest.add(android.Manifest.permission.CAMERA)
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permsToRequest.add(android.Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permsToRequest.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_VIDEO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permsToRequest.add(android.Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permsToRequest.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permsToRequest.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (permsToRequest.isNotEmpty()) {
            requestPermissions(permsToRequest.toTypedArray(), 101)
        }

        val app = application as OrchestratorApp
        accountRepo = try {
            AccountRepository(app.database.accountDao())
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Database initialization fallback to in-memory store")
            AccountRepository(com.aaa.orchestrator.data.local.InMemoryAccountDao())
        }
        engine = OrchestratorEngine(this, accountRepo)

        // Bind hardware kill switch trigger callback
        OrchestratorAccessibilityService.onKillSwitchTriggered = {
            runOnUiThread {
                engine.triggerEmergencyKillSwitch()
                OrchestratorForegroundService.stop(this)
                Toast.makeText(this, "HARDWARE KILL SWITCH TRIGGERED: Aborted in <1ms", Toast.LENGTH_LONG).show()
            }
        }

        // Bind automatic phone number detection from active screen
        OrchestratorAccessibilityService.onPhoneDetected = { phone ->
            runOnUiThread {
                engine.updateActivePhoneNumber(phone)
                Toast.makeText(this, "Auto-detected Phone: $phone", Toast.LENGTH_SHORT).show()
            }
        }

        setContent {
            AAAXTheme {
                val scope = rememberCoroutineScope()
                val state by engine.state.collectAsState()
                val metrics by engine.metrics.collectAsState()
                val accounts by accountRepo.allAccounts.collectAsState(initial = emptyList())
                var selectedTab by remember { mutableStateOf<NavTab>(NavTab.Dashboard) }

                // Graceful Back Navigation to Dashboard before exit
                BackHandler(enabled = selectedTab != NavTab.Dashboard) {
                    selectedTab = NavTab.Dashboard
                }

                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = NavigationBarDefaults.Elevation
                        ) {
                            val items = listOf(
                                NavTab.Dashboard,
                                NavTab.Browser,
                                NavTab.Automation,
                                NavTab.Vault,
                                NavTab.Settings
                            )
                            items.forEach { tab ->
                                NavigationBarItem(
                                    selected = selectedTab == tab,
                                    onClick = { selectedTab = tab },
                                    icon = { Icon(tab.icon, contentDescription = tab.title, modifier = Modifier.size(22.dp)) },
                                    label = { Text(tab.title, maxLines = 1, fontSize = 11.sp, fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal) },
                                    alwaysShowLabel = true,
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = PrimaryBlue,
                                        selectedTextColor = PrimaryBlue,
                                        indicatorColor = PrimaryBlue.copy(alpha = 0.12f),
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        when (selectedTab) {
                            NavTab.Dashboard -> DashboardScreen(
                                state = state,
                                metrics = metrics,
                                onStartClick = {
                                    OrchestratorAccessibilityService.isAutomationRunning.set(true)
                                    OrchestratorForegroundService.start(this@MainActivity, "Autonomous Workflow Running")
                                    engine.startAutomation()
                                    selectedTab = NavTab.Automation
                                },
                                onStopClick = {
                                    OrchestratorAccessibilityService.isAutomationRunning.set(false)
                                    engine.stopAutomation()
                                    OrchestratorForegroundService.stop(this@MainActivity)
                                },
                                onOpenBrowser = { selectedTab = NavTab.Browser },
                                onOpenAutomation = { selectedTab = NavTab.Automation },
                                onNavigateToVault = { selectedTab = NavTab.Vault },
                                modifier = Modifier.fillMaxSize()
                            )
                            NavTab.Browser -> BrowserScreen(
                                isAutomationMode = false,
                                modifier = Modifier.fillMaxSize()
                            )
                            NavTab.Automation -> BrowserScreen(
                                engine = engine,
                                isAutomationMode = true,
                                modifier = Modifier.fillMaxSize()
                            )
                            NavTab.Vault -> VaultScreen(
                                accounts = accounts,
                                onDeleteAccount = { id ->
                                    scope.launch { accountRepo.deleteAccountById(id) }
                                },
                                onClearAllAccounts = {
                                    scope.launch { accountRepo.deleteAllAccounts() }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            NavTab.Settings -> SettingsScreen(
                                onTriggerKillSwitchTest = {
                                    engine.triggerEmergencyKillSwitch()
                                    Toast.makeText(this@MainActivity, "Emergency Kill Switch Activated (<1ms)", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        OrchestratorAccessibilityService.onKillSwitchTriggered = null
        OrchestratorAccessibilityService.onPhoneDetected = null
    }
}

package com.aaa.orchestrator

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.aaa.orchestrator.data.repository.AccountRepository
import com.aaa.orchestrator.engine.OrchestratorEngine
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
    object Vault : NavTab("Vault", Icons.Default.Lock)
    object Browser : NavTab("Browser", Icons.Default.Language)
    object Settings : NavTab("Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private lateinit var engine: OrchestratorEngine
    private lateinit var accountRepo: AccountRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as OrchestratorApp
        accountRepo = AccountRepository(app.database.accountDao())
        engine = OrchestratorEngine(this, accountRepo)

        // Bind hardware kill switch trigger callback
        OrchestratorAccessibilityService.onKillSwitchTriggered = {
            runOnUiThread {
                engine.triggerEmergencyKillSwitch()
                OrchestratorForegroundService.stop(this)
                Toast.makeText(this, "HARDWARE KILL SWITCH TRIGGERED: Aborted in <1ms", Toast.LENGTH_LONG).show()
            }
        }

        setContent {
            AAAXTheme {
                val state by engine.state.collectAsState()
                val metrics by engine.metrics.collectAsState()
                val accounts by accountRepo.allAccounts.collectAsState(initial = emptyList())
                var selectedTab by remember { mutableStateOf<NavTab>(NavTab.Dashboard) }

                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = NavigationBarDefaults.Elevation
                        ) {
                            val items = listOf(
                                NavTab.Dashboard,
                                NavTab.Vault,
                                NavTab.Browser,
                                NavTab.Settings
                            )
                            items.forEach { tab ->
                                NavigationBarItem(
                                    selected = selectedTab == tab,
                                    onClick = { selectedTab = tab },
                                    icon = { Icon(tab.icon, contentDescription = tab.title) },
                                    label = { Text(tab.title) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = PrimaryBlue,
                                        selectedTextColor = PrimaryBlue,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    when (selectedTab) {
                        NavTab.Dashboard -> DashboardScreen(
                            state = state,
                            metrics = metrics,
                            onStartClick = {
                                OrchestratorAccessibilityService.isAutomationRunning.set(true)
                                OrchestratorForegroundService.start(this, "Autonomous Workflow Running")
                                engine.startAutomation()
                            },
                            onStopClick = {
                                OrchestratorAccessibilityService.isAutomationRunning.set(false)
                                engine.triggerEmergencyKillSwitch()
                                OrchestratorForegroundService.stop(this)
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                        NavTab.Vault -> VaultScreen(
                            accounts = accounts,
                            modifier = Modifier.padding(innerPadding)
                        )
                        NavTab.Browser -> BrowserScreen(
                            modifier = Modifier.padding(innerPadding)
                        )
                        NavTab.Settings -> SettingsScreen(
                            onTriggerKillSwitchTest = {
                                engine.triggerEmergencyKillSwitch()
                                Toast.makeText(this, "Simulated Kill Switch Abort (<1ms)", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

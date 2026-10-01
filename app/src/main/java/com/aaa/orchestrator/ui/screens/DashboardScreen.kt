package com.aaa.orchestrator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaa.orchestrator.data.model.DashboardMetrics
import com.aaa.orchestrator.data.model.OrchestratorState
import com.aaa.orchestrator.engine.AppLauncher
import com.aaa.orchestrator.ui.components.KillSwitchHud
import com.aaa.orchestrator.ui.components.MetricCard
import com.aaa.orchestrator.ui.theme.*

@Composable
fun DashboardScreen(
    state: OrchestratorState,
    metrics: DashboardMetrics,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp)
    ) {
        // App Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AAA X-Orchestrator",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextSlateDark
                )
                Text(
                    text = "Galaxy A30 Autonomous Workflow Engine",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (metrics.isRunning) SoftGreenTile else SurfaceVariantLight
            ) {
                Text(
                    text = if (metrics.isRunning) "RUNNING" else "STANDBY",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (metrics.isRunning) SuccessGreen else TextMuted
                )
            }
        }

        // Hardware Kill Switch Banner
        KillSwitchHud(
            isRunning = metrics.isRunning,
            onEmergencyStop = onStopClick
        )

        // Active State Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "CURRENT PHASE",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = PrimaryBlue
                )
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = state.progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = PrimaryBlue,
                    trackColor = BorderSubtle
                )
            }
        }

        // 2nr Telephony Pool Slot Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SoftBlueTile)
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = "Telephony",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "2nr Telephony Buffer (Poland +48)",
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryBlue,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = metrics.currentSlotInfo,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                            color = TextSlateDark
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(
                        onClick = { AppLauncher.open2nrApp(context, launchOverlay = true) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = SurfaceWhite),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Open 2nr", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }

                    FilledTonalButton(
                        onClick = { AppLauncher.open2nrInSplitScreen(context) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = SoftBlueTile),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Split", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }
                }
            }
        }

        // Metrics Section
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            MetricCard(
                title = "Total Accounts Created",
                value = "${metrics.totalCreated}",
                subtitle = "Committed to local encrypted Room DB",
                icon = Icons.Default.CheckCircle,
                tileColor = SoftGreenTile,
                iconTint = SuccessGreen
            )

            MetricCard(
                title = "Pending Cloud Sync",
                value = "${metrics.pendingSyncCount}",
                subtitle = "Batching to Telegram (-1003932377927) & Sheets",
                icon = Icons.Default.CloudUpload,
                tileColor = SoftPurpleTile,
                iconTint = PrimaryBlue
            )

            MetricCard(
                title = "Battery & Thermal Monitor",
                value = "${metrics.batteryPercent}% (${metrics.batteryTempCelsius}°C)",
                subtitle = "Samsung Galaxy A30 safety guard active",
                icon = Icons.Default.BatteryChargingFull,
                tileColor = SoftAmberTile,
                iconTint = WarningAmber
            )

            MetricCard(
                title = "Stealth Proxy Egress",
                value = metrics.activeProxyIp,
                subtitle = "Strict Warsaw/Poland timezone match",
                icon = Icons.Default.Security,
                tileColor = SoftBlueTile,
                iconTint = PrimaryBlue
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main Action Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Button(
                onClick = {
                    if (metrics.isRunning) onStopClick() else onStartClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (metrics.isRunning) ErrorRed else PrimaryBlue
                )
            ) {
                Icon(
                    imageVector = if (metrics.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (metrics.isRunning) "STOP AUTOMATION" else "START AUTONOMOUS WORKFLOW",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

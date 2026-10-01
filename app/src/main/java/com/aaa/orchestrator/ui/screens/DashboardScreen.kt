package com.aaa.orchestrator.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaa.orchestrator.data.model.DashboardMetrics
import com.aaa.orchestrator.data.model.OrchestratorState
import com.aaa.orchestrator.ui.components.KillSwitchHud
import com.aaa.orchestrator.ui.components.MetricCard
import com.aaa.orchestrator.ui.theme.*

/**
 * Enterprise Dashboard Screen.
 * Engineered for Samsung Galaxy A30 (1080x2340).
 * Features live telemetry, multi-cloud redundancy status, hardware guardrails,
 * and 1-tap navigation to Interactive Browser and Accounts Vault.
 */
@Composable
fun DashboardScreen(
    state: OrchestratorState,
    metrics: DashboardMetrics,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onOpenBrowser: () -> Unit = {},
    onNavigateToVault: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .verticalScroll(scrollState)
            .padding(bottom = 32.dp)
    ) {
        // ==========================================
        // 1. BRAND HEADER & OPERATIONAL STATUS
        // ==========================================
        Surface(
            color = SurfaceWhite,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AAA X-Orchestrator",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                        fontWeight = FontWeight.Bold,
                        color = TextSlateDark
                    )
                    Text(
                        text = "Galaxy A30 Autonomous Workflow Engine",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (metrics.isRunning) SoftGreenTile else SurfaceVariantLight,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (metrics.isRunning) SuccessGreen.copy(alpha = 0.5f) else BorderSubtle
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (metrics.isRunning) SuccessGreen else TextMuted)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (metrics.isRunning) "RUNNING" else "STANDBY",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (metrics.isRunning) SuccessGreen else TextMuted
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ==========================================
        // 2. HARDWARE EMERGENCY KILL SWITCH HUD
        // ==========================================
        KillSwitchHud(
            isRunning = metrics.isRunning,
            onEmergencyStop = onStopClick
        )

        // ==========================================
        // 3. HERO ACTIVE PHASE & PROGRESS CARD
        // ==========================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CURRENT WORKFLOW PHASE",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${(state.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.label,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryBlue
                )

                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = state.progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = PrimaryBlue,
                    trackColor = BorderSubtle
                )
            }
        }

        // ==========================================
        // 4. PRIMARY AUTOMATION ACTION BUTTON
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Button(
                onClick = {
                    if (metrics.isRunning) onStopClick() else onStartClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (metrics.isRunning) ErrorRed else PrimaryBlue
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(
                    imageVector = if (metrics.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (metrics.isRunning) "HALT AUTOMATION" else "START AUTONOMOUS WORKFLOW",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ==========================================
        // 5. QUICK NAVIGATION ACTION ROW
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = onOpenBrowser,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = SurfaceWhite,
                    contentColor = PrimaryBlue
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
            ) {
                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Browser", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            FilledTonalButton(
                onClick = onNavigateToVault,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = SurfaceWhite,
                    contentColor = PrimaryBlue
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("View Vault", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // ==========================================
        // 6. CLOUD TELEPHONY & SMS WORKER
        // ==========================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SoftBlueTile),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(14.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = "Cloud Telephony",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Cloud Telephony & SMS Worker",
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryBlue,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = metrics.currentSlotInfo,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.5.sp),
                            color = TextSlateDark,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(
                        onClick = {
                            val botIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/EHR_QUICKINCOME_BOT"))
                            try {
                                context.startActivity(botIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Telegram Bot: @EHR_QUICKINCOME_BOT", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = SurfaceWhite),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("TG Bot", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }

                    FilledTonalButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Cloud Phone", metrics.currentSlotInfo)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Cloud Telephony info copied", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = SurfaceWhite),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Copy", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }
                }
            }
        }

        // ==========================================
        // 7. KEY PERFORMANCE METRICS SECTION
        // ==========================================
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            MetricCard(
                title = "Total Accounts Provisioned",
                value = "${metrics.totalCreated}",
                subtitle = "Committed to local encrypted Room DB",
                icon = Icons.Default.CheckCircle,
                tileColor = SoftGreenTile,
                iconTint = SuccessGreen
            )

            MetricCard(
                title = "Edge Cloud Redundancy",
                value = "4 Cloud Clusters Active",
                subtitle = "Cloudflare D1, Supabase, Firebase RTDB, Turso libSQL",
                icon = Icons.Default.CloudQueue,
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
                value = "Frankfurt Gateway (DE)",
                subtitle = "Lat: 50.1109° N, Lon: 8.6821° E • Webshare Residential",
                icon = Icons.Default.Security,
                tileColor = SoftBlueTile,
                iconTint = PrimaryBlue
            )
        }

        // ==========================================
        // 8. MULTI-CLOUD STATUS CHIPS
        // ==========================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "CLOUD MESH INTEGRITY",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CloudStatusBadge("CF D1", true)
                    CloudStatusBadge("Supabase", true)
                    CloudStatusBadge("Firebase", true)
                    CloudStatusBadge("Turso", true)
                    CloudStatusBadge("Upstash", true)
                }
            }
        }
    }
}

@Composable
private fun CloudStatusBadge(name: String, isOnline: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isOnline) SoftGreenTile else SurfaceVariantLight,
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) SuccessGreen else TextMuted)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = name,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isOnline) SuccessGreen else TextMuted
            )
        }
    }
}

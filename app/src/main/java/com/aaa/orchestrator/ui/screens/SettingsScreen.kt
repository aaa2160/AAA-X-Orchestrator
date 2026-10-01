package com.aaa.orchestrator.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaa.orchestrator.engine.PermissionManager
import com.aaa.orchestrator.engine.PermissionStatus
import com.aaa.orchestrator.engine.ProxyEngine
import com.aaa.orchestrator.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onTriggerKillSwitchTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val proxyEngine = remember { ProxyEngine() }
    var isPinging by remember { mutableStateOf(false) }

    var permissionsList by remember { mutableStateOf(PermissionManager.getAllPermissions(context)) }

    fun refreshPermissions() {
        permissionsList = PermissionManager.getAllPermissions(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "System Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = TextSlateDark
        )
        Text(
            text = "System permissions, on-device AI engines, and fail-safe controls",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Macroify-Style Permission Center
        Card(
            modifier = Modifier.fillMaxWidth(),
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Permissions",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "System Permissions (Macroify Setup)",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextSlateDark
                            )
                            Text(
                                text = "Required for 2nr OTP interception & background services",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            refreshPermissions()
                            Toast.makeText(context, "Permissions refreshed", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                permissionsList.forEach { perm ->
                    PermissionRow(
                        permission = perm,
                        onGrantClick = {
                            perm.onGrant(context)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // What is AI & Offline Engines Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SoftBlueTile),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "AI Engine",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "What is the AI in this App?",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSlateDark
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Google ML Kit Offline Vision: Built-in local neural network (zero cloud APIs, zero cost).\n" +
                            "• On-Device OCR: Scans phone numbers and SMS captchas directly on your Samsung Galaxy A30.\n" +
                            "• Pure Privacy: No images, screenshots, or personal data ever leave your phone.\n" +
                            "• 100% Offline: Operates with zero internet connection required for AI vision processing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSlateDark,
                    lineHeight = 21.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Telegram Backup Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Telegram",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Telegram Backup Channel",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSlateDark
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Target Channel ID: -1003932377927\nTitle: AAA X accounts backup\nBatch Frequency: Every 40 accounts\nBot: @My_agy_Ai_bot",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Google Sheets Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TableChart,
                        contentDescription = "Google Sheets",
                        tint = SecondaryEmerald,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Google Sheets Logging",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSlateDark
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Sheet ID: 17Rgdfzx2PwNyOlD2byhH0btM1ywUzEuGlxAUYPsmdXY\nService Account: agy-bot@gen-lang-client-0633111390.iam.gserviceaccount.com\nColumns: Username: | Password: | 2FA: | Cookies",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Webshare Proxy Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val country by proxyEngine.currentCountry.collectAsState()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = "Proxy",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Proxy Routing",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSlateDark
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            val newCountry = proxyEngine.toggleCountry()
                            Toast.makeText(
                                context,
                                if (newCountry == "DE") "🇩🇪 Germany Route: Face Verification Bypass & 6 OTPs active" else "🇵🇱 Poland Route: Carrier Match active",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (country == "DE") SoftGreenTile else SoftBlueTile
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = if (country == "DE") "🇩🇪 Germany (Recommended)" else "🇵🇱 Poland",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (country == "DE") SuccessGreen else PrimaryBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SoftGreenTile,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "🔥 Twitter Method: Face Verification Solution",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Routing traffic via Germany (🇩🇪) eliminates Twitter/X Face Verification challenges and allows receiving up to 6 OTPs per Polish 2nr phone number.",
                            fontSize = 11.sp,
                            color = TextSlateDark,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Active Egress: ${if (country == "DE") "Germany (DE) - Bypass Mode" else "Poland (PL) - Direct Carrier"}\nEndpoints: p.webshare.io:80 (lebvkslv / acdyvomx)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Diagnostic Buttons
        Text(
            text = "HARDWARE & FAIL-SAFE DIAGNOSTICS",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    if (!isPinging) {
                        scope.launch {
                            isPinging = true
                            val (success, latency) = proxyEngine.verifyProxyHealth()
                            isPinging = false
                            val endpoint = proxyEngine.getActiveProxy()
                            if (success) {
                                Toast.makeText(context, "Proxy OK: ${endpoint.host}:${endpoint.port} (${endpoint.country}) in ${latency}ms", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Proxy ping failed (${latency}ms). Rotated to fallback endpoint.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                enabled = !isPinging,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isPinging) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = PrimaryBlue)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pinging...")
                } else {
                    Text("Ping Proxy")
                }
            }

            Button(
                onClick = onTriggerKillSwitchTest,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
            ) {
                Text("Test Kill Switch")
            }
        }
    }
}

@Composable
private fun PermissionRow(
    permission: PermissionStatus,
    onGrantClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SurfaceVariantLight,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (permission.isGranted) SoftGreenTile else SoftAmberTile
                    ) {
                        Text(
                            text = if (permission.isGranted) "GRANTED" else "SETUP NEEDED",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (permission.isGranted) SuccessGreen else WarningAmber,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = permission.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = TextSlateDark
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = permission.description,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (!permission.isGranted) {
                FilledTonalButton(
                    onClick = onGrantClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = PrimaryBlue),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Grant ↗", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                }
            } else {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Active",
                    tint = SuccessGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

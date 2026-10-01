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
import com.aaa.orchestrator.engine.CloudIntegrationEngine
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

    var permissionsList by remember { mutableStateOf(PermissionManager.getAllPermissions(context)) }

    // Cloud connection test states
    var cloudflareStatus by remember { mutableStateOf("Ready to Ping") }
    var supabaseStatus by remember { mutableStateOf("Ready to Ping") }
    var groqStatus by remember { mutableStateOf("Ready to Ping") }
    var openRouterStatus by remember { mutableStateOf("Ready to Ping") }
    var telegramStatus by remember { mutableStateOf("Ready to Ping") }
    var firebaseStatus by remember { mutableStateOf("Ready to Ping") }
    var tursoStatus by remember { mutableStateOf("Ready to Ping") }
    var upstashStatus by remember { mutableStateOf("Ready to Ping") }
    var betterStackStatus by remember { mutableStateOf("Ready to Ping") }
    var ipinfoStatus by remember { mutableStateOf("Ready to Ping") }

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
            text = "System Settings & Cloud Hub",
            style = MaterialTheme.typography.headlineMedium,
            color = TextSlateDark
        )
        Text(
            text = "Multi-cloud architecture, neural AI engines, and hardware guardrails",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Device Permissions Card
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
                                text = "Device Permissions & KYC",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextSlateDark
                            )
                            Text(
                                text = "Camera, SMS listening, accessibility, and background stability",
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

        Spacer(modifier = Modifier.height(16.dp))

        // Multi-Cloud & AI Infrastructure Hub
        Text(
            text = "Connected Cloud Services",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
            fontWeight = FontWeight.Bold,
            color = TextSlateDark
        )
        Text(
            text = "Active credentials verified from environment configuration",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Cloudflare D1 & Workers AI Card
        CloudServiceCard(
            title = "Cloudflare D1 & Workers AI",
            subtitle = "Edge SQLite & Llama 3.1 Neural Models",
            details = "D1 UUID: 273c4762-15c2-4008-8f16-ffb99a965880\nAccount: 0a9ea11bd9be8ba399d8c56be841d7a7\nAI Model: @cf/meta/llama-3.1-8b-instruct",
            status = cloudflareStatus,
            icon = Icons.Default.Cloud,
            iconTint = PrimaryBlue,
            onPing = {
                scope.launch {
                    cloudflareStatus = "Pinging D1..."
                    val (d1Ok, d1Msg) = CloudIntegrationEngine.executeD1Query("SELECT COUNT(*) FROM accounts;")
                    cloudflareStatus = if (d1Ok) d1Msg else "D1 Error"
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Supabase Realtime Card
        CloudServiceCard(
            title = "Supabase Realtime Database",
            subtitle = "PostgreSQL & Instant Synchronization",
            details = "Project: znbbaozpevurvbfkxakz\nEndpoint: https://znbbaozpevurvbfkxakz.supabase.co\nAuth: Service Role Secret Key",
            status = supabaseStatus,
            icon = Icons.Default.Storage,
            iconTint = SecondaryEmerald,
            onPing = {
                scope.launch {
                    supabaseStatus = "Pinging Supabase..."
                    val (ok, msg) = CloudIntegrationEngine.testSupabase()
                    supabaseStatus = msg
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Groq High-Speed AI Engine Card
        CloudServiceCard(
            title = "Groq Ultra-Fast AI (64ms)",
            subtitle = "500+ Tokens/sec LLM Inference Engine",
            details = "Active Model: openai/gpt-oss-20b\nLatency: 60-80ms\nPurpose: Smart Form Filling & Profile Generation",
            status = groqStatus,
            icon = Icons.Default.Speed,
            iconTint = WarningAmber,
            onPing = {
                scope.launch {
                    groqStatus = "Pinging Groq..."
                    val (ok, msg) = CloudIntegrationEngine.testGroqAI()
                    groqStatus = msg
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // OpenRouter Multi-Model Free Tier Card
        CloudServiceCard(
            title = "OpenRouter AI (Free Tier)",
            subtitle = "Redundant Edge LLM Fallback",
            details = "Endpoint: openrouter.ai/api/v1\nModel: openrouter/free\nCost: $0.00 / token",
            status = openRouterStatus,
            icon = Icons.Default.Psychology,
            iconTint = AccentIndigo,
            onPing = {
                scope.launch {
                    openRouterStatus = "Pinging OpenRouter..."
                    val (ok, msg) = CloudIntegrationEngine.testOpenRouterAI()
                    openRouterStatus = msg
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Telegram Backup & Cloud SMS Gateway Card
        CloudServiceCard(
            title = "Telegram Bot & Backup Channel",
            subtitle = "Encrypted Vault Fan-out & Number Workers",
            details = "Target Channel: -1003932377927\nBot: @My_agy_Ai_bot\nTelephony Worker: @EHR_QUICKINCOME_BOT",
            status = telegramStatus,
            icon = Icons.Default.Send,
            iconTint = PrimaryBlue,
            onPing = {
                scope.launch {
                    telegramStatus = "Pinging Bot..."
                    val (ok, msg) = CloudIntegrationEngine.testTelegram()
                    telegramStatus = msg
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Firebase Realtime Database Card
        CloudServiceCard(
            title = "Firebase Realtime DB",
            subtitle = "Google Cloud Realtime Sync & Backup",
            details = "Project: gen-lang-client-0633111390 (AAA-TEAM)\nDatabase: gen-lang-client-0633111390-default-rtdb\nMode: Realtime JSON REST Endpoint",
            status = firebaseStatus,
            icon = Icons.Default.CloudQueue,
            iconTint = WarningAmber,
            onPing = {
                scope.launch {
                    firebaseStatus = "Pinging Firebase..."
                    val (ok, msg) = CloudIntegrationEngine.testFirebase()
                    firebaseStatus = msg
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Turso libSQL Edge SQLite Card
        CloudServiceCard(
            title = "Turso libSQL Edge SQLite",
            subtitle = "Distributed libSQL Database Pipeline",
            details = "Endpoint: my-agy-fleet-db-aaa2743.aws-ap-south-1.turso.io\nProtocol: libSQL Pipeline v2 REST API\nLatency: Sub-50ms Global Edge",
            status = tursoStatus,
            icon = Icons.Default.Storage,
            iconTint = SecondaryEmerald,
            onPing = {
                scope.launch {
                    tursoStatus = "Pinging Turso..."
                    val (ok, msg) = CloudIntegrationEngine.testTurso()
                    tursoStatus = msg
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Upstash Serverless Redis Card
        CloudServiceCard(
            title = "Upstash Serverless Redis",
            subtitle = "In-Memory Global Cache & Anti-Inactivity",
            details = "Endpoint: relaxing-starfish-285827.upstash.io\nCommand: REST PING -> PONG\nHeartbeat: Keepalive Active",
            status = upstashStatus,
            icon = Icons.Default.Memory,
            iconTint = ErrorRed,
            onPing = {
                scope.launch {
                    upstashStatus = "Pinging Upstash..."
                    val (ok, msg) = CloudIntegrationEngine.testUpstash()
                    upstashStatus = msg
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Better Stack Uptime Sentinel Card
        CloudServiceCard(
            title = "Better Stack Uptime Sentinel",
            subtitle = "Global Availability & Telemetry Monitor",
            details = "Endpoint: api.betterstack.com/v2/monitors\nActive Monitors: 8 Cloud Nodes (Vercel, Render, Cloudflare, Turso, Upstash)\nCheck Frequency: 180s Automatic",
            status = betterStackStatus,
            icon = Icons.Default.CheckCircle,
            iconTint = SuccessGreen,
            onPing = {
                scope.launch {
                    betterStackStatus = "Checking Monitors..."
                    val (ok, msg) = CloudIntegrationEngine.testBetterStack()
                    betterStackStatus = msg
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Ipinfo.io Geolocation & Reputation Card
        CloudServiceCard(
            title = "Ipinfo.io Geolocation Intelligence",
            subtitle = "Live External Network & ASN Verification",
            details = "Token: 3274419685c2d1\nTarget: Frankfurt Cloud Gateway / Residential Egress\nVerification: ASN, Region, Timezone, Carrier",
            status = ipinfoStatus,
            icon = Icons.Default.LocationOn,
            iconTint = PrimaryBlue,
            onPing = {
                scope.launch {
                    ipinfoStatus = "Querying Ipinfo..."
                    val (ok, msg) = CloudIntegrationEngine.testIpinfo()
                    ipinfoStatus = msg
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Dedicated Storage Directory Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SoftBlueTile),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Dedicated Storage",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Dedicated Storage Directory",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSlateDark
                        )
                        Text(
                            text = "/storage/emulated/0/Download/AAAX/",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "All compiled APK updates, exported accounts, credentials logs, and stream downloads are delivered directly to this persistent device folder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

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
                    Column {
                        Text(
                            text = "Google Sheets Logging",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSlateDark
                        )
                        Text(
                            text = "Auto-append accounts into Sheet1",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Sheet Title: AAA-X-Accounts\nSheet ID: 17Rgdfzx2PwNyOlD2byhH0btM1ywUzEuGlxAUYPsmdXY\nService Account: agy-bot@gen-lang-client-0633111390.iam.gserviceaccount.com",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

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
                            Toast.makeText(context, "Proxy Route updated: $newCountry", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (country == "RANDOM") SoftGreenTile else SoftBlueTile
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = if (country == "RANDOM") "Random Locations" else "Region: $country",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (country == "RANDOM") SuccessGreen else PrimaryBlue
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
                            text = "Automated Rotation & Anti-Detection Shield",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Pre-flight latency test (<150ms) ensures smooth mobile browsing on Galaxy A30.",
                            fontSize = 11.sp,
                            color = TextSlateDark
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Diagnostic Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onTriggerKillSwitchTest,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
            ) {
                Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test Hardware Kill Switch (<1ms)")
            }
        }
    }
}

@Composable
private fun CloudServiceCard(
    title: String,
    subtitle: String,
    details: String,
    status: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    onPing: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                            fontWeight = FontWeight.Bold,
                            color = TextSlateDark
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextMuted
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onPing,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = SoftBlueTile),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Ping", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextMuted,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (status.contains("OK") || status.contains("Online") || status.contains("ready", ignoreCase = true)) SoftGreenTile else SoftBlueTile
            ) {
                Text(
                    text = "Status: $status",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (status.contains("OK") || status.contains("Online")) SuccessGreen else PrimaryBlue,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
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

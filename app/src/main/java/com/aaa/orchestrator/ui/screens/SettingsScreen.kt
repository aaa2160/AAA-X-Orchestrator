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
import kotlinx.coroutines.launch
import com.aaa.orchestrator.engine.ProxyEngine

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
            text = "Cloud integrations and hardware fail-safe parameters",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(16.dp))

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = "Proxy",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Proxy Network (Webshare.io)",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSlateDark
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Egress Country: Poland (PL) - Strict Match\nActive Tunnel: lebvkslv@p.webshare.io:80\nFallback: acdyvomx@p.webshare.io:80",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    lineHeight = 20.sp
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

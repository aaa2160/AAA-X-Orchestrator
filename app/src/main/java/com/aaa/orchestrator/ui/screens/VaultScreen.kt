package com.aaa.orchestrator.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaa.orchestrator.data.model.AccountRecord
import com.aaa.orchestrator.engine.CloudIntegrationEngine
import com.aaa.orchestrator.ui.components.TotpRing
import com.aaa.orchestrator.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

/**
 * Enterprise Accounts Vault Screen.
 * Phone-optimized credential store with live TOTP generation,
 * 4-Cloud synchronization (Cloudflare D1, Supabase, Firebase, Turso),
 * and export to /storage/emulated/0/Download/AAAX/.
 */
@Composable
fun VaultScreen(
    accounts: List<AccountRecord>,
    onDeleteAccount: (Long) -> Unit = {},
    onClearAllAccounts: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }

    val filtered = remember(accounts, searchQuery) {
        if (searchQuery.isBlank()) accounts
        else accounts.filter {
            it.username.contains(searchQuery, ignoreCase = true) ||
            it.phoneNumberUsed.contains(searchQuery)
        }
    }

    val scope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Accounts?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently remove all ${accounts.size} account records from the local encrypted database. Accounts synced to the cloud will remain intact.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        onClearAllAccounts()
                        Toast.makeText(context, "All local accounts cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(16.dp)
    ) {
        // App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Accounts Vault",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    fontWeight = FontWeight.Bold,
                    color = TextSlateDark
                )
                Text(
                    text = "${accounts.size} Provisioned Twitter/X Accounts",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }

            if (accounts.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(
                        onClick = {
                            if (!isSyncing) {
                                isSyncing = true
                                scope.launch {
                                    var cfCount = 0
                                    var sbCount = 0
                                    var fbCount = 0
                                    var trCount = 0
                                    accounts.forEach { acc ->
                                        if (CloudIntegrationEngine.syncAccountToCloudflare(acc)) cfCount++
                                        if (CloudIntegrationEngine.syncAccountToSupabase(acc)) sbCount++
                                        if (CloudIntegrationEngine.syncAccountToFirebase(acc)) fbCount++
                                        if (CloudIntegrationEngine.syncAccountToTurso(acc)) trCount++
                                    }
                                    isSyncing = false
                                    Toast.makeText(
                                        context,
                                        "Multi-Cloud Sync: $cfCount CF D1, $sbCount Supabase, $fbCount Firebase, $trCount Turso",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = SoftGreenTile),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = SuccessGreen
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = "Cloud Sync",
                                tint = SuccessGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isSyncing) "Syncing..." else "Sync Cloud",
                            color = SuccessGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    FilledTonalButton(
                        onClick = { exportAndShareAccounts(context, accounts) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = SoftBlueTile),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export All",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Export",
                            color = PrimaryBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear All",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search accounts or phone...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceWhite,
                unfocusedContainerColor = SurfaceWhite,
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = BorderSubtle
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.LockClock,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = TextMuted.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (accounts.isEmpty()) "No accounts provisioned yet.\nStart automation from the Dashboard." else "No matching accounts found.",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { account ->
                    AccountVaultCard(
                        account = account,
                        onCopyText = { label, text ->
                            copyToClipboard(context, label, text)
                        },
                        onDelete = {
                            if (account.id > 0) onDeleteAccount(account.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AccountVaultCard(
    account: AccountRecord,
    onCopyText: (String, String) -> Unit,
    onDelete: () -> Unit = {}
) {
    var isPasswordVisible by remember { mutableStateOf(false) }

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
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(SoftBlueTile),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = account.username,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.5.sp),
                            fontWeight = FontWeight.Bold,
                            color = TextSlateDark
                        )
                        Text(
                            text = account.phoneNumberUsed,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val has2fa = account.twoFactorSecret.isNotBlank()
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (has2fa) SoftGreenTile else SurfaceVariantLight
                    ) {
                        Text(
                            text = if (has2fa) "2FA" else "STD",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (has2fa) SuccessGreen else TextMuted,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Password row with toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Pass: " + if (isPasswordVisible) account.password else "••••••••••••",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSlateDark,
                    fontWeight = FontWeight.Medium
                )
                IconButton(
                    onClick = { isPasswordVisible = !isPasswordVisible },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle password",
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            if (account.twoFactorSecret.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                TotpRing(secret = account.twoFactorSecret)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1-Tap Copy Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { onCopyText("Username", account.username) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text("User", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = { onCopyText("Password", account.password) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text("Pass", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = { onCopyText("Phone", account.phoneNumberUsed) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text("Phone", fontSize = 11.sp)
                }

                Button(
                    onClick = { onCopyText("Full Record", account.toDelimitedLine(":")) },
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text("Copy All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
}

private fun exportAndShareAccounts(context: Context, accounts: List<AccountRecord>) {
    if (accounts.isEmpty()) {
        Toast.makeText(context, "No accounts to export", Toast.LENGTH_SHORT).show()
        return
    }

    val sb = StringBuilder()
    for (acc in accounts) {
        sb.append(acc.toDelimitedLine(":")).append("\n")
    }

    // Save directly to dedicated folder: /storage/emulated/0/Download/AAAX/
    try {
        val targetDir = File("/storage/emulated/0/Download/AAAX")
        if (!targetDir.exists()) targetDir.mkdirs()
        val exportFile = File(targetDir, "accounts_export.txt")
        exportFile.writeText(sb.toString())
        context.sendBroadcast(
            Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, android.net.Uri.fromFile(exportFile))
        )
        Toast.makeText(context, "Exported to Download/AAAX/accounts_export.txt", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        // Fallback to internal storage or direct share
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "AAA X-Orchestrator Export (${accounts.size} accounts)")
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    context.startActivity(Intent.createChooser(intent, "Share Accounts Export"))
}

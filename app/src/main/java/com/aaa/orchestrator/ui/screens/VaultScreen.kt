package com.aaa.orchestrator.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.aaa.orchestrator.data.model.AccountRecord
import com.aaa.orchestrator.ui.components.TotpRing
import com.aaa.orchestrator.ui.theme.*
import kotlinx.coroutines.launch

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
        else accounts.filter { it.username.contains(searchQuery, ignoreCase = true) }
    }

    val scope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Accounts?") },
            text = { Text("This will permanently delete all ${accounts.size} account records from local storage. Real sessions should be exported first.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        onClearAllAccounts()
                        Toast.makeText(context, "All accounts cleared", Toast.LENGTH_SHORT).show()
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Accounts Vault",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextSlateDark
                )
                Text(
                    text = "${accounts.size} Provisioned Twitter/X Accounts",
                    style = MaterialTheme.typography.bodyMedium,
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
                                    var cfSuccess = 0
                                    var sbSuccess = 0
                                    var fbSuccess = 0
                                    accounts.forEach { acc ->
                                        if (com.aaa.orchestrator.engine.CloudIntegrationEngine.syncAccountToCloudflare(acc)) {
                                            cfSuccess++
                                        }
                                        if (com.aaa.orchestrator.engine.CloudIntegrationEngine.syncAccountToSupabase(acc)) {
                                            sbSuccess++
                                        }
                                        if (com.aaa.orchestrator.engine.CloudIntegrationEngine.syncAccountToFirebase(acc)) {
                                            fbSuccess++
                                        }
                                    }
                                    isSyncing = false
                                    Toast.makeText(
                                        context,
                                        "Synced ${accounts.size} accounts: $cfSuccess Cloudflare D1, $sbSuccess Supabase, $fbSuccess Firebase",
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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    FilledTonalButton(
                        onClick = { shareAllAccounts(context, accounts) },
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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.size(36.dp)
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

        Spacer(modifier = Modifier.height(12.dp))

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by username...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
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

        Spacer(modifier = Modifier.height(16.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (accounts.isEmpty()) "No accounts generated yet. Start automation." else "No matching accounts.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyLarge
                )
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.username,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSlateDark
                    )
                    Text(
                        text = "Phone: ${account.phoneNumberUsed} • Pass: ${account.password}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val has2fa = account.twoFactorSecret.isNotBlank()
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (has2fa) SoftGreenTile else SurfaceVariantLight
                    ) {
                        Text(
                            text = if (has2fa) "2FA ACTIVE" else "SESSION ONLY",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (has2fa) SuccessGreen else TextMuted,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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

            if (account.twoFactorSecret.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                // Live TOTP Ring
                TotpRing(secret = account.twoFactorSecret)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onCopyText("Username", account.username) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text("User", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = { onCopyText("Password", account.password) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text("Pass", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = { onCopyText("2FA Secret", account.twoFactorSecret) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text("2FA", fontSize = 11.sp)
                }

                Button(
                    onClick = { onCopyText("Export Line", account.toDelimitedLine(":")) },
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
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
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
}

private fun shareAllAccounts(context: Context, accounts: List<AccountRecord>) {
    if (accounts.isEmpty()) {
        Toast.makeText(context, "No accounts to export", Toast.LENGTH_SHORT).show()
        return
    }
    val sb = StringBuilder()
    for (acc in accounts) {
        sb.append(acc.toDelimitedLine(":")).append("\n")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "AAA X-Orchestrator Export (${accounts.size} accounts)")
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    context.startActivity(Intent.createChooser(intent, "Export All Accounts"))
}

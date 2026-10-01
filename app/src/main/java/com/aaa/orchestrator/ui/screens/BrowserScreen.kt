package com.aaa.orchestrator.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aaa.orchestrator.data.model.OrchestratorState
import com.aaa.orchestrator.engine.AppLauncher
import com.aaa.orchestrator.engine.OrchestratorEngine
import com.aaa.orchestrator.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.net.URLEncoder

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    engine: OrchestratorEngine? = null,
    activeUrl: String = "https://x.com/i/flow/signup",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Browser navigation & state
    var currentUrl by remember { mutableStateOf(activeUrl) }
    var inputUrl by remember { mutableStateOf(activeUrl) }
    var pageTitle by remember { mutableStateOf("Browser") }
    var pageProgress by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isDesktopMode by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // Workflow HUD visibility (minimizable so user can browse normally)
    var isHudExpanded by remember { mutableStateOf(false) }
    var showPhoneEditDialog by remember { mutableStateOf(false) }
    var phoneInputText by remember { mutableStateOf("") }

    // Stats
    var blockedAdsCount by remember { mutableStateOf(0) }
    var blockedTrackersCount by remember { mutableStateOf(0) }

    // Engine bindings
    val state by (engine?.state ?: remember { MutableStateFlow<OrchestratorState>(OrchestratorState.Idle) }).collectAsState()
    val phoneNumber by (engine?.activePhoneNumber ?: remember { MutableStateFlow("+48459074091") }).collectAsState()
    val password by (engine?.activePassword ?: remember { MutableStateFlow("AAA_Auto_2026") }).collectAsState()
    val latestOtp by (engine?.latestOtp ?: remember { MutableStateFlow<String?>(null) }).collectAsState()

    val mobileUserAgent = "Mozilla/5.0 (Linux; Android 11; SM-A305F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    val desktopUserAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    val adBlockHosts = remember {
        setOf(
            "doubleclick.net",
            "google-analytics.com",
            "adservice.google.com",
            "googlesyndication.com",
            "adnxs.com",
            "scorecardresearch.com",
            "facebook.net/tr",
            "taboola.com",
            "outbrain.com",
            "criteo.com",
            "telemetry.x.com",
            "ads-twitter.com",
            "analytics.twitter.com"
        )
    }

    fun navigateTo(rawQuery: String) {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) return
        val destination = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else if (trimmed.contains(".") && !trimmed.contains(" ")) {
            "https://$trimmed"
        } else {
            "https://www.google.com/search?q=" + URLEncoder.encode(trimmed, "UTF-8")
        }
        inputUrl = destination
        currentUrl = destination
        webViewInstance?.loadUrl(destination)
    }

    // Phone Edit Dialog
    if (showPhoneEditDialog) {
        AlertDialog(
            onDismissRequest = { showPhoneEditDialog = false },
            title = { Text("Set Real Phone Number") },
            text = {
                Column {
                    Text(
                        text = "Enter your real Polish 2nr phone number (e.g. +48459074091):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phoneInputText,
                        onValueChange = { phoneInputText = it },
                        label = { Text("Phone Number") },
                        placeholder = { Text("+48...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (phoneInputText.isNotBlank()) {
                            engine?.updateActivePhoneNumber(phoneInputText.trim())
                            showPhoneEditDialog = false
                            Toast.makeText(context, "Active phone updated: ${phoneInputText.trim()}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPhoneEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        // Collapsible Workflow Registration HUD Bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                // Compact Header with Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (!state.label.contains("Idle")) SoftGreenTile else SurfaceVariantLight
                        ) {
                            Text(
                                text = if (!state.label.contains("Idle")) "AUTONOMOUS ACTIVE" else "WORKFLOW TOOLS",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (!state.label.contains("Idle")) SuccessGreen else TextMuted,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isHudExpanded) state.label else "Phone: $phoneNumber",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSlateDark,
                            maxLines = 1
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Open real 2nr installed app on device
                        FilledTonalButton(
                            onClick = { AppLauncher.open2nrApp(context) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = SoftGreenTile),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = "Open 2nr App",
                                tint = SuccessGreen,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open 2nr", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Capture session button always accessible
                        FilledTonalButton(
                            onClick = {
                                val cookies = CookieManager.getInstance().getCookie("https://x.com") ?: ""
                                if (engine != null) {
                                    scope.launch {
                                        val result = engine.captureRealSession(cookies)
                                        if (result.isSuccess) {
                                            val acc = result.getOrNull()
                                            Toast.makeText(
                                                context,
                                                "✅ Real account saved: ${acc?.username}!\nCookies synced to Vault & Telegram.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "⚠️ ${result.exceptionOrNull()?.message ?: "Not logged in yet. Please complete signup on X.com."}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "Engine not connected", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = SoftBlueTile),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Capture Session",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save Session", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Toggle button to expand/collapse HUD
                        IconButton(
                            onClick = { isHudExpanded = !isHudExpanded },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = if (isHudExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isHudExpanded) "Collapse" else "Expand",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Expanded Workflow Details
                AnimatedVisibility(visible = isHudExpanded) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Phone Chip
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SoftBlueTile,
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "2nr Phone", fontSize = 9.sp, color = TextMuted)
                                        Text(text = phoneNumber, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSlateDark)
                                    }
                                    Row {
                                        IconButton(
                                            onClick = {
                                                phoneInputText = phoneNumber
                                                showPhoneEditDialog = true
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, "Edit Phone", modifier = Modifier.size(12.dp), tint = PrimaryBlue)
                                        }
                                        IconButton(
                                            onClick = { copyToClipboard(context, "Phone", phoneNumber) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(12.dp), tint = PrimaryBlue)
                                        }
                                        IconButton(
                                            onClick = {
                                                injectValueIntoInput(webViewInstance, phoneNumber)
                                                Toast.makeText(context, "Filled phone into input", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Input, "Fill", modifier = Modifier.size(12.dp), tint = PrimaryBlue)
                                        }
                                    }
                                }
                            }

                            // OTP Chip
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (latestOtp != null) SoftGreenTile else SurfaceVariantLight,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("2nr SMS OTP", fontSize = 9.sp, color = if (latestOtp != null) SuccessGreen else TextMuted)
                                        Text(
                                            text = latestOtp ?: "Waiting...",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (latestOtp != null) SuccessGreen else TextMuted
                                        )
                                    }
                                    if (latestOtp != null) {
                                        IconButton(
                                            onClick = {
                                                injectValueIntoInput(webViewInstance, latestOtp!!)
                                                Toast.makeText(context, "Filled OTP: $latestOtp", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Input, "Fill OTP", modifier = Modifier.size(13.dp), tint = SuccessGreen)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Password Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Password: $password",
                                fontSize = 11.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Medium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = { copyToClipboard(context, "Password", password) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Copy Pass", fontSize = 10.sp)
                                }
                                TextButton(
                                    onClick = {
                                        engine?.regeneratePassword()
                                        Toast.makeText(context, "New secure password generated", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("New Pass", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Real Interactive Address Bar & Search Omnibox
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // SSL Lock indicator
                Icon(
                    imageVector = if (currentUrl.startsWith("https://")) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "SSL Status",
                    tint = if (currentUrl.startsWith("https://")) SuccessGreen else TextMuted,
                    modifier = Modifier.size(15.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Editable URL / Search Input Field
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    placeholder = { Text("Search or type URL...", fontSize = 12.sp, color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            focusManager.clearFocus()
                            navigateTo(inputUrl)
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, color = TextSlateDark),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (inputUrl.isNotBlank()) {
                                IconButton(
                                    onClick = { inputUrl = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = TextMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    navigateTo(inputUrl)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = "Go",
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                )
            }
        }

        // Real Page Loading Progress Bar
        if (isLoading) {
            LinearProgressIndicator(
                progress = pageProgress / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = PrimaryBlue,
                trackColor = Color.Transparent
            )
        } else {
            Spacer(modifier = Modifier.height(2.5.dp))
        }

        // Standard Browser Navigation Bar & Quick Bookmarks
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Navigation controls (Back, Forward, Refresh/Stop, Home)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { if (webViewInstance?.canGoBack() == true) webViewInstance?.goBack() },
                    enabled = canGoBack,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = if (canGoBack) PrimaryBlue else TextMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = { if (webViewInstance?.canGoForward() == true) webViewInstance?.goForward() },
                    enabled = canGoForward,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (canGoForward) PrimaryBlue else TextMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = {
                        if (isLoading) {
                            webViewInstance?.stopLoading()
                        } else {
                            webViewInstance?.reload()
                        }
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                        contentDescription = if (isLoading) "Stop" else "Reload",
                        tint = TextSlateDark,
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = { navigateTo("https://x.com") },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Home",
                        tint = TextSlateDark,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            // Desktop site toggle & Clear Session
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        isDesktopMode = !isDesktopMode
                        webViewInstance?.settings?.userAgentString = if (isDesktopMode) desktopUserAgent else mobileUserAgent
                        webViewInstance?.settings?.useWideViewPort = isDesktopMode
                        webViewInstance?.reload()
                        Toast.makeText(context, if (isDesktopMode) "Desktop mode enabled" else "Mobile mode enabled", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = if (isDesktopMode) Icons.Default.DesktopWindows else Icons.Default.PhoneAndroid,
                        contentDescription = "Toggle Desktop Mode",
                        tint = if (isDesktopMode) PrimaryBlue else TextMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = {
                        CookieManager.getInstance().removeAllCookies(null)
                        webViewInstance?.clearCache(true)
                        webViewInstance?.clearHistory()
                        canGoBack = false
                        canGoForward = false
                        Toast.makeText(context, "Browser cache and cookies wiped clean", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Clear Cache",
                        tint = TextMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        // Quick Navigation Bookmarks Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BookmarkChip("𝕏 X.com") { navigateTo("https://x.com") }
            BookmarkChip("📝 X Signup") { navigateTo("https://x.com/i/flow/signup") }
            BookmarkChip("🔍 Google") { navigateTo("https://www.google.com") }
            BookmarkChip("🦆 DuckDuckGo") { navigateTo("https://duckduckgo.com") }
            BookmarkChip("🌐 Wikipedia") { navigateTo("https://en.m.wikipedia.org") }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Full Android WebView with WebChromeClient and Adblock
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewInstance = this
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // Full normal browser settings
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            javaScriptCanOpenWindowsAutomatically = true
                            mediaPlaybackRequiresUserGesture = false
                            allowFileAccess = true
                            allowContentAccess = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            userAgentString = mobileUserAgent
                        }

                        // Enable cookies
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        // WebChromeClient for page progress, title, and JavaScript alert handling
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                pageProgress = newProgress
                                isLoading = newProgress < 100
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (title != null) pageTitle = title
                            }

                            override fun onJsAlert(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                result: JsResult?
                            ): Boolean {
                                Toast.makeText(ctx, message ?: "", Toast.LENGTH_SHORT).show()
                                result?.confirm()
                                return true
                            }

                            override fun onJsConfirm(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                result: JsResult?
                            ): Boolean {
                                result?.confirm()
                                return true
                            }
                        }

                        // WebViewClient with adblock and render crash protection
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val host = request?.url?.host ?: ""

                                for (blockedHost in adBlockHosts) {
                                    if (host.contains(blockedHost, ignoreCase = true)) {
                                        if (blockedHost.contains("analytic") || blockedHost.contains("telemetry") || blockedHost.contains("scorecard")) {
                                            blockedTrackersCount++
                                        } else {
                                            blockedAdsCount++
                                        }
                                        return WebResourceResponse(
                                            "text/plain",
                                            "UTF-8",
                                            ByteArrayInputStream(ByteArray(0))
                                        )
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                if (url != null) {
                                    currentUrl = url
                                    inputUrl = url
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                if (url != null) {
                                    currentUrl = url
                                    inputUrl = url
                                }
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }

                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: RenderProcessGoneDetail?
                            ): Boolean {
                                Timber.w("WebView render process died. Re-initializing safely...")
                                view?.let {
                                    it.destroy()
                                    webViewInstance = null
                                }
                                return true
                            }
                        }

                        loadUrl(activeUrl)
                    }
                }
            )
        }
    }
}

@Composable
private fun BookmarkChip(
    title: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = SurfaceVariantLight,
        modifier = Modifier.height(28.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = TextSlateDark
            )
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied: $text", Toast.LENGTH_SHORT).show()
}

private fun injectValueIntoInput(webView: WebView?, value: String) {
    if (webView == null) return
    val script = """
        (function() {
            var active = document.activeElement;
            if (active && (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA')) {
                active.value = '$value';
                active.dispatchEvent(new Event('input', { bubbles: true }));
                active.dispatchEvent(new Event('change', { bubbles: true }));
                return 'focused_filled';
            }
            var inputs = document.querySelectorAll('input');
            for (var i = 0; i < inputs.length; i++) {
                if (!inputs[i].disabled && inputs[i].type !== 'hidden') {
                    inputs[i].value = '$value';
                    inputs[i].dispatchEvent(new Event('input', { bubbles: true }));
                    inputs[i].dispatchEvent(new Event('change', { bubbles: true }));
                    return 'first_filled';
                }
            }
            return 'not_found';
        })();
    """.trimIndent()
    webView.evaluateJavascript(script, null)
}

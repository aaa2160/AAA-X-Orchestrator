package com.aaa.orchestrator.ui.screens

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aaa.orchestrator.data.model.OrchestratorState
import com.aaa.orchestrator.engine.AccountProfileGenerator
import com.aaa.orchestrator.engine.AdBlockEngine
import com.aaa.orchestrator.engine.FaceVerificationNotifier
import com.aaa.orchestrator.engine.OrchestratorEngine
import com.aaa.orchestrator.engine.TwitterAutoPilot
import com.aaa.orchestrator.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    var url: String,
    var title: String = "New Tab"
)

data class HistoryItem(
    val url: String,
    val title: String,
    val timestamp: String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
)

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

    // Multi-Tab State
    var tabs by remember { mutableStateOf(listOf(BrowserTab(url = activeUrl, title = "X Signup"))) }
    var activeTabIndex by remember { mutableStateOf(0) }
    var showTabSwitcher by remember { mutableStateOf(false) }

    // Navigation & Web State
    var currentUrl by remember { mutableStateOf(activeUrl) }
    var inputUrl by remember { mutableStateOf(activeUrl) }
    var pageTitle by remember { mutableStateOf("X Signup") }
    var pageProgress by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isDesktopMode by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // Tools & Bookmarks
    var showBookmarksHistory by remember { mutableStateOf(false) }
    var bookmarks by remember {
        mutableStateOf(
            mutableSetOf(
                "https://x.com/i/flow/signup",
                "https://x.com",
                "https://www.google.com",
                "https://duckduckgo.com"
            )
        )
    }
    var history by remember { mutableStateOf(mutableListOf<HistoryItem>()) }

    // Sleek Auto-Pilot HUD State
    var isHudVisible by remember { mutableStateOf(true) }
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
    val proxyCountry by (engine?.proxyCountry ?: remember { MutableStateFlow("DE") }).collectAsState()

    val currentProfileName = remember { mutableStateOf(AccountProfileGenerator.generateFullName()) }
    val currentBirthDate = remember { mutableStateOf(AccountProfileGenerator.generateBirthDate()) }

    // Active WebView reference & Container reference for multi-tab management
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    val webViewPool = remember { mutableMapOf<String, WebView>() }
    var containerLayout by remember { mutableStateOf<FrameLayout?>(null) }

    // Chrome-Style Hardware Back Navigation: browse back before exiting tab
    BackHandler(enabled = canGoBack) {
        activeWebView?.goBack()
    }

    // AdBlock Engine
    val adBlockEngine = remember { AdBlockEngine(context) }

    // Geolocation Privacy Spoof Script (Frankfurt Gateway)
    val geoPrivacyScript = """
        (function() {
            var fakeCoords = {
                latitude: 50.1109,
                longitude: 8.6821,
                accuracy: 35.0,
                altitude: null,
                altitudeAccuracy: null,
                heading: null,
                speed: null
            };
            if (navigator.geolocation) {
                navigator.geolocation.getCurrentPosition = function(success, error, options) {
                    if (typeof success === 'function') {
                        success({ coords: fakeCoords, timestamp: Date.now() });
                    }
                };
                navigator.geolocation.watchPosition = function(success, error, options) {
                    if (typeof success === 'function') {
                        success({ coords: fakeCoords, timestamp: Date.now() });
                    }
                    return 101;
                };
                navigator.geolocation.clearWatch = function(id) {};
            }
        })();
    """.trimIndent()

    // Auto-pilot bridge and notification dispatcher
    val autoPilot = remember {
        TwitterAutoPilot(
            onFaceVerificationDetected = {
                FaceVerificationNotifier.showFaceVerificationAlert(
                    context,
                    "Security challenge active. Complete the selfie/face check on screen."
                )
            },
            onStepChanged = { step ->
                Timber.i("Twitter AutoPilot Step: $step")
            },
            onAccountCompleted = { cookies ->
                if (engine != null) {
                    scope.launch {
                        engine.captureRealSession(cookies)
                    }
                }
            }
        )
    }

    val mobileUserAgent = "Mozilla/5.0 (Linux; Android 11; SM-A305F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    val desktopUserAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    // Helper to evaluate Twitter AutoPilot script on the active WebView
    fun runAutoPilotOnActiveTab() {
        val script = TwitterAutoPilot.buildAutoPilotScript(
            name = currentProfileName.value,
            phone = phoneNumber,
            birthMonth = currentBirthDate.value.month,
            birthDay = currentBirthDate.value.day,
            birthYear = currentBirthDate.value.year,
            password = password,
            otp = latestOtp
        )
        activeWebView?.evaluateJavascript(script, null)
    }

    // Auto-inject incoming OTP into Twitter signup form instantly and advance
    LaunchedEffect(latestOtp) {
        if (!latestOtp.isNullOrBlank()) {
            injectValueIntoInput(activeWebView, latestOtp!!)
            Toast.makeText(context, "Auto-filled verification code: $latestOtp", Toast.LENGTH_SHORT).show()
            runAutoPilotOnActiveTab()
        }
    }

    // Function to create and configure a persistent WebView for a specific tab
    fun createConfiguredWebView(ctx: Context, tab: BrowserTab): WebView {
        return WebView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

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
                userAgentString = if (isDesktopMode) desktopUserAgent else mobileUserAgent
            }

            // Enable and manage cookies
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            // Connect JavaScript Bridge
            addJavascriptInterface(autoPilot, "AndroidBridge")

            // Integrated Download Manager
            setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                try {
                    val request = DownloadManager.Request(Uri.parse(url)).apply {
                        setMimeType(mimetype)
                        val cookies = CookieManager.getInstance().getCookie(url)
                        addRequestHeader("cookie", cookies)
                        addRequestHeader("User-Agent", userAgent)
                        setDescription("Downloading file...")
                        setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype))
                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS,
                            URLUtil.guessFileName(url, contentDisposition, mimetype)
                        )
                    }
                    val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    dm.enqueue(request)
                    Toast.makeText(ctx, "Download started...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Timber.e(e, "Error initiating download")
                    Toast.makeText(ctx, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            // Chrome client for progress, titles, alerts, and strict location privacy
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (activeWebView == view) {
                        pageProgress = newProgress
                        isLoading = newProgress < 100
                    }
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (title != null) {
                        if (activeWebView == view) {
                            pageTitle = title
                        }
                        val index = tabs.indexOfFirst { it.id == tab.id }
                        if (index != -1) {
                            val updated = tabs.toMutableList()
                            updated[index] = updated[index].copy(title = title)
                            tabs = updated
                        }
                    }
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    // Strict Location Privacy: Deny physical GPS / network location disclosure
                    callback?.invoke(origin, false, false)
                }

                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    Toast.makeText(ctx, message ?: "", Toast.LENGTH_SHORT).show()
                    result?.confirm()
                    return true
                }

                override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    result?.confirm()
                    return true
                }
            }

            // Client with Titanium AdBlock and AutoPilot injection
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    if (adBlockEngine.shouldBlock(request)) {
                        val host = request?.url?.host ?: ""
                        if (host.contains("analytic") || host.contains("telemetry") || host.contains("scorecard") || host.contains("tracker") || host.contains("clarity")) {
                            blockedTrackersCount++
                        } else {
                            blockedAdsCount++
                        }
                        return adBlockEngine.createEmptyResponse()
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (activeWebView == view) {
                        isLoading = true
                        if (url != null) {
                            currentUrl = url
                            inputUrl = url
                        }
                    }
                    // Inject location privacy spoof early
                    view?.evaluateJavascript(geoPrivacyScript, null)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (activeWebView == view) {
                        isLoading = false
                        if (url != null) {
                            currentUrl = url
                            inputUrl = url
                            history.add(HistoryItem(url = url, title = pageTitle))
                        }
                        canGoBack = view?.canGoBack() == true
                        canGoForward = view?.canGoForward() == true
                    }

                    // Apply cosmetic ad hiding
                    adBlockEngine.injectCosmeticAdHiding(view)

                    // Inject location privacy spoof again for SPA navigation
                    view?.evaluateJavascript(geoPrivacyScript, null)

                    // Inject Twitter AutoPilot if on signup/flow/challenge
                    if (url != null && (url.contains("signup") || url.contains("flow") || url.contains("challenge") || url.contains("x.com"))) {
                        val script = TwitterAutoPilot.buildAutoPilotScript(
                            name = currentProfileName.value,
                            phone = phoneNumber,
                            birthMonth = currentBirthDate.value.month,
                            birthDay = currentBirthDate.value.day,
                            birthYear = currentBirthDate.value.year,
                            password = password,
                            otp = latestOtp
                        )
                        view?.evaluateJavascript(script, null)
                    }
                }
            }

            loadUrl(tab.url)
        }
    }

    // Function to switch active tab WITHOUT reloading page state
    fun switchTab(newIndex: Int) {
        if (newIndex in tabs.indices) {
            val oldTab = tabs.getOrNull(activeTabIndex)
            val newTab = tabs[newIndex]

            // Hide old webview
            if (oldTab != null) {
                webViewPool[oldTab.id]?.visibility = View.GONE
            }

            // Get or create new webview
            val newWebView = webViewPool[newTab.id] ?: run {
                val created = createConfiguredWebView(context, newTab)
                webViewPool[newTab.id] = created
                containerLayout?.addView(created)
                created
            }

            newWebView.visibility = View.VISIBLE
            newWebView.bringToFront()

            activeTabIndex = newIndex
            activeWebView = newWebView
            currentUrl = newWebView.url ?: newTab.url
            inputUrl = currentUrl
            pageTitle = newWebView.title ?: newTab.title
            canGoBack = newWebView.canGoBack()
            canGoForward = newWebView.canGoForward()
            isLoading = false
        }
    }

    // Function to add a new tab
    fun addNewTab(url: String = "https://x.com/i/flow/signup") {
        val newTab = BrowserTab(url = url, title = "New Tab")
        tabs = tabs + newTab
        switchTab(tabs.size - 1)
        showTabSwitcher = false
    }

    // Function to close a tab
    fun closeTab(index: Int) {
        if (tabs.size <= 1) {
            // Keep at least one tab open
            val tab = tabs[0]
            tab.url = "https://x.com/i/flow/signup"
            tab.title = "X Signup"
            webViewPool[tab.id]?.loadUrl(tab.url)
            return
        }

        val closingTab = tabs[index]
        val webViewToDestroy = webViewPool.remove(closingTab.id)
        containerLayout?.removeView(webViewToDestroy)
        webViewToDestroy?.destroy()

        val updatedTabs = tabs.toMutableList()
        updatedTabs.removeAt(index)
        tabs = updatedTabs

        val nextIndex = when {
            activeTabIndex >= tabs.size -> tabs.size - 1
            activeTabIndex > index -> activeTabIndex - 1
            else -> activeTabIndex
        }
        switchTab(nextIndex)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ==========================================
        // 1. PROFESSIONAL CHROME-STYLE TOP APP BAR
        // ==========================================
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Home Button
                IconButton(
                    onClick = {
                        activeWebView?.loadUrl("https://x.com/i/flow/signup")
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = "Home",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Chrome-like Pill Omnibox
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .padding(horizontal = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // SSL Lock Security Icon
                        Icon(
                            imageVector = if (currentUrl.startsWith("https")) Icons.Default.Lock else Icons.Default.Language,
                            contentDescription = "Security",
                            tint = if (currentUrl.startsWith("https")) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // URL / Search Input Field
                        TextField(
                            value = inputUrl,
                            onValueChange = { inputUrl = it },
                            placeholder = {
                                Text(
                                    text = "Search or type URL",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    maxLines = 1
                                )
                            },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    focusManager.clearFocus()
                                    val formatted = when {
                                        inputUrl.startsWith("http://") || inputUrl.startsWith("https://") -> inputUrl
                                        inputUrl.contains(".") && !inputUrl.contains(" ") -> "https://$inputUrl"
                                        else -> "https://www.google.com/search?q=" + java.net.URLEncoder.encode(inputUrl, "UTF-8")
                                    }
                                    activeWebView?.loadUrl(formatted)
                                }
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        // Clear or Refresh Button inside Omnibox
                        if (isLoading) {
                            IconButton(
                                onClick = { activeWebView?.stopLoading() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Stop",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { activeWebView?.reload() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Reload",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Chrome-Style Tab Counter Badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(28.dp)
                        .border(
                            width = 1.8.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = RoundedCornerShape(7.dp)
                        )
                        .clickable { showTabSwitcher = true }
                ) {
                    Text(
                        text = tabs.size.toString(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 3-Dots Overflow Menu
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Chrome Overflow Menu Dropdown
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("New tab") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                addNewTab("https://x.com/i/flow/signup")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isHudVisible) "Hide Auto-Pilot HUD" else "Show Auto-Pilot HUD") },
                            leadingIcon = { Icon(Icons.Default.SmartToy, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                isHudVisible = !isHudVisible
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("History") },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showBookmarksHistory = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Bookmarks") },
                            leadingIcon = { Icon(Icons.Default.BookmarkBorder, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showBookmarksHistory = true
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Desktop site")
                                    Checkbox(
                                        checked = isDesktopMode,
                                        onCheckedChange = { checked ->
                                            isDesktopMode = checked
                                            activeWebView?.settings?.userAgentString = if (checked) desktopUserAgent else mobileUserAgent
                                            activeWebView?.reload()
                                            showMenu = false
                                        }
                                    )
                                }
                            },
                            leadingIcon = { Icon(Icons.Default.DesktopWindows, contentDescription = null) },
                            onClick = {
                                isDesktopMode = !isDesktopMode
                                activeWebView?.settings?.userAgentString = if (isDesktopMode) desktopUserAgent else mobileUserAgent
                                activeWebView?.reload()
                                showMenu = false
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Privacy: Frankfurt Gateway") },
                            leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null, tint = SuccessGreen) },
                            onClick = {
                                showMenu = false
                                Toast.makeText(context, "Exact GPS blocked. Spoofed to Frankfurt, Germany gateway.", Toast.LENGTH_LONG).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Blocked: $blockedAdsCount ads") },
                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = PrimaryBlue) },
                            onClick = {
                                showMenu = false
                                Toast.makeText(context, "Titanium AdBlocker: $blockedAdsCount ads, $blockedTrackersCount trackers blocked.", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        // ==========================================
        // 2. ULTRA-THIN SMOOTH LOADING PROGRESS BAR
        // ==========================================
        if (isLoading) {
            LinearProgressIndicator(
                progress = { pageProgress / 100f },
                color = PrimaryBlue,
                trackColor = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
            )
        }

        // ==========================================
        // 3. SLEEK COLLAPSIBLE AUTO-PILOT COMPANION
        // ==========================================
        AnimatedVisibility(visible = isHudVisible) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Status & Active Phone Chip
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { isHudExpanded = !isHudExpanded }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(SuccessGreen, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Auto-Pilot Active",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = phoneNumber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // OTP badge if available
                        if (!latestOtp.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SoftGreenTile,
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .clickable {
                                        injectValueIntoInput(activeWebView, latestOtp!!)
                                        runAutoPilotOnActiveTab()
                                    }
                            ) {
                                Text(
                                    text = "OTP: $latestOtp",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Quick 1-Tap Trigger AutoPilot Button
                        FilledTonalButton(
                            onClick = {
                                runAutoPilotOnActiveTab()
                                Toast.makeText(context, "Auto-Pilot scan dispatched", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Fill Form", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Expand / Collapse Chevron
                        IconButton(
                            onClick = { isHudExpanded = !isHudExpanded },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (isHudExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Expand",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Expanded Controls Panel
                    if (isHudExpanded) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Copy Phone
                            OutlinedButton(
                                onClick = {
                                    copyToClipboard(context, "Phone", phoneNumber)
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy Phone", fontSize = 10.sp)
                            }

                            // Edit Phone
                            OutlinedButton(
                                onClick = {
                                    phoneInputText = phoneNumber
                                    showPhoneEditDialog = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit Phone", fontSize = 10.sp)
                            }

                            // Fill Password
                            OutlinedButton(
                                onClick = {
                                    injectValueIntoInput(activeWebView, password)
                                    runAutoPilotOnActiveTab()
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Fill Pass", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 4. MULTI-WEBVIEW CONTAINER (NO TAB RESETS)
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        containerLayout = this
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // Initialize the initial tab webview
                        val initialTab = tabs[0]
                        val initialWebView = createConfiguredWebView(ctx, initialTab)
                        webViewPool[initialTab.id] = initialWebView
                        addView(initialWebView)
                        activeWebView = initialWebView
                    }
                }
            )
        }

        // ==========================================
        // 5. CHROME-STYLE BOTTOM NAVIGATION BAR
        // ==========================================
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                IconButton(
                    onClick = { activeWebView?.goBack() },
                    enabled = canGoBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Forward Button
                IconButton(
                    onClick = { activeWebView?.goForward() },
                    enabled = canGoForward,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Direct X Signup Shortcut
                IconButton(
                    onClick = {
                        activeWebView?.loadUrl("https://x.com/i/flow/signup")
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.PersonAdd,
                        contentDescription = "X Signup",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Bookmark Toggle
                val isBookmarked = bookmarks.contains(currentUrl)
                IconButton(
                    onClick = {
                        if (isBookmarked) {
                            bookmarks.remove(currentUrl)
                            Toast.makeText(context, "Bookmark removed", Toast.LENGTH_SHORT).show()
                        } else {
                            bookmarks.add(currentUrl)
                            Toast.makeText(context, "Bookmark saved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Bookmark",
                        tint = if (isBookmarked) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Share URL
                IconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, currentUrl)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share URL"))
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // ==========================================
    // 6. CHROME-STYLE TAB SWITCHER DIALOG
    // ==========================================
    if (showTabSwitcher) {
        AlertDialog(
            onDismissRequest = { showTabSwitcher = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tabs (${tabs.size})", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    FilledTonalButton(
                        onClick = {
                            addNewTab("https://x.com/i/flow/signup")
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New tab", fontSize = 12.sp)
                    }
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tabs.indices.toList()) { index ->
                        val tab = tabs[index]
                        val isSelected = index == activeTabIndex
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) PrimaryBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, PrimaryBlue) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    switchTab(index)
                                    showTabSwitcher = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.Language,
                                        contentDescription = null,
                                        tint = if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = tab.title,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = tab.url,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { closeTab(index) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close Tab",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTabSwitcher = false }) {
                    Text("Done")
                }
            }
        )
    }

    // ==========================================
    // 7. PHONE NUMBER EDIT DIALOG
    // ==========================================
    if (showPhoneEditDialog) {
        AlertDialog(
            onDismissRequest = { showPhoneEditDialog = false },
            title = { Text("Active 2nr Telephony Number") },
            text = {
                Column {
                    Text(
                        "Set the Polish number captured from 2nr or Render Cloud:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = phoneInputText,
                        onValueChange = { phoneInputText = it },
                        label = { Text("Phone Number") },
                        placeholder = { Text("+48459074091") },
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
                            runAutoPilotOnActiveTab()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Save & Apply")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPhoneEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ==========================================
    // 8. BOOKMARKS & HISTORY DIALOG
    // ==========================================
    if (showBookmarksHistory) {
        AlertDialog(
            onDismissRequest = { showBookmarksHistory = false },
            title = { Text("History & Bookmarks") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Bookmarks", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PrimaryBlue)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                    ) {
                        items(bookmarks.toList()) { bUrl ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable {
                                        activeWebView?.loadUrl(bUrl)
                                        showBookmarksHistory = false
                                    }
                            ) {
                                Text(
                                    text = bUrl,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Recent History", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PrimaryBlue)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                    ) {
                        items(history.takeLast(10).reversed()) { hItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        activeWebView?.loadUrl(hItem.url)
                                        showBookmarksHistory = false
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = hItem.title.ifEmpty { hItem.url },
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(text = hItem.timestamp, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBookmarksHistory = false }) {
                    Text("Close")
                }
            }
        )
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
            function triggerNative(el, val) {
                el.focus();
                var proto = window.HTMLInputElement.prototype;
                var descriptor = Object.getOwnPropertyDescriptor(proto, 'value');
                if (descriptor && descriptor.set) {
                    descriptor.set.call(el, val);
                } else {
                    el.value = val;
                }
                el.dispatchEvent(new Event('input', { bubbles: true, cancelable: true }));
                el.dispatchEvent(new Event('change', { bubbles: true, cancelable: true }));
                try {
                    el.dispatchEvent(new InputEvent('input', { bubbles: true, cancelable: true, inputType: 'insertText', data: val }));
                } catch(e) {}
            }

            if (active && (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA')) {
                triggerNative(active, '$value');
                return 'focused_filled';
            }
            var inputs = document.querySelectorAll('input:not([type="hidden"]):not([disabled])');
            for (var i = 0; i < inputs.length; i++) {
                triggerNative(inputs[i], '$value');
                return 'first_filled';
            }
            return 'not_found';
        })();
    """.trimIndent()
    webView.evaluateJavascript(script, null)
}

package com.aaa.orchestrator.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.app.PictureInPictureParams
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
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
    isVisible: Boolean = true,
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

    // Find In Page State
    var isFindInPageVisible by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findMatchCount by remember { mutableStateOf(0) }
    var findActiveMatchIndex by remember { mutableStateOf(0) }

    // MX Player Fullscreen & Video State
    var customVideoView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var showMediaController by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var detectedVideoUrl by remember { mutableStateOf<String?>(null) }

    // MX Player Gesture HUD State
    var gestureHudText by remember { mutableStateOf<String?>(null) }
    var gestureHudIcon by remember { mutableStateOf<ImageVector?>(null) }

    // Audio & Window utilities
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

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

    // Sleek Auto-Pilot HUD State (Collapsed by default for Chrome-like clean browsing)
    var isHudVisible by remember { mutableStateOf(false) }
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

    var pendingWebPermission by remember { mutableStateOf<PermissionRequest?>(null) }
    var fileUploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    LaunchedEffect(isVisible) {
        if (activeWebView != null) {
            activeWebView?.visibility = if (isVisible) View.VISIBLE else View.INVISIBLE
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[android.Manifest.permission.CAMERA] ?: false
        if (cameraGranted) {
            pendingWebPermission?.grant(pendingWebPermission?.resources)
        } else {
            pendingWebPermission?.deny()
            Toast.makeText(context, "Camera permission needed for face verification", Toast.LENGTH_SHORT).show()
        }
        pendingWebPermission = null
    }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val clipData = data?.clipData
            when {
                clipData != null -> (0 until clipData.itemCount).map { clipData.getItemAt(it).uri }.toTypedArray()
                data?.data != null -> arrayOf(data.data!!)
                else -> null
            }
        } else null
        fileUploadCallback?.onReceiveValue(uris)
        fileUploadCallback = null
    }

    fun showGestureFeedback(icon: ImageVector, text: String) {
        gestureHudIcon = icon
        gestureHudText = text
        scope.launch {
            kotlinx.coroutines.delay(1200)
            if (gestureHudText == text) {
                gestureHudText = null
                gestureHudIcon = null
            }
        }
    }

    fun adjustVolume(deltaFraction: Float) {
        try {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val change = (deltaFraction * maxVol).toInt().coerceAtLeast(-maxVol).coerceAtMost(maxVol)
            val newVol = (currentVol + change).coerceIn(0, maxVol)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
            val percent = (newVol * 100) / maxVol
            val icon = if (newVol == 0) Icons.Default.VolumeMute else if (percent > 50) Icons.Default.VolumeUp else Icons.Default.VolumeDown
            showGestureFeedback(icon, "Volume: $percent%")
        } catch (e: Exception) {
            Timber.e(e, "Error adjusting volume")
        }
    }

    fun adjustBrightness(deltaFraction: Float) {
        try {
            val activity = context as? Activity ?: return
            val lp = activity.window.attributes
            val cur = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
            val newBrightness = (cur + deltaFraction).coerceIn(0.05f, 1.0f)
            lp.screenBrightness = newBrightness
            activity.window.attributes = lp
            val percent = (newBrightness * 100).toInt()
            showGestureFeedback(Icons.Default.BrightnessMedium, "Brightness: $percent%")
        } catch (e: Exception) {
            Timber.e(e, "Error adjusting brightness")
        }
    }

    fun seekActiveVideo(offsetSeconds: Int) {
        val sign = if (offsetSeconds > 0) "+" else ""
        val icon = if (offsetSeconds > 0) Icons.Default.FastForward else Icons.Default.FastRewind
        showGestureFeedback(icon, "Seek: $sign${offsetSeconds}s")
        activeWebView?.evaluateJavascript(
            """
            (function() {
                var v = document.querySelector('video');
                if (v) { v.currentTime = Math.max(0, v.currentTime + $offsetSeconds); }
            })();
            """.trimIndent(), null
        )
    }

    fun setVideoSpeed(speed: Float) {
        playbackSpeed = speed
        showGestureFeedback(Icons.Default.Speed, "Speed: ${speed}x")
        activeWebView?.evaluateJavascript(
            """
            (function() {
                var videos = document.querySelectorAll('video');
                for (var i = 0; i < videos.length; i++) {
                    videos[i].playbackRate = $speed;
                }
            })();
            """.trimIndent(), null
        )
    }

    fun triggerVideoPip() {
        val activity = context as? Activity
        activeWebView?.evaluateJavascript(
            """
            (function() {
                var v = document.querySelector('video');
                if (v && v.requestPictureInPicture) {
                    v.requestPictureInPicture();
                    return 'js_pip';
                }
                return 'no_js_pip';
            })();
            """.trimIndent()
        ) { res ->
            if (res?.contains("no_js_pip") == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    activity?.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
                } catch (e: Exception) {
                    Toast.makeText(context, "PiP not supported for this media", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun detectAndDownloadVideo() {
        activeWebView?.evaluateJavascript(
            """
            (function() {
                var v = document.querySelector('video');
                if (!v) return '';
                return v.currentSrc || v.src || (v.querySelector('source') ? v.querySelector('source').src : '');
            })();
            """.trimIndent()
        ) { videoSrc ->
            val cleanUrl = videoSrc?.replace("\"", "")?.trim()
            if (!cleanUrl.isNullOrBlank() && (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://"))) {
                detectedVideoUrl = cleanUrl
                try {
                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val fileName = "video_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) + ".mp4"
                    val request = DownloadManager.Request(Uri.parse(cleanUrl)).apply {
                        setTitle(fileName)
                        setDescription("Downloading stream via MX Media Downloader")
                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS,
                            "AAAX/$fileName"
                        )
                    }
                    dm.enqueue(request)
                    Toast.makeText(context, "Downloading video to Download/AAAX/$fileName", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Timber.e(e, "Error downloading video")
                    Toast.makeText(context, "Failed to download stream: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "No downloadable HTML5 video stream found on current page", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Chrome-Style Hardware Back Navigation: exit fullscreen video -> close find bar -> go back
    BackHandler(enabled = customVideoView != null || isFindInPageVisible || canGoBack) {
        when {
            customVideoView != null -> {
                customViewCallback?.onCustomViewHidden()
                customVideoView = null
                customViewCallback = null
            }
            isFindInPageVisible -> {
                isFindInPageVisible = false
                findQuery = ""
                activeWebView?.clearMatches()
            }
            canGoBack -> {
                activeWebView?.goBack()
            }
        }
    }

    // AdBlock Engine
    val adBlockEngine = remember { AdBlockEngine(context) }

    var videoAspectMode by remember { mutableStateOf("Fit") }

    fun boostAudioVolume() {
        showGestureFeedback(Icons.Default.VolumeUp, "Audio Boost: 200%")
        activeWebView?.evaluateJavascript(
            """
            (function() {
                var v = document.querySelector('video');
                if (v) {
                    try {
                        var AudioContext = window.AudioContext || window.webkitAudioContext;
                        if (AudioContext && !v._audioBoosted) {
                            var ctx = new AudioContext();
                            var src = ctx.createMediaElementSource(v);
                            var gain = ctx.createGain();
                            gain.gain.value = 2.0;
                            src.connect(gain);
                            gain.connect(ctx.destination);
                            v._audioBoosted = true;
                        }
                    } catch(e) {}
                }
            })();
            """.trimIndent(), null
        )
    }

    fun cycleAspectMode() {
        val nextMode = when (videoAspectMode) {
            "Fit" -> "Stretch"
            "Stretch" -> "Zoom"
            else -> "Fit"
        }
        videoAspectMode = nextMode
        showGestureFeedback(Icons.Default.AspectRatio, "Aspect: $nextMode")
        val cssObjectFit = when (nextMode) {
            "Stretch" -> "fill"
            "Zoom" -> "cover"
            else -> "contain"
        }
        activeWebView?.evaluateJavascript(
            """
            (function() {
                var videos = document.querySelectorAll('video');
                for (var i = 0; i < videos.length; i++) {
                    videos[i].style.objectFit = '$cssObjectFit';
                }
            })();
            """.trimIndent(), null
        )
    }

    // Geolocation Privacy Spoof Script (Frankfurt Gateway)
    val geoPrivacyScript = """
        (function() {
            try {
                var fakeCoords = {
                    latitude: 50.1109,
                    longitude: 8.6821,
                    accuracy: 35.0,
                    altitude: null,
                    altitudeAccuracy: null,
                    heading: null,
                    speed: null
                };
                var fakePos = { coords: fakeCoords, timestamp: Date.now() };
                var fakeGeo = {
                    getCurrentPosition: function(s, e, o) { 
                        if (typeof s === 'function') {
                            setTimeout(function() { s(fakePos); }, 20);
                        } 
                    },
                    watchPosition: function(s, e, o) { 
                        if (typeof s === 'function') {
                            setTimeout(function() { s(fakePos); }, 20);
                        } 
                        return 101; 
                    },
                    clearWatch: function(id) {}
                };
                try {
                    Object.defineProperty(navigator, 'geolocation', {
                        get: function() { return fakeGeo; },
                        configurable: true
                    });
                } catch(e) {
                    try {
                        navigator.geolocation.getCurrentPosition = fakeGeo.getCurrentPosition;
                        navigator.geolocation.watchPosition = fakeGeo.watchPosition;
                        navigator.geolocation.clearWatch = fakeGeo.clearWatch;
                    } catch(e2) {}
                }
                if (navigator.permissions && navigator.permissions.query) {
                    var origQuery = navigator.permissions.query.bind(navigator.permissions);
                    navigator.permissions.query = function(desc) {
                        if (desc && desc.name === 'geolocation') {
                            return Promise.resolve({ state: 'granted', onchange: null });
                        }
                        return origQuery(desc);
                    };
                }
            } catch(err) {}
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
        val targetPhone = if (phoneNumber.isNotBlank()) phoneNumber else "+48459074091"
        val script = TwitterAutoPilot.buildAutoPilotScript(
            name = currentProfileName.value,
            phone = targetPhone,
            birthMonth = currentBirthDate.value.month,
            birthDay = currentBirthDate.value.day,
            birthYear = currentBirthDate.value.year,
            password = password,
            otp = latestOtp
        )
        activeWebView?.evaluateJavascript(script) { result ->
            Timber.i("runAutoPilotOnActiveTab result: $result")
        }
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
                setGeolocationEnabled(false)
            }

            visibility = View.INVISIBLE

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
                    if (newProgress >= 70) {
                        val currentWebUrl = view?.url ?: ""
                        if (currentWebUrl.contains("signup") || currentWebUrl.contains("flow") || currentWebUrl.contains("x.com")) {
                            val targetPhone = if (phoneNumber.isNotBlank()) phoneNumber else "+48459074091"
                            val script = TwitterAutoPilot.buildAutoPilotScript(
                                name = currentProfileName.value,
                                phone = targetPhone,
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

                override fun onPermissionRequest(request: PermissionRequest?) {
                    // Enable camera & audio for Twitter / security identity check
                    val reqResources = request?.resources ?: emptyArray()
                    val activity = ctx as? android.app.Activity
                    if (activity != null) {
                        activity.runOnUiThread {
                            val hasCamera = ctx.checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            val hasAudio = ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (hasCamera && hasAudio) {
                                request?.grant(reqResources)
                            } else {
                                pendingWebPermission = request
                                cameraLauncher.launch(
                                    arrayOf(
                                        android.Manifest.permission.CAMERA,
                                        android.Manifest.permission.RECORD_AUDIO
                                    )
                                )
                            }
                        }
                    } else {
                        request?.grant(reqResources)
                    }
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    // Enable selfie/photo and document upload for verification
                    fileUploadCallback?.onReceiveValue(null)
                    fileUploadCallback = filePathCallback
                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "image/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    return try {
                        fileChooserLauncher.launch(intent)
                        true
                    } catch (e: Exception) {
                        fileUploadCallback?.onReceiveValue(null)
                        fileUploadCallback = null
                        false
                    }
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

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    customVideoView = view
                    customViewCallback = callback
                }

                override fun onHideCustomView() {
                    customViewCallback?.onCustomViewHidden()
                    customVideoView = null
                    customViewCallback = null
                }
            }

            // Find in Page listener
            setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                findActiveMatchIndex = activeMatchOrdinal
                findMatchCount = numberOfMatches
            }

            // Client with Titanium AdBlock and AutoPilot injection
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    Timber.w("WebView onReceivedError: ${error?.description} on ${request?.url}")
                }

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

                    // Update the tab's url and title so tab switcher stays accurate
                    val tabIndex = tabs.indexOfFirst { it.id == tab.id }
                    if (tabIndex != -1 && url != null) {
                        val updated = tabs.toMutableList()
                        val currentTitle = view?.title ?: updated[tabIndex].title
                        updated[tabIndex] = updated[tabIndex].copy(url = url, title = currentTitle)
                        tabs = updated
                    }

                    // Apply cosmetic ad hiding
                    adBlockEngine.injectCosmeticAdHiding(view)

                    // Inject location privacy spoof again for SPA navigation
                    view?.evaluateJavascript(geoPrivacyScript, null)

                    // Inject Twitter AutoPilot if on signup/flow/challenge
                    if (url != null && (url.contains("signup") || url.contains("flow") || url.contains("challenge") || url.contains("x.com"))) {
                        val targetPhone = if (phoneNumber.isNotBlank()) phoneNumber else "+48459074091"
                        val script = TwitterAutoPilot.buildAutoPilotScript(
                            name = currentProfileName.value,
                            phone = targetPhone,
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

            // Get or create new webview
            val newWebView = webViewPool[newTab.id] ?: run {
                val created = createConfiguredWebView(context, newTab)
                webViewPool[newTab.id] = created
                containerLayout?.addView(created)
                created
            }

            // Ensure properly attached
            if (newWebView.parent != containerLayout) {
                (newWebView.parent as? ViewGroup)?.removeView(newWebView)
                containerLayout?.addView(newWebView)
            }

            // Manage visibility cleanly: ACTIVE is VISIBLE, all others are INVISIBLE (preserving layout!)
            webViewPool.forEach { (tabId, wv) ->
                if (tabId == newTab.id) {
                    wv.visibility = View.VISIBLE
                    wv.bringToFront()
                } else {
                    wv.visibility = View.INVISIBLE
                }
            }

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

                        // URL / Search Input Field using BasicTextField for vertically-centered crisp text
                        BasicTextField(
                            value = inputUrl,
                            onValueChange = { inputUrl = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Normal
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
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (inputUrl.isEmpty()) {
                                        Text(
                                            text = "Search or type URL",
                                            fontSize = 13.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            maxLines = 1
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        // Clear or Refresh Button inside Omnibox
                        if (inputUrl != currentUrl && inputUrl.isNotEmpty()) {
                            IconButton(
                                onClick = { inputUrl = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else if (isLoading) {
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
                        // Chrome Quick Action Row: Back, Forward, Bookmark, Reload, Share
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    showMenu = false
                                    activeWebView?.goBack()
                                },
                                enabled = canGoBack,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    showMenu = false
                                    activeWebView?.goForward()
                                },
                                enabled = canGoForward,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.ArrowForward,
                                    contentDescription = "Forward",
                                    tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
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
                                    showMenu = false
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Bookmark",
                                    tint = if (isBookmarked) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    showMenu = false
                                    activeWebView?.reload()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Reload",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    showMenu = false
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
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        HorizontalDivider()

                        DropdownMenuItem(
                            text = { Text("New tab") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                addNewTab("https://x.com/i/flow/signup")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Find in page") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryBlue) },
                            onClick = {
                                showMenu = false
                                isFindInPageVisible = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("MX Media Controls") },
                            leadingIcon = { Icon(Icons.Default.PlayCircle, contentDescription = null, tint = PrimaryBlue) },
                            onClick = {
                                showMenu = false
                                showMediaController = true
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
        // 1.5 CHROME-STYLE FIND IN PAGE BAR
        // ==========================================
        AnimatedVisibility(visible = isFindInPageVisible) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Find",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    BasicTextField(
                        value = findQuery,
                        onValueChange = { query ->
                            findQuery = query
                            if (query.isNotEmpty()) {
                                activeWebView?.findAllAsync(query)
                            } else {
                                activeWebView?.clearMatches()
                                findMatchCount = 0
                                findActiveMatchIndex = 0
                            }
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        decorationBox = { inner ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (findQuery.isEmpty()) {
                                    Text(
                                        text = "Find in page...",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                inner()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    if (findQuery.isNotEmpty()) {
                        Text(
                            text = if (findMatchCount > 0) "${findActiveMatchIndex + 1}/$findMatchCount" else "0/0",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }

                    IconButton(
                        onClick = { activeWebView?.findNext(false) },
                        enabled = findMatchCount > 0,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous", modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = { activeWebView?.findNext(true) },
                        enabled = findMatchCount > 0,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next", modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = {
                            isFindInPageVisible = false
                            findQuery = ""
                            activeWebView?.clearMatches()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
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

                        // Initialize or reuse initial tab webview
                        val initialTab = tabs.getOrNull(activeTabIndex) ?: tabs[0]
                        val existingWv = webViewPool[initialTab.id]
                        val initialWebView = if (existingWv != null) {
                            (existingWv.parent as? ViewGroup)?.removeView(existingWv)
                            existingWv
                        } else {
                            val created = createConfiguredWebView(ctx, initialTab)
                            webViewPool[initialTab.id] = created
                            created
                        }
                        addView(initialWebView)
                        initialWebView.visibility = View.VISIBLE
                        activeWebView = initialWebView
                    }
                },
                update = { layout ->
                    containerLayout = layout
                    // Ensure all webviews in pool remain attached without reparenting errors
                    webViewPool.values.forEach { wv ->
                        if (wv.parent != layout) {
                            (wv.parent as? ViewGroup)?.removeView(wv)
                            layout.addView(wv)
                        }
                    }
                    val currentTab = tabs.getOrNull(activeTabIndex)
                    if (currentTab != null) {
                        val active = webViewPool[currentTab.id]
                        if (active != null && active.visibility != View.VISIBLE) {
                            active.visibility = View.VISIBLE
                            active.bringToFront()
                        }
                    }
                }
            )

            // Floating 1-Tap AutoFill Registration Pill
            val isTwitterSignup = currentUrl.contains("signup") || currentUrl.contains("flow") || currentUrl.contains("x.com")
            if (isTwitterSignup && !isFindInPageVisible && customVideoView == null) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = PrimaryBlue,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clickable {
                                runAutoPilotOnActiveTab()
                                Toast.makeText(context, "Executing AutoFill on registration form...", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = "AutoFill", tint = SurfaceWhite, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AutoFill Form",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SurfaceWhite
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = if (phoneNumber.isNotBlank()) phoneNumber else "+48459074091",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SurfaceWhite,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Gesture HUD Indicator Pill
            if (gestureHudText != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        gestureHudIcon?.let { icon ->
                            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = gestureHudText ?: "",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
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
    // 7. CLOUD PHONE NUMBER EDIT DIALOG
    // ==========================================
    if (showPhoneEditDialog) {
        AlertDialog(
            onDismissRequest = { showPhoneEditDialog = false },
            title = { Text("Active Cloud Phone Number") },
            text = {
                Column {
                    Text(
                        "Set the active phone number for automated signup:",
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
    // 7.5 MX PLAYER MEDIA CONTROLLER DIALOG
    // ==========================================
    if (showMediaController) {
        AlertDialog(
            onDismissRequest = { showMediaController = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("MX Media & Video Tools", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Playback Speed", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            FilterChip(
                                selected = playbackSpeed == speed,
                                onClick = { setVideoSpeed(speed) },
                                label = { Text("${speed}x", fontSize = 10.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Media Controls", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // 200% Audio Volume Boost Button
                    OutlinedButton(
                        onClick = {
                            boostAudioVolume()
                            showMediaController = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp), tint = SuccessGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("200% Audio Volume Boost", fontSize = 12.sp, color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Video Aspect Ratio Toggle (Fit / Stretch / Zoom)
                    OutlinedButton(
                        onClick = {
                            cycleAspectMode()
                            showMediaController = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.AspectRatio, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Aspect Ratio: $videoAspectMode", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // PiP Button
                    OutlinedButton(
                        onClick = {
                            triggerVideoPip()
                            showMediaController = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PictureInPicture, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Picture-in-Picture (PiP)", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 1-Tap Stream Downloader
                    Button(
                        onClick = {
                            detectAndDownloadVideo()
                            showMediaController = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download Video Stream", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMediaController = false }) {
                    Text("Done")
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

    // ==========================================
    // 9. FULLSCREEN MX PLAYER VIDEO OVERLAY
    // ==========================================
    if (customVideoView != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    var startSideIsLeft = true
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            startSideIsLeft = offset.x < size.width / 2
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val deltaFraction = -dragAmount / size.height.toFloat()
                            if (startSideIsLeft) {
                                adjustBrightness(deltaFraction * 1.5f)
                            } else {
                                adjustVolume(deltaFraction * 1.5f)
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            if (offset.x < size.width * 0.35f) {
                                seekActiveVideo(-10)
                            } else if (offset.x > size.width * 0.65f) {
                                seekActiveVideo(10)
                            } else {
                                activeWebView?.evaluateJavascript(
                                    "var v = document.querySelector('video'); if (v) { if (v.paused) v.play(); else v.pause(); }",
                                    null
                                )
                                showGestureFeedback(Icons.Default.PlayArrow, "Play / Pause")
                            }
                        }
                    )
                }
        ) {
            AndroidView(
                factory = { customVideoView!! },
                modifier = Modifier.fillMaxSize()
            )

            // Top Bar Controls Overlay for Fullscreen Video
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Exit Fullscreen Button
                IconButton(
                    onClick = {
                        customViewCallback?.onCustomViewHidden()
                        customVideoView = null
                        customViewCallback = null
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Exit Fullscreen", tint = Color.White)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Speed Toggle
                    FilledTonalButton(
                        onClick = {
                            val nextSpeed = when (playbackSpeed) {
                                1.0f -> 1.25f
                                1.25f -> 1.5f
                                1.5f -> 2.0f
                                2.0f -> 0.5f
                                else -> 1.0f
                            }
                            setVideoSpeed(nextSpeed)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("${playbackSpeed}x", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // PiP Button
                    IconButton(
                        onClick = { triggerVideoPip() },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.PictureInPicture, contentDescription = "PiP", tint = Color.White)
                    }
                }
            }

            // Gesture HUD Pill Center Screen
            if (gestureHudText != null) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        gestureHudIcon?.let { icon ->
                            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            text = gestureHudText ?: "",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
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

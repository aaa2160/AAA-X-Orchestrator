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
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.text.format.Formatter
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.core.content.FileProvider
import com.aaa.orchestrator.data.model.OrchestratorState
import com.aaa.orchestrator.engine.AccountProfileGenerator
import com.aaa.orchestrator.engine.AdBlockEngine
import com.aaa.orchestrator.engine.FaceVerificationNotifier
import com.aaa.orchestrator.engine.OrchestratorEngine
import com.aaa.orchestrator.engine.TwitterAutoPilot
import com.aaa.orchestrator.ui.theme.*
import com.aaa.orchestrator.engine.BrowserTabManager
import com.aaa.orchestrator.engine.BrowserTab
import com.aaa.orchestrator.engine.HistoryItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.random.Random

data class GeoLocationPreset(
    val id: String,
    val name: String,
    val flag: String,
    val city: String,
    val country: String,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val timeZone: String
)

val locationPresets = listOf(
    GeoLocationPreset("random", "Random Global Hub (Auto)", "🎲", "Random Global", "Worldwide", "us", 0.0, 0.0, "UTC"),
    GeoLocationPreset("nyc", "New York, USA", "🗽", "New York, NY", "United States", "us", 40.7128, -74.0060, "America/New_York"),
    GeoLocationPreset("lon", "London, UK", "🏰", "London", "United Kingdom", "gb", 51.5074, -0.1278, "Europe/London"),
    GeoLocationPreset("tky", "Tokyo, Japan", "🗾", "Tokyo", "Japan", "jp", 35.6762, 139.6503, "Asia/Tokyo"),
    GeoLocationPreset("fra", "Frankfurt, Germany", "🏦", "Frankfurt", "Germany", "de", 50.1109, 8.6821, "Europe/Berlin"),
    GeoLocationPreset("zrh", "Zurich, Switzerland", "🏔️", "Zurich", "Switzerland", "ch", 47.3769, 8.5417, "Europe/Zurich"),
    GeoLocationPreset("sin", "Singapore", "🇸🇬", "Singapore", "Singapore", "sg", 1.3521, 103.8198, "Asia/Singapore"),
    GeoLocationPreset("rey", "Reykjavik, Iceland", "🌋", "Reykjavik", "Iceland", "is", 64.1466, -21.9426, "Atlantic/Reykjavik")
)

data class DownloadedFileItem(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isMedia: Boolean
)

fun buildGeoPrivacyScript(preset: GeoLocationPreset): String {
    val activePreset = if (preset.id == "random") {
        val candidates = locationPresets.filter { it.id != "random" }
        val candidate = candidates.random()
        val jitterLat = (Random.nextDouble() - 0.5) * 0.03
        val jitterLon = (Random.nextDouble() - 0.5) * 0.03
        candidate.copy(
            latitude = candidate.latitude + jitterLat,
            longitude = candidate.longitude + jitterLon
        )
    } else {
        preset
    }

    val tz = java.util.TimeZone.getTimeZone(activePreset.timeZone)
    val offsetMinutes = -(tz.getOffset(System.currentTimeMillis()) / (60 * 1000))

    return """
        (function() {
            try {
                // 1. Spoof HTML5 Geolocation API
                var fakeCoords = {
                    latitude: ${activePreset.latitude},
                    longitude: ${activePreset.longitude},
                    accuracy: 15.0 + Math.random() * 10.0,
                    altitude: 35.0,
                    altitudeAccuracy: 5.0,
                    heading: null,
                    speed: null
                };
                var fakePos = { coords: fakeCoords, timestamp: Date.now() };
                var fakeGeo = {
                    getCurrentPosition: function(success, error, options) {
                        if (typeof success === 'function') {
                            setTimeout(function() { success(fakePos); }, 20);
                        }
                    },
                    watchPosition: function(success, error, options) {
                        if (typeof success === 'function') {
                            setTimeout(function() { success(fakePos); }, 20);
                        }
                        return 201;
                    },
                    clearWatch: function(id) {}
                };

                try {
                    Object.defineProperty(navigator, 'geolocation', {
                        get: function() { return fakeGeo; },
                        set: function() {},
                        configurable: true
                    });
                } catch(e) {
                    try {
                        navigator.geolocation.getCurrentPosition = fakeGeo.getCurrentPosition;
                        navigator.geolocation.watchPosition = fakeGeo.watchPosition;
                    } catch(e2) {}
                }

                // 2. Spoof Permissions API for Geolocation
                if (navigator.permissions && navigator.permissions.query) {
                    var origQuery = navigator.permissions.query.bind(navigator.permissions);
                    navigator.permissions.query = function(desc) {
                        if (desc && desc.name === 'geolocation') {
                            return Promise.resolve({ state: 'granted', onchange: null });
                        }
                        return origQuery(desc);
                    };
                }

                // 3. Spoof Timezone and Locale APIs
                try {
                    var origResolved = Intl.DateTimeFormat.prototype.resolvedOptions;
                    Intl.DateTimeFormat.prototype.resolvedOptions = function() {
                        var res = origResolved.call(this);
                        res.timeZone = '${activePreset.timeZone}';
                        res.locale = 'en-US';
                        return res;
                    };
                } catch(e) {}

                try {
                    Date.prototype.getTimezoneOffset = function() {
                        return $offsetMinutes;
                    };
                } catch(e) {}

                try {
                    Object.defineProperty(navigator, 'languages', {
                        get: function() { return ['en-US', 'en']; },
                        configurable: true
                    });
                    Object.defineProperty(navigator, 'language', {
                        get: function() { return 'en-US'; },
                        configurable: true
                    });
                } catch(e) {}

                // 4. Strip WebRTC STUN/TURN Candidate Gathering to Prevent Public IP Leak
                try {
                    if (window.RTCPeerConnection) {
                        var origRTCPeerConnection = window.RTCPeerConnection;
                        window.RTCPeerConnection = function(config, constraints) {
                            if (config && config.iceServers) {
                                config.iceServers = [];
                            }
                            return new origRTCPeerConnection(config, constraints);
                        };
                        window.RTCPeerConnection.prototype = origRTCPeerConnection.prototype;
                    }
                } catch(e) {}

                // 5. Active DOM Masking for Search Engine IP and Location Footers
                function maskSearchFooters() {
                    var selectors = [
                        '#footcnt', '#fbar', '.fbar', '#swml', '#swml-loc',
                        'div[role="contentinfo"]', '.b2hzbe', '.W8L43c', '.K2Pfre',
                        'span.Q8LRLc', 'div.b0KoTc', '.u7387', 'div[data-sokoban-container] div.fbar'
                    ];
                    var elements = document.querySelectorAll(selectors.join(','));
                    for (var i = 0; i < elements.length; i++) {
                        var el = elements[i];
                        if (el && !el.getAttribute('data-shielded')) {
                            var text = el.innerText || '';
                            if (text.indexOf('IP') !== -1 || text.indexOf('location') !== -1 || text.indexOf('From') !== -1 || text.indexOf('Update') !== -1 || text.indexOf('precise') !== -1) {
                                el.setAttribute('data-shielded', 'true');
                                el.innerHTML = '<div style="padding:10px 14px;font-size:12px;color:#10B981;text-align:center;font-weight:600;background:rgba(16,185,129,0.09);border:1px solid rgba(16,185,129,0.25);border-radius:10px;margin:8px auto;max-width:600px;">🛡️ Antigravity Privacy Shield: Virtual Location Active: ${activePreset.city}, ${activePreset.country} (${activePreset.timeZone}) • Real IP & GPS Concealed</div>';
                            }
                        }
                    }
                }

                if (window.location.hostname.indexOf('google.') !== -1 || window.location.hostname.indexOf('bing.') !== -1) {
                    maskSearchFooters();
                    setInterval(maskSearchFooters, 1000);
                }
            } catch(err) {}
        })();
    """.trimIndent()
}

fun formatVideoTime(seconds: Int): String {
    val totalSecs = seconds.coerceAtLeast(0)
    val hrs = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return if (hrs > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }
}

fun scanDownloadedFiles(context: Context): List<DownloadedFileItem> {
    val list = mutableListOf<DownloadedFileItem>()
    try {
        val dirs = listOf(
            File("/storage/emulated/0/Download/AAAX"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        )
        val seen = mutableSetOf<String>()
        for (dir in dirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && seen.add(file.absolutePath)) {
                        val name = file.name
                        val ext = file.extension.lowercase()
                        val isMedia = ext in listOf("mp4", "webm", "mkv", "avi", "mov", "mp3", "m4a", "wav", "aac", "flac")
                        list.add(
                            DownloadedFileItem(
                                file = file,
                                name = name,
                                sizeBytes = file.length(),
                                lastModified = file.lastModified(),
                                isMedia = isMedia
                            )
                        )
                    }
                }
            }
        }
        list.sortByDescending { it.lastModified }
    } catch (e: Exception) {
        Timber.e(e, "Error scanning downloads")
    }
    return list
}

fun openDownloadedFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val ext = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open with..."))
    } catch (e: Exception) {
        Timber.e(e, "Error opening downloaded file")
        Toast.makeText(context, "Could not open file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun shareDownloadedFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val ext = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share file"))
    } catch (e: Exception) {
        Timber.e(e, "Error sharing file")
        Toast.makeText(context, "Could not share file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun deleteDownloadedFile(context: Context, file: File, onDeleted: () -> Unit) {
    try {
        val path = file.absolutePath
        if (file.delete()) {
            MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
            Toast.makeText(context, "File deleted", Toast.LENGTH_SHORT).show()
            onDeleted()
        }
    } catch (e: Exception) {
        Timber.e(e, "Error deleting file")
    }
}

fun downloadStreamUrl(context: Context, url: String) {
    try {
        val targetDir = File("/storage/emulated/0/Download/AAAX")
        if (!targetDir.exists()) targetDir.mkdirs()
        val guessedName = URLUtil.guessFileName(url, null, "video/mp4")
        val filename = if (!guessedName.contains(".")) "$guessedName.mp4" else guessedName

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setMimeType("video/mp4")
            val cookies = CookieManager.getInstance().getCookie(url)
            addRequestHeader("cookie", cookies)
            setDescription("Downloading video stream")
            setTitle(filename)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "AAAX/$filename"
            )
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        Toast.makeText(context, "📥 Downloading: $filename", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Timber.e(e, "Error downloading stream")
        Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun sniffWebVideos(webView: WebView?, onFound: (List<String>) -> Unit) {
    val script = """
        (function() {
            var media = [];
            var nodes = document.querySelectorAll('video, video source, audio, audio source');
            for (var i = 0; i < nodes.length; i++) {
                var s = nodes[i].currentSrc || nodes[i].src;
                if (s && s.length > 5 && media.indexOf(s) === -1) {
                    media.push(s);
                }
            }
            return JSON.stringify(media);
        })();
    """.trimIndent()
    webView?.evaluateJavascript(script) { result ->
        try {
            if (result != null && result != "null" && result.length > 4) {
                val cleaned = if (result.startsWith("\"") && result.endsWith("\"")) {
                    org.json.JSONTokener(result).nextValue().toString()
                } else result
                val jsonArray = org.json.JSONArray(cleaned)
                val urls = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) {
                    urls.add(jsonArray.getString(i))
                }
                onFound(urls)
            } else {
                onFound(emptyList())
            }
        } catch (e: Exception) {
            onFound(emptyList())
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    engine: OrchestratorEngine? = null,
    isAutomationMode: Boolean = false,
    activeUrl: String = if (isAutomationMode) "https://x.com/i/flow/signup" else "https://www.google.com",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Hoisted Multi-Tab State from persistent BrowserTabManager based on mode
    val tabs = remember(isAutomationMode) { BrowserTabManager.getTabs(isAutomationMode) }
    var activeTabIndex by remember(isAutomationMode) { BrowserTabManager.getActiveIndex(isAutomationMode) }
    var showTabSwitcher by remember { mutableStateOf(false) }

    // Navigation & Web State
    val initialTab = BrowserTabManager.getCurrentTab(isAutomationMode) ?: tabs.firstOrNull() ?: BrowserTab(url = activeUrl, title = if (isAutomationMode) "X Signup Bot" else "Google")
    var currentUrl by remember(isAutomationMode) { mutableStateOf(initialTab.url) }
    var inputUrl by remember(isAutomationMode) { mutableStateOf(initialTab.url) }
    var pageTitle by remember(isAutomationMode) { mutableStateOf(initialTab.title) }
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
    var currentAspectRatioIndex by remember { mutableStateOf(0) }
    val aspectRatios = listOf("fit", "fill", "16:9", "4:3", "stretch")
    var isAudioBoosted by remember { mutableStateOf(false) }
    var isScreenLocked by remember { mutableStateOf(false) }

    // Location Privacy Shield State
    var selectedLocationPreset by remember { mutableStateOf(locationPresets[0]) }
    var showLocationDialog by remember { mutableStateOf(false) }

    // Downloads & Media Stream Sniffer State
    var detectedMediaUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var showDownloadsDialog by remember { mutableStateOf(false) }
    var showDetectedStreamsDialog by remember { mutableStateOf(false) }

    // Professional Browser Tools State
    var isReaderModeActive by remember { mutableStateOf(false) }
    var isForceDarkMode by remember { mutableStateOf(false) }
    var showSearchEngineDialog by remember { mutableStateOf(false) }
    var selectedSearchEngine by remember { mutableStateOf("Google") }
    val searchEngines = remember(selectedLocationPreset) {
        val cc = if (selectedLocationPreset.id == "random" || selectedLocationPreset.countryCode.isBlank()) "us" else selectedLocationPreset.countryCode
        mapOf(
            "Google" to "https://www.google.com/search?q={query}&gl=$cc&hl=en&pws=0",
            "DuckDuckGo" to "https://duckduckgo.com/?q={query}",
            "Brave Search" to "https://search.brave.com/search?q={query}",
            "Bing" to "https://www.bing.com/search?q={query}&cc=$cc",
            "Ecosia" to "https://www.ecosia.org/search?q={query}"
        )
    }

    // MX Player Gesture HUD State
    var gestureHudText by remember { mutableStateOf<String?>(null) }
    var gestureHudIcon by remember { mutableStateOf<ImageVector?>(null) }

    // Audio & Window utilities
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    // Tools & Bookmarks
    var showBookmarksHistory by remember { mutableStateOf(false) }
    val bookmarks = BrowserTabManager.bookmarks
    val history = BrowserTabManager.history

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
    val phoneNumber by (engine?.activePhoneNumber ?: remember { MutableStateFlow("") }).collectAsState()
    val password by (engine?.activePassword ?: remember { MutableStateFlow("AAA_Auto_2026") }).collectAsState()
    val latestOtp by (engine?.latestOtp ?: remember { MutableStateFlow<String?>(null) }).collectAsState()
    val proxyCountry by (engine?.proxyCountry ?: remember { MutableStateFlow("DE") }).collectAsState()

    val currentProfileName = remember { mutableStateOf(AccountProfileGenerator.generateFullName()) }
    val currentBirthDate = remember { mutableStateOf(AccountProfileGenerator.generateBirthDate()) }

    // Active WebView reference & Container reference for multi-tab management
    var activeWebView by remember(isAutomationMode) { mutableStateOf<WebView?>(null) }
    val webViewPool = remember(isAutomationMode) { BrowserTabManager.getPool(isAutomationMode) }
    var containerLayout by remember { mutableStateOf<FrameLayout?>(null) }

    var pendingWebPermission by remember { mutableStateOf<PermissionRequest?>(null) }
    var fileUploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = (permissions[android.Manifest.permission.CAMERA] ?: false) ||
                context.checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val audioGranted = (permissions[android.Manifest.permission.RECORD_AUDIO] ?: false) ||
                context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val grantedResources = mutableListOf<String>()
        pendingWebPermission?.resources?.forEach { res ->
            if (res == PermissionRequest.RESOURCE_VIDEO_CAPTURE && cameraGranted) {
                grantedResources.add(res)
            } else if (res == PermissionRequest.RESOURCE_AUDIO_CAPTURE && audioGranted) {
                grantedResources.add(res)
            }
        }

        if (grantedResources.isNotEmpty()) {
            pendingWebPermission?.grant(grantedResources.toTypedArray())
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

    fun cycleAspectRatio() {
        currentAspectRatioIndex = (currentAspectRatioIndex + 1) % aspectRatios.size
        val mode = aspectRatios[currentAspectRatioIndex]
        val (cssMode, label) = when (mode) {
            "fit" -> Pair("contain", "Fit Screen")
            "fill" -> Pair("cover", "Crop / Fill")
            "16:9" -> Pair("contain", "16:9 Aspect")
            "4:3" -> Pair("contain", "4:3 Aspect")
            "stretch" -> Pair("fill", "Stretch Full")
            else -> Pair("contain", "Fit")
        }
        showGestureFeedback(Icons.Default.AspectRatio, "Aspect: $label")
        activeWebView?.evaluateJavascript(
            """
            (function() {
                var videos = document.querySelectorAll('video');
                for (var i = 0; i < videos.length; i++) {
                    videos[i].style.objectFit = '$cssMode';
                }
            })();
            """.trimIndent(), null
        )
    }

    fun toggleAudioBoost() {
        isAudioBoosted = !isAudioBoosted
        val gain = if (isAudioBoosted) 2.0f else 1.0f
        val label = if (isAudioBoosted) "Audio Boost: 200%" else "Audio Boost: 100%"
        showGestureFeedback(Icons.Default.VolumeUp, label)
        activeWebView?.evaluateJavascript(
            """
            (function() {
                var v = document.querySelector('video') || document.querySelector('audio');
                if (!v) return;
                try {
                    if (!window._audioBoostCtx) {
                        window._audioBoostCtx = new (window.AudioContext || window.webkitAudioContext)();
                        window._audioSource = window._audioBoostCtx.createMediaElementSource(v);
                        window._audioGain = window._audioBoostCtx.createGain();
                        window._audioSource.connect(window._audioGain);
                        window._audioGain.connect(window._audioBoostCtx.destination);
                    }
                    window._audioGain.gain.value = $gain;
                } catch(e) { console.log('Audio boost error', e); }
            })();
            """.trimIndent(), null
        )
    }

    fun toggleReaderMode() {
        isReaderModeActive = !isReaderModeActive
        if (isReaderModeActive) {
            activeWebView?.evaluateJavascript(
                """
                (function() {
                    if (window._originalBody) return;
                    window._originalBody = document.body.innerHTML;
                    var article = document.querySelector('article') || document.querySelector('main') || document.body;
                    var title = document.title;
                    var paragraphs = article.querySelectorAll('p, h1, h2, h3, img');
                    var content = '';
                    paragraphs.forEach(function(el) {
                        if (el.tagName === 'IMG') {
                            if (el.src) content += '<img src="' + el.src + '" style="max-width:100%;border-radius:12px;margin:16px 0;" />';
                        } else {
                            content += '<' + el.tagName.toLowerCase() + '>' + el.innerHTML + '</' + el.tagName.toLowerCase() + '>';
                        }
                    });
                    document.body.innerHTML = '<div id="clean-reader-view" style="max-width:680px;margin:0 auto;padding:24px 16px;font-family:sans-serif;font-size:18px;line-height:1.7;color:#1e293b;background:#fafafa;"><h1>' + title + '</h1>' + content + '</div>';
                })();
                """.trimIndent(), null
            )
            Toast.makeText(context, "Reader Mode Activated", Toast.LENGTH_SHORT).show()
        } else {
            activeWebView?.evaluateJavascript(
                """
                (function() {
                    if (window._originalBody) {
                        document.body.innerHTML = window._originalBody;
                        window._originalBody = null;
                    }
                })();
                """.trimIndent(), null
            )
            Toast.makeText(context, "Reader Mode Deactivated", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleForceDarkMode() {
        isForceDarkMode = !isForceDarkMode
        activeWebView?.evaluateJavascript(
            """
            (function() {
                var el = document.getElementById('force-dark-mode-style');
                if (el) {
                    el.remove();
                } else {
                    var style = document.createElement('style');
                    style.id = 'force-dark-mode-style';
                    style.innerHTML = 'html { filter: invert(90%) hue-rotate(180deg) !important; background: #121212 !important; } img, video, iframe, canvas { filter: invert(100%) hue-rotate(180deg) !important; }';
                    document.head.appendChild(style);
                }
            })();
            """.trimIndent(), null
        )
        Toast.makeText(context, if (isForceDarkMode) "Dark Reader: ON" else "Dark Reader: OFF", Toast.LENGTH_SHORT).show()
    }

    fun clearSiteData() {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies(null)
        cm.flush()
        WebStorage.getInstance().deleteAllData()
        activeWebView?.clearCache(true)
        activeWebView?.clearFormData()
        Toast.makeText(context, "Site cookies, cache, and storage cleared", Toast.LENGTH_SHORT).show()
    }

    // Chrome-Style Hardware Back Navigation: exit fullscreen video -> close find bar -> go back
    BackHandler(enabled = customVideoView != null || isFindInPageVisible || canGoBack) {
        when {
            customVideoView != null -> {
                (context as? Activity)?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                (context as? Activity)?.window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
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

    // Dynamic Geolocation Privacy Spoof Script (Active Gateway)
    fun getGeoPrivacyScript(): String = buildGeoPrivacyScript(selectedLocationPreset)

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
        if (phoneNumber.isBlank()) {
            Toast.makeText(context, "Awaiting Telegram Bot Phone Number (@EHR_QUICKINCOME_BOT)", Toast.LENGTH_SHORT).show()
            phoneInputText = ""
            showPhoneEditDialog = true
            return
        }
        val script = TwitterAutoPilot.buildAutoPilotScript(
            name = currentProfileName.value,
            phone = phoneNumber,
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

    // Auto-trigger AutoPilot scan when a new verified phone number arrives
    LaunchedEffect(phoneNumber) {
        if (isAutomationMode && phoneNumber.isNotBlank()) {
            Timber.i("New verified bot phone detected: $phoneNumber, executing AutoPilot on active tab")
            runAutoPilotOnActiveTab()
        }
    }

    // Auto-inject incoming OTP into Twitter signup form instantly and advance
    LaunchedEffect(latestOtp) {
        if (isAutomationMode && !latestOtp.isNullOrBlank()) {
            injectValueIntoInput(activeWebView, latestOtp!!, "otp")
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
                setGeolocationEnabled(true)
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
                    val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
                    val targetDir = File("/storage/emulated/0/Download/AAAX")
                    if (!targetDir.exists()) targetDir.mkdirs()

                    val request = DownloadManager.Request(Uri.parse(url)).apply {
                        setMimeType(mimetype)
                        val cookies = CookieManager.getInstance().getCookie(url)
                        addRequestHeader("cookie", cookies)
                        addRequestHeader("User-Agent", userAgent)
                        setDescription("Downloading via Antigravity Browser")
                        setTitle(filename)
                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS,
                            "AAAX/$filename"
                        )
                    }
                    val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    dm.enqueue(request)
                    Toast.makeText(ctx, "📥 Download started: $filename", Toast.LENGTH_SHORT).show()
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

                    // Early and recurring location privacy injection across DOM render cycles
                    if (newProgress in listOf(5, 15, 30, 60, 95, 100)) {
                        view?.evaluateJavascript(getGeoPrivacyScript(), null)
                    }

                    if (isAutomationMode && newProgress >= 70) {
                        val currentWebUrl = view?.url ?: ""
                        if (currentWebUrl.contains("signup") || currentWebUrl.contains("flow") || currentWebUrl.contains("x.com")) {
                            if (phoneNumber.isNotBlank()) {
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
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (title != null) {
                        if (activeWebView == view) {
                            pageTitle = title
                        }
                        val index = tabs.indexOfFirst { it.id == tab.id }
                        if (index != -1) {
                            tabs[index].title = title
                        }
                    }
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    // Grant permission to website so it doesn't fail or fall back to crude IP geolocation,
                    // while our injected navigator.geolocation mock safely supplies the spoofed virtual coordinates!
                    callback?.invoke(origin, true, false)
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    // Enable camera & audio for Twitter / security identity check
                    val reqResources = request?.resources ?: emptyArray()
                    val activity = ctx as? android.app.Activity
                    if (activity != null) {
                        activity.runOnUiThread {
                            val needsCamera = reqResources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                            val needsAudio = reqResources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                            val hasCamera = !needsCamera || ctx.checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            val hasAudio = !needsAudio || ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED

                            if (hasCamera && hasAudio) {
                                request?.grant(reqResources)
                            } else {
                                pendingWebPermission = request
                                val perms = mutableListOf<String>()
                                if (needsCamera && ctx.checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    perms.add(android.Manifest.permission.CAMERA)
                                }
                                if (needsAudio && ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    perms.add(android.Manifest.permission.RECORD_AUDIO)
                                }
                                if (perms.isNotEmpty()) {
                                    cameraLauncher.launch(perms.toTypedArray())
                                } else {
                                    request?.grant(reqResources)
                                }
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
                    // Enable immersive sticky fullscreen
                    (ctx as? Activity)?.let { act ->
                        act.window.decorView.systemUiVisibility = (
                            View.SYSTEM_UI_FLAG_FULLSCREEN
                                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        )
                    }
                }

                override fun onHideCustomView() {
                    customViewCallback?.onCustomViewHidden()
                    customVideoView = null
                    customViewCallback = null
                    // Restore system UI and sensor orientation
                    (ctx as? Activity)?.let { act ->
                        act.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                        act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            }

            // Find in Page listener
            setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                findActiveMatchIndex = activeMatchOrdinal
                findMatchCount = numberOfMatches
            }

            // Client with Titanium AdBlock and AutoPilot injection
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return false
                    val urlStr = uri.toString()
                    val scheme = uri.scheme?.lowercase() ?: ""

                    // External intent handling (tel:, mailto:, intent:)
                    if (scheme != "http" && scheme != "https") {
                        try {
                            val intent = Intent.parseUri(urlStr, Intent.URI_INTENT_SCHEME)
                            ctx.startActivity(intent)
                            return true
                        } catch (e: Exception) {
                            return true
                        }
                    }

                    // Enforce Virtual Location Routing on Google Queries
                    val host = uri.host?.lowercase() ?: ""
                    if (host.contains("google.") && !urlStr.contains("accounts.google") && !urlStr.contains("signin") && !urlStr.contains("auth")) {
                        val cc = selectedLocationPreset.countryCode.ifBlank { "us" }
                        val needsGl = !urlStr.contains("gl=")
                        val needsHl = !urlStr.contains("hl=")
                        if (needsGl || needsHl) {
                            val sep = if (urlStr.contains("?")) "&" else "?"
                            val extra = buildString {
                                if (needsGl) append("gl=$cc")
                                if (needsHl) {
                                    if (isNotEmpty()) append("&")
                                    append("hl=en")
                                }
                                append("&pws=0")
                            }
                            view?.loadUrl("$urlStr$sep$extra")
                            return true
                        }
                    }
                    return false
                }
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
                    view?.evaluateJavascript(getGeoPrivacyScript(), null)
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
                        val currentTitle = view?.title ?: tabs[tabIndex].title
                        tabs[tabIndex].url = url
                        tabs[tabIndex].title = currentTitle
                    }

                    // Apply cosmetic ad hiding
                    adBlockEngine.injectCosmeticAdHiding(view)

                    // Inject location privacy spoof again for SPA navigation
                    view?.evaluateJavascript(getGeoPrivacyScript(), null)

                    // Sniff video media streams on the page
                    sniffWebVideos(view) { urls ->
                        detectedMediaUrls = urls
                    }

                    // Inject Twitter AutoPilot ONLY if in automation mode on signup/flow/challenge
                    if (isAutomationMode && url != null && (url.contains("signup") || url.contains("flow") || url.contains("challenge") || url.contains("x.com"))) {
                        if (phoneNumber.isNotBlank()) {
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
            }

            loadUrl(tab.url)
        }
    }

    fun getOrCreateWebView(tab: BrowserTab): WebView {
        val existing = webViewPool[tab.id]
        if (existing != null) {
            return existing
        }
        val created = createConfiguredWebView(context, tab)
        webViewPool[tab.id] = created
        containerLayout?.let { layout ->
            if (created.parent != layout) {
                (created.parent as? ViewGroup)?.removeView(created)
                layout.addView(created)
            }
        }
        return created
    }

    // Function to switch active tab WITHOUT reloading page state
    fun switchTab(newIndex: Int) {
        if (newIndex !in tabs.indices) return
        val oldTab = BrowserTabManager.getCurrentTab(isAutomationMode)
        oldTab?.let {
            webViewPool[it.id]?.visibility = View.INVISIBLE
        }

        BrowserTabManager.switchTab(isAutomationMode, newIndex)
        val targetTab = tabs[newIndex]
        val active = getOrCreateWebView(targetTab)
        active.visibility = View.VISIBLE
        active.bringToFront()
        activeWebView = active
        currentUrl = active.url ?: targetTab.url
        inputUrl = currentUrl
        pageTitle = active.title ?: targetTab.title
        canGoBack = active.canGoBack()
        canGoForward = active.canGoForward()
        isLoading = false
    }

    // Function to add a new tab
    fun addNewTab(url: String = if (isAutomationMode) "https://x.com/i/flow/signup" else "https://www.google.com") {
        BrowserTabManager.addNewTab(isAutomationMode, url)
        val newIndex = tabs.size - 1
        if (newIndex in tabs.indices) {
            val newTab = tabs[newIndex]
            getOrCreateWebView(newTab)
            switchTab(newIndex)
        }
        showTabSwitcher = false
    }

    // Function to close a tab
    fun closeTab(index: Int) {
        BrowserTabManager.closeTab(isAutomationMode, index)
        val activeIdx = BrowserTabManager.getActiveIndex(isAutomationMode).value
        switchTab(activeIdx)
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
                        val cc = selectedLocationPreset.countryCode.ifBlank { "us" }
                        val homeUrl = if (isAutomationMode) "https://x.com/i/flow/signup" else "https://www.google.com/webhp?gl=$cc&hl=en&pws=0"
                        activeWebView?.loadUrl(homeUrl)
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
                                    val cc = selectedLocationPreset.countryCode.ifBlank { "us" }
                                    val template = searchEngines[selectedSearchEngine] ?: "https://www.google.com/search?q={query}&gl=$cc&hl=en&pws=0"
                                    val formatted = when {
                                        inputUrl.equals("google.com", ignoreCase = true) ||
                                            inputUrl.equals("www.google.com", ignoreCase = true) ||
                                            inputUrl.equals("https://google.com", ignoreCase = true) ||
                                            inputUrl.equals("https://www.google.com", ignoreCase = true) ||
                                            inputUrl.equals("http://google.com", ignoreCase = true) ||
                                            inputUrl.equals("http://www.google.com", ignoreCase = true) ->
                                                "https://www.google.com/webhp?gl=$cc&hl=en&pws=0"
                                        inputUrl.startsWith("http://") || inputUrl.startsWith("https://") -> inputUrl
                                        inputUrl.contains(".") && !inputUrl.contains(" ") -> "https://$inputUrl"
                                        else -> template.replace("{query}", java.net.URLEncoder.encode(inputUrl, "UTF-8"))
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
                                            text = if (isAutomationMode) "Enter URL or Twitter flow" else "Search ($selectedSearchEngine) or type URL",
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

                // Location Shield Button
                if (!isAutomationMode) {
                    IconButton(
                        onClick = { showLocationDialog = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Text(
                            text = selectedLocationPreset.flag,
                            fontSize = 17.sp
                        )
                    }

                    // Download Sniffer / Manager Button
                    IconButton(
                        onClick = {
                            if (detectedMediaUrls.isNotEmpty()) {
                                showDetectedStreamsDialog = true
                            } else {
                                showDownloadsDialog = true
                            }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        BadgedBox(
                            badge = {
                                if (detectedMediaUrls.isNotEmpty()) {
                                    Badge(containerColor = SuccessGreen) {
                                        Text(detectedMediaUrls.size.toString(), fontSize = 9.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Downloads",
                                tint = if (detectedMediaUrls.isNotEmpty()) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
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
                                addNewTab()
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
                            text = { Text("Downloads") },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = PrimaryBlue) },
                            onClick = {
                                showMenu = false
                                showDownloadsDialog = true
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
                            text = { Text("Search Engine ($selectedSearchEngine)") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryBlue) },
                            onClick = {
                                showMenu = false
                                showSearchEngineDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Reader Mode")
                                    Checkbox(
                                        checked = isReaderModeActive,
                                        onCheckedChange = {
                                            showMenu = false
                                            toggleReaderMode()
                                        }
                                    )
                                }
                            },
                            leadingIcon = { Icon(Icons.Default.Article, contentDescription = null, tint = PrimaryBlue) },
                            onClick = {
                                showMenu = false
                                toggleReaderMode()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Dark Reader")
                                    Checkbox(
                                        checked = isForceDarkMode,
                                        onCheckedChange = {
                                            showMenu = false
                                            toggleForceDarkMode()
                                        }
                                    )
                                }
                            },
                            leadingIcon = { Icon(Icons.Default.DarkMode, contentDescription = null, tint = PrimaryBlue) },
                            onClick = {
                                showMenu = false
                                toggleForceDarkMode()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear Cookies & Storage") },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = ErrorRed) },
                            onClick = {
                                showMenu = false
                                clearSiteData()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Location Shield: ${selectedLocationPreset.flag} ${selectedLocationPreset.name}") },
                            leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null, tint = SuccessGreen) },
                            onClick = {
                                showMenu = false
                                showLocationDialog = true
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
                                        injectValueIntoInput(activeWebView, latestOtp!!, "otp")
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
                                    injectValueIntoInput(activeWebView, password, "password")
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
                        val defaultTab = BrowserTab(url = if (isAutomationMode) "https://x.com/i/flow/signup" else "https://www.google.com")
                        val currentTab = BrowserTabManager.getCurrentTab(isAutomationMode) ?: tabs.firstOrNull() ?: defaultTab
                        val initialWebView = getOrCreateWebView(currentTab)
                        if (initialWebView.parent != this) {
                            (initialWebView.parent as? ViewGroup)?.removeView(initialWebView)
                            addView(initialWebView)
                        }
                        initialWebView.visibility = View.VISIBLE
                        initialWebView.bringToFront()
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
                    val defaultTab = BrowserTab(url = if (isAutomationMode) "https://x.com/i/flow/signup" else "https://www.google.com")
                    val currentTab = BrowserTabManager.getCurrentTab(isAutomationMode) ?: tabs.firstOrNull() ?: defaultTab
                    val active = getOrCreateWebView(currentTab)
                    if (active.visibility != View.VISIBLE) {
                        active.visibility = View.VISIBLE
                        active.bringToFront()
                    }
                    activeWebView = active
                }
            )

            // Floating 1-Tap AutoFill Registration Pill
            val isTwitterSignup = isAutomationMode && (currentUrl.contains("signup") || currentUrl.contains("flow") || currentUrl.contains("x.com"))
            if (isTwitterSignup && !isFindInPageVisible && customVideoView == null) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = if (phoneNumber.isNotBlank()) PrimaryBlue else Color(0xFFD97706),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clickable {
                                if (phoneNumber.isBlank()) {
                                    phoneInputText = ""
                                    showPhoneEditDialog = true
                                } else {
                                    runAutoPilotOnActiveTab()
                                    Toast.makeText(context, "Executing AutoFill on registration form...", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (phoneNumber.isNotBlank()) Icons.Default.Bolt else Icons.Default.PhoneAndroid,
                            contentDescription = "AutoFill",
                            tint = SurfaceWhite,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (phoneNumber.isNotBlank()) "AutoFill Form" else "Set TG Bot Phone",
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
                                text = if (phoneNumber.isNotBlank()) phoneNumber else "Required",
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
                            addNewTab()
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
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Telegram Bot Phone Number")
                }
            },
            text = {
                Column {
                    Text(
                        "Paste or enter the genuine phone number received from @EHR_QUICKINCOME_BOT:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = phoneInputText,
                        onValueChange = { phoneInputText = it },
                        label = { Text("Bot Phone Number") },
                        placeholder = { Text("+1234567890") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            val clipMgr = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = clipMgr?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                            if (!clip.isNullOrBlank()) {
                                phoneInputText = clip
                            } else {
                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste from Clipboard", fontSize = 12.sp)
                    }
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

    // Search Engine Selection Dialog
    if (showSearchEngineDialog) {
        AlertDialog(
            onDismissRequest = { showSearchEngineDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Default Search Engine")
                }
            },
            text = {
                Column {
                    searchEngines.keys.forEach { engineName ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSearchEngine = engineName
                                    showSearchEngineDialog = false
                                    Toast.makeText(context, "Default Search Engine: $engineName", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedSearchEngine == engineName,
                                onClick = {
                                    selectedSearchEngine = engineName
                                    showSearchEngineDialog = false
                                    Toast.makeText(context, "Default Search Engine: $engineName", Toast.LENGTH_SHORT).show()
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(engineName, fontSize = 14.sp, fontWeight = if (selectedSearchEngine == engineName) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchEngineDialog = false }) {
                    Text("Close")
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
    // 8.5 LOCATION PRIVACY SHIELD DIALOG
    // ==========================================
    if (showLocationDialog) {
        val applyPreset = { preset: GeoLocationPreset ->
            selectedLocationPreset = preset
            val script = buildGeoPrivacyScript(preset)
            activeWebView?.evaluateJavascript(script, null)
            val cur = activeWebView?.url ?: ""
            if (cur.contains("google.")) {
                val cc = preset.countryCode.ifBlank { "us" }
                val newUrl = if (cur.contains("gl=")) {
                    cur.replace(Regex("gl=[a-zA-Z]+"), "gl=$cc")
                } else {
                    val sep = if (cur.contains("?")) "&" else "?"
                    "$cur${sep}gl=$cc&hl=en&pws=0"
                }
                activeWebView?.loadUrl(newUrl)
            }
            showLocationDialog = false
            Toast.makeText(context, "Virtual Location: ${preset.flag} ${preset.name} active", Toast.LENGTH_SHORT).show()
        }

        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = SuccessGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Location Privacy Shield", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Websites and search engines cannot access your physical GPS, IP location, or timezone. Select your virtual gateway:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        items(locationPresets) { preset ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { applyPreset(preset) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedLocationPreset.id == preset.id,
                                    onClick = { applyPreset(preset) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(preset.flag, fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = preset.name,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (selectedLocationPreset.id == preset.id) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = if (preset.id == "random") "Dynamic jittered coordinates on every session" else "${preset.city} • ${preset.timeZone}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLocationDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // ==========================================
    // 8.6 DOWNLOADS MANAGER DIALOG
    // ==========================================
    if (showDownloadsDialog) {
        var downloadedFiles by remember { mutableStateOf(scanDownloadedFiles(context)) }
        var selectedFilter by remember { mutableStateOf("All") }

        val filteredFiles = remember(downloadedFiles, selectedFilter) {
            when (selectedFilter) {
                "Videos" -> downloadedFiles.filter { it.isMedia }
                "Documents" -> downloadedFiles.filter { !it.isMedia }
                else -> downloadedFiles
            }
        }

        AlertDialog(
            onDismissRequest = { showDownloadsDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Downloads", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = {
                            try {
                                val targetDir = File("/storage/emulated/0/Download/AAAX")
                                if (!targetDir.exists()) targetDir.mkdirs()
                                val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Location: Download/AAAX", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Open Folder", fontSize = 12.sp)
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("All", "Videos", "Documents").forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (filteredFiles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "No files downloaded yet",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                        ) {
                            items(filteredFiles) { item ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (item.isMedia) Icons.Default.VideoFile else Icons.Default.InsertDriveFile,
                                            contentDescription = null,
                                            tint = if (item.isMedia) SuccessGreen else PrimaryBlue,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.name,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${Formatter.formatFileSize(context, item.sizeBytes)} • ${SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(item.lastModified))}",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { openDownloadedFile(context, item.file) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Open", tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(
                                            onClick = { shareDownloadedFile(context, item.file) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                deleteDownloadedFile(context, item.file) {
                                                    downloadedFiles = scanDownloadedFiles(context)
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDownloadsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // ==========================================
    // 8.7 DETECTED MEDIA STREAMS DIALOG
    // ==========================================
    if (showDetectedStreamsDialog) {
        AlertDialog(
            onDismissRequest = { showDetectedStreamsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = SuccessGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download Media Streams", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Found ${detectedMediaUrls.size} media stream(s) on current page:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        items(detectedMediaUrls) { streamUrl ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = streamUrl,
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Button(
                                            onClick = {
                                                downloadStreamUrl(context, streamUrl)
                                                showDetectedStreamsDialog = false
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Download Stream", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetectedStreamsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // ==========================================
    // 9. FULLSCREEN MX PLAYER VIDEO OVERLAY
    // ==========================================
    if (customVideoView != null) {
        var areControlsVisible by remember { mutableStateOf(true) }
        var controlsJob by remember { mutableStateOf<Job?>(null) }
        var videoDurationSec by remember { mutableStateOf(0f) }
        var videoCurrentSec by remember { mutableStateOf(0f) }
        var isPlayingState by remember { mutableStateOf(true) }
        var currentOrientation by remember { mutableStateOf("auto") }

        fun triggerControlsAutoHide() {
            controlsJob?.cancel()
            controlsJob = scope.launch {
                delay(3500)
                areControlsVisible = false
            }
        }

        LaunchedEffect(customVideoView) {
            triggerControlsAutoHide()
            while (customVideoView != null) {
                activeWebView?.evaluateJavascript(
                    "(function(){ var v = document.querySelector('video'); return v ? JSON.stringify({t: v.currentTime || 0, d: v.duration || 0, p: v.paused}) : '{}'; })()"
                ) { res ->
                    try {
                        if (res != null && res != "null" && res != "\"{}\"") {
                            val clean = if (res.startsWith("\"") && res.endsWith("\"")) {
                                org.json.JSONTokener(res).nextValue().toString()
                            } else res
                            val json = org.json.JSONObject(clean)
                            videoCurrentSec = json.optDouble("t", 0.0).toFloat()
                            val d = json.optDouble("d", 0.0).toFloat()
                            if (d > 0) videoDurationSec = d
                            isPlayingState = !json.optBoolean("p", false)
                        }
                    } catch (e: Exception) {}
                }
                delay(1000)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Native Video Surface
            AndroidView(
                factory = { customVideoView!! },
                modifier = Modifier.fillMaxSize()
            )

            // Transparent Gesture Interceptor Layer (on top of video surface)
            if (!isScreenLocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            var startSideIsLeft = true
                            var isHorizontalDrag = false
                            var isVerticalDrag = false
                            detectDragGestures(
                                onDragStart = { offset ->
                                    startSideIsLeft = offset.x < size.width / 2
                                    isHorizontalDrag = false
                                    isVerticalDrag = false
                                    triggerControlsAutoHide()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (!isHorizontalDrag && !isVerticalDrag) {
                                        if (abs(dragAmount.x) > abs(dragAmount.y) && abs(dragAmount.x) > 3f) {
                                            isHorizontalDrag = true
                                        } else if (abs(dragAmount.y) > 3f) {
                                            isVerticalDrag = true
                                        }
                                    }
                                    if (isHorizontalDrag) {
                                        val deltaSeconds = (dragAmount.x / 14f).toInt()
                                        if (deltaSeconds != 0) {
                                            seekActiveVideo(deltaSeconds)
                                        }
                                    } else if (isVerticalDrag) {
                                        val deltaFraction = -dragAmount.y / size.height.toFloat()
                                        if (startSideIsLeft) {
                                            adjustBrightness(deltaFraction * 1.5f)
                                        } else {
                                            adjustVolume(deltaFraction * 1.5f)
                                        }
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    areControlsVisible = !areControlsVisible
                                    if (areControlsVisible) triggerControlsAutoHide()
                                },
                                onDoubleTap = { offset ->
                                    triggerControlsAutoHide()
                                    if (offset.x < size.width * 0.35f) {
                                        seekActiveVideo(-10)
                                    } else if (offset.x > size.width * 0.65f) {
                                        seekActiveVideo(10)
                                    } else {
                                        activeWebView?.evaluateJavascript(
                                            "var v = document.querySelector('video'); if (v) { if (v.paused) v.play(); else v.pause(); }",
                                            null
                                        )
                                        isPlayingState = !isPlayingState
                                        showGestureFeedback(
                                            if (isPlayingState) Icons.Default.PlayArrow else Icons.Default.Pause,
                                            if (isPlayingState) "Play" else "Pause"
                                        )
                                    }
                                }
                            )
                        }
                )
            }

            // Screen Locked Floating Unlock Button
            if (isScreenLocked) {
                IconButton(
                    onClick = {
                        isScreenLocked = false
                        showGestureFeedback(Icons.Default.LockOpen, "Screen Unlocked")
                        triggerControlsAutoHide()
                    },
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopStart)
                        .size(46.dp)
                        .background(Color.Black.copy(alpha = 0.75f), CircleShape)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = "Unlock Screen", tint = Color(0xFFF59E0B))
                }
            } else {
                // Top Bar Controls Overlay for Fullscreen Video (MX Player style)
                AnimatedVisibility(
                    visible = areControlsVisible,
                    enter = fadeIn() + slideInVertically { -it },
                    exit = fadeOut() + slideOutVertically { -it },
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Exit Fullscreen Button
                            IconButton(
                                onClick = {
                                    (context as? Activity)?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    (context as? Activity)?.window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                                    customViewCallback?.onCustomViewHidden()
                                    customVideoView = null
                                    customViewCallback = null
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Exit Fullscreen", tint = Color.White)
                            }

                            Text(
                                text = pageTitle.ifEmpty { "Web Video Stream" },
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Screen Rotation Toggle
                                IconButton(
                                    onClick = {
                                        val act = context as? Activity
                                        val next = when (currentOrientation) {
                                            "auto" -> "landscape"
                                            "landscape" -> "portrait"
                                            else -> "auto"
                                        }
                                        currentOrientation = next
                                        when (next) {
                                            "landscape" -> {
                                                act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                                showGestureFeedback(Icons.Default.ScreenRotation, "Screen: Landscape")
                                            }
                                            "portrait" -> {
                                                act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                                showGestureFeedback(Icons.Default.ScreenRotation, "Screen: Portrait")
                                            }
                                            else -> {
                                                act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                                showGestureFeedback(Icons.Default.ScreenRotation, "Screen: Auto-Rotate")
                                            }
                                        }
                                        triggerControlsAutoHide()
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ScreenRotation,
                                        contentDescription = "Screen Rotation",
                                        tint = if (currentOrientation != "auto") PrimaryBlue else Color.White
                                    )
                                }

                                // Aspect Ratio Cycle
                                IconButton(
                                    onClick = { cycleAspectRatio(); triggerControlsAutoHide() },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.AspectRatio, contentDescription = "Aspect Ratio", tint = Color.White)
                                }

                                // 200% Audio Volume Boost
                                IconButton(
                                    onClick = { toggleAudioBoost(); triggerControlsAutoHide() },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.VolumeUp,
                                        contentDescription = "Audio Boost",
                                        tint = if (isAudioBoosted) SuccessGreen else Color.White
                                    )
                                }

                                // Download Video Stream
                                IconButton(
                                    onClick = { detectAndDownloadVideo(); triggerControlsAutoHide() },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = "Download Video", tint = Color.White)
                                }

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
                                        triggerControlsAutoHide()
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("${playbackSpeed}x", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                // PiP Button
                                IconButton(
                                    onClick = { triggerVideoPip(); triggerControlsAutoHide() },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.PictureInPicture, contentDescription = "PiP", tint = Color.White)
                                }

                                // Screen Lock Button
                                IconButton(
                                    onClick = {
                                        isScreenLocked = true
                                        showGestureFeedback(Icons.Default.Lock, "Screen Locked")
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = "Lock Screen", tint = Color.White)
                                }
                            }
                        }
                    }
                }

                // Bottom Bar Controls Overlay for Fullscreen Video (MX Player style)
                AnimatedVisibility(
                    visible = areControlsVisible,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            // Scrubbable Progress Slider & Timestamps
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatVideoTime(videoCurrentSec.toInt()),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Slider(
                                    value = videoCurrentSec.coerceIn(0f, if (videoDurationSec > 0) videoDurationSec else 100f),
                                    onValueChange = { newVal ->
                                        videoCurrentSec = newVal
                                        activeWebView?.evaluateJavascript(
                                            "var v = document.querySelector('video'); if (v) v.currentTime = $newVal;",
                                            null
                                        )
                                        triggerControlsAutoHide()
                                    },
                                    valueRange = 0f..(if (videoDurationSec > 0) videoDurationSec else 100f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = PrimaryBlue,
                                        activeTrackColor = PrimaryBlue,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                    )
                                )
                                Text(
                                    text = formatVideoTime(videoDurationSec.toInt()),
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Playback action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { seekActiveVideo(-10); triggerControlsAutoHide() },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                IconButton(
                                    onClick = {
                                        activeWebView?.evaluateJavascript(
                                            "var v = document.querySelector('video'); if (v) { if (v.paused) v.play(); else v.pause(); }",
                                            null
                                        )
                                        isPlayingState = !isPlayingState
                                        triggerControlsAutoHide()
                                    },
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(PrimaryBlue, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingState) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                IconButton(
                                    onClick = { seekActiveVideo(10); triggerControlsAutoHide() },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White)
                                }
                            }
                        }
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

private fun injectValueIntoInput(webView: WebView?, value: String, fieldType: String = "auto") {
    if (webView == null || value.isBlank()) return
    val script = """
        (function() {
            var active = document.activeElement;
            var targetType = '$fieldType';
            function triggerNative(el, val) {
                if (!el) return false;
                el.focus();
                if (el._valueTracker) {
                    el._valueTracker.setValue('');
                }
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
                return true;
            }

            if (targetType === 'otp') {
                var otpInput = document.querySelector('input[name="verfication_code"], input[name="verification_code"], input[autocomplete="one-time-code"], input[data-testid*="verification"], input[inputmode="numeric"]');
                if (otpInput && triggerNative(otpInput, '$value')) return 'otp_filled';
            } else if (targetType === 'phone') {
                var phoneInput = document.querySelector('input[name="phone_number"], input[type="tel"], input[autocomplete="tel"]');
                if (phoneInput && triggerNative(phoneInput, '$value')) return 'phone_filled';
            } else if (targetType === 'password') {
                var passInput = document.querySelector('input[name="password"], input[type="password"], input[autocomplete="new-password"], input[autocomplete="current-password"]');
                if (passInput && triggerNative(passInput, '$value')) return 'password_filled';
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

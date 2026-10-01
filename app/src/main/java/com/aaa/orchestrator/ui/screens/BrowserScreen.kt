package com.aaa.orchestrator.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.*
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.viewinterop.AndroidView
import com.aaa.orchestrator.engine.OrchestratorEngine
import com.aaa.orchestrator.ui.theme.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.ByteArrayInputStream

@Composable
fun BrowserScreen(
    engine: OrchestratorEngine? = null,
    activeUrl: String = "https://x.com/i/flow/signup",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var blockedAdsCount by remember { mutableStateOf(42) }
    var blockedTrackersCount by remember { mutableStateOf(18) }
    var currentUrl by remember { mutableStateOf(activeUrl) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    val state = engine?.state?.collectAsState()?.value
    val phoneNumber by (engine?.activePhoneNumber ?: remember { mutableStateOf("+48459074091") }).let {
        if (engine != null) it.collectAsState() else remember { mutableStateOf("+48459074091") }
    }
    val password by (engine?.activePassword ?: remember { mutableStateOf("AAA_Auto_2026") }).let {
        if (engine != null) it.collectAsState() else remember { mutableStateOf("AAA_Auto_2026") }
    }
    val latestOtp by (engine?.latestOtp ?: remember { mutableStateOf(null) }).let {
        if (engine != null) it.collectAsState() else remember { mutableStateOf(null) }
    }

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        // Real Workflow HUD Bar (Phone, Password, OTP, Session Capture)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Header with status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (state?.label?.contains("Idle") == false) SoftGreenTile else SurfaceVariantLight
                        ) {
                            Text(
                                text = if (state?.label?.contains("Idle") == false) "WORKFLOW ACTIVE" else "BROWSER STANDBY",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (state?.label?.contains("Idle") == false) SuccessGreen else TextMuted,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state?.label ?: "Ready",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSlateDark,
                            maxLines = 1
                        )
                    }

                    // Save / Capture Real Session Button
                    Button(
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
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save Session",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Capture Session", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Credentials & OTP quick actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Phone Chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SoftBlueTile,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("2nr Phone", fontSize = 10.sp, color = TextMuted)
                                Text(phoneNumber, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSlateDark)
                            }
                            Row {
                                IconButton(
                                    onClick = { copyToClipboard(context, "Phone", phoneNumber) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(14.dp), tint = PrimaryBlue)
                                }
                                IconButton(
                                    onClick = {
                                        // Inject phone into active web input
                                        injectValueIntoInput(webViewInstance, phoneNumber)
                                        Toast.makeText(context, "Filled phone into input", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Input, "Fill", modifier = Modifier.size(14.dp), tint = PrimaryBlue)
                                }
                            }
                        }
                    }

                    // OTP / SMS Chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (latestOtp != null) SoftGreenTile else SurfaceVariantLight,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("2nr SMS OTP", fontSize = 10.sp, color = if (latestOtp != null) SuccessGreen else TextMuted)
                                Text(
                                    text = latestOtp ?: "Waiting SMS...",
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
                                    Icon(Icons.Default.Input, "Fill OTP", modifier = Modifier.size(14.dp), tint = SuccessGreen)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Web Address Bar & Navigation Controls
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceVariantLight)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (webViewInstance?.canGoBack() == true) webViewInstance?.goBack()
                    },
                    enabled = canGoBack,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = if (canGoBack) PrimaryBlue else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = {
                        if (webViewInstance?.canGoForward() == true) webViewInstance?.goForward()
                    },
                    enabled = canGoForward,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (canGoForward) PrimaryBlue else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { webViewInstance?.reload() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reload",
                        tint = TextSlateDark,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "SSL",
                    tint = SuccessGreen,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = currentUrl,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = TextSlateDark,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        CookieManager.getInstance().removeAllCookies(null)
                        webViewInstance?.clearCache(true)
                        Toast.makeText(context, "Session and cookies cleared", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Clear Session",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Embedded Privacy WebView with Crash Protection and Ad Filter
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewInstance = this
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            userAgentString = "Mozilla/5.0 (Linux; Android 11; SM-A305F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val url = request?.url?.toString() ?: return null
                                val host = request.url?.host ?: ""

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

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (url != null) currentUrl = url
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
            for (var i = 0; i < inputs.size; i++) {
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

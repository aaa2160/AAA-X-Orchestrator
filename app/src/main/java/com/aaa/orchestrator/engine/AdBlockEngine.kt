package com.aaa.orchestrator.engine

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import timber.log.Timber
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader

/**
 * High-performance, offline Titanium AdBlock & Anti-Tracking Engine.
 * Loads rules from local application assets (easylist_rules.txt and trackers_hosts.txt)
 * and applies both network-level blocking and DOM-level cosmetic element hiding.
 */
class AdBlockEngine(context: Context) {

    private val blockedHosts = HashSet<String>(500)

    init {
        loadRulesFromAssets(context)
    }

    private fun loadRulesFromAssets(context: Context) {
        // Built-in high-frequency ad & tracker domains
        val defaultAdDomains = listOf(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "adservice.google.com",
            "pagead2.googlesyndication.com",
            "securepubads.g.doubleclick.net",
            "adnxs.com",
            "scorecardresearch.com",
            "amazon-adsystem.com",
            "criteo.com",
            "criteo.net",
            "quantserve.com",
            "outbrain.com",
            "taboola.com",
            "pubmatic.com",
            "rubiconproject.com",
            "openx.net",
            "casalemedia.com",
            "advertising.com",
            "adtechus.com",
            "applovin.com",
            "unityads.unity3d.com",
            "vungle.com",
            "chartboost.com",
            "ironsrc.com",
            "inmobi.com",
            "mopub.com",
            "adjust.com",
            "appsflyer.com",
            "branch.io",
            "kochava.com",
            "flurry.com",
            "segment.io",
            "mixpanel.com",
            "amplitude.com",
            "clarity.ms",
            "google-analytics.com",
            "analytics.twitter.com",
            "ads-twitter.com",
            "telemetry.twitter.com",
            "telemetry.x.com",
            "adroll.com",
            "smartadserver.com",
            "bidswitch.net",
            "contextweb.com",
            "sovrn.com",
            "popads.net",
            "adcash.com",
            "propellerads.com",
            "mgid.com",
            "yandex.ru/metrika",
            "mc.yandex.ru"
        )
        blockedHosts.addAll(defaultAdDomains)

        // Load trackers_hosts.txt
        try {
            context.assets.open("trackers_hosts.txt").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                    lines.forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            val parts = trimmed.split(Regex("\\s+"))
                            val host = if (parts.size >= 2) parts[1] else parts[0]
                            if (host.isNotEmpty()) blockedHosts.add(host.lowercase())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Could not load trackers_hosts.txt from assets")
        }

        // Load easylist_rules.txt
        try {
            context.assets.open("easylist_rules.txt").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                    lines.forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            var cleanRule = trimmed.removePrefix("||").removeSuffix("^")
                            if (cleanRule.isNotEmpty()) {
                                blockedHosts.add(cleanRule.lowercase())
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Could not load easylist_rules.txt from assets")
        }

        Timber.i("AdBlockEngine initialized with ${blockedHosts.size} rules.")
    }

    /**
     * Determines whether the requested resource is an advertisement or tracker.
     */
    fun shouldBlock(request: WebResourceRequest?): Boolean {
        if (request == null) return false
        val host = request.url?.host?.lowercase() ?: return false
        val path = request.url?.path?.lowercase() ?: ""

        // Never block core social, authentication, verification, and worker domains
        if (host == "twitter.com" || host.endsWith(".twitter.com") ||
            host == "x.com" || host.endsWith(".x.com") ||
            host == "twimg.com" || host.endsWith(".twimg.com") ||
            host == "t.co" || host.endsWith(".t.co") ||
            host == "telegram.org" || host.endsWith(".telegram.org") ||
            host == "t.me" || host.endsWith(".t.me") ||
            host == "render.com" || host.endsWith(".render.com") ||
            host == "onrender.com" || host.endsWith(".onrender.com") ||
            host.contains("arkoselabs.com") ||
            host.contains("prelude.dev") ||
            host.contains("rekognition") ||
            host.contains("amazonaws.com")
        ) {
            return false
        }

        // Fast O(1) host lookup
        if (blockedHosts.contains(host)) {
            return true
        }
        val parts = host.split(".")
        if (parts.size >= 2) {
            val rootDomain = parts.takeLast(2).joinToString(".")
            if (blockedHosts.contains(rootDomain)) return true
        }
        if (parts.size >= 3) {
            val subDomain = parts.takeLast(3).joinToString(".")
            if (blockedHosts.contains(subDomain)) return true
        }

        // Common ad url signatures
        if (path.contains("/ad/") || path.contains("/ads/") || path.contains("adsbygoogle") ||
            path.contains("/pagead/") || path.contains("/adclick") || path.contains("/advert")
        ) {
            return true
        }

        return false
    }

    /**
     * Creates an empty response that cancels the network request for blocked ads.
     */
    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }

    /**
     * Injects cosmetic DOM element hiding rules into the web page to remove ad banners,
     * floating overlays, and sponsored containers from view.
     */
    fun injectCosmeticAdHiding(webView: WebView?) {
        if (webView == null) return
        val cssScript = """
            (function() {
                var adSelectors = [
                    'ins.adsbygoogle',
                    'div[id^="google_ads_"]',
                    'div[id*="-ad-"]',
                    'div[class*="-ad-"]',
                    'div[class*="ad_"]',
                    'div[class*="ad-banner"]',
                    'div[class*="ad-container"]',
                    'div[class*="sponsored"]',
                    'div[aria-label="Advertisements"]',
                    'div[data-ad]',
                    'iframe[src*="doubleclick"]',
                    'iframe[src*="adservice"]',
                    'iframe[src*="googlesyndication"]',
                    'iframe[src*="adnxs"]',
                    '.ad-container',
                    '.ad-box',
                    '.ad-banner',
                    '.ad_unit',
                    '#ad-banner',
                    '#ad-header',
                    '#sidebar-ads'
                ];
                function hideAds() {
                    adSelectors.forEach(function(sel) {
                        try {
                            var elements = document.querySelectorAll(sel);
                            for (var i = 0; i < elements.length; i++) {
                                elements[i].style.setProperty('display', 'none', 'important');
                                elements[i].style.setProperty('visibility', 'hidden', 'important');
                                elements[i].style.setProperty('height', '0px', 'important');
                                elements[i].style.setProperty('max-height', '0px', 'important');
                            }
                        } catch (e) {}
                    });
                }
                hideAds();
                setTimeout(hideAds, 1000);
                setTimeout(hideAds, 2500);
            })();
        """.trimIndent()
        webView.evaluateJavascript(cssScript, null)
    }
}

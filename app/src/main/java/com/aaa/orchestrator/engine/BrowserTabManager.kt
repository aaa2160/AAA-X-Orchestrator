package com.aaa.orchestrator.engine

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
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

/**
 * Enterprise Dual-Browser State Manager.
 * Maintains complete project isolation between:
 * 1. Normal Everyday Web Browser (Google, video streaming, general browsing)
 * 2. Dedicated Automation Browser (Twitter/X signup bot, AutoPilot injection, OTP verification)
 */
object BrowserTabManager {
    // Normal Browser Tabs & WebView Pool
    val normalTabs = mutableStateListOf<BrowserTab>()
    val normalActiveTabIndex = mutableStateOf(0)
    val normalWebViewPool = mutableMapOf<String, WebView>()

    // Automation Browser Tabs & WebView Pool
    val autoTabs = mutableStateListOf<BrowserTab>()
    val autoActiveTabIndex = mutableStateOf(0)
    val autoWebViewPool = mutableMapOf<String, WebView>()

    val bookmarks = mutableStateListOf(
        "https://www.google.com",
        "https://duckduckgo.com",
        "https://github.com",
        "https://news.ycombinator.com"
    )
    val history = mutableStateListOf<HistoryItem>()

    init {
        normalTabs.add(BrowserTab(url = "https://www.google.com", title = "Google"))
        autoTabs.add(BrowserTab(url = "https://x.com/i/flow/signup", title = "X Signup Bot"))
    }

    fun getTabs(isAutomation: Boolean): androidx.compose.runtime.snapshots.SnapshotStateList<BrowserTab> {
        val list = if (isAutomation) autoTabs else normalTabs
        if (list.isEmpty()) {
            list.add(
                BrowserTab(
                    url = if (isAutomation) "https://x.com/i/flow/signup" else "https://www.google.com",
                    title = if (isAutomation) "X Signup Bot" else "Google"
                )
            )
        }
        return list
    }
    fun getActiveIndex(isAutomation: Boolean) = if (isAutomation) autoActiveTabIndex else normalActiveTabIndex
    fun getPool(isAutomation: Boolean) = if (isAutomation) autoWebViewPool else normalWebViewPool

    fun getCurrentTab(isAutomation: Boolean): BrowserTab? {
        val tabs = getTabs(isAutomation)
        val idx = getActiveIndex(isAutomation).value
        return tabs.getOrNull(idx) ?: tabs.firstOrNull()
    }

    fun switchTab(isAutomation: Boolean, newIndex: Int) {
        val tabs = getTabs(isAutomation)
        val pool = getPool(isAutomation)
        val activeIdx = getActiveIndex(isAutomation)

        if (newIndex in tabs.indices) {
            val oldTab = tabs.getOrNull(activeIdx.value)
            val newTab = tabs[newIndex]

            if (oldTab?.id != newTab.id) {
                oldTab?.let { pool[it.id]?.visibility = View.INVISIBLE }
            }

            activeIdx.value = newIndex
            val newWv = pool[newTab.id]
            if (newWv != null) {
                newWv.visibility = View.VISIBLE
                newWv.bringToFront()
            }
        }
    }

    fun addNewTab(isAutomation: Boolean, url: String) {
        val tabs = getTabs(isAutomation)
        val newTab = BrowserTab(url = url, title = if (isAutomation) "X Automation" else "New Tab")
        tabs.add(newTab)
        switchTab(isAutomation, tabs.size - 1)
    }

    fun closeTab(isAutomation: Boolean, index: Int) {
        val tabs = getTabs(isAutomation)
        val pool = getPool(isAutomation)
        val activeIdx = getActiveIndex(isAutomation)

        if (index !in tabs.indices) return

        if (tabs.size <= 1) {
            val fallbackUrl = if (isAutomation) "https://x.com/i/flow/signup" else "https://www.google.com"
            val tab = tabs.firstOrNull() ?: return
            tab.url = fallbackUrl
            tab.title = if (isAutomation) "X Signup Bot" else "Google"
            pool[tab.id]?.loadUrl(fallbackUrl)
            return
        }

        val closingTab = tabs[index]
        val webViewToDestroy = pool.remove(closingTab.id)
        (webViewToDestroy?.parent as? ViewGroup)?.removeView(webViewToDestroy)
        webViewToDestroy?.destroy()

        tabs.removeAt(index)

        val nextIndex = when {
            activeIdx.value >= tabs.size -> tabs.size - 1
            activeIdx.value > index -> activeIdx.value - 1
            else -> activeIdx.value
        }
        switchTab(isAutomation, nextIndex)
    }
}

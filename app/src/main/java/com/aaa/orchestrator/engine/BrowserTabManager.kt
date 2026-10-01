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
 * Enterprise Browser State Manager.
 * Preserves active WebViews, tab DOM, inputs, and JavaScript sessions across
 * bottom navigation tab switching without reloads or data loss.
 */
object BrowserTabManager {
    val tabs = mutableStateListOf<BrowserTab>()
    val activeTabIndex = mutableStateOf(0)
    val webViewPool = mutableMapOf<String, WebView>()
    val bookmarks = mutableStateListOf(
        "https://x.com/i/flow/signup",
        "https://x.com",
        "https://www.google.com",
        "https://duckduckgo.com"
    )
    val history = mutableStateListOf<HistoryItem>()

    init {
        tabs.add(BrowserTab(url = "https://x.com/i/flow/signup", title = "X Signup"))
    }

    val currentTab: BrowserTab?
        get() = tabs.getOrNull(activeTabIndex.value)

    fun getActiveWebView(): WebView? {
        val tab = currentTab ?: return null
        return webViewPool[tab.id]
    }

    fun switchTab(newIndex: Int) {
        if (newIndex in tabs.indices) {
            val oldTab = currentTab
            val newTab = tabs[newIndex]

            oldTab?.let {
                webViewPool[it.id]?.visibility = View.INVISIBLE
            }

            activeTabIndex.value = newIndex
            val newWv = webViewPool[newTab.id]
            if (newWv != null) {
                newWv.visibility = View.VISIBLE
                newWv.bringToFront()
            }
        }
    }

    fun addNewTab(url: String = "https://x.com/i/flow/signup") {
        val newTab = BrowserTab(url = url, title = "New Tab")
        tabs.add(newTab)
        switchTab(tabs.size - 1)
    }

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
        (webViewToDestroy?.parent as? ViewGroup)?.removeView(webViewToDestroy)
        webViewToDestroy?.destroy()

        tabs.removeAt(index)

        val nextIndex = when {
            activeTabIndex.value >= tabs.size -> tabs.size - 1
            activeTabIndex.value > index -> activeTabIndex.value - 1
            else -> activeTabIndex.value
        }
        switchTab(nextIndex)
    }
}

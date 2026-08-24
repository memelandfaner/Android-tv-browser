package com.example.tvbrowser

import android.content.Context
import android.content.SharedPreferences
import android.util.Patterns
import java.net.URLEncoder

class BrowserViewModel(context: Context) {

    private val repository = BrowserRepository(context)
    private val prefs: SharedPreferences = context.getSharedPreferences("browser_settings", Context.MODE_PRIVATE)

    var onStateChanged: ((BrowserUiState) -> Unit)? = null

    var state = BrowserUiState(
        bookmarks = repository.getBookmarks(),
        history = repository.getHistory()
    )
        private set(value) {
            field = value
            onStateChanged?.invoke(value)
        }

    fun canAddTab(): Boolean = state.tabs.size < 4

    fun homeUrl(): String {
        val engine = prefs.getString("search_engine", "google") ?: "google"
        return when (engine.lowercase()) {
            "duckduckgo" -> "https://duckduckgo.com"
            "bing" -> "https://www.bing.com"
            else -> "https://www.google.com"
        }
    }

    private fun searchUrl(encoded: String): String {
        val engine = prefs.getString("search_engine", "google") ?: "google"
        return when (engine.lowercase()) {
            "duckduckgo" -> "https://duckduckgo.com/?q=$encoded"
            "bing" -> "https://www.bing.com/search?q=$encoded"
            else -> "https://www.google.com/search?q=$encoded"
        }
    }

    fun addNewTab(initialUrl: String = homeUrl(), title: String = "Google Iskanje") {
        if (!canAddTab()) return

        val newTab = TabState(
            id = "tab_${System.currentTimeMillis()}",
            title = title,
            url = initialUrl
        )
        val updatedTabs = state.tabs + newTab
        state = state.copy(
            tabs = updatedTabs,
            activeTabIndex = updatedTabs.lastIndex,
            currentUrl = initialUrl,
            activePanel = ActivePanel.NONE
        )
    }

    fun selectTab(index: Int) {
        val tabs = state.tabs
        if (index in tabs.indices) {
            state = state.copy(
                activeTabIndex = index,
                currentUrl = tabs[index].url,
                activePanel = ActivePanel.NONE
            )
        }
    }

    fun closeTab(index: Int) {
        val current = state.tabs
        if (current.size <= 1 || index !in current.indices) return

        val updated = current.toMutableList().apply { removeAt(index) }
        val newActive = when {
            state.activeTabIndex >= updated.size -> updated.lastIndex
            state.activeTabIndex > index -> state.activeTabIndex - 1
            else -> state.activeTabIndex
        }

        state = state.copy(
            tabs = updated,
            activeTabIndex = newActive,
            currentUrl = updated[newActive].url
        )
    }

    fun updateTabUrl(index: Int, url: String) {
        val tabs = state.tabs
        if (index in tabs.indices) {
            tabs[index].url = url
        }
    }

    fun updateTabTitle(index: Int, title: String) {
        val tabs = state.tabs
        if (index !in tabs.indices) return
        val tab = tabs[index]
        if (tab.title == title) return  // no duplicated work

        tab.title = title
        state = state.copy(tabs = tabs.toList())
        repository.addHistory(title, tab.url)
    }

    fun updateTabProgress(index: Int, progress: Int) {
        val tabs = state.tabs
        if (index in tabs.indices) {
            tabs[index].progress = progress
            tabs[index].isLoading = progress in 1..99
            // NE kličemo state = state.copy — to prepreči stotine ponovnih renderTabsBar klicov na stran
        }
    }

    fun toggleCursorMode() {
        state = state.copy(isCursorMode = !state.isCursorMode)
    }

    fun setCursorMode(enabled: Boolean) {
        state = state.copy(isCursorMode = enabled)
    }

    fun showPanel(panel: ActivePanel) {
        state = state.copy(activePanel = if (state.activePanel == panel) ActivePanel.NONE else panel)
    }

    fun hideAllPanels() {
        state = state.copy(activePanel = ActivePanel.NONE)
    }

    fun addBookmark(title: String, url: String, icon: String = "⭐") {
        repository.addBookmark(title, url, icon)
        state = state.copy(bookmarks = repository.getBookmarks())
    }

    fun deleteBookmark(id: Int) {
        repository.deleteBookmark(id)
        state = state.copy(bookmarks = repository.getBookmarks())
    }

    fun setVoiceListening(listening: Boolean, status: String = "Poslušam... Govorite zdaj!") {
        state = state.copy(
            isVoiceListening = listening,
            voiceStatusText = status,
            activePanel = if (listening) ActivePanel.VOICE_HUD else if (state.activePanel == ActivePanel.VOICE_HUD) ActivePanel.NONE else state.activePanel
        )
    }

    fun processUrlInput(input: String): String {
        var raw = input.trim()
        if (raw.isEmpty()) return homeUrl()

        if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
            raw = if (Patterns.WEB_URL.matcher(raw).matches() || (raw.contains(".") && !raw.contains(" "))) {
                "https://$raw"
            } else {
                val current = state.tabs.getOrNull(state.activeTabIndex)?.url ?: ""
                val encoded = try { URLEncoder.encode(raw, "UTF-8") } catch (e: Exception) { raw }
                if (current.contains("youtube.com")) {
                    "https://www.youtube.com/results?search_query=$encoded"
                } else {
                    searchUrl(encoded)
                }
            }
        }
        return raw
    }
}

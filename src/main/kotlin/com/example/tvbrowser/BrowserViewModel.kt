package com.example.tvbrowser

import android.content.Context
import android.util.Patterns
import java.net.URLEncoder

class BrowserViewModel(context: Context) {

    private val repository = BrowserRepository(context)

    var onStateChanged: ((BrowserUiState) -> Unit)? = null

    var state = BrowserUiState(
        bookmarks = repository.getBookmarks(),
        history = repository.getHistory()
    )
        private set(value) {
            field = value
            onStateChanged?.invoke(value)
        }

    private var defaultSearchEngine = "https://www.google.com/search?q="
    private val homeSearchUrl = "https://www.google.com"

    fun addNewTab(initialUrl: String = homeSearchUrl, title: String = "Google Iskanje") {
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
        if (current.size <= 1) return

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
            state = state.copy(currentUrl = url)
        }
    }

    fun updateTabTitle(index: Int, title: String) {
        val tabs = state.tabs
        if (index in tabs.indices) {
            tabs[index].title = title
            state = state.copy(tabs = tabs.toList())
            repository.addHistory(title, tabs[index].url)
            state = state.copy(history = repository.getHistory())
        }
    }

    fun updateTabProgress(index: Int, progress: Int) {
        val tabs = state.tabs
        if (index in tabs.indices) {
            tabs[index].progress = progress
            tabs[index].isLoading = progress in 1..99
            state = state.copy(tabs = tabs.toList())
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
        if (raw.isEmpty()) return homeSearchUrl

        if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
            raw = if (Patterns.WEB_URL.matcher(raw).matches() || (raw.contains(".") && !raw.contains(" "))) {
                "https://$raw"
            } else {
                try {
                    defaultSearchEngine + URLEncoder.encode(raw, "UTF-8")
                } catch (e: Exception) {
                    defaultSearchEngine + raw
                }
            }
        }
        return raw
    }
}

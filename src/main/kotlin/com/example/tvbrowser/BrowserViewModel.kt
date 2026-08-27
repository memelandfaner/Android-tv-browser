package com.example.tvbrowser

import android.content.Context
import android.content.SharedPreferences
import java.net.URLDecoder
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
            state = state.copy(tabs = tabs.toList(), currentUrl = if (index == state.activeTabIndex) url else state.currentUrl)
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

    fun updateBookmark(id: Int, title: String, url: String, icon: String = "⭐") {
        repository.updateBookmark(id, title, url, icon)
        state = state.copy(bookmarks = repository.getBookmarks())
    }

    fun moveBookmark(fromIndex: Int, toIndex: Int) {
        val current = state.bookmarks.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            repository.reorderBookmarks(current)
            state = state.copy(bookmarks = repository.getBookmarks())
        }
    }

    fun deleteBookmark(id: Int) {
        repository.deleteBookmark(id)
        state = state.copy(bookmarks = repository.getBookmarks())
    }

    fun getHistory(): List<HistoryItem> {
        val h = repository.getHistory()
        state = state.copy(history = h)
        return h
    }

    fun clearHistory() {
        repository.clearHistory()
        state = state.copy(history = emptyList())
    }

    fun deleteHistoryItem(id: Int) {
        repository.writableDatabase.delete("history", "id = ?", arrayOf(id.toString()))
        state = state.copy(history = repository.getHistory())
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

        // 0. Decode percent-encoded text if needed (e.g. search%20for%20rtv -> search for rtv)
        try {
            if (raw.contains("%20") || raw.contains("+")) {
                raw = URLDecoder.decode(raw, "UTF-8").trim()
            }
        } catch (ignored: Exception) {}

        // 1. Če uporabnik izrecno začne z "yt:" ali "youtube: ", išči na YouTube
        if (raw.startsWith("yt:", ignoreCase = true) || raw.startsWith("youtube:", ignoreCase = true)) {
            val q = raw.substringAfter(":").trim()
            val encoded = try { URLEncoder.encode(q, "UTF-8") } catch (e: Exception) { q }
            return "https://www.youtube.com/results?search_query=$encoded"
        }

        // 2. Če je že polni veljaven URL (http:// ali https://)
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return raw
        }

        // 3. Če je čista spletna domena (npr. 24ur.com, rtvslo.si, github.com) brez presledkov
        val hasSpace = raw.contains(" ") || raw.contains("\t")
        val isCleanDomain = !hasSpace && raw.contains(".") && !raw.startsWith("search", ignoreCase = true) && raw.matches(Regex("^[a-zA-Z0-9-]+\\.[a-zA-Z]{2,}(/.*)?$"))

        if (isCleanDomain) {
            return "https://$raw"
        }

        // 4. Za VSA ostala iskanja ("search for rtv", "rtv", "slovenija", itd.) -> VEDNO GOOGLE
        var cleanQuery = raw
        val prefixes = listOf("search for ", "search ", "poišči ", "išči ", "najdi ", "google ")
        for (p in prefixes) {
            if (cleanQuery.startsWith(p, ignoreCase = true)) {
                cleanQuery = cleanQuery.substring(p.length).trim()
                break
            }
        }
        val encoded = try { URLEncoder.encode(cleanQuery.ifEmpty { raw }, "UTF-8") } catch (e: Exception) { raw }
        return searchUrl(encoded)
    }
}

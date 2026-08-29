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

        // 1. Če je že polni veljaven URL (http:// ali https://)
        if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            return raw
        }

        // 2. Če je čista spletna domena (npr. 24ur.com, hydrahd.ws, rtvslo.si, github.com) brez presledkov
        val hasSpace = raw.contains(" ") || raw.contains("\t")
        val isCleanDomain = !hasSpace && raw.contains(".") && !raw.startsWith("search", ignoreCase = true) && raw.matches(Regex("^[a-zA-Z0-9-]+\\.[a-zA-Z]{2,}(/.*)?$"))

        if (isCleanDomain) {
            return "https://$raw"
        }

        // 3. Če uporabnik išče pesem, glasbo ali izrecno išče na YouTube
        val activeUrl = state.tabs.getOrNull(state.activeTabIndex)?.url ?: ""
        val isExplicitYt = raw.startsWith("yt:", ignoreCase = true) ||
                           raw.startsWith("youtube:", ignoreCase = true) ||
                           raw.startsWith("youtube ", ignoreCase = true) ||
                           raw.startsWith("yt ", ignoreCase = true) ||
                           raw.startsWith("pesem ", ignoreCase = true) ||
                           raw.startsWith("komad ", ignoreCase = true) ||
                           raw.startsWith("spot ", ignoreCase = true) ||
                           raw.startsWith("predvajaj ", ignoreCase = true) ||
                           raw.startsWith("zavrti ", ignoreCase = true) ||
                           raw.startsWith("poslušaj ", ignoreCase = true) ||
                           raw.contains("na youtube", ignoreCase = true) ||
                           raw.contains("na youtubu", ignoreCase = true)

        val isSearchingOnYouTubeTab = activeUrl.contains("youtube.com", ignoreCase = true) && !raw.startsWith("google", ignoreCase = true)

        if (isExplicitYt || isSearchingOnYouTubeTab) {
            var q = raw
            val ytPrefixes = listOf(
                "odpri youtube in poišči ", "odpri youtube in predvajaj ", "odpri youtube ",
                "poišči na youtubu ", "poišči na youtube ", "poišči v youtubu ",
                "predvajaj pesem od ", "predvajaj glasbo od ", "predvajaj spot od ",
                "predvajaj pesem ", "predvajaj komad ", "predvajaj glasbo ", "predvajaj spot ", "predvajaj ",
                "zavrti pesem od ", "zavrti glasbo ", "zavrti spot ", "zavrti ",
                "poslušaj pesem ", "poslušaj glasbo ", "poslušaj ",
                "pesem od ", "pesem ", "komad ", "spot ",
                "yt:", "youtube:", "youtube ", "yt "
            )
            for (p in ytPrefixes) {
                if (q.startsWith(p, ignoreCase = true)) {
                    q = q.substring(p.length).trim()
                    break
                }
            }
            q = q.replace(Regex("(?i)\\s+na\\s+youtub[ue]"), "").replace(Regex("(?i)\\s+on\\s+youtube"), "").trim()
            val finalQuery = if (q.isNotEmpty()) q else raw
            val encoded = try { URLEncoder.encode(finalQuery, "UTF-8") } catch (e: Exception) { finalQuery }
            return "https://m.youtube.com/results?search_query=$encoded"
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

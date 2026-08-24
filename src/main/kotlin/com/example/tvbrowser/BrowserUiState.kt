package com.example.tvbrowser

data class TabState(
    val id: String,
    var title: String = "Google Iskanje",
    var url: String = "https://www.google.com",
    var progress: Int = 100,
    var canGoBack: Boolean = false,
    var canGoForward: Boolean = false,
    var isLoading: Boolean = false
)

data class BookmarkItem(
    val id: Int = 0,
    val title: String,
    val url: String,
    val icon: String = "⭐",
    val createdAt: Long = System.currentTimeMillis()
)

data class HistoryItem(
    val id: Int = 0,
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis()
)

enum class ActivePanel {
    NONE,
    BOOKMARKS,
    DOWNLOADS,
    SETTINGS,
    VOICE_HUD
}

data class BrowserUiState(
    val tabs: List<TabState> = listOf(TabState(id = "tab_initial")),
    val activeTabIndex: Int = 0,
    val currentUrl: String = "https://www.google.com",
    val isCursorMode: Boolean = false,
    val isAdBlockEnabled: Boolean = true,
    val isDarkModeForced: Boolean = true,
    val activePanel: ActivePanel = ActivePanel.NONE,
    val bookmarks: List<BookmarkItem> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    val voiceStatusText: String = "Poslušam... Govorite zdaj!",
    val isVoiceListening: Boolean = false
)

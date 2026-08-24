package com.example.tvbrowser

import java.net.URLEncoder

enum class CommandType {
    OPEN_URL,
    SEARCH,
    NEW_TAB,
    CLOSE_TAB,
    OPEN_BOOKMARKS,
    OPEN_DOWNLOADS,
    OPEN_SETTINGS,
    TOGGLE_CURSOR,
    RELOAD
}

data class VoiceResult(val type: CommandType, val payload: String = "")

object VoiceCommandEngine {

    fun parse(spokenText: String?): VoiceResult {
        if (spokenText.isNullOrBlank()) {
            return VoiceResult(CommandType.SEARCH, "")
        }

        val raw = spokenText.trim().lowercase()

        // 1. App / Direct portal commands
        if (raw.contains("smarttube") || raw.contains("youtube") || raw.contains("jutub")) {
            val query = raw.replace("odpri", "").replace("youtube", "").replace("smarttube", "").replace("jutub", "").replace("poišči", "").replace("posnetek", "").trim()
            return if (query.isNotEmpty()) {
                VoiceResult(CommandType.OPEN_URL, "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8"))
            } else {
                VoiceResult(CommandType.OPEN_URL, "https://www.youtube.com")
            }
        }

        if (raw.contains("tmdb") || raw.contains("filmi") || raw.contains("film") || raw.contains("serija")) {
            val query = raw.replace("odpri", "").replace("tmdb", "").replace("filmi", "").replace("film", "").replace("poišči", "").trim()
            return if (query.isNotEmpty()) {
                VoiceResult(CommandType.OPEN_URL, "https://www.themoviedb.org/search?query=" + URLEncoder.encode(query, "UTF-8"))
            } else {
                VoiceResult(CommandType.OPEN_URL, "https://www.themoviedb.org")
            }
        }

        if (raw.contains("github") || raw.contains("git")) {
            return VoiceResult(CommandType.OPEN_URL, "https://github.com")
        }

        if (raw.contains("streamnexus") || raw.contains("nexus")) {
            return VoiceResult(CommandType.OPEN_URL, "http://192.168.0.135:3000")
        }

        // 2. Browser Controls
        if (raw.contains("nov zavihek") || raw.contains("odpri zavihek") || raw.contains("new tab")) {
            return VoiceResult(CommandType.NEW_TAB)
        }
        if (raw.contains("zapri zavihek") || raw.contains("close tab")) {
            return VoiceResult(CommandType.CLOSE_TAB)
        }
        if (raw.contains("zaznamki") || raw.contains("priljubljene") || raw.contains("bookmarks")) {
            return VoiceResult(CommandType.OPEN_BOOKMARKS)
        }
        if (raw.contains("prenosi") || raw.contains("downloads")) {
            return VoiceResult(CommandType.OPEN_DOWNLOADS)
        }
        if (raw.contains("nastavitve") || raw.contains("settings")) {
            return VoiceResult(CommandType.OPEN_SETTINGS)
        }
        if (raw.contains("kazalec") || raw.contains("miška") || raw.contains("kurzor") || raw.contains("cursor")) {
            return VoiceResult(CommandType.TOGGLE_CURSOR)
        }
        if (raw.contains("osveži") || raw.contains("ponovno naloži") || raw.contains("reload")) {
            return VoiceResult(CommandType.RELOAD)
        }

        // 3. Fallback: Search on Google
        val cleaned = raw.replace("poišči", "").replace("išči", "").replace("najdi", "").replace("google", "").trim()
        return VoiceResult(CommandType.SEARCH, cleaned.ifEmpty { raw })
    }
}

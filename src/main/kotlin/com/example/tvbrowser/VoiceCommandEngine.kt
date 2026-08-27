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

        // 1. App / Direct portal commands - le če izrecno zahteva odpiranje YouTube strani
        if (raw == "youtube" || raw == "odpri youtube" || raw == "open youtube" || raw == "smarttube" || raw == "odpri smarttube") {
            return VoiceResult(CommandType.OPEN_URL, "https://www.youtube.com")
        }
        if (raw.startsWith("youtube ") || raw.startsWith("poišči na youtube") || raw.startsWith("poišči na youtubu")) {
            val query = raw.replace("youtube", "").replace("smarttube", "").replace("jutub", "").replace("poišči na youtubu", "").replace("poišči na youtube", "").replace("poišči v youtubu", "").replace("poišči", "").trim()
            return if (query.isNotEmpty()) {
                VoiceResult(CommandType.OPEN_URL, "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8"))
            } else {
                VoiceResult(CommandType.OPEN_URL, "https://www.youtube.com")
            }
        }

        if (raw == "tmdb" || raw == "odpri tmdb") {
            return VoiceResult(CommandType.OPEN_URL, "https://www.themoviedb.org")
        }

        if (raw == "github" || raw == "odpri github") {
            return VoiceResult(CommandType.OPEN_URL, "https://github.com")
        }

        if (raw == "streamnexus" || raw == "odpri streamnexus") {
            return VoiceResult(CommandType.OPEN_URL, "https://www.themoviedb.org/movie")
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

        // 3. Glavni privzeti iskalnik: VEDNO GOOGLE za vsa iskanja ("search for rtv", "poišči...", itd.)
        var cleanQuery = raw
        val prefixes = listOf("search for ", "search ", "poišči ", "išči ", "najdi ", "google ")
        for (p in prefixes) {
            if (cleanQuery.startsWith(p, ignoreCase = true)) {
                cleanQuery = cleanQuery.substring(p.length).trim()
                break
            }
        }
        return VoiceResult(CommandType.SEARCH, cleanQuery.ifEmpty { raw })
    }
}

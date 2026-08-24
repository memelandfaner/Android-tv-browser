package com.example.tvbrowser;

public class VoiceCommandEngine {

    public enum ActionType {
        OPEN_URL,
        SEARCH,
        OPEN_BOOKMARKS,
        OPEN_DOWNLOADS,
        OPEN_SETTINGS,
        TOGGLE_CURSOR,
        NEW_TAB,
        CLOSE_TAB,
        RELOAD
    }

    public static class CommandResult {
        public ActionType type;
        public String payload;
        public String spokenText;

        public CommandResult(ActionType type, String payload, String spokenText) {
            this.type = type;
            this.payload = payload;
            this.spokenText = spokenText;
        }
    }

    public static CommandResult parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new CommandResult(ActionType.SEARCH, "", "");
        }

        String raw = text.trim();
        String q = raw.toLowerCase();

        // 1. Direct App / Portal Intenti
        if (q.contains("youtube") || q.contains("jutub") || q.contains("ju tub")) {
            return new CommandResult(ActionType.OPEN_URL, "https://www.youtube.com", raw);
        }
        if (q.contains("stream") || q.contains("nexus") || q.contains("film") || q.contains("filmi") || q.contains("serij")) {
            return new CommandResult(ActionType.OPEN_URL, "http://192.168.0.135:3000", raw);
        }
        if (q.contains("tmdb") || q.contains("the movie database") || q.contains("baza filmov")) {
            return new CommandResult(ActionType.OPEN_URL, "https://www.themoviedb.org", raw);
        }
        if (q.equals("google") || q.contains("odpri google")) {
            return new CommandResult(ActionType.OPEN_URL, "https://www.google.com", raw);
        }
        if (q.contains("reddit")) {
            return new CommandResult(ActionType.OPEN_URL, "https://www.reddit.com", raw);
        }
        if (q.contains("github") || q.contains("git hub")) {
            return new CommandResult(ActionType.OPEN_URL, "https://github.com", raw);
        }
        if (q.contains("twitch")) {
            return new CommandResult(ActionType.OPEN_URL, "https://www.twitch.tv", raw);
        }
        if (q.contains("radio") || q.contains("radio garden") || q.contains("glasb")) {
            return new CommandResult(ActionType.OPEN_URL, "https://radio.garden", raw);
        }

        // 2. Tab Management Intenti
        if (q.contains("nov zavihek") || q.contains("novi zavihek") || q.contains("new tab") || q.contains("dodaj zavihek")) {
            return new CommandResult(ActionType.NEW_TAB, null, raw);
        }
        if (q.contains("zapri zavihek") || q.contains("close tab") || q.contains("odstrani zavihek")) {
            return new CommandResult(ActionType.CLOSE_TAB, null, raw);
        }

        // 3. UI Navigation Intenti
        if (q.contains("zaznamk") || q.contains("priljubljen") || q.contains("bookmark")) {
            return new CommandResult(ActionType.OPEN_BOOKMARKS, null, raw);
        }
        if (q.contains("prenos") || q.contains("datotek") || q.contains("download")) {
            return new CommandResult(ActionType.OPEN_DOWNLOADS, null, raw);
        }
        if (q.contains("nastavitv") || q.contains("setting") || q.contains("opcij")) {
            return new CommandResult(ActionType.OPEN_SETTINGS, null, raw);
        }
        if (q.contains("kurzor") || q.contains("miš") || q.contains("pointer") || q.contains("kazalec")) {
            return new CommandResult(ActionType.TOGGLE_CURSOR, null, raw);
        }
        if (q.contains("osveži") || q.contains("reload") || q.contains("ponovno naloži")) {
            return new CommandResult(ActionType.RELOAD, null, raw);
        }

        // 4. Prefix Search / AI Query Stripping
        String searchPayload = raw;
        String[] prefixes = {"poišči", "išči", "najdi", "search", "pokaži", "odpri"};
        for (String p : prefixes) {
            if (q.startsWith(p + " ")) {
                searchPayload = raw.substring(p.length() + 1).trim();
                break;
            }
        }

        return new CommandResult(ActionType.SEARCH, searchPayload, raw);
    }
}

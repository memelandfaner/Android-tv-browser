package com.example.tvbrowser;

import android.webkit.WebResourceResponse;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class AdBlockEngine {
    private boolean mEnabled = true;
    private final Set<String> mBlockedPatterns = new HashSet<>();

    public AdBlockEngine() {
        // High-risk ad networks, betting domains & trackers
        String[] patterns = {
            "20bet", "1xbet", "popads", "popcash", "adsterra", "exoclick", "propellerads",
            "monetag", "clickadu", "trafficstars", "juicyads", "bestadbid", "onclickalgo",
            "alwingulla", "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adservice.google", "amazon-adsystem.com", "adnxs.com", "criteo.com", "taboola.com",
            "outbrain.com", "histats.com", "tsyndicate.com", "vignette.wikia.nocookie.net",
            "betwinner", "bet365", "parimatch", "mostbet", "pinup", "vulkan", "1win"
        };
        for (String p : patterns) {
            mBlockedPatterns.add(p.toLowerCase());
        }
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    public boolean isBlocked(String url) {
        if (!mEnabled || url == null) return false;
        String lower = url.toLowerCase();

        for (String pattern : mBlockedPatterns) {
            if (lower.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    public boolean isDevToolBlocker(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.contains("disable-devtool") || lower.endsWith("disable-devtool.js") || lower.contains("disable-devtool.js?");
    }

    public static WebResourceResponse createEmptyResponse(String mimeType) {
        return new WebResourceResponse(
            mimeType != null ? mimeType : "text/plain",
            "UTF-8",
            new ByteArrayInputStream(new byte[0])
        );
    }
}

package com.example.tvbrowser

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class AdBlockEngine {
    var isEnabled: Boolean = true
    var isAntiAntiAdblockEnabled: Boolean = true
    var isCosmeticFilteringEnabled: Boolean = true

    // 🚫 EasyList & EasyPrivacy High-Impact Ad, Tracking & Betting Networks
    private val blockedDomains = hashSetOf(
        // Betting & Popunder Hijack Networks
        "20bet.com", "1xbet.com", "1xbet.eu", "betwinner.com", "melbet.com",
        "monetag.com", "popads.net", "popcash.net", "propellerads.com",
        "clickadu.com", "adsterra.com", "exoclick.com", "juicyads.com",
        "trafficjunky.net", "trafficfactory.biz", "hilltopads.net", "richpush.co",
        "admaven.com", "pushground.com", "zeropark.com",

        // Major Ad Exchanges & Trackers (EasyList / EasyPrivacy)
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "adservice.google.com", "adnxs.com", "outbrain.com", "taboola.com",
        "revcontent.com", "mgid.com", "adblade.com", "adroll.com",
        "criteo.com", "rubiconproject.com", "pubmatic.com", "openx.net",
        "smartadserver.com", "yieldmo.com", "inmobi.com", "applovin.com",
        "unityads.unity3d.com", "vungle.com", "chartboost.com", "adcolony.com",
        "fyber.com", "ironsrc.com", "smaato.net", "amazon-adsystem.com",
        "facebook.com/tr", "analytics.google.com", "googletagmanager.com",
        "hotjar.com", "segment.io", "mixpanel.com", "scorecardresearch.com",
        "quantserve.com", "imrworldwide.com", "moatads.com", "casalemedia.com",
        "advertising.com", "admob.com", "adtech.de", "serving-sys.com",
        "spotxchange.com", "zemanta.com", "sovrn.com", "lijit.com"
    )

    fun isBlocked(url: String?): Boolean {
        if (!isEnabled || url == null) return false
        val lower = url.lowercase()
        return blockedDomains.any { lower.contains(it) }
    }

    // 🛡️ Anti-Anti-Adblock script detection:
    // Intercepts detection scripts so we can return a valid 200 OK JS that satisfies their checks
    fun isAntiAdblockScript(url: String?): Boolean {
        if (!isAntiAntiAdblockEnabled || url == null) return false
        val lower = url.lowercase()
        return lower.contains("fuckadblock") ||
               lower.contains("blockadblock") ||
               lower.contains("adblock-detect") ||
               lower.contains("disable-adblock") ||
               lower.contains("adblockdetector") ||
               lower.contains("adblock-checker") ||
               lower.contains("detect-adblock")
    }

    fun isDevToolBlocker(url: String?): Boolean {
        if (url == null) return false
        val lower = url.lowercase()
        return lower.contains("disable-devtool") || lower.contains("devtools-detector")
    }

    fun createEmptyJsResponse(): WebResourceResponse {
        val emptyJs = "// TV Browser Anti-Anti-AdBlock Neutralized\nwindow.canRunAds=true;window.google_ad_status=1;\n".toByteArray(StandardCharsets.UTF_8)
        val stream = ByteArrayInputStream(emptyJs)
        val headers = hashMapOf(
            "Access-Control-Allow-Origin" to "*",
            "Content-Type" to "application/javascript",
            "Cache-Control" to "no-cache"
        )
        return WebResourceResponse("application/javascript", "UTF-8", 200, "OK", headers, stream)
    }

    companion object {
        fun createEmptyResponse(mimeType: String): WebResourceResponse {
            val emptyStream = ByteArrayInputStream("".toByteArray(StandardCharsets.UTF_8))
            val headers = hashMapOf(
                "Access-Control-Allow-Origin" to "*",
                "Cache-Control" to "no-cache"
            )
            return WebResourceResponse(mimeType, "UTF-8", 200, "OK", headers, emptyStream)
        }
    }
}

package com.example.tvbrowser

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class AdBlockEngine {
    var isEnabled: Boolean = true

    private val blockedDomains = hashSetOf(
        "20bet.com", "1xbet.com", "1xbet.eu", "betwinner.com", "melbet.com",
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "adservice.google.com", "adnxs.com", "monetag.com", "popads.net",
        "popcash.net", "propellerads.com", "clickadu.com", "adsterra.com",
        "exoclick.com", "juicyads.com", "trafficjunky.net", "outbrain.com",
        "taboola.com", "revcontent.com", "mgid.com", "adblade.com",
        "adroll.com", "criteo.com", "rubiconproject.com", "pubmatic.com",
        "openx.net", "smartadserver.com", "yieldmo.com", "inmobi.com",
        "applovin.com", "unityads.unity3d.com", "vungle.com", "chartboost.com",
        "adcolony.com", "fyber.com", "ironsrc.com", "smaato.net",
        "amazon-adsystem.com", "facebook.com/tr", "analytics.google.com",
        "googletagmanager.com", "hotjar.com", "segment.io", "mixpanel.com"
    )

    fun isBlocked(url: String?): Boolean {
        if (!isEnabled || url == null) return false
        val lower = url.lowercase()
        return blockedDomains.any { lower.contains(it) }
    }

    fun isDevToolBlocker(url: String?): Boolean {
        if (url == null) return false
        val lower = url.lowercase()
        return lower.contains("disable-devtool") || lower.contains("devtools-detector")
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

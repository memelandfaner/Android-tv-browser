package com.example.tvbrowser

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets

class AdBlockEngine(context: Context? = null) {
    var isEnabled: Boolean = true
    var isAntiAntiAdblockEnabled: Boolean = true
    var isCosmeticFilteringEnabled: Boolean = true
    var isAntiAmpEnabled: Boolean = true
    var isStripTrackingParamsEnabled: Boolean = true

    // 🛡️ Comprehensive EasyList, EasyPrivacy & Fanboy Annoyances Blocklist
    private val blockedHostRules = hashSetOf(
        // === Betting, Malware & Popunder Hijack Networks ===
        "20bet.com", "1xbet.com", "1xbet.eu", "betwinner.com", "melbet.com",
        "monetag.com", "popads.net", "popcash.net", "propellerads.com",
        "clickadu.com", "adsterra.com", "exoclick.com", "juicyads.com",
        "trafficjunky.net", "trafficfactory.biz", "hilltopads.net", "richpush.co",
        "admaven.com", "pushground.com", "zeropark.com", "adxpansion.com",
        "clickaine.com", "onclickalgo.com", "tsyndicate.com", "adxxx.com",
        "mgid.com", "revcontent.com", "taboola.com", "outbrain.com",

        // === Major Ad Exchanges & Networks (EasyList) ===
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "adservice.google.com", "adnxs.com", "criteo.com", "criteo.net",
        "rubiconproject.com", "pubmatic.com", "openx.net", "smartadserver.com",
        "yieldmo.com", "inmobi.com", "applovin.com", "unityads.unity3d.com",
        "vungle.com", "chartboost.com", "adcolony.com", "fyber.com",
        "ironsrc.com", "smaato.net", "amazon-adsystem.com", "admob.com",
        "adtech.de", "serving-sys.com", "spotxchange.com", "zemanta.com",
        "sovrn.com", "lijit.com", "casalemedia.com", "advertising.com",
        "bidswitch.net", "teads.tv", "sharethrough.com", "exponential.com",
        "contextweb.com", "media.net", "adblade.com", "adroll.com",
        "smartclip.net", "triplelift.com", "unrulymedia.com", "yieldlab.net",
        "flashtalking.com", "gumgum.com", "indexexchange.com", "kargo.com",
        "nativo.com", "undertone.com", "conversantmedia.com",

        // === Telemetry & Invasive User Tracking (EasyPrivacy) ===
        "analytics.google.com", "hotjar.com", "segment.io", "segment.com",
        "mixpanel.com", "scorecardresearch.com", "quantserve.com",
        "imrworldwide.com", "moatads.com", "crazyegg.com", "kissmetrics.com",
        "mouseflow.com", "fullstory.com", "amplitude.com", "branch.io",
        "appsflyer.com", "adjust.com", "kochava.com", "singular.net",
        "yandex.ru/metrika", "mc.yandex.ru", "statcounter.com", "chartbeat.com",
        "newrelic.com", "datadoghq.com", "clarity.ms", "bugsnag.com",
        "sentry.io", "telemetry.mozilla.org", "graph.facebook.com/tr",
        "tr.snapchat.com", "analytics.twitter.com", "analytics.tiktok.com",
        "byteoversea.com", "ib.adnxs.com", "pixel.wp.com",

        // === Annoyances, Cookie Walls & Push Prompt Networks ===
        "onesignal.com", "pushassist.com", "subscribers.com", "izooto.com",
        "pushwoosh.com", "wonderpush.com", "webpushr.com", "gravitec.net",
        "optinmonster.com", "sumo.com", "privy.com", "sleeknote.com",
        "trustarc.com", "cookielaw.org", "onetrust.com", "cookiebot.com",
        "didomi.io", "quantcast.mgr.consensu.org", "consensu.org"
    )

    init {
        loadCustomRulesFromDisk(context)
    }

    private fun loadCustomRulesFromDisk(context: Context?) {
        if (context == null) return
        try {
            val cacheFile = File(context.filesDir, "custom_adblock_rules.txt")
            if (cacheFile.exists()) {
                cacheFile.forEachLine { line ->
                    val clean = line.trim().lowercase()
                    if (clean.isNotEmpty() && !clean.startsWith("#")) {
                        blockedHostRules.add(clean)
                    }
                }
            }
        } catch (ignored: Exception) {}
    }

    /**
     * Fast Host-Based Matching:
     * Extracts host from URL and checks if host or any parent domain is in the blocklist.
     */
    fun isBlocked(url: String?): Boolean {
        if (!isEnabled || url == null || url.isEmpty()) return false

        try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: return false

            // Check exact host or parent host domains (e.g., "ads.google.com" -> "google.com")
            var currentHost: String? = host
            while (currentHost != null && currentHost.contains(".")) {
                if (blockedHostRules.contains(currentHost)) {
                    return true
                }
                val dotIdx = currentHost.indexOf('.')
                if (dotIdx != -1 && dotIdx < currentHost.length - 1) {
                    currentHost = currentHost.substring(dotIdx + 1)
                } else {
                    break
                }
            }
        } catch (ignored: Exception) {}

        return false
    }

    /**
     * Anti-Anti-AdBlock Detection:
     * Intercepts known anti-adblock detector libraries to return valid dummy 200 OK JS.
     */
    fun isAntiAdblockScript(url: String?): Boolean {
        if (!isAntiAntiAdblockEnabled || url == null) return false
        val lower = url.lowercase()
        return lower.contains("fuckadblock") ||
                lower.contains("blockadblock") ||
                lower.contains("adblock-detect") ||
                lower.contains("disable-adblock") ||
                lower.contains("adblockdetector") ||
                lower.contains("adblock-checker") ||
                lower.contains("anti-adblock") ||
                lower.contains("antiadblock")
    }

    /**
     * DevTool Blocker Protection:
     * Intercepts third-party anti-inspection scripts that crash TV WebViews.
     */
    fun isDevToolBlocker(url: String?): Boolean {
        if (url == null) return false
        val lower = url.lowercase()
        return lower.contains("disable-devtool") ||
                lower.contains("devtools-detector") ||
                lower.contains("console-ban")
    }

    /**
     * Anti-AMP & Tracking Stripper:
     * 1. Converts Google AMP URLs (e.g. google.com/amp/s/example.com) to canonical https://example.com
     * 2. Strips surveillance parameters (utm_*, fbclid, gclid, etc.)
     */
    fun sanitizeUrl(rawUrl: String): String {
        var cleanUrl = rawUrl.trim()

        // 1. Anti-AMP Redirection
        if (isAntiAmpEnabled) {
            if (cleanUrl.contains("google.com/amp/s/")) {
                val idx = cleanUrl.indexOf("google.com/amp/s/")
                cleanUrl = "https://" + cleanUrl.substring(idx + 17)
            } else if (cleanUrl.contains(".ampproject.org/c/s/")) {
                val idx = cleanUrl.indexOf(".ampproject.org/c/s/")
                cleanUrl = "https://" + cleanUrl.substring(idx + 20)
            }
        }

        // 2. Strip Tracking Surveillance Query Parameters
        if (isStripTrackingParamsEnabled && cleanUrl.contains("?")) {
            try {
                val uri = Uri.parse(cleanUrl)
                val cleanBuilder = uri.buildUpon().clearQuery()
                for (param in uri.queryParameterNames) {
                    val lower = param.lowercase()
                    if (lower.startsWith("utm_") ||
                        lower == "fbclid" ||
                        lower == "gclid" ||
                        lower == "msclkid" ||
                        lower == "yclid" ||
                        lower == "mc_cid" ||
                        lower == "mc_eid" ||
                        lower == "_hsenc" ||
                        lower == "_hsmi") {
                        continue // Drop surveillance tracker
                    }
                    for (valStr in uri.getQueryParameters(param)) {
                        cleanBuilder.appendQueryParameter(param, valStr)
                    }
                }
                cleanUrl = cleanBuilder.build().toString()
            } catch (ignored: Exception) {}
        }

        return cleanUrl
    }

    fun createEmptyJsResponse(): WebResourceResponse {
        val dummyJs = "/* FreeNet Anti-Anti-AdBlock Shield Active */ window.canRunAds=true; window.adblock=false; window.google_ad_status=1;"
        val data = dummyJs.toByteArray(StandardCharsets.UTF_8)
        return WebResourceResponse("application/javascript", "UTF-8", 200, "OK", mapOf("Access-Control-Allow-Origin" to "*"), ByteArrayInputStream(data))
    }

    companion object {
        fun createEmptyResponse(mimeType: String = "text/plain"): WebResourceResponse {
            val emptyStream = ByteArrayInputStream(ByteArray(0))
            return WebResourceResponse(
                mimeType,
                "UTF-8",
                200,
                "OK",
                mapOf(
                    "Access-Control-Allow-Origin" to "*",
                    "Cache-Control" to "no-cache"
                ),
                emptyStream
            )
        }
    }
}

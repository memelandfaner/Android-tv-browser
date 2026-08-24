package com.example.tvbrowser

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class AdBlockEngine(context: Context? = null) {
    var isEnabled: Boolean = true
    var isAntiAntiAdblockEnabled: Boolean = true
    var isCosmeticFilteringEnabled: Boolean = true
    var isAntiAmpEnabled: Boolean = true
    var isStripTrackingParamsEnabled: Boolean = true

    // 🛡️ Pre-bundled High-Traffic Ad & Tracking Rules
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

        // === Push Prompt Networks (Non-CMP) ===
        "onesignal.com", "pushassist.com", "subscribers.com", "izooto.com",
        "pushwoosh.com", "wonderpush.com", "webpushr.com", "gravitec.net",
        "optinmonster.com", "sumo.com", "privy.com", "sleeknote.com"
    )

    // 🔒 Essential Whitelist (Critical for Account Login, Banking & Essential Stream Services)
    private val whitelistDomains = hashSetOf(
        "youtube.com", "m.youtube.com", "music.youtube.com", "googlevideo.com", "ytimg.com",
        "accounts.google.com", "myaccount.google.com", "accounts.youtube.com",
        "nlb.si", "nkbm.si", "skb.si", "dh.si", "intesa.si", "intesasanpaolobank.si",
        "sparkasse.si", "revolut.com", "n26.com", "delavska-hranilnica.si",
        "bks-bank.si", "unicreditbank.si", "lon.si", "gorenjska-banka.si",
        "rtvslo.si", "24ur.com", "siol.net", "github.com", "themoviedb.org", "tmdb.org"
    )

    init {
        loadCachedRules(context)
        startBackgroundEasyListUpdate(context)
    }

    private fun loadCachedRules(context: Context?) {
        if (context == null) return
        try {
            val cacheFile = File(context.filesDir, "easylist_cache.txt")
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
     * Downloads & Parses EasyList / EasyPrivacy (1x per day in background)
     * Matches standard ||domain^ and ||domain/ rules
     */
    private fun startBackgroundEasyListUpdate(context: Context?) {
        if (context == null) return
        Thread {
            try {
                val prefs = context.getSharedPreferences("freenet_adblock", Context.MODE_PRIVATE)
                val lastUpdate = prefs.getLong("last_easylist_update", 0L)
                val now = System.currentTimeMillis()
                val oneDayMs = 24 * 60 * 60 * 1000L

                val cacheFile = File(context.filesDir, "easylist_cache.txt")
                if (!cacheFile.exists() || (now - lastUpdate > oneDayMs)) {
                    val parsedDomains = mutableSetOf<String>()

                    val urls = listOf(
                        "https://easylist.to/easylist/easylist.txt",
                        "https://easylist.to/easylist/easyprivacy.txt"
                    )

                    for (u in urls) {
                        try {
                            val conn = URL(u).openConnection() as HttpURLConnection
                            conn.connectTimeout = 10000
                            conn.readTimeout = 15000
                            conn.requestMethod = "GET"
                            if (conn.responseCode == 200) {
                                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                                var line: String? = reader.readLine()
                                while (line != null) {
                                    val trimmed = line.trim()
                                    // Parse EasyList host rules: ||example.com^
                                    if (trimmed.startsWith("||")) {
                                        val endIdx = trimmed.indexOfAny(charArrayOf('^', '/', '$', '?'))
                                        val domain = if (endIdx != -1) {
                                            trimmed.substring(2, endIdx)
                                        } else {
                                            trimmed.substring(2)
                                        }.lowercase().trim()

                                        if (domain.contains(".") && !domain.contains("*") && !whitelistDomains.contains(domain)) {
                                            parsedDomains.add(domain)
                                        }
                                    }
                                    line = reader.readLine()
                                }
                                reader.close()
                            }
                            conn.disconnect()
                        } catch (ignored: Exception) {}
                    }

                    if (parsedDomains.isNotEmpty()) {
                        cacheFile.bufferedWriter().use { writer ->
                            parsedDomains.forEach { d ->
                                writer.write(d)
                                writer.newLine()
                            }
                        }
                        synchronized(blockedHostRules) {
                            blockedHostRules.addAll(parsedDomains)
                        }
                        prefs.edit().putLong("last_easylist_update", now).apply()
                    }
                }
            } catch (ignored: Exception) {}
        }.start()
    }

    /**
     * Fast Host-Based Matching with Whitelist Guard:
     */
    fun isBlocked(url: String?): Boolean {
        if (!isEnabled || url == null || url.isEmpty()) return false

        try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: return false

            // Whitelist Check: Never block login, streaming backbone, or banking
            var checkHost: String? = host
            while (checkHost != null && checkHost.contains(".")) {
                if (whitelistDomains.contains(checkHost)) {
                    return false
                }
                val dotIdx = checkHost.indexOf('.')
                if (dotIdx != -1 && dotIdx < checkHost.length - 1) {
                    checkHost = checkHost.substring(dotIdx + 1)
                } else {
                    break
                }
            }

            // Blocklist Check: Host or parent domain
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

    fun isDevToolBlocker(url: String?): Boolean {
        if (url == null) return false
        val lower = url.lowercase()
        return lower.contains("disable-devtool") ||
                lower.contains("devtools-detector") ||
                lower.contains("console-ban")
    }

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

        // 2. Strip Surveillance Parameters
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
                        continue
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

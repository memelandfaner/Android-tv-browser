package com.example.tvbrowser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.*

enum class UserAgentMode {
    TV, DESKTOP, MOBILE
}

@SuppressLint("SetJavaScriptEnabled")
class ChromiumEngineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    var onProgressChangedListener: ((Int) -> Unit)? = null
    var onTitleReceivedListener: ((String) -> Unit)? = null
    var onUrlChangedListener: ((String) -> Unit)? = null
    var onShowCustomViewListener: ((View, WebChromeClient.CustomViewCallback) -> Unit)? = null
    var onHideCustomViewListener: (() -> Unit)? = null
    var onEdgeReachedTopListener: (() -> Unit)? = null
    var onToggleFullscreenRequestListener: ((Boolean?) -> Unit)? = null

    val adBlockEngine = AdBlockEngine(context)
    var currentUaMode: UserAgentMode = UserAgentMode.TV
    var lastVideoUrl: String? = null

    init {
        configureUngoogledChromiumSettings()
    }

    fun setUserAgentMode(mode: UserAgentMode) {
        currentUaMode = mode
        val s = settings
        when (mode) {
            UserAgentMode.TV -> {
                s.userAgentString = "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                s.useWideViewPort = true
                s.loadWithOverviewMode = true
            }
            UserAgentMode.DESKTOP -> {
                s.userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                s.useWideViewPort = true
                s.loadWithOverviewMode = true
            }
            UserAgentMode.MOBILE -> {
                s.userAgentString = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.3 Mobile/15E148 Safari/604.1"
                s.useWideViewPort = false
                s.loadWithOverviewMode = false
            }
        }
    }

    override fun loadUrl(url: String) {
        val prefs = context.getSharedPreferences("browser_settings", Context.MODE_PRIVATE)
        val uaOrdinal = prefs.getInt("user_agent_mode", UserAgentMode.TV.ordinal)
        setUserAgentMode(UserAgentMode.values().getOrElse(uaOrdinal) { UserAgentMode.TV })
        super.loadUrl(url)
    }

    override fun loadUrl(url: String, additionalHttpHeaders: Map<String, String>) {
        val prefs = context.getSharedPreferences("browser_settings", Context.MODE_PRIVATE)
        val uaOrdinal = prefs.getInt("user_agent_mode", UserAgentMode.TV.ordinal)
        setUserAgentMode(UserAgentMode.values().getOrElse(uaOrdinal) { UserAgentMode.TV })
        super.loadUrl(url, additionalHttpHeaders)
    }

    private fun configureUngoogledChromiumSettings() {
        val s = settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.allowFileAccessFromFileURLs = true
        s.allowUniversalAccessFromFileURLs = true
        s.setSupportZoom(false)
        s.builtInZoomControls = false
        s.javaScriptCanOpenWindowsAutomatically = false
        s.setSupportMultipleWindows(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            s.safeBrowsingEnabled = false
        }
        s.setRenderPriority(WebSettings.RenderPriority.HIGH)
        s.cacheMode = WebSettings.LOAD_DEFAULT

        // Default: TV / Optimized Pixel 5 User-Agent
        setUserAgentMode(UserAgentMode.TV)
        s.textZoom = 75
        s.defaultFontSize = 15
        s.defaultFixedFontSize = 13
        s.setNeedInitialFocus(true)

        isFocusable = true
        isFocusableInTouchMode = true

        // 🌙 Forced Dark Engine (Android 10+ / API 29+)
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                s.forceDark = WebSettings.FORCE_DARK_ON
            }
        } catch (ignored: Throwable) {}
        try {
            s.javaClass.getMethod("setAlgorithmicDarkeningAllowed", Boolean::class.javaPrimitiveType).invoke(s, true)
        } catch (ignored: Throwable) {}

        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // 🍪 Cookie Policy: Allow 3rd-party cookies for HLS media streams & auth tokens
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cm.setAcceptThirdPartyCookies(this, true)
        }
        try {
            cm.setCookie(".youtube.com", "SOCS=CAESEwgDEgk0ODE3Nzk3MjQaAnNsIAEaBgiA_LyaBg; path=/; domain=.youtube.com; SameSite=Lax")
            cm.setCookie(".youtube.com", "CONSENT=YES+cb.20230531-04-p0.sl+FX+999; path=/; domain=.youtube.com")
            cm.setCookie(".google.com", "SOCS=CAESEwgDEgk0ODE3Nzk3MjQaAnNsIAEaBgiA_LyaBg; path=/; domain=.google.com; SameSite=Lax")
            cm.setCookie(".google.com", "CONSENT=YES+cb.20230531-04-p0.sl+FX+999; path=/; domain=.google.com")
            cm.flush()
        } catch (ignored: Exception) {}

        addJavascriptInterface(AndroidNativeBridge(), "AndroidNativeBridge")

        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            DownloadHandler.enqueueDownload(context, url, userAgent, contentDisposition, mimeType)
        }

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val rawUrl = request?.url?.toString() ?: return false

                // 🛡️ 0. Google Warning / Interstitial Direct Destination Bypass
                if (rawUrl.contains("google.") && (rawUrl.contains("/interstitial") || rawUrl.contains("/url?") || rawUrl.contains("url="))) {
                    try {
                        val parsed = Uri.parse(rawUrl)
                        val target = parsed.getQueryParameter("url") ?: parsed.getQueryParameter("q")
                        if (!target.isNullOrEmpty() && (target.startsWith("http://") || target.startsWith("https://"))) {
                            view?.loadUrl(target)
                            return true
                        }
                    } catch (ignored: Exception) {}
                }

                // ⚡ 1. Anti-AMP & Tracking Stripper (Only for main-frame navigations)
                val cleanUrl = adBlockEngine.sanitizeUrl(rawUrl)
                if (request != null && request.isForMainFrame && cleanUrl != rawUrl) {
                    view?.loadUrl(cleanUrl)
                    return true
                }

                val url = cleanUrl
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        return true
                    }
                }

                // Top-Frame Lock against betting popunders
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && request != null && request.isForMainFrame) {
                    if (adBlockEngine.isBlocked(url)) return true
                }
                return false
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null

                // 🛡️ 1. Anti-Anti-AdBlock Interceptor: Return dummy JS to satisfy detection
                if (adBlockEngine.isAntiAdblockScript(url)) {
                    return adBlockEngine.createEmptyJsResponse()
                }

                // 🛡️ 2. DevTool Crash Protection
                if (adBlockEngine.isDevToolBlocker(url)) {
                    return adBlockEngine.createEmptyJsResponse()
                }

                // 🎬 Video Stream Sniffer for HLS / DASH / MP4 native FullscreenVideo playback
                val lowerUrl = url.lowercase()
                if (lowerUrl.contains(".m3u8") || lowerUrl.contains(".mpd") || lowerUrl.contains("manifest") || (lowerUrl.contains(".mp4") && !lowerUrl.contains("favicon"))) {
                    lastVideoUrl = url
                    Log.d("TvChromium", "Captured video stream URL: $url")
                }

                // 🛡️ 3. YouTube Ad & Telemetry Stream Blocking
                if (adBlockEngine.isYouTubeAd(url)) {
                    return AdBlockEngine.createEmptyResponse("text/plain")
                }

                // 🚫 4. Ad & Popunder Domain Blocking
                if (adBlockEngine.isBlocked(url)) {
                    return AdBlockEngine.createEmptyResponse("text/plain")
                }

                return super.shouldInterceptRequest(view, request)
            }

            private fun runScriptInjections() {
                val prefs = context.getSharedPreferences("browser_settings", Context.MODE_PRIVATE)
                val ytFreedom = prefs.getBoolean("yt_freedom_enabled", true)
                UserScriptManager.injectAll(
                    this@ChromiumEngineView,
                    adBlockEngine.isAntiAntiAdblockEnabled,
                    adBlockEngine.isCosmeticFilteringEnabled,
                    ytFreedom
                )
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (url != null) onUrlChangedListener?.invoke(url)
                runScriptInjections()
            }

            // ⚡ Ultra-Early Injection as soon as DOM is committed
            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url)
                runScriptInjections()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (url != null) onUrlChangedListener?.invoke(url)
                runScriptInjections()

                if (url != null && (url.contains("youtube.com/watch") || url.contains("m.youtube.com/watch"))) {
                    val triggerKey = {
                        requestFocus()
                        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
                        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
                    }
                    postDelayed({ triggerKey() }, 400)
                    postDelayed({ triggerKey() }, 1000)
                    postDelayed({ triggerKey() }, 1800)
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    val rawUrl = request.url?.toString() ?: ""
                    val failingUrl = android.text.Html.escapeHtml(rawUrl)
                    val prefs = context.getSharedPreferences("browser_settings", Context.MODE_PRIVATE)
                    val engine = prefs.getString("search_engine", "google") ?: "google"
                    val homeUrl = when (engine.lowercase()) {
                        "duckduckgo" -> "https://duckduckgo.com"
                        "bing" -> "https://www.bing.com"
                        else -> "https://www.google.com"
                    }
                    val errorHtml = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="utf-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <style>
                                body {
                                    background: #0b0f19;
                                    color: #f8fafc;
                                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                                    display: flex;
                                    flex-direction: column;
                                    align-items: center;
                                    justify-content: center;
                                    height: 100vh;
                                    margin: 0;
                                    text-align: center;
                                }
                                .card {
                                    background: #131b2e;
                                    border: 1px solid rgba(56, 189, 248, 0.3);
                                    border-radius: 20px;
                                    padding: 40px;
                                    box-shadow: 0 10px 40px rgba(0,0,0,0.6);
                                    max-width: 600px;
                                }
                                h1 { font-size: 28px; margin-bottom: 12px; color: #38bdf8; }
                                p { font-size: 16px; color: #94a3b8; margin-bottom: 24px; line-height: 1.5; }
                                .btn-group { display: flex; gap: 16px; justify-content: center; }
                                button {
                                    background: linear-gradient(135deg, #0284c7, #0369a1);
                                    color: white;
                                    border: none;
                                    padding: 14px 28px;
                                    font-size: 16px;
                                    font-weight: bold;
                                    border-radius: 12px;
                                    cursor: pointer;
                                    outline: none;
                                }
                                button:focus {
                                    outline: 3px solid #38bdf8;
                                    box-shadow: 0 0 20px rgba(56, 189, 248, 0.8);
                                }
                            </style>
                        </head>
                        <body>
                            <div class="card">
                                <h1>⚠️ Povezave ni bilo mogoče vzpostaviti</h1>
                                <p>Preverite internetno povezavo ali naslov spletne strani:<br><small style="color: #64748b;">$failingUrl</small></p>
                                <div class="btn-group">
                                    <button onclick="window.location.reload();" autofocus>🔄 Poskusi znova</button>
                                    <button onclick="window.location.href='$homeUrl';">🏠 Domov</button>
                                </div>
                            </div>
                        </body>
                        </html>
                    """.trimIndent()
                    view?.loadDataWithBaseURL(rawUrl, errorHtml, "text/html", "UTF-8", rawUrl)
                }
            }

            override fun onSafeBrowsingHit(
                view: WebView?,
                request: WebResourceRequest?,
                threatType: Int,
                callback: SafeBrowsingResponse?
            ) {
                // 🛡️ Disable Google Safe Browsing warning pages & false positives
                callback?.proceed(true)
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                // 🚫 100% Permanently block popup windows, redirect tabs and unrequested dialogs (kon-na-andrid-tv-stream-movie logic)
                return false
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onProgressChangedListener?.invoke(newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrEmpty()) onTitleReceivedListener?.invoke(title)
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view != null && callback != null) {
                    onShowCustomViewListener?.invoke(view, callback)
                }
            }

            override fun onHideCustomView() {
                onHideCustomViewListener?.invoke()
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (consoleMessage != null) {
                    Log.d("TvChromium", "[${consoleMessage.messageLevel()}] ${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})")
                }
                return true
            }
        }
    }

    inner class AndroidNativeBridge {
        @JavascriptInterface
        fun triggerCenterTap() {
            post {
                try {
                    val cx = if (width > 0) (width / 2).toFloat() else 960f
                    val cy = if (height > 0) (height / 2).toFloat() else 540f
                    val downTime = SystemClock.uptimeMillis()
                    val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, cx, cy, 0)
                    val up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, cx, cy, 0)
                    dispatchTouchEvent(down)
                    dispatchTouchEvent(up)
                    down.recycle()
                    up.recycle()
                } catch (ignored: Exception) {}
            }
        }

        private var lastUnmuteTimestamp = 0L

        @JavascriptInterface
        fun forceUnmuteAudio() {
            val now = SystemClock.uptimeMillis()
            if (now - lastUnmuteTimestamp < 5000) return
            lastUnmuteTimestamp = now
            post {
                try {
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    if (am != null) {
                        am.setStreamMute(AudioManager.STREAM_MUSIC, false)
                        am.setMode(AudioManager.MODE_NORMAL)
                        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                        if (cur == 0) {
                            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * 0.85).toInt(), 0)
                        }
                    }
                } catch (ignored: Exception) {}
            }
        }

        @JavascriptInterface
        fun hideKeyboard() {
            post {
                try {
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.hideSoftInputFromWindow(windowToken, 0)
                } catch (ignored: Exception) {}
            }
        }

        @JavascriptInterface
        fun focusToolbar() {
            post {
                onEdgeReachedTopListener?.invoke()
            }
        }

        @JavascriptInterface
        fun launchNativeVideo(url: String?, title: String? = null) {
            post {
                try {
                    val finalUrl = if (!url.isNullOrEmpty() && !url.startsWith("blob:") && !url.startsWith("data:")) {
                        url
                    } else {
                        lastVideoUrl ?: ""
                    }
                    if (finalUrl.isNotEmpty() && (finalUrl.startsWith("http://") || finalUrl.startsWith("https://") || finalUrl.startsWith("file://"))) {
                        val intent = Intent(context, FullscreenVideoActivity::class.java).apply {
                            putExtra("VIDEO_URL", finalUrl)
                            putExtra("VIDEO_TITLE", title ?: "Predvajalnik Videa")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                } catch (e: Exception) {
                    Log.e("TvChromium", "Failed to launch FullscreenVideoActivity: ${e.message}")
                }
            }
        }

        @JavascriptInterface
        fun requestTvFullscreen() {
            post {
                try {
                    onToggleFullscreenRequestListener?.invoke(true)
                    (context as? MainActivity)?.runOnUiThread {
                        (context as? MainActivity)?.toggleFullscreenMode(true)
                    }
                } catch (ignored: Exception) {}
            }
        }

        @JavascriptInterface
        fun exitTvFullscreen() {
            post {
                try {
                    onToggleFullscreenRequestListener?.invoke(false)
                    (context as? MainActivity)?.runOnUiThread {
                        (context as? MainActivity)?.toggleFullscreenMode(false)
                    }
                } catch (ignored: Exception) {}
            }
        }

        @JavascriptInterface
        fun toggleTvFullscreen() {
            post {
                try {
                    onToggleFullscreenRequestListener?.invoke(null)
                    (context as? MainActivity)?.runOnUiThread {
                        (context as? MainActivity)?.toggleFullscreenMode(null)
                    }
                } catch (ignored: Exception) {}
            }
        }

        @JavascriptInterface
        fun onPlayerStateChanged(stateJson: String) {
            post {
                try {
                    (context as? MainActivity)?.onWebPlayerStateReceived(stateJson)
                } catch (ignored: Exception) {}
            }
        }

        @JavascriptInterface
        fun launchVlcOrExternal(streamUrl: String, title: String) {
            post {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(streamUrl), "video/*")
                        putExtra("title", title)
                        putExtra("from_start", true)
                        setPackage("org.videolan.vlc")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val chooser = Intent.createChooser(Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(streamUrl), "video/*")
                            putExtra("title", title)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }, "Predvajaj z")
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooser)
                    } catch (ignored: Exception) {}
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (scrollY <= 15) {
                onEdgeReachedTopListener?.invoke()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && scrollY <= 15) {
            onEdgeReachedTopListener?.invoke()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    fun setPageZoom(percent: Int) {
        val clamped = percent.coerceIn(50, 300)
        settings.textZoom = clamped
    }

    fun getPageZoom(): Int {
        return settings.textZoom
    }

    fun setCookiePrivacyMode(mode: CookiePrivacyMode) {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cm.setAcceptThirdPartyCookies(this, mode == CookiePrivacyMode.COMFORT)
        }
    }

    fun openInAlternativeFrontend(engineName: String = "piped") {
        val currentUrl = url ?: return
        val match = Regex("[?&]v=([a-zA-Z0-9_-]{11})").find(currentUrl)
        val vId = match?.groupValues?.get(1)
        if (!vId.isNullOrEmpty()) {
            val targetUrl = when (engineName.lowercase()) {
                "invidious" -> "https://yewtu.be/watch?v=$vId"
                else -> "https://piped.video/watch?v=$vId"
            }
            loadUrl(targetUrl)
        }
    }
}

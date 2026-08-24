package com.example.tvbrowser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
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

    val adBlockEngine = AdBlockEngine()
    var currentUaMode: UserAgentMode = UserAgentMode.TV

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

    private fun configureUngoogledChromiumSettings() {
        val s = settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.setSupportZoom(false)
        s.builtInZoomControls = false
        s.displayZoomControls = false

        // Default: TV / Optimized Pixel 5 User-Agent
        setUserAgentMode(UserAgentMode.TV)
        s.textZoom = 100
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

        // 🍪 Pre-seed GDPR Consent Cookies (Google & YouTube)
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

        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            DownloadHandler.enqueueDownload(context, url, userAgent, contentDisposition, mimeType)
        }

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && request.isForMainFrame) {
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

                // 🚫 3. Ad & Popunder Domain Blocking
                if (adBlockEngine.isBlocked(url)) {
                    return AdBlockEngine.createEmptyResponse("text/plain")
                }

                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (url != null) onUrlChangedListener?.invoke(url)
                UserScriptManager.injectAll(
                    this@ChromiumEngineView,
                    adBlockEngine.isAntiAntiAdblockEnabled,
                    adBlockEngine.isCosmeticFilteringEnabled
                )
            }

            // ⚡ Ultra-Early Injection as soon as DOM is committed
            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url)
                UserScriptManager.injectAll(
                    this@ChromiumEngineView,
                    adBlockEngine.isAntiAntiAdblockEnabled,
                    adBlockEngine.isCosmeticFilteringEnabled
                )
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (url != null) onUrlChangedListener?.invoke(url)
                UserScriptManager.injectAll(
                    this@ChromiumEngineView,
                    adBlockEngine.isAntiAntiAdblockEnabled,
                    adBlockEngine.isCosmeticFilteringEnabled
                )
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
        }

        webChromeClient = object : WebChromeClient() {
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
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && scrollY <= 15) {
            onEdgeReachedTopListener?.invoke()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

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

    private val adBlockEngine = AdBlockEngine()

    init {
        configureUngoogledChromiumSettings()
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

        // 🌟 Full Google & YouTube Compatible Mobile/TV User-Agent (Pixel 5 / Chrome 122)
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.textZoom = 100
        s.defaultFontSize = 15
        s.defaultFixedFontSize = 13
        s.userAgentString = "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
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
                if (adBlockEngine.isBlocked(url)) {
                    return AdBlockEngine.createEmptyResponse("text/plain")
                }
                if (adBlockEngine.isDevToolBlocker(url)) {
                    return AdBlockEngine.createEmptyResponse("application/javascript")
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (url != null) onUrlChangedListener?.invoke(url)
                injectOptimizations()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (url != null) onUrlChangedListener?.invoke(url)
                injectOptimizations()
                if (url != null && url.contains("youtube.com/watch")) {
                    postDelayed({
                        try {
                            val downTime = SystemClock.uptimeMillis()
                            val eventDown = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 100f, 310f, 0)
                            val eventUp = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 100f, 310f, 0)
                            dispatchTouchEvent(eventDown)
                            dispatchTouchEvent(eventUp)
                            eventDown.recycle()
                            eventUp.recycle()
                        } catch (ignored: Exception) {}
                    }, 1200)
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

    fun injectOptimizations() {
        val js = """
            (function autoUnmuteAndDark() {
              var style = document.getElementById('tv_browser_forced_dark');
              if (!style) {
                style = document.createElement('style');
                style.id = 'tv_browser_forced_dark';
                style.innerHTML = 'html, body { background-color: #0b0f19 !important; color: #e2e8f0 !important; } input, textarea, select { background-color: #1a2234 !important; color: #ffffff !important; } :focus, a:focus, button:focus, input:focus, [tabindex]:focus { outline: 3px solid #38bdf8 !important; outline-offset: 2px !important; }';
                if (document.head) document.head.appendChild(style);
              }
              function ensureUnmute() {
                document.querySelectorAll('video, audio').forEach(function(v) {
                  if (v.muted) v.muted = false;
                  if (v.volume < 1.0) v.volume = 1.0;
                });
                var all = document.querySelectorAll('button, div, span, a, [role="button"]');
                for (var i = 0; i < all.length; i++) {
                  var el = all[i];
                  var txt = (el.innerText || el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || '').trim().toLowerCase();
                  if (txt === 'vklopite zvok' || txt === 'vklopi zvok' || txt === 'unmute' || txt.indexOf('vklopite zvok') !== -1 || txt.indexOf('vklopi zvok') !== -1) {
                    try {
                      el.click();
                      el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
                    } catch(e) {}
                  }
                }
              }
              ensureUnmute();
              document.addEventListener('play', ensureUnmute, true);
              document.addEventListener('playing', ensureUnmute, true);
              document.addEventListener('loadeddata', ensureUnmute, true);
              document.addEventListener('volumechange', function(e) {
                if (e.target && e.target.muted) {
                  e.target.muted = false;
                  e.target.volume = 1.0;
                }
              }, true);
              if (!window._tvUnmuteInterval) {
                window._tvUnmuteInterval = setInterval(ensureUnmute, 800);
              }
              function clickConsent() {
                var target = document.querySelector('button#L2AGLb, button[aria-label*="Sprejmi"], button[aria-label*="Accept"], form[action*="consent"] button, ytd-consent-bump-v2-lightbox button, .consent-bump-v2 button');
                if (target) { target.click(); return true; }
                var all = document.querySelectorAll('button, input[type="submit"], a, [role="button"]');
                for (var i = 0; i < all.length; i++) {
                  var el = all[i];
                  var txt = (el.innerText || el.textContent || el.getAttribute('aria-label') || '').trim().toLowerCase();
                  if (txt === 'sprejmi vse' || txt === 'sprejmi' || txt === 'accept all' || txt === 'i agree' || txt === 'strinjam se' || txt === 'soglašam' || txt.indexOf('sprejmi vse') !== -1 || txt.indexOf('accept all') !== -1) {
                    el.click();
                    return true;
                  }
                }
                return false;
              }
              if (!clickConsent()) {
                setTimeout(clickConsent, 400);
                setTimeout(clickConsent, 1000);
              }
              setTimeout(function() {
                var dialogs = document.querySelectorAll('#consent-bump, ytd-consent-bump-v2-lightbox, .consent-bump-v2');
                dialogs.forEach(function(d) {
                  if (d.innerText && (d.innerText.indexOf('YouTube') !== -1 || d.innerText.indexOf('Piškotk') !== -1 || d.innerText.indexOf('Preden') !== -1)) {
                    d.remove();
                    document.body.style.overflow = 'auto';
                  }
                });
              }, 1200);
            })();
        """.trimIndent()
        evaluateJavascript(js, null)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && scrollY <= 15) {
            onEdgeReachedTopListener?.invoke()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

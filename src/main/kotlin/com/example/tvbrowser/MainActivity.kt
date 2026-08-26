package com.example.tvbrowser

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : android.app.Activity() {

    private lateinit var viewModel: BrowserViewModel
    private lateinit var focusManager: TvFocusManager

    private lateinit var webViewContainer: FrameLayout
    private lateinit var customViewContainer: FrameLayout
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var isTvFullscreenMode = false
    private var lastBackPressTime: Long = 0

    private lateinit var cursorOverlay: CursorOverlay
    private var cursorSpeed = 24f
    private var keyRepeatCount = 0
    private var lastDirectionKeyCode = 0

    private lateinit var editUrl: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var bookmarksPanel: ScrollView
    private lateinit var downloadsPanel: ScrollView
    private lateinit var historyPanel: ScrollView
    private lateinit var settingsPanel: ScrollView
    private lateinit var bookmarksGrid: GridLayout
    private lateinit var downloadsListContainer: LinearLayout
    private lateinit var historyListContainer: LinearLayout
    private lateinit var tabsLayout: LinearLayout

    // Voice HUD
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var voiceListeningOverlay: View
    private lateinit var textVoiceStatus: TextView
    private lateinit var textVoiceMicIcon: TextView

    // Active Chromium WebViews pool mapped by Tab index
    private val webViewPool = mutableListOf<ChromiumEngineView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
        } catch (ignored: Exception) {}

        setContentView(R.layout.activity_main)

        viewModel = BrowserViewModel(this)
        initViews()
        unmuteAudioHardware()

        focusManager = TvFocusManager(
            editUrl = editUrl,
            isFullscreenActive = { isTvFullscreenMode || customView != null },
            onToggleCursor = { toggleCursorMode() },
            onStartVoice = { startVoiceSearch() },
            onToggleBookmarks = {
                if (bookmarksPanel.visibility == View.VISIBLE) hideAllPanels()
                else showBookmarksPanel()
            },
            onToggleFullscreen = {
                toggleFullscreenMode()
            },
            onSubtitlesKey = {
                sendPlayerCommand("SUBTITLES")
                Toast.makeText(this, "💬 Odpiram podnapise", Toast.LENGTH_SHORT).show()
            },
            onServersKey = {
                sendPlayerCommand("SERVERS")
                Toast.makeText(this, "🔄 Izbira strežnika", Toast.LENGTH_SHORT).show()
            }
        )

        viewModel.onStateChanged = {
            renderTabsBar()
        }

        // Vedno čist začetni zagon: odpri domačo stran (Google / Nastavljeni iskalnik)
        val homeUrl = viewModel.homeUrl()
        val initialUrl = intent?.data?.toString()?.takeIf { it.isNotBlank() } ?: homeUrl
        createAndSelectTab(initialUrl, "Iskalnik")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val url = intent?.data?.toString()
        if (!url.isNullOrBlank()) {
            loadUrl(url)
        } else {
            // Ob ponovnem zagonu iz TV menija vedno osveži na začetno stran
            loadUrl(viewModel.homeUrl())
        }
    }

    override fun onPause() {
        super.onPause()
        webViewPool.forEach { it.onPause() }
        getActiveWebView()?.pauseTimers()
    }

    override fun onResume() {
        super.onResume()
        getActiveWebView()?.resumeTimers()
        getActiveWebView()?.onResume()
    }

    override fun onStop() {
        super.onStop()
        performStrictPrivacyCleanupIfEnabled()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE || level == TRIM_MEMORY_UI_HIDDEN) {
            webViewPool.forEach { wv ->
                try {
                    wv.clearCache(false)
                } catch (ignored: Exception) {}
            }
            System.gc()
        }
    }

    override fun finish() {
        performCacheAndMemoryCleanup()
        super.finish()
    }

    override fun onDestroy() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (ignored: Exception) {}

        webViewPool.forEach { wv ->
            try {
                webViewContainer.removeView(wv)
                wv.stopLoading()
                wv.clearCache(true)
                wv.loadUrl("about:blank")
                wv.destroy()
            } catch (ignored: Exception) {}
        }
        webViewPool.clear()

        performCacheAndMemoryCleanup()

        super.onDestroy()
    }

    /**
     * 🧹 Sprostitev celotnega predpomnilnika in sistemskega pomnilnika (RAM) za optimalno delovanje TV-ja
     */
    private fun performCacheAndMemoryCleanup() {
        try {
            // 1. Počisti predpomnilnik vseh Chromium WebViews
            webViewPool.forEach { wv ->
                try {
                    wv.stopLoading()
                    wv.clearCache(true)
                    wv.clearFormData()
                    wv.clearHistory()
                } catch (ignored: Exception) {}
            }

            // 2. Pobriši začasne datoteke predpomnilnika (HTTP & GPU cache)
            try {
                cacheDir?.deleteRecursively()
                externalCacheDir?.deleteRecursively()
                val webViewCache = File(applicationInfo.dataDir, "app_webview/Default/HTTP Cache")
                if (webViewCache.exists()) webViewCache.deleteRecursively()
                val gpuCache = File(applicationInfo.dataDir, "app_webview/Default/GPUCache")
                if (gpuCache.exists()) gpuCache.deleteRecursively()
                codeCacheDir?.deleteRecursively()
            } catch (ignored: Exception) {}

            // 3. Strict privacy cleanup (če je vklopljen)
            performStrictPrivacyCleanupIfEnabled()

            // 4. Takojšnja sprostitev RAM-a preko Java Garbage Collectorja
            System.gc()
            Runtime.getRuntime().gc()
            Log.d("TvBrowser", "🧹 Predpomnilnik in RAM sta bila uspešno sproščena ob zaprtju.")
        } catch (ignored: Exception) {}
    }

    private fun performStrictPrivacyCleanupIfEnabled() {
        try {
            val prefs = getSharedPreferences("browser_settings", Context.MODE_PRIVATE)
            val cookieModeOrdinal = prefs.getInt("cookie_mode", CookiePrivacyMode.STANDARD.ordinal)
            if (cookieModeOrdinal == CookiePrivacyMode.STRICT.ordinal) {
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                android.webkit.CookieManager.getInstance().flush()
                android.webkit.WebStorage.getInstance().deleteAllData()
            }
        } catch (ignored: Exception) {}
    }

    private fun initViews() {
        webViewContainer = findViewById(R.id.webViewContainer)
        customViewContainer = findViewById(R.id.customViewContainer)
        cursorOverlay = findViewById(R.id.cursorOverlay)
        editUrl = findViewById(R.id.editUrl)
        progressBar = findViewById(R.id.pageProgressBar)

        bookmarksPanel = findViewById(R.id.bookmarksPanel)
        downloadsPanel = findViewById(R.id.downloadsPanel)
        historyPanel = findViewById(R.id.historyPanel)
        settingsPanel = findViewById(R.id.settingsPanel)
        bookmarksGrid = findViewById(R.id.bookmarksGrid)
        downloadsListContainer = findViewById(R.id.downloadsListContainer)
        historyListContainer = findViewById(R.id.historyListContainer)
        tabsLayout = findViewById(R.id.tabsLayout)

        // Voice Listening HUD (Triggered via Remote Red Button)
        voiceListeningOverlay = findViewById(R.id.voiceListeningOverlay)
        textVoiceStatus = findViewById(R.id.textVoiceStatus)
        textVoiceMicIcon = findViewById(R.id.textVoiceMicIcon)

        findViewById<View>(R.id.btnCancelVoice).setOnClickListener {
            stopVoiceSearch()
        }

        // TV D-Pad Focus Handoff: Pressing DOWN from header immediately focuses webpage
        val focusToWebListener = View.OnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                getActiveWebView()?.requestFocus()
                true
            } else false
        }
        findViewById<View>(R.id.btnAddTab).setOnKeyListener(focusToWebListener)
        findViewById<View>(R.id.tabsScrollView).setOnKeyListener(focusToWebListener)
        findViewById<View?>(R.id.btnQuickYouTube)?.setOnKeyListener(focusToWebListener)
        findViewById<View?>(R.id.btnQuickStreamNexus)?.setOnKeyListener(focusToWebListener)

        findViewById<View>(R.id.btnBack).setOnClickListener {
            val active = getActiveWebView()
            if (active != null && active.canGoBack()) {
                hideAllPanels()
                active.goBack()
            }
        }

        findViewById<View>(R.id.btnForward).setOnClickListener {
            val active = getActiveWebView()
            if (active != null && active.canGoForward()) {
                hideAllPanels()
                active.goForward()
            }
        }

        // 🏠 Home -> Selected Search Engine
        findViewById<View>(R.id.btnHome).setOnClickListener {
            loadUrl(viewModel.homeUrl())
        }

        editUrl.setSelectAllOnFocus(true)
        editUrl.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                editUrl.selectAll()
            }
        }
        editUrl.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                handleUrlSubmit()
                true
            } else false
        }
        editUrl.setOnKeyListener(focusToWebListener)

        findViewById<View>(R.id.btnStarBookmark).setOnClickListener {
            val active = getActiveWebView()
            if (active != null) {
                val currentUrl = active.url ?: ""
                val currentTitle = active.title ?: currentUrl
                if (currentUrl.isNotEmpty() && currentUrl != "about:blank") {
                    val icon = when {
                        currentUrl.contains("youtube.com") -> "📺"
                        currentUrl.contains("github.com") -> "🐙"
                        currentUrl.contains("themoviedb.org") -> "🍿"
                        else -> "⭐"
                    }
                    viewModel.addBookmark(currentTitle, currentUrl, icon)
                    Toast.makeText(this, "⭐ Dodano med zaznamke: $currentTitle", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // ⚙️ Settings Switches
        val prefs = getSharedPreferences("browser_settings", Context.MODE_PRIVATE)

        // 🛡️ AdBlock & Top-Frame Lock Toggle
        val btnToggleAdblock = findViewById<Button>(R.id.btnToggleAdblock)
        var adblockEnabled = prefs.getBoolean("adblock_enabled", true)
        fun updateAdblockBtn() {
            btnToggleAdblock.text = if (adblockEnabled) "VKLOPLJENO" else "IZKLOPLJENO"
            btnToggleAdblock.setTextColor(Color.parseColor(if (adblockEnabled) "#38bdf8" else "#ef4444"))
        }
        updateAdblockBtn()
        btnToggleAdblock.setOnClickListener {
            adblockEnabled = !adblockEnabled
            prefs.edit().putBoolean("adblock_enabled", adblockEnabled).apply()
            updateAdblockBtn()
            webViewPool.forEach { it.adBlockEngine.isEnabled = adblockEnabled }
            Toast.makeText(this, "AdBlocker: ${if (adblockEnabled) "Vklopljen" else "Izklopljen"}", Toast.LENGTH_SHORT).show()
        }

        // ⚔️ Anti-Anti-AdBlock Toggle
        val btnToggleAntiAnti = findViewById<Button>(R.id.btnToggleAntiAnti)
        var antiAntiEnabled = prefs.getBoolean("anti_anti_adblock", true)
        fun updateAntiAntiBtn() {
            btnToggleAntiAnti.text = if (antiAntiEnabled) "VKLOPLJENO" else "IZKLOPLJENO"
            btnToggleAntiAnti.setTextColor(Color.parseColor(if (antiAntiEnabled) "#38bdf8" else "#ef4444"))
        }
        updateAntiAntiBtn()
        btnToggleAntiAnti.setOnClickListener {
            antiAntiEnabled = !antiAntiEnabled
            prefs.edit().putBoolean("anti_anti_adblock", antiAntiEnabled).apply()
            updateAntiAntiBtn()
            webViewPool.forEach { it.adBlockEngine.isAntiAntiAdblockEnabled = antiAntiEnabled }
            Toast.makeText(this, "Anti-Anti-AdBlock: ${if (antiAntiEnabled) "Vklopljen" else "Izklopljen"}", Toast.LENGTH_SHORT).show()
        }

        // 🎨 Cosmetic Filtering Toggle
        val btnToggleCosmetic = findViewById<Button>(R.id.btnToggleCosmetic)
        var cosmeticEnabled = prefs.getBoolean("cosmetic_filtering", true)
        fun updateCosmeticBtn() {
            btnToggleCosmetic.text = if (cosmeticEnabled) "VKLOPLJENO" else "IZKLOPLJENO"
            btnToggleCosmetic.setTextColor(Color.parseColor(if (cosmeticEnabled) "#38bdf8" else "#ef4444"))
        }
        updateCosmeticBtn()
        btnToggleCosmetic.setOnClickListener {
            cosmeticEnabled = !cosmeticEnabled
            prefs.edit().putBoolean("cosmetic_filtering", cosmeticEnabled).apply()
            updateCosmeticBtn()
            webViewPool.forEach { it.adBlockEngine.isCosmeticFilteringEnabled = cosmeticEnabled }
            Toast.makeText(this, "Kozmetično filtriranje: ${if (cosmeticEnabled) "Vklopljeno" else "Izklopljeno"}", Toast.LENGTH_SHORT).show()
        }

        // 🖥️ User-Agent Mode Switcher
        val textCurrentUa = findViewById<TextView>(R.id.textCurrentUa)
        val btnSwitchUa = findViewById<Button>(R.id.btnSwitchUa)
        var uaModeOrdinal = prefs.getInt("ua_mode", UserAgentMode.TV.ordinal)
        fun updateUaUi() {
            val mode = UserAgentMode.values().getOrElse(uaModeOrdinal) { UserAgentMode.TV }
            textCurrentUa.text = when (mode) {
                UserAgentMode.TV -> "Trenutno: Android TV (Optimizirano)"
                UserAgentMode.DESKTOP -> "Trenutno: Namizni Računalnik (Desktop)"
                UserAgentMode.MOBILE -> "Trenutno: Mobilni Telefon (Mobile)"
            }
        }
        updateUaUi()
        btnSwitchUa.setOnClickListener {
            uaModeOrdinal = (uaModeOrdinal + 1) % UserAgentMode.values().size
            prefs.edit().putInt("ua_mode", uaModeOrdinal).apply()
            val newMode = UserAgentMode.values()[uaModeOrdinal]
            updateUaUi()
            webViewPool.forEach { it.setUserAgentMode(newMode) }
            getActiveWebView()?.reload()
            Toast.makeText(this, "Način spremenjen: ${textCurrentUa.text}", Toast.LENGTH_SHORT).show()
        }

        // 🔍 Search Engine Mode Switcher
        val textCurrentEngine = findViewById<TextView>(R.id.textCurrentEngine)
        val btnSwitchEngine = findViewById<Button>(R.id.btnSwitchEngine)
        var searchEngineMode = prefs.getString("search_engine", "google") ?: "google"
        fun updateEngineUi() {
            textCurrentEngine.text = when (searchEngineMode) {
                "google" -> "Trenutno: Google Iskanje"
                "duckduckgo" -> "Trenutno: DuckDuckGo (Zasebno)"
                "bing" -> "Trenutno: Microsoft Bing"
                else -> "Trenutno: Google Iskanje"
            }
            getActiveWebView()?.url?.let { updateOmniboxHint(it) }
        }
        updateEngineUi()
        btnSwitchEngine.setOnClickListener {
            searchEngineMode = when (searchEngineMode) {
                "google" -> "duckduckgo"
                "duckduckgo" -> "bing"
                else -> "google"
            }
            prefs.edit().putString("search_engine", searchEngineMode).apply()
            updateEngineUi()
            Toast.makeText(this, "Iskalnik: ${textCurrentEngine.text}", Toast.LENGTH_SHORT).show()
        }

        // 🍪 3-Tier Cookie Privacy Switcher
        val textCurrentCookiePrivacy = findViewById<TextView>(R.id.textCurrentCookiePrivacy)
        val btnSwitchCookiePrivacy = findViewById<Button>(R.id.btnSwitchCookiePrivacy)
        var cookieModeOrdinal = prefs.getInt("cookie_mode", CookiePrivacyMode.STANDARD.ordinal)
        fun updateCookieUi() {
            val mode = CookiePrivacyMode.values().getOrElse(cookieModeOrdinal) { CookiePrivacyMode.STANDARD }
            textCurrentCookiePrivacy.text = when (mode) {
                CookiePrivacyMode.STRICT -> "Trenutno: Strogo (Samo 1st-party, izbris ob izhodu)"
                CookiePrivacyMode.STANDARD -> "Trenutno: Običajno (Blokada 3rd-party sledilcev)"
                CookiePrivacyMode.COMFORT -> "Trenutno: Udobno (Dovoljena Google prijava)"
            }
        }
        updateCookieUi()
        btnSwitchCookiePrivacy.setOnClickListener {
            cookieModeOrdinal = (cookieModeOrdinal + 1) % CookiePrivacyMode.values().size
            prefs.edit().putInt("cookie_mode", cookieModeOrdinal).apply()
            val newMode = CookiePrivacyMode.values()[cookieModeOrdinal]
            updateCookieUi()
            webViewPool.forEach { it.setCookiePrivacyMode(newMode) }
            Toast.makeText(this, "Nadzor piškotkov: ${textCurrentCookiePrivacy.text}", Toast.LENGTH_SHORT).show()
        }

        // ⚡ YouTube Freedom Engine Toggle (SponsorBlock & Dislike)
        val btnToggleYouTubeFreedom = findViewById<Button>(R.id.btnToggleYouTubeFreedom)
        var ytFreedomEnabled = prefs.getBoolean("yt_freedom_enabled", true)
        fun updateYtFreedomBtn() {
            btnToggleYouTubeFreedom.text = if (ytFreedomEnabled) "VKLOPLJENO" else "IZKLOPLJENO"
            btnToggleYouTubeFreedom.setTextColor(Color.parseColor(if (ytFreedomEnabled) "#38bdf8" else "#ef4444"))
        }
        updateYtFreedomBtn()
        btnToggleYouTubeFreedom.setOnClickListener {
            ytFreedomEnabled = !ytFreedomEnabled
            prefs.edit().putBoolean("yt_freedom_enabled", ytFreedomEnabled).apply()
            updateYtFreedomBtn()
            Toast.makeText(this, "YouTube Freedom: ${if (ytFreedomEnabled) "Vklopljen" else "Izklopljen"}", Toast.LENGTH_SHORT).show()
        }

        // 🌐 Private DNS & Anti-Censorship
        findViewById<View>(R.id.btnOpenDnsSettings).setOnClickListener {
            try {
                val intent = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
                    startActivity(intent)
                } catch (err: Exception) {
                    Toast.makeText(this, "Odprite sistemske nastavitve TV-ja -> Omrežje -> Private DNS", Toast.LENGTH_LONG).show()
                }
            }
        }

        // 🔎 Page Zoom & Magnification Controls (User Customizable)
        val btnNavZoom = findViewById<Button>(R.id.btnNavZoom)
        val textCurrentZoomLevel = findViewById<TextView>(R.id.textCurrentZoomLevel)
        val badgeZoomPercent = findViewById<TextView>(R.id.badgeZoomPercent)
        val btnZoomOut = findViewById<Button>(R.id.btnZoomOut)
        val btnZoomIn = findViewById<Button>(R.id.btnZoomIn)
        val btnZoomPreset75 = findViewById<Button>(R.id.btnZoomPreset75)
        val btnZoomPreset100 = findViewById<Button>(R.id.btnZoomPreset100)
        val btnZoomPreset125 = findViewById<Button>(R.id.btnZoomPreset125)
        val btnZoomPreset150 = findViewById<Button>(R.id.btnZoomPreset150)
        val btnZoomPreset175 = findViewById<Button>(R.id.btnZoomPreset175)
        val btnZoomPreset200 = findViewById<Button>(R.id.btnZoomPreset200)

        val presetButtons = mapOf(
            75 to btnZoomPreset75,
            100 to btnZoomPreset100,
            125 to btnZoomPreset125,
            150 to btnZoomPreset150,
            175 to btnZoomPreset175,
            200 to btnZoomPreset200
        )

        var currentZoomLevel = prefs.getInt("page_zoom", 75)

        fun updateZoomUi(zoom: Int) {
            btnNavZoom.text = "🔍 ${zoom}%"
            badgeZoomPercent.text = "${zoom}%"
            val desc = when {
                zoom <= 75 -> "Osnovna TV nastavitev (75%)"
                zoom == 100 -> "Standardno (100%)"
                zoom <= 130 -> "Udobno povečano"
                else -> "Velika kinematografska povečava"
            }
            textCurrentZoomLevel.text = "Trenutna povečava: ${zoom}% ($desc)"

            presetButtons.forEach { (level, btn) ->
                if (btn != null) {
                    if (level == zoom) {
                        btn.setTextColor(Color.parseColor("#38bdf8"))
                        btn.setTypeface(null, android.graphics.Typeface.BOLD)
                    } else {
                        btn.setTextColor(Color.parseColor("#94a3b8"))
                        btn.setTypeface(null, android.graphics.Typeface.NORMAL)
                    }
                }
            }
        }

        fun applyZoom(zoom: Int, showToast: Boolean = false) {
            val clamped = zoom.coerceIn(50, 300)
            currentZoomLevel = clamped
            prefs.edit().putInt("page_zoom", clamped).apply()
            updateZoomUi(clamped)
            webViewPool.forEach { it.setPageZoom(clamped) }
            if (showToast) {
                Toast.makeText(this, "🔎 Povečava: ${clamped}%", Toast.LENGTH_SHORT).show()
            }
        }

        updateZoomUi(currentZoomLevel)

        btnNavZoom.setOnClickListener {
            // Quick cycle through common presets: 75% -> 100% -> 125% -> 150% -> 175% -> 75%
            val nextZoom = when (currentZoomLevel) {
                75 -> 100
                100 -> 125
                125 -> 150
                150 -> 175
                175 -> 75
                else -> 75
            }
            applyZoom(nextZoom, true)
        }

        btnZoomIn.setOnClickListener {
            applyZoom(currentZoomLevel + 10, true)
        }

        btnZoomOut.setOnClickListener {
            applyZoom(currentZoomLevel - 10, true)
        }

        presetButtons.forEach { (level, btn) ->
            btn.setOnClickListener {
                applyZoom(level, true)
            }
        }

        findViewById<View>(R.id.btnTestDnsCensorship).setOnClickListener {
            Toast.makeText(this, "Preverjam dostopnost necenzuriranih povezav...", Toast.LENGTH_SHORT).show()
            Thread {
                var quad9Reachable = false
                var cloudflareReachable = false
                try {
                    val q9 = java.net.InetAddress.getByName("dns.quad9.net")
                    quad9Reachable = q9 != null
                } catch (ignored: Exception) {}
                try {
                    val cf = java.net.InetAddress.getByName("one.one.one.one")
                    cloudflareReachable = cf != null
                } catch (ignored: Exception) {}

                runOnUiThread {
                    val msg = buildString {
                        append("Status omrežne cenzure & DNS:\n\n")
                        append(if (quad9Reachable) "✅ Quad9 DNS: Dostopen (Necenzurirano)\n" else "⚠️ Quad9 DNS: Blokiran / Nedostopen\n")
                        append(if (cloudflareReachable) "✅ Cloudflare 1.1.1.1: Dostopen\n\n" else "⚠️ Cloudflare: Blokiran / Nedostopen\n\n")
                        append("Zaščita pred cenzuro je aktivna.")
                    }
                    android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("🌐 Rezultat Testa Povezave")
                        .setMessage(msg)
                        .setPositiveButton("V redu", null)
                        .show()
                }
            }.start()
        }

        findViewById<View>(R.id.btnNavBookmarks).setOnClickListener {
            if (bookmarksPanel.visibility == View.VISIBLE) hideAllPanels()
            else showBookmarksPanel()
        }

        findViewById<View>(R.id.btnNavDownloads).setOnClickListener {
            if (downloadsPanel.visibility == View.VISIBLE) hideAllPanels()
            else showDownloadsPanel()
        }

        findViewById<View>(R.id.btnNavHistory).setOnClickListener {
            if (historyPanel.visibility == View.VISIBLE) hideAllPanels()
            else showHistoryPanel()
        }

        findViewById<View>(R.id.btnNavSettings).setOnClickListener {
            if (settingsPanel.visibility == View.VISIBLE) hideAllPanels()
            else showSettingsPanel()
        }

        findViewById<View>(R.id.btnNavFullscreen).setOnClickListener {
            toggleFullscreenMode()
        }

        findViewById<View>(R.id.fullscreenExitPill)?.setOnClickListener {
            toggleFullscreenMode(false)
        }

        findViewById<View>(R.id.btnClearHistory).setOnClickListener {
            android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("🗑️ Počisti Zgodovino")
                .setMessage("Ali res želite izbrisati celotno zgodovino brskanja?")
                .setPositiveButton("Izbriši vse") { _, _ ->
                    viewModel.clearHistory()
                    renderHistoryList()
                    Toast.makeText(this, "Zgodovina izbrisana.", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Prekliči", null)
                .show()
        }

        // ➕ Add Tab -> Max 4 Tabs limit
        findViewById<View>(R.id.btnAddTab).setOnClickListener {
            if (webViewPool.size >= 4) {
                Toast.makeText(this, "Največ 4 zavihki. Zapri enega.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val homeUrl = viewModel.homeUrl()
            createAndSelectTab(homeUrl, "Nov zavihek")
            Toast.makeText(this, "➕ Odprt nov zavihek", Toast.LENGTH_SHORT).show()
        }

        // ▶ Quick 1-Click YouTube Launcher
        findViewById<View?>(R.id.btnQuickYouTube)?.setOnClickListener {
            hideAllPanels()
            loadUrl("https://www.youtube.com")
            Toast.makeText(this, "▶ Odpiram YouTube...", Toast.LENGTH_SHORT).show()
        }

        // 🎬 Quick 1-Click StreamNexus Launcher
        findViewById<View?>(R.id.btnQuickStreamNexus)?.setOnClickListener {
            hideAllPanels()
            loadUrl("file:///android_asset/stream/index.html")
            Toast.makeText(this, "🎬 Odpiram StreamNexus...", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btnAddCustomBookmark).setOnClickListener {
            showAddBookmarkDialog()
        }

        findViewById<View>(R.id.btnClearCache).setOnClickListener {
            BrowserRepository(this).clearHistory()
            try {
                WebStorage.getInstance().deleteAllData()
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            } catch (ignored: Exception) {}
            Toast.makeText(this, "Predpomnilnik, piškotki in zgodovina so bili izbrisani.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun unmuteAudioHardware() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamMute(AudioManager.STREAM_MUSIC, false)
            am.setMode(AudioManager.MODE_NORMAL)
            JblSoundManager.unlockAndUnmute(this)
        } catch (ignored: Exception) {}
    }

    private fun hideSoftKeyboard() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val view = currentFocus ?: findViewById<View>(android.R.id.content)
            if (view != null) {
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
        } catch (ignored: Exception) {}
    }

    private fun handleUrlSubmit() {
        hideSoftKeyboard()
        val target = viewModel.processUrlInput(editUrl.text.toString())
        loadUrl(target)
        getActiveWebView()?.requestFocus()
    }

    fun loadUrl(url: String) {
        hideAllPanels()
        val active = getActiveWebView()
        if (active != null) {
            editUrl.setText(formatDisplayUrl(url))
            updateOmniboxHint(url)
            active.loadUrl(url)
        } else {
            createAndSelectTab(url, "Nalagam...")
        }
    }

    private fun createAndSelectTab(url: String, title: String) {
        if (webViewPool.size >= 4) {
            Toast.makeText(this, "Največ 4 zavihki. Zapri enega.", Toast.LENGTH_SHORT).show()
            return
        }

        val webView = ChromiumEngineView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus(View.FOCUS_DOWN)
            onProgressChangedListener = { p ->
                if (webViewPool.indexOf(this) == viewModel.state.activeTabIndex) {
                    progressBar.progress = p
                    progressBar.visibility = if (p in 1..99) View.VISIBLE else View.GONE
                }
            }
            onTitleReceivedListener = { t ->
                val idx = webViewPool.indexOf(this)
                if (idx >= 0) {
                    viewModel.updateTabTitle(idx, t)
                    runOnUiThread { renderTabsBar() }
                }
            }
            onUrlChangedListener = { u ->
                val idx = webViewPool.indexOf(this)
                if (idx >= 0) {
                    if (idx == viewModel.state.activeTabIndex && !editUrl.hasFocus()) {
                        editUrl.setText(formatDisplayUrl(u))
                        updateOmniboxHint(u)
                    }
                    viewModel.updateTabUrl(idx, u)
                }
            }
            onShowCustomViewListener = { v, cb ->
                try {
                    val active = this
                    active.evaluateJavascript("(function(){ var v = document.querySelector('video'); return v ? (v.currentSrc || v.src || '') : ''; })()") { rawSrc ->
                        val src = rawSrc?.replace("\"", "")?.trim() ?: ""
                        val finalUrl = if (src.isNotEmpty() && !src.startsWith("blob:") && !src.startsWith("data:")) {
                            src
                        } else {
                            active.lastVideoUrl ?: ""
                        }
                        if (finalUrl.isNotEmpty() && (finalUrl.startsWith("http://") || finalUrl.startsWith("https://") || finalUrl.startsWith("file://"))) {
                            val intent = Intent(this@MainActivity, FullscreenVideoActivity::class.java).apply {
                                putExtra("VIDEO_URL", finalUrl)
                                putExtra("VIDEO_TITLE", active.title ?: "Predvajalnik Videa")
                            }
                            startActivity(intent)
                            try { cb.onCustomViewHidden() } catch (ignored: Exception) {}
                        } else {
                            if (customView != null) {
                                try {
                                    customViewCallback?.onCustomViewHidden()
                                } catch (ignored: Exception) {}
                                customViewContainer.removeAllViews()
                                customView = null
                            }
                            customView = v
                            customViewCallback = cb

                            (v.parent as? ViewGroup)?.removeView(v)
                            customViewContainer.removeAllViews()
                            customViewContainer.addView(
                                v,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                            customViewContainer.visibility = View.VISIBLE
                            customViewContainer.bringToFront()
                            findViewById<View>(R.id.headerContainer)?.visibility = View.GONE
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            hideSystemUI()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TvChromium", "Error in onShowCustomView: ${e.message}", e)
                }
            }
            onHideCustomViewListener = {
                try {
                    if (customView != null) {
                        (customView?.parent as? ViewGroup)?.removeView(customView)
                        customViewContainer.removeAllViews()
                        customView = null
                        customViewContainer.visibility = View.GONE
                        findViewById<View>(R.id.headerContainer)?.visibility = View.VISIBLE
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        try {
                            customViewCallback?.onCustomViewHidden()
                        } catch (ignored: Exception) {}
                        customViewCallback = null
                        hideSystemUI()
                    }
                } catch (e: Exception) {
                    Log.e("TvChromium", "Error in onHideCustomView: ${e.message}", e)
                }
            }
            onEdgeReachedTopListener = {
                runOnUiThread {
                    if (!isTvFullscreenMode && customView == null) {
                        editUrl.requestFocus()
                        editUrl.selectAll()
                    }
                }
            }
            onToggleFullscreenRequestListener = { enable ->
                runOnUiThread {
                    toggleFullscreenMode(enable)
                }
            }
        }

        val prefs = getSharedPreferences("browser_settings", Context.MODE_PRIVATE)
        webView.adBlockEngine.isEnabled = prefs.getBoolean("adblock_enabled", true)
        webView.adBlockEngine.isAntiAntiAdblockEnabled = prefs.getBoolean("anti_anti_adblock", true)
        webView.adBlockEngine.isCosmeticFilteringEnabled = prefs.getBoolean("cosmetic_filtering", true)
        val uaOrdinal = prefs.getInt("ua_mode", UserAgentMode.TV.ordinal)
        webView.setUserAgentMode(UserAgentMode.values().getOrElse(uaOrdinal) { UserAgentMode.TV })
        val currentZoom = prefs.getInt("page_zoom", 75)
        webView.setPageZoom(currentZoom)

        webViewPool.add(webView)
        webViewContainer.addView(webView)

        viewModel.addNewTab(url, title)
        selectTab(webViewPool.lastIndex)
        webView.loadUrl(url)
        renderTabsBar()
    }

    private fun formatDisplayUrl(rawUrl: String): String {
        if (rawUrl.isEmpty() || rawUrl == "about:blank") return ""
        try {
            val uri = Uri.parse(rawUrl)
            if (rawUrl.contains("google.com/search") || rawUrl.contains("google.si/search")) {
                val q = uri.getQueryParameter("q")
                if (!q.isNullOrEmpty()) return q
            }
            if (rawUrl.contains("duckduckgo.com/")) {
                val q = uri.getQueryParameter("q")
                if (!q.isNullOrEmpty()) return q
            }
            if (rawUrl.contains("bing.com/search")) {
                val q = uri.getQueryParameter("q")
                if (!q.isNullOrEmpty()) return q
            }
            if (rawUrl.contains("youtube.com/results")) {
                val q = uri.getQueryParameter("search_query")
                if (!q.isNullOrEmpty()) return q
            }
            return rawUrl
        } catch (ignored: Exception) {}
        return rawUrl
    }

    private fun updateOmniboxHint(url: String) {
        if (url.contains("youtube.com")) {
            editUrl.hint = "🔍 Išči v YouTubu ali vnesite naslov..."
        } else {
            val prefs = getSharedPreferences("browser_settings", Context.MODE_PRIVATE)
            val engine = prefs.getString("search_engine", "google") ?: "google"
            val name = when (engine) {
                "duckduckgo" -> "DuckDuckGo"
                "bing" -> "Bingu"
                else -> "Googlu"
            }
            editUrl.hint = "🔍 Išči v $name ali vnesite naslov..."
        }
    }

    private fun selectTab(index: Int) {
        if (index !in webViewPool.indices) return

        for (i in webViewPool.indices) {
            val v = webViewPool[i]
            if (i == index) {
                v.visibility = View.VISIBLE
                v.onResume()
                val currentU = v.url ?: ""
                editUrl.setText(formatDisplayUrl(currentU))
                updateOmniboxHint(currentU)
            } else {
                v.visibility = View.GONE
                v.onPause()
            }
        }

        viewModel.selectTab(index)
        renderTabsBar()
    }

    private fun closeTab(index: Int) {
        if (webViewPool.size <= 1 || index !in webViewPool.indices) return

        val viewToRemove = webViewPool.removeAt(index)
        webViewContainer.removeView(viewToRemove)
        viewToRemove.stopLoading()
        viewToRemove.loadUrl("about:blank")
        viewToRemove.destroy()

        viewModel.closeTab(index)
        val newIndex = viewModel.state.activeTabIndex
        selectTab(newIndex)
        renderTabsBar()
    }

    private fun getActiveWebView(): ChromiumEngineView? {
        val index = viewModel.state.activeTabIndex
        return if (index in webViewPool.indices) webViewPool[index] else null
    }

    private fun renderTabsBar() {
        tabsLayout.removeAllViews()
        val tabs = viewModel.state.tabs
        val activeIndex = viewModel.state.activeTabIndex
        val d = resources.displayMetrics.density

        for (i in tabs.indices) {
            val tab = tabs[i]
            val isActive = (i == activeIndex)

            val tabLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((12 * d).toInt(), (2 * d).toInt(), (8 * d).toInt(), (2 * d).toInt())
                isFocusable = true
                isFocusableInTouchMode = true
                setBackgroundResource(if (isActive) R.drawable.bg_tab_active else R.drawable.bg_tab_inactive)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    (30 * d).toInt()
                ).apply {
                    marginEnd = (8 * d).toInt()
                }
                layoutParams = lp
            }

            val textTitle = TextView(this).apply {
                val displayTitle = if (tab.title.isNullOrBlank() || tab.title == "about:blank") "Zavihek ${i + 1}" else tab.title
                text = "🌐 $displayTitle"
                textSize = 12f
                setTextColor(if (isActive) Color.parseColor("#38bdf8") else Color.parseColor("#94a3b8"))
                typeface = if (isActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                maxWidth = (180 * d).toInt()
            }
            tabLayout.addView(textTitle)

            if (tabs.size > 1) {
                val tabIdx = i
                val btnClose = TextView(this).apply {
                    text = "  ✕"
                    textSize = 13f
                    setTextColor(if (isActive) Color.parseColor("#f87171") else Color.parseColor("#ef4444"))
                    setPadding((6 * d).toInt(), 0, (4 * d).toInt(), 0)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        closeTab(tabIdx)
                        Toast.makeText(this@MainActivity, "Zavihek odstranjen", Toast.LENGTH_SHORT).show()
                    }
                }
                tabLayout.addView(btnClose)
            }

            val tabIdx = i
            tabLayout.setOnClickListener { selectTab(tabIdx) }
            tabLayout.setOnLongClickListener {
                if (webViewPool.size > 1) {
                    closeTab(tabIdx)
                    Toast.makeText(this, "Zavihek zaprt.", Toast.LENGTH_SHORT).show()
                }
                true
            }

            if (i == tabs.size - 1) {
                tabLayout.nextFocusRightId = R.id.btnQuickYouTube
            }

            // Down key from tab moves to webview
            tabLayout.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    getActiveWebView()?.requestFocus()
                    true
                } else false
            }

            tabsLayout.addView(tabLayout)
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val view = currentFocus ?: window.decorView
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun hideAllPanels() {
        hideKeyboard()
        bookmarksPanel.visibility = View.GONE
        downloadsPanel.visibility = View.GONE
        historyPanel.visibility = View.GONE
        settingsPanel.visibility = View.GONE
        voiceListeningOverlay.visibility = View.GONE
        viewModel.hideAllPanels()
    }

    private fun showBookmarksPanel() {
        hideAllPanels()
        bookmarksPanel.visibility = View.VISIBLE
        renderBookmarksGrid()
        viewModel.showPanel(ActivePanel.BOOKMARKS)
        bookmarksPanel.post {
            findViewById<View>(R.id.btnAddCustomBookmark)?.requestFocus()
        }
    }

    private fun showDownloadsPanel() {
        hideAllPanels()
        downloadsPanel.visibility = View.VISIBLE
        renderDownloadsList()
        viewModel.showPanel(ActivePanel.DOWNLOADS)
    }

    private fun showHistoryPanel() {
        hideAllPanels()
        historyPanel.visibility = View.VISIBLE
        renderHistoryList()
        viewModel.showPanel(ActivePanel.HISTORY)
    }

    private fun showSettingsPanel() {
        hideAllPanels()
        settingsPanel.visibility = View.VISIBLE
        viewModel.showPanel(ActivePanel.SETTINGS)
    }

    private fun renderBookmarksGrid() {
        bookmarksGrid.removeAllViews()
        val bookmarks = viewModel.state.bookmarks
        val d = resources.displayMetrics.density

        if (bookmarks.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "Trenutno nimate shranjenih zaznamkov.\nKliknite '+ Dodaj zaznamek' zgoraj desno za dodajanje priljubljenih strani."
                textSize = 15f
                setTextColor(Color.parseColor("#94a3b8"))
                setPadding((24 * d).toInt(), (40 * d).toInt(), (24 * d).toInt(), (40 * d).toInt())
                gravity = Gravity.CENTER
            }
            bookmarksGrid.addView(emptyText)
            return
        }

        for (i in bookmarks.indices) {
            val bm = bookmarks[i]
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.TOP
                setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
                isFocusable = true
                isFocusableInTouchMode = true
                setBackgroundResource(R.drawable.bg_card)
                val lp = GridLayout.LayoutParams().apply {
                    width = (280 * d).toInt()
                    height = (170 * d).toInt()
                    setMargins((10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
                }
                layoutParams = lp
            }

            // Top Header: Icon + Action Buttons (⬅️ ➡️ ✏️ 🗑️)
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // Icon Badge
            val iconBadge = TextView(this).apply {
                text = bm.icon.ifBlank { "⭐" }
                textSize = 26f
                gravity = Gravity.CENTER
                setPadding(0, 0, (8 * d).toInt(), 0)
            }
            headerRow.addView(iconBadge)

            // Flexible Spacer
            val spacer = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1.0f)
            }
            headerRow.addView(spacer)

            // Quick Move Left Button (⬅️)
            if (i > 0) {
                val btnLeft = Button(this).apply {
                    text = "⬅️"
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    setBackgroundResource(R.drawable.bg_nav_button)
                    val lpBtn = LinearLayout.LayoutParams((32 * d).toInt(), (30 * d).toInt()).apply {
                        marginEnd = (4 * d).toInt()
                    }
                    layoutParams = lpBtn
                    isFocusable = true
                    setOnClickListener {
                        viewModel.moveBookmark(i, i - 1)
                        renderBookmarksGrid()
                    }
                }
                headerRow.addView(btnLeft)
            }

            // Quick Move Right Button (➡️)
            if (i < bookmarks.lastIndex) {
                val btnRight = Button(this).apply {
                    text = "➡️"
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    setBackgroundResource(R.drawable.bg_nav_button)
                    val lpBtn = LinearLayout.LayoutParams((32 * d).toInt(), (30 * d).toInt()).apply {
                        marginEnd = (4 * d).toInt()
                    }
                    layoutParams = lpBtn
                    isFocusable = true
                    setOnClickListener {
                        viewModel.moveBookmark(i, i + 1)
                        renderBookmarksGrid()
                    }
                }
                headerRow.addView(btnRight)
            }

            // Edit Button (✏️)
            val btnEdit = Button(this).apply {
                text = "✏️"
                textSize = 11f
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.bg_nav_button)
                val lpBtn = LinearLayout.LayoutParams((32 * d).toInt(), (30 * d).toInt()).apply {
                    marginEnd = (4 * d).toInt()
                }
                layoutParams = lpBtn
                isFocusable = true
                setOnClickListener {
                    showEditBookmarkDialog(bm)
                }
            }
            headerRow.addView(btnEdit)

            // Delete Button (🗑️)
            val btnDel = Button(this).apply {
                text = "🗑️"
                textSize = 11f
                setTextColor(Color.parseColor("#ef4444"))
                setBackgroundResource(R.drawable.bg_nav_button)
                val lpBtn = LinearLayout.LayoutParams((32 * d).toInt(), (30 * d).toInt())
                layoutParams = lpBtn
                isFocusable = true
                setOnClickListener {
                    showDeleteBookmarkDialog(bm)
                }
            }
            headerRow.addView(btnDel)

            card.addView(headerRow)

            // Bookmark Title: 15sp, bold, clean high contrast white text
            val titleText = TextView(this).apply {
                text = bm.title
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, (8 * d).toInt(), 0, (2 * d).toInt())
            }
            card.addView(titleText)

            // Bookmark URL / Domain Host
            val hostText = TextView(this).apply {
                val displayHost = try {
                    if (bm.url.startsWith("file:///")) "StreamNexus HD"
                    else Uri.parse(bm.url).host ?: bm.url
                } catch (e: Exception) {
                    bm.url
                }
                text = "🔗 $displayHost"
                textSize = 12f
                setTextColor(Color.parseColor("#38bdf8"))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            card.addView(hostText)

            card.setOnClickListener {
                hideAllPanels()
                loadUrl(bm.url)
            }

            card.setOnLongClickListener {
                showBookmarkActionsDialog(bm, i)
                true
            }

            bookmarksGrid.addView(card)
        }
    }

    private fun showEditBookmarkDialog(bm: BookmarkItem) {
        val d = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * d).toInt(), (16 * d).toInt(), (24 * d).toInt(), (16 * d).toInt())
        }

        val textLabelTitle = TextView(this).apply {
            text = "Ime zaznamka:"
            setTextColor(Color.parseColor("#94a3b8"))
            textSize = 13f
        }
        container.addView(textLabelTitle)

        val inputTitle = EditText(this).apply {
            setText(bm.title)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748b"))
            setBackgroundResource(R.drawable.bg_omnibox)
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
        }
        container.addView(inputTitle)

        val textLabelUrl = TextView(this).apply {
            text = "Spletni naslov (URL):"
            setTextColor(Color.parseColor("#94a3b8"))
            textSize = 13f
            setPadding(0, (12 * d).toInt(), 0, 0)
        }
        container.addView(textLabelUrl)

        val inputUrl = EditText(this).apply {
            setText(bm.url)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748b"))
            setBackgroundResource(R.drawable.bg_omnibox)
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
        }
        container.addView(inputUrl)

        val textLabelIcon = TextView(this).apply {
            text = "Ikona / Emoji (npr. ⭐, 🎬, 📺, 🐙, 🍿, 🌐):"
            setTextColor(Color.parseColor("#94a3b8"))
            textSize = 13f
            setPadding(0, (12 * d).toInt(), 0, 0)
        }
        container.addView(textLabelIcon)

        val inputIcon = EditText(this).apply {
            setText(bm.icon)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748b"))
            setBackgroundResource(R.drawable.bg_omnibox)
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
        }
        container.addView(inputIcon)

        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("✏️ Uredi Zaznamek")
            .setView(container)
            .setPositiveButton("Shrani") { _, _ ->
                val newTitle = inputTitle.text.toString().trim()
                val newUrl = inputUrl.text.toString().trim()
                val newIcon = inputIcon.text.toString().trim().ifBlank { "⭐" }
                if (newTitle.isNotEmpty() && newUrl.isNotEmpty()) {
                    viewModel.updateBookmark(bm.id, newTitle, newUrl, newIcon)
                    renderBookmarksGrid()
                    Toast.makeText(this, "Zaznamek posodobljen!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Prekliči", null)
            .setOnDismissListener {
                if (bookmarksPanel.visibility == View.VISIBLE && bookmarksGrid.childCount > 0) {
                    bookmarksGrid.getChildAt(0)?.requestFocus()
                }
            }
            .show()
    }

    private fun showDeleteBookmarkDialog(bm: BookmarkItem) {
        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("🗑️ Odstrani Zaznamek")
            .setMessage("Ali res želite odstraniti zaznamek \"${bm.title}\"?")
            .setPositiveButton("Odstrani") { _, _ ->
                viewModel.deleteBookmark(bm.id)
                renderBookmarksGrid()
                Toast.makeText(this, "Zaznamek odstranjen.", Toast.LENGTH_SHORT).show()
                if (bookmarksGrid.childCount > 0) {
                    bookmarksGrid.getChildAt(0)?.requestFocus()
                } else {
                    findViewById<View>(R.id.btnAddCustomBookmark)?.requestFocus()
                }
            }
            .setNegativeButton("Prekliči") { _, _ ->
                renderBookmarksGrid()
            }
            .setOnDismissListener {
                if (bookmarksPanel.visibility == View.VISIBLE && bookmarksGrid.childCount > 0) {
                    bookmarksGrid.getChildAt(0)?.requestFocus()
                }
            }
            .show()
    }

    private fun showBookmarkActionsDialog(bm: BookmarkItem, index: Int) {
        val total = viewModel.state.bookmarks.size
        val options = mutableListOf<String>()
        options.add("🚀 Odpri spletno stran")
        options.add("✏️ Uredi ime in naslov")
        if (index > 0) options.add("⬅️ Premakni levo (vrstni red)")
        if (index < total - 1) options.add("➡️ Premakni desno (vrstni red)")
        options.add("🗑️ Odstrani zaznamek")

        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("⭐ ${bm.title}")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "🚀 Odpri spletno stran" -> {
                        hideAllPanels()
                        loadUrl(bm.url)
                    }
                    "✏️ Uredi ime in naslov" -> showEditBookmarkDialog(bm)
                    "⬅️ Premakni levo (vrstni red)" -> {
                        viewModel.moveBookmark(index, index - 1)
                        renderBookmarksGrid()
                        val newIdx = (index - 1).coerceAtLeast(0)
                        if (newIdx < bookmarksGrid.childCount) bookmarksGrid.getChildAt(newIdx)?.requestFocus()
                    }
                    "➡️ Premakni desno (vrstni red)" -> {
                        viewModel.moveBookmark(index, index + 1)
                        renderBookmarksGrid()
                        val newIdx = (index + 1).coerceAtMost(bookmarksGrid.childCount - 1)
                        if (newIdx < bookmarksGrid.childCount) bookmarksGrid.getChildAt(newIdx)?.requestFocus()
                    }
                    "🗑️ Odstrani zaznamek" -> showDeleteBookmarkDialog(bm)
                }
            }
            .setNegativeButton("Zapri", null)
            .setOnDismissListener {
                if (bookmarksPanel.visibility == View.VISIBLE && bookmarksGrid.childCount > 0) {
                    val target = index.coerceAtMost(bookmarksGrid.childCount - 1).coerceAtLeast(0)
                    bookmarksGrid.getChildAt(target)?.requestFocus()
                }
            }
            .show()
    }

    private fun renderDownloadsList() {
        downloadsListContainer.removeAllViews()
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

        if (files.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "V mapi Prenosi trenutno ni datotek."
                setTextColor(Color.parseColor("#94a3b8"))
                textSize = 14f
                setPadding(16, 24, 16, 24)
            }
            downloadsListContainer.addView(emptyText)
            return
        }

        for (file in files) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(20, 16, 20, 16)
                isFocusable = true
                setBackgroundResource(R.drawable.bg_card)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 10
                }
                layoutParams = lp
            }

            val isApk = file.name.lowercase().endsWith(".apk")
            val icon = TextView(this).apply {
                text = if (isApk) "📦" else "📄"
                textSize = 20f
                setPadding(0, 0, 16, 0)
            }
            row.addView(icon)

            val infoLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            }

            val name = TextView(this).apply {
                text = file.name
                setTextColor(Color.WHITE)
                textSize = 14f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            infoLayout.addView(name)

            val size = TextView(this).apply {
                val mb = file.length() / (1024.0 * 1024.0)
                text = String.format("%.2f MB", mb)
                setTextColor(Color.parseColor("#94a3b8"))
                textSize = 11f
            }
            infoLayout.addView(size)
            row.addView(infoLayout)

            val btnOpen = Button(this).apply {
                text = if (isApk) "Namesti" else "Odpri"
                setTextColor(Color.WHITE)
                textSize = 12f
                isFocusable = false
                setBackgroundResource(R.drawable.bg_nav_button)
                setOnClickListener {
                    DownloadHandler.openDownloadedFile(this@MainActivity, file)
                }
            }
            row.addView(btnOpen)

            row.setOnClickListener {
                DownloadHandler.openDownloadedFile(this@MainActivity, file)
            }

            downloadsListContainer.addView(row)
        }
    }

    private fun renderHistoryList() {
        historyListContainer.removeAllViews()
        val history = viewModel.getHistory()
        val textNoHistory = findViewById<TextView>(R.id.textNoHistory)

        if (history.isEmpty()) {
            textNoHistory.visibility = View.VISIBLE
            return
        }
        textNoHistory.visibility = View.GONE

        val sdf = SimpleDateFormat("HH:mm - dd.MM.yyyy", Locale.getDefault())

        for (item in history) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(20, 14, 20, 14)
                isFocusable = true
                setBackgroundResource(R.drawable.bg_card)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 10
                }
                layoutParams = lp
            }

            val icon = TextView(this).apply {
                text = "🕒"
                textSize = 18f
                setPadding(0, 0, 16, 0)
            }
            row.addView(icon)

            val infoLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            }

            val title = TextView(this).apply {
                text = item.title
                setTextColor(Color.WHITE)
                textSize = 14f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            infoLayout.addView(title)

            val details = TextView(this).apply {
                val timeStr = sdf.format(Date(item.visitedAt))
                text = "$timeStr • ${item.url}"
                setTextColor(Color.parseColor("#94a3b8"))
                textSize = 11f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            infoLayout.addView(details)
            row.addView(infoLayout)

            val btnOpen = Button(this).apply {
                text = "Odpri"
                setTextColor(Color.WHITE)
                textSize = 12f
                isFocusable = false
                setBackgroundResource(R.drawable.bg_nav_button)
                setOnClickListener {
                    loadUrl(item.url)
                }
            }
            row.addView(btnOpen)

            row.setOnClickListener {
                loadUrl(item.url)
            }

            row.setOnLongClickListener {
                android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("🗑️ Odstrani Vnos")
                    .setMessage("Ali želite odstraniti \"${item.title}\" iz zgodovine?")
                    .setPositiveButton("Odstrani") { _, _ ->
                        viewModel.deleteHistoryItem(item.id)
                        renderHistoryList()
                        Toast.makeText(this, "Vnos odstranjen.", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Prekliči", null)
                    .show()
                true
            }

            historyListContainer.addView(row)
        }
    }

    private fun showAddBookmarkDialog() {
        val active = getActiveWebView()
        val currentUrl = active?.url ?: ""
        val currentTitle = active?.title ?: ""
        val d = resources.displayMetrics.density

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * d).toInt(), (16 * d).toInt(), (24 * d).toInt(), (16 * d).toInt())
        }

        val textLabelTitle = TextView(this).apply {
            text = "Ime portala:"
            setTextColor(Color.parseColor("#94a3b8"))
            textSize = 13f
        }
        container.addView(textLabelTitle)

        val inputTitle = EditText(this).apply {
            hint = "npr. TMDB Filmi"
            setText(currentTitle)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748b"))
            setBackgroundResource(R.drawable.bg_omnibox)
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
        }
        container.addView(inputTitle)

        val textLabelUrl = TextView(this).apply {
            text = "Spletni naslov (URL):"
            setTextColor(Color.parseColor("#94a3b8"))
            textSize = 13f
            setPadding(0, (12 * d).toInt(), 0, 0)
        }
        container.addView(textLabelUrl)

        val inputUrl = EditText(this).apply {
            hint = "https://..."
            setText(currentUrl)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748b"))
            setBackgroundResource(R.drawable.bg_omnibox)
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
        }
        container.addView(inputUrl)

        val textLabelIcon = TextView(this).apply {
            text = "Ikona / Emoji (npr. ⭐, 🎬, 📺, 🍿, 🐙, 🌐):"
            setTextColor(Color.parseColor("#94a3b8"))
            textSize = 13f
            setPadding(0, (12 * d).toInt(), 0, 0)
        }
        container.addView(textLabelIcon)

        val inputIcon = EditText(this).apply {
            val detectedIcon = when {
                currentUrl.contains("youtube.com") -> "📺"
                currentUrl.contains("themoviedb.org") || currentUrl.contains("tmdb") -> "🍿"
                currentUrl.contains("github.com") -> "🐙"
                currentUrl.contains("stream") -> "🎬"
                else -> "⭐"
            }
            setText(detectedIcon)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748b"))
            setBackgroundResource(R.drawable.bg_omnibox)
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
        }
        container.addView(inputIcon)

        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("⭐ Dodaj Nov Zaznamek")
            .setView(container)
            .setPositiveButton("Dodaj") { _, _ ->
                val title = inputTitle.text.toString().trim()
                val url = inputUrl.text.toString().trim()
                val icon = inputIcon.text.toString().trim().ifBlank { "⭐" }
                if (title.isNotEmpty() && url.isNotEmpty()) {
                    viewModel.addBookmark(title, url, icon)
                    renderBookmarksGrid()
                    Toast.makeText(this, "Zaznamek shranjen!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Prekliči", null)
            .show()
    }

    private fun toggleCursorMode() {
        val current = cursorOverlay.isCursorActive()
        val next = !current
        cursorOverlay.setCursorEnabled(next)
        viewModel.setCursorMode(next)
        if (next) {
            val cx = if (cursorOverlay.width > 0) cursorOverlay.width / 2f else 960f
            val cy = if (cursorOverlay.height > 0) cursorOverlay.height / 2f else 540f
            cursorOverlay.setCursorPosition(cx, cy)
            Toast.makeText(this, "🟡 Virtualni kurzor: VKLOPLJEN (Uporabite smerne tipke)", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "D-Pad navigacija aktivna", Toast.LENGTH_SHORT).show()
            getActiveWebView()?.requestFocus()
        }
    }

    // =========================================================================
    // 🎙️ SPEECH RECOGNITION (PHILIPS TV MIC INTEGRATION)
    // =========================================================================
    private fun startVoiceSearch() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 102)
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            launchSpeechIntentFallback()
            return
        }

        showVoiceListeningHUD()

        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            }

            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "sl-SI")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "sl-SI")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "sl-SI")
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    textVoiceStatus.text = "Govorite zdaj... (npr. 'Odpri YouTube', 'TMDB Filmi')"
                }

                override fun onBeginningOfSpeech() {
                    textVoiceStatus.text = "Poslušam..."
                }

                override fun onRmsChanged(rmsdB: Float) {
                    val scale = 1.0f + (rmsdB.coerceIn(0f, 10f) / 15f)
                    textVoiceMicIcon.scaleX = scale
                    textVoiceMicIcon.scaleY = scale
                }

                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    textVoiceStatus.text = "Obdelujem glasovni ukaz..."
                }

                override fun onError(error: Int) {
                    hideVoiceListeningHUD()
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        Toast.makeText(this@MainActivity, "Govor ni bil zaznan. Poskusite znova.", Toast.LENGTH_SHORT).show()
                    } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                        // Ignore busy error
                    } else {
                        launchSpeechIntentFallback()
                    }
                }

                override fun onResults(results: Bundle?) {
                    hideVoiceListeningHUD()
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val spoken = matches[0]
                        Toast.makeText(this@MainActivity, "🎙️ '$spoken'", Toast.LENGTH_SHORT).show()
                        executeVoiceCommand(spoken)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!partial.isNullOrEmpty()) {
                        textVoiceStatus.text = "» ${partial[0]} «"
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            speechRecognizer?.startListening(recognizerIntent)

        } catch (e: Exception) {
            hideVoiceListeningHUD()
            launchSpeechIntentFallback()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 102) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceSearch()
            } else {
                Toast.makeText(this, "Dovoljenje za mikrofon je bilo zavrnjeno.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showVoiceListeningHUD() {
        hideAllPanels()
        voiceListeningOverlay.visibility = View.VISIBLE
        textVoiceStatus.text = "Pripravljam mikrofon..."
        textVoiceMicIcon.scaleX = 1.0f
        textVoiceMicIcon.scaleY = 1.0f
        viewModel.setVoiceListening(true)
    }

    private fun hideVoiceListeningHUD() {
        voiceListeningOverlay.visibility = View.GONE
        viewModel.setVoiceListening(false)
    }

    private fun stopVoiceSearch() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (ignored: Exception) {}
        hideVoiceListeningHUD()
    }

    private fun launchSpeechIntentFallback() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt))
            }
            startActivityForResult(intent, 101)
        } catch (e: Exception) {
            Toast.makeText(this, "Vnesite želeni naslov v iskalnik z daljincem.", Toast.LENGTH_SHORT).show()
            editUrl.requestFocus()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == RESULT_OK && data != null) {
            val matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val spoken = matches[0]
                Toast.makeText(this, "🎙️ '$spoken'", Toast.LENGTH_SHORT).show()
                executeVoiceCommand(spoken)
            }
        }
    }

    fun executeVoiceCommand(spokenText: String) {
        val result = VoiceCommandEngine.parse(spokenText)
        when (result.type) {
            CommandType.OPEN_URL -> loadUrl(result.payload)
            CommandType.SEARCH -> {
                editUrl.setText(result.payload)
                handleUrlSubmit()
            }
            CommandType.NEW_TAB -> {
                if (webViewPool.size < 4) {
                    val homeUrl = viewModel.homeUrl()
                    createAndSelectTab(homeUrl, "Nov zavihek")
                } else {
                    Toast.makeText(this, "Največ 4 zavihki. Zapri enega.", Toast.LENGTH_SHORT).show()
                }
            }
            CommandType.CLOSE_TAB -> {
                if (webViewPool.size > 1) closeTab(viewModel.state.activeTabIndex)
            }
            CommandType.OPEN_BOOKMARKS -> showBookmarksPanel()
            CommandType.OPEN_DOWNLOADS -> showDownloadsPanel()
            CommandType.OPEN_SETTINGS -> showSettingsPanel()
            CommandType.TOGGLE_CURSOR -> toggleCursorMode()
            CommandType.RELOAD -> getActiveWebView()?.reload()
        }
    }

    // =========================================================================
    // 🎮 D-PAD & VIRTUAL CURSOR DISPATCH
    // =========================================================================
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (customView != null) {
            getActiveWebView()?.onHideCustomViewListener?.invoke()
            return
        }
        if (isTvFullscreenMode) {
            toggleFullscreenMode(false)
            return
        }
        if (bookmarksPanel.visibility == View.VISIBLE ||
            downloadsPanel.visibility == View.VISIBLE ||
            historyPanel.visibility == View.VISIBLE ||
            settingsPanel.visibility == View.VISIBLE ||
            voiceListeningOverlay.visibility == View.VISIBLE) {
            hideAllPanels()
            return
        }
        val active = getActiveWebView()
        if (active != null && active.canGoBack()) {
            active.goBack()
            return
        }
        // Izhod iz brskalnika: sprosti predpomnilnik in zaključi aktivnost
        finish()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Color shortcuts (🟢 Green, 🟡 Yellow, 🔴 Red, 🔵 Blue)
        if (focusManager.handleTvKey(event.keyCode, event)) {
            return true
        }

        if (cursorOverlay.isCursorActive()) {
            if (handleCursorKeyEvent(event)) return true
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                    sendPlayerCommand("TOGGLE_PLAY")
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    sendPlayerCommand("PLAY")
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    sendPlayerCommand("PAUSE")
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD -> {
                    sendPlayerCommand("SEEK_RELATIVE", -10)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> {
                    sendPlayerCommand("SEEK_RELATIVE", 10)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_STOP -> {
                    sendPlayerCommand("PAUSE")
                    return true
                }
            }

            if (isTvFullscreenMode && !cursorOverlay.isCursorActive()) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        sendPlayerCommand("SEEK_RELATIVE", -10)
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        sendPlayerCommand("SEEK_RELATIVE", 10)
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        sendPlayerCommand("SHOW_CONTROLS")
                    }
                }
            }
        }

        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (isTvFullscreenMode || customView != null) {
                if (customView != null) {
                    getActiveWebView()?.onHideCustomViewListener?.invoke()
                }
                if (isTvFullscreenMode) {
                    toggleFullscreenMode(false)
                }
                getActiveWebView()?.evaluateJavascript("""
                    (function() {
                        try {
                            if (document.fullscreenElement || document.webkitFullscreenElement) {
                                if (document.exitFullscreen) document.exitFullscreen();
                                else if (document.webkitExitFullscreen) document.webkitExitFullscreen();
                            }
                            document.querySelectorAll('.tv-fullscreen-video, [data-tv-fullscreen]').forEach(function(el) {
                                el.classList.remove('tv-fullscreen-video');
                                el.removeAttribute('data-tv-fullscreen');
                                el.style.position = '';
                                el.style.top = '';
                                el.style.left = '';
                                el.style.width = '';
                                el.style.height = '';
                                el.style.zIndex = '';
                            });
                        } catch(e) {}
                    })();
                """.trimIndent(), null)
                return true
            }
            if (bookmarksPanel.visibility == View.VISIBLE ||
                downloadsPanel.visibility == View.VISIBLE ||
                historyPanel.visibility == View.VISIBLE ||
                settingsPanel.visibility == View.VISIBLE ||
                voiceListeningOverlay.visibility == View.VISIBLE) {
                hideAllPanels()
                return true
            }
            val active = getActiveWebView()
            if (active != null && active.canGoBack()) {
                active.goBack()
                return true
            }

            // Close modal / player inside single-page apps (StreamNexus / Web players)
            if (active != null) {
                active.evaluateJavascript("""
                    (function() {
                        try {
                            const closeBtn = document.querySelector('#modalCloseBtn, .modal-close, .close-player, #closePlayerBtn, [data-dismiss="modal"], .player-back-btn, #btnBackFromPlayer, .btn-close');
                            if (closeBtn && closeBtn.offsetParent !== null) {
                                closeBtn.click();
                                return "MODAL_CLOSED";
                            }
                            if (window.app && typeof window.app.closePlayer === 'function') {
                                const m = document.getElementById('playerModal');
                                if (m && m.style && m.style.display !== 'none') {
                                    window.app.closePlayer();
                                    return "MODAL_CLOSED";
                                }
                            }
                        } catch(e) {}
                        return "NO_MODAL";
                    })();
                """.trimIndent(), null)
            }

            val now = System.currentTimeMillis()
            if (now - lastBackPressTime < 2500) {
                finish()
            } else {
                lastBackPressTime = now
                Toast.makeText(this, "Pritisnite NAZAJ še enkrat za izhod iz brskalnika", Toast.LENGTH_SHORT).show()
            }
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    private fun handleCursorKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val isDirection = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> true
                else -> false
            }

            if (isDirection) {
                if (event.keyCode == lastDirectionKeyCode) {
                    keyRepeatCount++
                } else {
                    keyRepeatCount = 0
                    lastDirectionKeyCode = event.keyCode
                }
            }

            val currentSpeed = (cursorSpeed + (keyRepeatCount * 4f)).coerceAtMost(60f)
            val h = if (cursorOverlay.height > 0) cursorOverlay.height.toFloat() else 1080f

            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    cursorOverlay.moveBy(0f, -currentSpeed)
                    if (cursorOverlay.getCursorY() <= h * 0.15f) {
                        getActiveWebView()?.scrollBy(0, -60)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    cursorOverlay.moveBy(0f, currentSpeed)
                    if (cursorOverlay.getCursorY() >= h * 0.85f) {
                        getActiveWebView()?.scrollBy(0, 60)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    cursorOverlay.moveBy(-currentSpeed, 0f)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    cursorOverlay.moveBy(currentSpeed, 0f)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    dispatchHardwareTapAtCursor()
                    return true
                }
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            keyRepeatCount = 0
        }
        return false
    }

    private fun dispatchHardwareTapAtCursor() {
        val cx = cursorOverlay.getCursorX()
        val cy = cursorOverlay.getCursorY()

        val active = getActiveWebView() ?: return

        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, cx, cy, 0)
        val up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, cx, cy, 0)

        active.dispatchTouchEvent(down)
        active.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    fun toggleFullscreenMode(forceEnable: Boolean? = null) {
        val target = forceEnable ?: !isTvFullscreenMode
        isTvFullscreenMode = target

        val header = findViewById<View>(R.id.headerContainer)
        val tabs = findViewById<View>(R.id.tabsBarContainer)
        val pill = findViewById<View>(R.id.fullscreenExitPill)

        if (isTvFullscreenMode) {
            hideAllPanels()
            header.visibility = View.GONE
            tabs.visibility = View.GONE
            if (pill != null) pill.visibility = View.GONE
            hideSystemUI()
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            getActiveWebView()?.requestFocus()
        } else {
            header.visibility = View.VISIBLE
            tabs.visibility = View.VISIBLE
            if (pill != null) pill.visibility = View.GONE
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            getActiveWebView()?.evaluateJavascript("""
                (function() {
                    try {
                        if (document.fullscreenElement || document.webkitFullscreenElement) {
                            if (document.exitFullscreen) document.exitFullscreen();
                            else if (document.webkitExitFullscreen) document.webkitExitFullscreen();
                        }
                    } catch(e) {}
                })();
            """.trimIndent(), null)
        }
    }

    fun onWebPlayerStateReceived(stateJson: String) {
        runOnUiThread {
            try {
                val obj = org.json.JSONObject(stateJson)
                val isPlaying = obj.optBoolean("isPlaying", false)

                if (isPlaying) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else if (!isTvFullscreenMode) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            } catch (ignored: Exception) {}
        }
    }

    fun sendPlayerCommand(action: String, value: Any? = null) {
        val js = if (value != null) {
            "window.FreenetPlayerBridge && window.FreenetPlayerBridge.sendCommand('$action', $value);"
        } else {
            "window.FreenetPlayerBridge && window.FreenetPlayerBridge.sendCommand('$action');"
        }
        getActiveWebView()?.evaluateJavascript(js, null)
    }
}

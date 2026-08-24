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
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
        } catch (ignored: Exception) {}

        setContentView(R.layout.activity_main)

        viewModel = BrowserViewModel(this)
        initViews()
        unmuteAudioHardware()

        focusManager = TvFocusManager(
            editUrl = editUrl,
            onToggleCursor = { toggleCursorMode() },
            onStartVoice = { startVoiceSearch() },
            onToggleBookmarks = {
                if (bookmarksPanel.visibility == View.VISIBLE) hideAllPanels()
                else showBookmarksPanel()
            }
        )

        viewModel.onStateChanged = {
            renderTabsBar()
        }

        // Handle initial intent or default search engine home (Max 1 initial tab)
        val homeUrl = viewModel.homeUrl()
        val initialUrl = intent?.data?.toString() ?: homeUrl
        createAndSelectTab(initialUrl, "Iskalnik")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.data?.toString()?.let { url ->
            loadUrl(url)
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

    override fun onDestroy() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (ignored: Exception) {}

        webViewPool.forEach { wv ->
            try {
                webViewContainer.removeView(wv)
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.destroy()
            } catch (ignored: Exception) {}
        }
        webViewPool.clear()

        super.onDestroy()
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

        // Fast Header Links -> YouTube / GitHub / TMDB
        findViewById<View>(R.id.btnQuickSmartTube).setOnClickListener {
            loadUrl("https://www.youtube.com")
        }
        findViewById<View>(R.id.btnQuickGithub).setOnClickListener {
            loadUrl("https://github.com")
        }
        findViewById<View>(R.id.btnQuickTmdb).setOnClickListener {
            loadUrl("https://www.themoviedb.org")
        }

        // TV D-Pad Focus Handoff: Pressing DOWN from header immediately focuses webpage
        val focusToWebListener = View.OnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                getActiveWebView()?.requestFocus()
                true
            } else false
        }
        findViewById<View>(R.id.btnAddTab).setOnKeyListener(focusToWebListener)
        findViewById<View>(R.id.btnQuickSmartTube).setOnKeyListener(focusToWebListener)
        findViewById<View>(R.id.btnQuickGithub).setOnKeyListener(focusToWebListener)
        findViewById<View>(R.id.btnQuickTmdb).setOnKeyListener(focusToWebListener)
        findViewById<View>(R.id.tabsScrollView).setOnKeyListener(focusToWebListener)

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

        findViewById<View>(R.id.btnGo).setOnClickListener {
            handleUrlSubmit()
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
                customView = v
                customViewCallback = cb
                customViewContainer.addView(v)
                customViewContainer.visibility = View.VISIBLE
                findViewById<View>(R.id.headerContainer).visibility = View.GONE
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            onHideCustomViewListener = {
                customViewContainer.removeAllViews()
                customView = null
                customViewContainer.visibility = View.GONE
                findViewById<View>(R.id.headerContainer).visibility = View.VISIBLE
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
            }
            onEdgeReachedTopListener = {
                editUrl.requestFocus()
                editUrl.selectAll()
            }
        }

        val prefs = getSharedPreferences("browser_settings", Context.MODE_PRIVATE)
        webView.adBlockEngine.isEnabled = prefs.getBoolean("adblock_enabled", true)
        webView.adBlockEngine.isAntiAntiAdblockEnabled = prefs.getBoolean("anti_anti_adblock", true)
        webView.adBlockEngine.isCosmeticFilteringEnabled = prefs.getBoolean("cosmetic_filtering", true)
        val uaOrdinal = prefs.getInt("ua_mode", UserAgentMode.TV.ordinal)
        webView.setUserAgentMode(UserAgentMode.values().getOrElse(uaOrdinal) { UserAgentMode.TV })

        webViewPool.add(webView)
        webViewContainer.addView(webView)

        viewModel.addNewTab(url, title)
        selectTab(webViewPool.lastIndex)
        webView.loadUrl(url)
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
            if (rawUrl.startsWith("https://www.google.") || rawUrl.startsWith("http://www.google.") ||
                rawUrl.startsWith("https://duckduckgo.com") || rawUrl.startsWith("https://www.bing.com")) {
                return ""
            }
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
    }

    private fun getActiveWebView(): ChromiumEngineView? {
        val index = viewModel.state.activeTabIndex
        return if (index in webViewPool.indices) webViewPool[index] else null
    }

    private fun renderTabsBar() {
        tabsLayout.removeAllViews()
        val tabs = viewModel.state.tabs
        val activeIndex = viewModel.state.activeTabIndex

        for (i in tabs.indices) {
            val tab = tabs[i]
            val tabLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(20, 6, 20, 6)
                isFocusable = true
                setBackgroundResource(if (i == activeIndex) R.drawable.bg_nav_button else R.drawable.bg_card)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    marginEnd = 12
                }
                layoutParams = lp
            }

            val textTitle = TextView(this).apply {
                text = tab.title
                textSize = 12f
                setTextColor(if (i == activeIndex) Color.parseColor("#38bdf8") else Color.parseColor("#94a3b8"))
                typeface = if (i == activeIndex) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                maxWidth = 260
            }
            tabLayout.addView(textTitle)

            if (tabs.size > 1) {
                val tabIdx = i
                val btnClose = TextView(this).apply {
                    text = " ✕"
                    textSize = 12f
                    setTextColor(Color.parseColor("#ef4444"))
                    setPadding(16, 0, 4, 0)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        closeTab(tabIdx)
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

            tabsLayout.addView(tabLayout)
        }
    }

    private fun hideAllPanels() {
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

        for (bm in bookmarks) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(24, 20, 24, 20)
                isFocusable = true
                setBackgroundResource(R.drawable.bg_card)
                val lp = GridLayout.LayoutParams().apply {
                    width = 220
                    height = 140
                    setMargins(14, 14, 14, 14)
                }
                layoutParams = lp
            }

            val iconText = TextView(this).apply {
                text = bm.icon
                textSize = 28f
                gravity = Gravity.CENTER
            }
            card.addView(iconText)

            val titleText = TextView(this).apply {
                text = bm.title
                textSize = 13f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, 8, 0, 0)
            }
            card.addView(titleText)

            card.setOnClickListener {
                loadUrl(bm.url)
            }

            card.setOnLongClickListener {
                android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("🗑️ Odstrani Zaznamek")
                    .setMessage("Ali res želite odstraniti zaznamek \"${bm.title}\"?")
                    .setPositiveButton("Odstrani") { _, _ ->
                        viewModel.deleteBookmark(bm.id)
                        Toast.makeText(this, "Zaznamek odstranjen.", Toast.LENGTH_SHORT).show()
                        renderBookmarksGrid()
                    }
                    .setNegativeButton("Prekliči", null)
                    .show()
                true
            }

            bookmarksGrid.addView(card)
        }
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

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val inputTitle = EditText(this).apply {
            hint = "Ime portala (npr. TMDB Filmi)"
            setText(currentTitle)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748b"))
        }
        container.addView(inputTitle)

        val inputUrl = EditText(this).apply {
            hint = "Spletni naslov (npr. https://...)"
            setText(currentUrl)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748b"))
        }
        container.addView(inputUrl)

        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("⭐ Dodaj Nov Zaznamek")
            .setView(container)
            .setPositiveButton("Dodaj") { _, _ ->
                val title = inputTitle.text.toString().trim()
                val url = inputUrl.text.toString().trim()
                if (title.isNotEmpty() && url.isNotEmpty()) {
                    viewModel.addBookmark(title, url, "⭐")
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
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Color shortcuts (🟢 Green, 🟡 Yellow, 🔴 Red, 🔵 Blue)
        if (focusManager.handleTvKey(event.keyCode, event)) {
            return true
        }

        if (cursorOverlay.isCursorActive()) {
            if (handleCursorKeyEvent(event)) return true
        }

        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (customView != null) {
                getActiveWebView()?.webChromeClient?.onHideCustomView()
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
}

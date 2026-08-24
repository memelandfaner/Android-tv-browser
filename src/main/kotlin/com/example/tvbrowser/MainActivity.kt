package com.example.tvbrowser

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.widget.*
import java.io.File

class MainActivity : Activity() {

    private lateinit var viewModel: BrowserViewModel
    private lateinit var focusManager: TvFocusManager

    private lateinit var webViewContainer: FrameLayout
    private lateinit var customViewContainer: FrameLayout
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var customView: View? = null

    private lateinit var cursorOverlay: CursorOverlay
    private var cursorSpeed = 26f

    private lateinit var editUrl: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var bookmarksPanel: ScrollView
    private lateinit var downloadsPanel: ScrollView
    private lateinit var settingsPanel: ScrollView
    private lateinit var bookmarksGrid: GridLayout
    private lateinit var downloadsListContainer: LinearLayout
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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

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

        // Handle initial intent if launched with URL
        val initialUrl = intent?.data?.toString() ?: "https://www.google.com"
        createAndSelectTab(initialUrl, if (initialUrl == "https://www.google.com") "Google Iskanje" else "Splet")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.data?.toString()?.let { url ->
            loadUrl(url)
        }
    }

    private fun initViews() {
        webViewContainer = findViewById(R.id.webViewContainer)
        customViewContainer = findViewById(R.id.customViewContainer)
        cursorOverlay = findViewById(R.id.cursorOverlay)
        editUrl = findViewById(R.id.editUrl)
        progressBar = findViewById(R.id.pageProgressBar)

        bookmarksPanel = findViewById(R.id.bookmarksPanel)
        downloadsPanel = findViewById(R.id.downloadsPanel)
        settingsPanel = findViewById(R.id.settingsPanel)
        bookmarksGrid = findViewById(R.id.bookmarksGrid)
        downloadsListContainer = findViewById(R.id.downloadsListContainer)
        tabsLayout = findViewById(R.id.tabsLayout)

        // Voice Listening HUD
        voiceListeningOverlay = findViewById(R.id.voiceListeningOverlay)
        textVoiceStatus = findViewById(R.id.textVoiceStatus)
        textVoiceMicIcon = findViewById(R.id.textVoiceMicIcon)

        findViewById<View>(R.id.btnCancelVoice).setOnClickListener {
            stopVoiceSearch()
        }

        // Fast Header Links -> YouTube Home feed
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

        // 🏠 Home -> Always Google Search
        findViewById<View>(R.id.btnHome).setOnClickListener {
            loadUrl("https://www.google.com")
        }

        findViewById<View>(R.id.btnGo).setOnClickListener {
            handleUrlSubmit()
        }

        findViewById<View>(R.id.btnMic).setOnClickListener {
            startVoiceSearch()
        }

        editUrl.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                handleUrlSubmit()
                hideSoftKeyboard()
                true
            } else false
        }
        editUrl.setOnKeyListener(focusToWebListener)

        findViewById<View>(R.id.btnStarBookmark).setOnClickListener {
            val active = getActiveWebView()
            if (active != null) {
                val currentUrl = active.url ?: ""
                val currentTitle = active.title ?: currentUrl
                if (currentUrl.isNotEmpty()) {
                    viewModel.addBookmark(currentTitle, currentUrl, "⭐")
                    Toast.makeText(this, "⭐ Shranjeno: $currentTitle", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<View>(R.id.btnNavBookmarks).setOnClickListener {
            if (bookmarksPanel.visibility == View.VISIBLE) hideAllPanels()
            else showBookmarksPanel()
        }

        findViewById<View>(R.id.btnNavDownloads).setOnClickListener {
            if (downloadsPanel.visibility == View.VISIBLE) hideAllPanels()
            else showDownloadsPanel()
        }

        findViewById<View>(R.id.btnToggleCursor).setOnClickListener {
            toggleCursorMode()
        }

        findViewById<View>(R.id.btnNavSettings).setOnClickListener {
            if (settingsPanel.visibility == View.VISIBLE) hideAllPanels()
            else showSettingsPanel()
        }

        // ➕ Add Tab -> Always Google Search
        findViewById<View>(R.id.btnAddTab).setOnClickListener {
            createAndSelectTab("https://www.google.com", "Google Iskanje")
            Toast.makeText(this, "➕ Odprt nov zavihek", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btnAddCustomBookmark).setOnClickListener {
            showAddBookmarkDialog()
        }

        findViewById<View>(R.id.btnClearCache).setOnClickListener {
            BrowserRepository(this).clearHistory()
            Toast.makeText(this, "Predpomnilnik in zgodovina sta bila izbrisana.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun unmuteAudioHardware() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamMute(AudioManager.STREAM_MUSIC, false)
            am.mode = AudioManager.MODE_NORMAL
        } catch (ignored: Exception) {}
    }

    private fun handleUrlSubmit() {
        val target = viewModel.processUrlInput(editUrl.text.toString())
        loadUrl(target)
    }

    fun loadUrl(url: String) {
        hideAllPanels()
        val active = getActiveWebView()
        if (active != null) {
            editUrl.setText(url)
            active.loadUrl(url)
        } else {
            createAndSelectTab(url, "Nalagam...")
        }
    }

    private fun createAndSelectTab(url: String, title: String) {
        val tabIndex = webViewPool.size

        val webView = ChromiumEngineView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            onProgressChangedListener = { p ->
                progressBar.progress = p
                progressBar.visibility = if (p in 1..99) View.VISIBLE else View.GONE
                viewModel.updateTabProgress(tabIndex, p)
            }
            onTitleReceivedListener = { t ->
                viewModel.updateTabTitle(tabIndex, t)
                renderTabsBar()
            }
            onUrlChangedListener = { u ->
                if (webViewPool.indexOf(this) == viewModel.state.activeTabIndex) {
                    editUrl.setText(u)
                }
                viewModel.updateTabUrl(tabIndex, u)
            }
            onShowCustomViewListener = { v, cb ->
                customView = v
                customViewCallback = cb
                customViewContainer.addView(v)
                customViewContainer.visibility = View.VISIBLE
                findViewById<View>(R.id.headerContainer).visibility = View.GONE
            }
            onHideCustomViewListener = {
                customViewContainer.removeAllViews()
                customView = null
                customViewContainer.visibility = View.GONE
                findViewById<View>(R.id.headerContainer).visibility = View.VISIBLE
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
            }
            onEdgeReachedTopListener = {
                editUrl.requestFocus()
                editUrl.selectAll()
            }
        }

        webViewPool.add(webView)
        webViewContainer.addView(webView)

        viewModel.addNewTab(url, title)
        selectTab(webViewPool.lastIndex)
        webView.loadUrl(url)
    }

    private fun selectTab(index: Int) {
        if (index !in webViewPool.indices) return

        for (i in webViewPool.indices) {
            val v = webViewPool[i]
            if (i == index) {
                v.visibility = View.VISIBLE
                v.onResume()
                editUrl.setText(v.url ?: "")
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
            }
            tabLayout.addView(textTitle)

            if (tabs.size > 1) {
                val btnClose = TextView(this).apply {
                    text = " ✕"
                    textSize = 11f
                    setTextColor(Color.parseColor("#ef4444"))
                    setPadding(12, 0, 4, 0)
                    setOnClickListener { closeTab(i) }
                }
                tabLayout.addView(btnClose)
            }

            tabLayout.setOnClickListener { selectTab(i) }
            tabsLayout.addView(tabLayout)
        }
    }

    private fun showBookmarksPanel() {
        hideAllPanels()
        bookmarksPanel.visibility = View.VISIBLE
        renderBookmarksGrid()
    }

    private fun showDownloadsPanel() {
        hideAllPanels()
        downloadsPanel.visibility = View.VISIBLE
        renderDownloadsList()
    }

    private fun showSettingsPanel() {
        hideAllPanels()
        settingsPanel.visibility = View.VISIBLE
    }

    private fun hideAllPanels() {
        bookmarksPanel.visibility = View.GONE
        downloadsPanel.visibility = View.GONE
        settingsPanel.visibility = View.GONE
        voiceListeningOverlay.visibility = View.GONE
        viewModel.hideAllPanels()
    }

    private fun renderBookmarksGrid() {
        bookmarksGrid.removeAllViews()
        val bookmarks = viewModel.state.bookmarks

        for (item in bookmarks) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(24, 24, 24, 24)
                setBackgroundResource(R.drawable.bg_card)
                isFocusable = true
                val lp = GridLayout.LayoutParams().apply {
                    width = 240
                    height = 140
                    setMargins(14, 14, 14, 14)
                }
                layoutParams = lp
            }

            val textIcon = TextView(this).apply {
                text = item.icon
                textSize = 28f
                gravity = Gravity.CENTER
            }
            val textTitle = TextView(this).apply {
                text = item.title
                textSize = 13f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                maxLines = 1
            }
            val textUrl = TextView(this).apply {
                text = item.url
                textSize = 10f
                setTextColor(Color.parseColor("#94a3b8"))
                gravity = Gravity.CENTER
                maxLines = 1
            }

            card.addView(textIcon)
            card.addView(textTitle)
            card.addView(textUrl)

            card.setOnClickListener {
                loadUrl(item.url)
            }

            card.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Izbriši zaznamek?")
                    .setMessage(item.title)
                    .setPositiveButton("Izbriši", DialogInterface.OnClickListener { _, _ ->
                        viewModel.deleteBookmark(item.id)
                        renderBookmarksGrid()
                    })
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
        val files = dir?.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

        if (files.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "V mapi Prenosi še ni nobenih datotek."
                setTextColor(Color.parseColor("#94a3b8"))
                setPadding(20, 20, 20, 20)
            }
            downloadsListContainer.addView(emptyTv)
            return
        }

        for (file in files) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(20, 16, 20, 16)
                setBackgroundResource(R.drawable.bg_card)
                isFocusable = true
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 10
                }
                layoutParams = lp
            }

            val iconTv = TextView(this).apply {
                text = if (file.name.lowercase().endsWith(".apk")) "📦"
                else if (file.name.lowercase().endsWith(".mp4") || file.name.lowercase().endsWith(".mkv")) "🎬"
                else "📄"
                textSize = 20f
                setPadding(0, 0, 16, 0)
            }

            val nameTv = TextView(this).apply {
                text = file.name
                textSize = 14f
                setTextColor(Color.WHITE)
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                layoutParams = lp
            }

            val sizeTv = TextView(this).apply {
                val sizeMb = file.length() / (1024.0 * 1024.0)
                text = String.format("%.2f MB", sizeMb)
                textSize = 12f
                setTextColor(Color.parseColor("#94a3b8"))
            }

            row.addView(iconTv)
            row.addView(nameTv)
            row.addView(sizeTv)

            row.setOnClickListener {
                DownloadHandler.openDownloadedFile(this, file)
            }

            downloadsListContainer.addView(row)
        }
    }

    private fun showAddBookmarkDialog() {
        val active = getActiveWebView()
        val curUrl = active?.url ?: "https://"
        val curTitle = active?.title ?: "Zaznamek"

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val inputTitle = EditText(this).apply {
            hint = "Ime zaznamka"
            setText(curTitle)
            setTextColor(Color.WHITE)
        }
        val inputUrl = EditText(this).apply {
            hint = "URL naslov"
            setText(curUrl)
            setTextColor(Color.WHITE)
        }

        layout.addView(inputTitle)
        layout.addView(inputUrl)

        AlertDialog.Builder(this)
            .setTitle("⭐ Dodaj Zaznamek")
            .setView(layout)
            .setPositiveButton("Shrani", DialogInterface.OnClickListener { _, _ ->
                val title = inputTitle.text.toString().trim()
                val url = inputUrl.text.toString().trim()
                if (url.isNotEmpty()) {
                    viewModel.addBookmark(title, url, "⭐")
                    renderBookmarksGrid()
                    Toast.makeText(this, "Zaznamek shranjen!", Toast.LENGTH_SHORT).show()
                }
            })
            .setNegativeButton("Prekliči", null)
            .show()
    }

    private fun toggleCursorMode() {
        val newMode = !cursorOverlay.isCursorActive()
        cursorOverlay.setCursorEnabled(newMode)
        viewModel.setCursorMode(newMode)

        val btn = findViewById<Button>(R.id.btnToggleCursor)
        if (newMode) {
            btn.text = "🖱️ Kazalec"
            btn.setBackgroundResource(R.drawable.bg_nav_button)
            Toast.makeText(this, "🟡 Kurzor VKLOPLJEN (Premikaj z D-Padom, OK za klik)", Toast.LENGTH_SHORT).show()
        } else {
            btn.text = "🎛️ Kurzor"
            btn.setBackgroundResource(R.drawable.bg_nav_button)
            Toast.makeText(this, "🟡 Kurzor IZKLOPLJEN (Standardni D-Pad fokus)", Toast.LENGTH_SHORT).show()
        }
    }

    // =========================================================================
    // 🎙️ IN-APP VOICE RECOGNITION (Katniss / Assistant Bridge)
    // =========================================================================
    private fun startVoiceSearch() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 102)
                return
            }
        }

        showVoiceListeningHUD()

        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    textVoiceStatus.text = "Poslušam... Govorite zdaj!"
                }

                override fun onBeginningOfSpeech() {
                    textVoiceStatus.text = "Zaznan govor... Prepoznavam..."
                }

                override fun onRmsChanged(rmsdB: Float) {
                    val scale = 1.0f + (rmsdB.coerceIn(0f, 10f) / 15f)
                    textVoiceMicIcon.scaleX = scale
                    textVoiceMicIcon.scaleY = scale
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    textVoiceStatus.text = "Obdelujem ukaz..."
                }

                override fun onError(error: Int) {
                    hideVoiceListeningHUD()
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        Toast.makeText(this@MainActivity, "Govor ni bil zaznan. Poskusite znova.", Toast.LENGTH_SHORT).show()
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
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        textVoiceStatus.text = "🎙️ '${matches[0]}'"
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "sl-SI")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

            speechRecognizer?.startListening(recognizerIntent)

        } catch (e: Exception) {
            hideVoiceListeningHUD()
            launchSpeechIntentFallback()
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
            CommandType.NEW_TAB -> createAndSelectTab("https://www.google.com", "Google Iskanje")
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
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    cursorOverlay.moveBy(0f, -cursorSpeed)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    cursorOverlay.moveBy(0f, cursorSpeed)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    cursorOverlay.moveBy(-cursorSpeed, 0f)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    cursorOverlay.moveBy(cursorSpeed, 0f)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    dispatchHardwareTapAtCursor()
                    return true
                }
            }
        }
        return false
    }

    private fun dispatchHardwareTapAtCursor() {
        val cx = cursorOverlay.getCursorX()
        val cy = cursorOverlay.getCursorY()
        val now = android.os.SystemClock.uptimeMillis()

        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, cx, cy, 0)
        val up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, cx, cy, 0)

        val active = getActiveWebView()
        if (active != null) {
            active.dispatchTouchEvent(down)
            active.dispatchTouchEvent(up)
        } else {
            window.decorView.dispatchTouchEvent(down)
            window.decorView.dispatchTouchEvent(up)
        }

        down.recycle()
        up.recycle()
    }

    private fun hideSoftKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        val view = currentFocus
        if (imm != null && view != null) {
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    override fun onDestroy() {
        try {
            speechRecognizer?.destroy()
        } catch (ignored: Exception) {}
        super.onDestroy()
    }
}

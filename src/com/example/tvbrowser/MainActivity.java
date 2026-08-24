package com.example.tvbrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_VOICE_SEARCH = 101;
    private static final String DEFAULT_HOME_URL = "http://192.168.0.135:3000";
    private static final String DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final String TV_UA = "Mozilla/5.0 (Linux; Android 11; Philips UHD Android TV Build/RTM4.220308.106) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    private FrameLayout mWebViewContainer;
    private FrameLayout mCustomViewContainer;
    private WebChromeClient.CustomViewCallback mCustomViewCallback;
    private View mCustomView;

    private CursorOverlay mCursorOverlay;
    private boolean mCursorMode = false;
    private float mCursorSpeed = 24f;

    private BookmarkManager mBookmarkManager;
    private AdBlockEngine mAdBlockEngine;
    private TabManager mTabManager;

    private EditText mEditUrl;
    private ProgressBar mProgressBar;
    private ScrollView mBookmarksPanel;
    private ScrollView mDownloadsPanel;
    private ScrollView mSettingsPanel;
    private GridLayout mBookmarksGrid;
    private LinearLayout mDownloadsListContainer;
    private LinearLayout mTabsLayout;
    private LinearLayout mAiSuggestionsLayout;

    private String mCurrentSearchEngine = "https://www.google.com/search?q=";
    private boolean mIsDesktopMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        try {
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
        } catch (Exception ignored) {}

        setContentView(R.layout.activity_main);

        mBookmarkManager = new BookmarkManager(this);
        mAdBlockEngine = new AdBlockEngine();
        mTabManager = new TabManager();

        initViews();
        initAiSuggestions();
        unmuteAudioHardware();

        // Create initial tab
        Intent intent = getIntent();
        if (intent != null && intent.getData() != null) {
            createAndSelectTab(intent.getData().toString(), "Začetna stran");
        } else {
            createAndSelectTab(DEFAULT_HOME_URL, "StreamNexus HD");
            showBookmarksPanel();
        }
    }

    private void initViews() {
        mWebViewContainer = findViewById(R.id.webViewContainer);
        mCustomViewContainer = findViewById(R.id.customViewContainer);
        mCursorOverlay = findViewById(R.id.cursorOverlay);
        mEditUrl = findViewById(R.id.editUrl);
        mProgressBar = findViewById(R.id.pageProgressBar);

        mBookmarksPanel = findViewById(R.id.bookmarksPanel);
        mDownloadsPanel = findViewById(R.id.downloadsPanel);
        mSettingsPanel = findViewById(R.id.settingsPanel);
        mBookmarksGrid = findViewById(R.id.bookmarksGrid);
        mDownloadsListContainer = findViewById(R.id.downloadsListContainer);
        mTabsLayout = findViewById(R.id.tabsLayout);
        mAiSuggestionsLayout = findViewById(R.id.aiSuggestionsLayout);

        // Edge scroll listener for virtual cursor
        mCursorOverlay.setOnEdgeScrollListener(new CursorOverlay.OnEdgeScrollListener() {
            @Override
            public void onEdgeScroll(int scrollY) {
                WebView activeView = getActiveWebView();
                if (activeView != null && mCursorMode) {
                    activeView.scrollBy(0, scrollY);
                }
            }
        });

        // Top Navigation buttons
        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WebView active = getActiveWebView();
                if (active != null && active.canGoBack()) {
                    hideAllPanels();
                    active.goBack();
                }
            }
        });

        findViewById(R.id.btnForward).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WebView active = getActiveWebView();
                if (active != null && active.canGoForward()) {
                    hideAllPanels();
                    active.goForward();
                }
            }
        });

        findViewById(R.id.btnRefresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideAllPanels();
                WebView active = getActiveWebView();
                if (active != null) active.reload();
            }
        });

        findViewById(R.id.btnHome).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showBookmarksPanel();
            }
        });

        findViewById(R.id.btnGo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleUrlSubmit();
            }
        });

        findViewById(R.id.btnMic).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startVoiceSearch();
            }
        });

        mEditUrl.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    handleUrlSubmit();
                    hideSoftKeyboard();
                    return true;
                }
                return false;
            }
        });

        findViewById(R.id.btnStarBookmark).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WebView active = getActiveWebView();
                if (active != null) {
                    String currentUrl = active.getUrl();
                    String currentTitle = active.getTitle();
                    if (currentUrl != null && !currentUrl.isEmpty()) {
                        mBookmarkManager.addBookmark(currentTitle, currentUrl, "⭐");
                        Toast.makeText(MainActivity.this, "⭐ Shranjeno med zaznamke: " + (currentTitle != null ? currentTitle : currentUrl), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Ni aktivne strani za shranjevanje.", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        findViewById(R.id.btnNavBookmarks).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mBookmarksPanel.getVisibility() == View.VISIBLE) hideAllPanels();
                else showBookmarksPanel();
            }
        });

        findViewById(R.id.btnNavDownloads).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mDownloadsPanel.getVisibility() == View.VISIBLE) hideAllPanels();
                else showDownloadsPanel();
            }
        });

        findViewById(R.id.btnToggleCursor).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleCursorMode();
            }
        });

        findViewById(R.id.btnNavSettings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSettingsPanel.getVisibility() == View.VISIBLE) hideAllPanels();
                else showSettingsPanel();
            }
        });

        findViewById(R.id.btnAddTab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createAndSelectTab("https://www.google.com", "Nov zavihek");
                Toast.makeText(MainActivity.this, "➕ Odprt nov zavihek", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btnAddCustomBookmark).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddBookmarkDialog();
            }
        });

        // Settings actions
        final Button btnToggleAdblock = findViewById(R.id.btnToggleAdblock);
        btnToggleAdblock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean newState = !mAdBlockEngine.isEnabled();
                mAdBlockEngine.setEnabled(newState);
                btnToggleAdblock.setText(newState ? "VKLOPLJENO" : "IZKLOPLJENO");
                btnToggleAdblock.setTextColor(newState ? Color.parseColor("#00d2ff") : Color.parseColor("#ff4b4b"));
                Toast.makeText(MainActivity.this, "AdBlocker: " + (newState ? "Vklopljen" : "Izklopljen"), Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btnSwitchEngine).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCurrentSearchEngine.contains("google")) {
                    mCurrentSearchEngine = "https://duckduckgo.com/?q=";
                    ((TextView) findViewById(R.id.textCurrentEngine)).setText("Trenutno: DuckDuckGo");
                } else if (mCurrentSearchEngine.contains("duckduckgo")) {
                    mCurrentSearchEngine = "https://www.bing.com/search?q=";
                    ((TextView) findViewById(R.id.textCurrentEngine)).setText("Trenutno: Bing Iskanje");
                } else {
                    mCurrentSearchEngine = "https://www.google.com/search?q=";
                    ((TextView) findViewById(R.id.textCurrentEngine)).setText("Trenutno: Google Iskanje");
                }
            }
        });

        findViewById(R.id.btnToggleUserAgent).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsDesktopMode = !mIsDesktopMode;
                for (TabManager.TabItem item : mTabManager.getTabs()) {
                    if (item.webView != null) {
                        item.webView.getSettings().setUserAgentString(mIsDesktopMode ? DESKTOP_UA : TV_UA);
                    }
                }
                ((TextView) findViewById(R.id.textCurrentUserAgent)).setText(mIsDesktopMode ?
                        "Trenutno: 4K Desktop Mode (Polna ločljivost strani)" : "Trenutno: Android TV Mode");
                WebView active = getActiveWebView();
                if (active != null) active.reload();
                Toast.makeText(MainActivity.this, "Način prikaza: " + (mIsDesktopMode ? "Desktop 4K" : "TV Mode"), Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btnClearCache).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (TabManager.TabItem item : mTabManager.getTabs()) {
                    if (item.webView != null) {
                        item.webView.clearCache(true);
                        item.webView.clearHistory();
                    }
                }
                CookieManager.getInstance().removeAllCookies(null);
                CookieManager.getInstance().flush();
                Toast.makeText(MainActivity.this, "🧹 Predpomnilnik in piškotki počiščeni!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initAiSuggestions() {
        mAiSuggestionsLayout.removeAllViews();

        class Suggestion {
            String label;
            String target;
            Suggestion(String l, String t) { label = l; target = t; }
        }

        Suggestion[] list = {
            new Suggestion("🎬 StreamNexus HD", "http://192.168.0.135:3000"),
            new Suggestion("🍿 TOP Filmi Danes", "https://www.themoviedb.org/movie"),
            new Suggestion("📺 YouTube TV", "https://www.youtube.com"),
            new Suggestion("🤖 Vprašaj AI", "https://www.google.com/search?q=AI+Smart+Assistant"),
            new Suggestion("🎵 Radio & Glasba", "https://radio.garden"),
            new Suggestion("💬 Reddit TV", "https://www.reddit.com"),
            new Suggestion("🐙 GitHub", "https://github.com"),
            new Suggestion("🎮 Twitch TV", "https://www.twitch.tv")
        };

        for (final Suggestion item : list) {
            Button pill = new Button(this);
            pill.setBackgroundResource(R.drawable.bg_ai_pill);
            pill.setText(item.label);
            pill.setTextColor(Color.WHITE);
            pill.setTextSize(12);
            pill.setPadding(20, 0, 20, 0);
            pill.setFocusable(true);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 10, 0);
            pill.setLayoutParams(params);

            pill.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    loadUrl(item.target);
                }
            });

            mAiSuggestionsLayout.addView(pill);
        }
    }

    // =========================================================================
    // 📑 MULTI-TAB ENGINE
    // =========================================================================
    public void createAndSelectTab(String url, String title) {
        WebView newWebView = createConfiguredWebView();
        String id = "tab_" + System.currentTimeMillis();
        TabManager.TabItem newTab = new TabManager.TabItem(id, title, url, newWebView);
        mTabManager.addTab(newTab);

        mWebViewContainer.addView(newWebView);
        selectTab(mTabManager.getActiveIndex());

        if (url != null && !url.isEmpty()) {
            newWebView.loadUrl(url);
        }
    }

    public void selectTab(int index) {
        mTabManager.setActiveIndex(index);
        TabManager.TabItem active = mTabManager.getActiveTab();

        for (int i = 0; i < mWebViewContainer.getChildCount(); i++) {
            View child = mWebViewContainer.getChildAt(i);
            child.setVisibility(View.GONE);
        }

        if (active != null && active.webView != null) {
            active.webView.setVisibility(View.VISIBLE);
            active.webView.requestFocus();
            if (active.webView.getUrl() != null) {
                mEditUrl.setText(active.webView.getUrl());
            }
        }

        renderTabsBar();
    }

    public void closeTab(int index) {
        TabManager.TabItem item = mTabManager.getTabs().get(index);
        if (item.webView != null) {
            mWebViewContainer.removeView(item.webView);
        }
        mTabManager.removeTab(index);
        selectTab(mTabManager.getActiveIndex());
    }

    private void renderTabsBar() {
        mTabsLayout.removeAllViews();
        List<TabManager.TabItem> tabs = mTabManager.getTabs();
        int activeIdx = mTabManager.getActiveIndex();

        for (int i = 0; i < tabs.size(); i++) {
            final int pos = i;
            final TabManager.TabItem tab = tabs.get(i);
            boolean isActive = (i == activeIdx);

            LinearLayout tabView = new LinearLayout(this);
            tabView.setOrientation(LinearLayout.HORIZONTAL);
            tabView.setGravity(Gravity.CENTER_VERTICAL);
            tabView.setBackgroundResource(isActive ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
            tabView.setFocusable(true);
            tabView.setPadding(16, 6, 12, 6);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 8, 0);
            tabView.setLayoutParams(params);

            TextView titleText = new TextView(this);
            titleText.setText("🌐 " + tab.title);
            titleText.setTextColor(isActive ? Color.parseColor("#00d2ff") : Color.WHITE);
            titleText.setTextSize(13);
            titleText.setMaxWidth(300);
            titleText.setSingleLine(true);

            Button closeBtn = new Button(this);
            closeBtn.setText("✕");
            closeBtn.setTextColor(Color.parseColor("#9ca3af"));
            closeBtn.setBackgroundColor(Color.TRANSPARENT);
            closeBtn.setTextSize(12);
            closeBtn.setPadding(8, 0, 8, 0);
            closeBtn.setFocusable(true);

            closeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mTabManager.getCount() > 1) {
                        closeTab(pos);
                    } else {
                        Toast.makeText(MainActivity.this, "Zadnjega zavihka ni mogoče zapreti.", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            tabView.addView(titleText);
            if (tabs.size() > 1) {
                tabView.addView(closeBtn);
            }

            tabView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectTab(pos);
                }
            });

            mTabsLayout.addView(tabView);
        }
    }

    private WebView getActiveWebView() {
        TabManager.TabItem active = mTabManager.getActiveTab();
        return active != null ? active.webView : null;
    }

    private WebView createConfiguredWebView() {
        final WebView webView = new WebView(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        webView.setLayoutParams(params);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUserAgentString(mIsDesktopMode ? DESKTOP_UA : TV_UA);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                DownloadHandler.enqueueDownload(MainActivity.this, url, userAgent, contentDisposition, mimeType);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return false;
                String url = request.getUrl().toString();

                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        return true;
                    }
                }

                // Top-Frame Lock against ad popunders & unwanted redirects
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && request.isForMainFrame()) {
                    if (mAdBlockEngine.isBlocked(url)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return null;
                String url = request.getUrl().toString();

                if (mAdBlockEngine.isBlocked(url)) {
                    return AdBlockEngine.createEmptyResponse("text/plain");
                }

                if (mAdBlockEngine.isDevToolBlocker(url)) {
                    return AdBlockEngine.createEmptyResponse("application/javascript");
                }

                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                mProgressBar.setVisibility(View.VISIBLE);
                if (view == getActiveWebView() && url != null) {
                    mEditUrl.setText(url);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                mProgressBar.setVisibility(View.GONE);
                if (view == getActiveWebView() && url != null) {
                    mEditUrl.setText(url);
                }

                TabManager.TabItem active = mTabManager.getActiveTab();
                if (active != null && active.webView == view) {
                    if (view.getTitle() != null && !view.getTitle().isEmpty()) {
                        active.title = view.getTitle();
                        renderTabsBar();
                    }
                }

                // Auto-unmute video tags and inject clean TV styling
                String js = "(function() {" +
                        "  document.querySelectorAll('video').forEach(function(v) {" +
                        "    v.muted = false;" +
                        "    v.volume = 1.0;" +
                        "  });" +
                        "})();";
                view.evaluateJavascript(js, null);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                mProgressBar.setProgress(newProgress);
                if (newProgress == 100) mProgressBar.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                for (TabManager.TabItem tab : mTabManager.getTabs()) {
                    if (tab.webView == view && title != null && !title.isEmpty()) {
                        tab.title = title;
                        renderTabsBar();
                        break;
                    }
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (mCustomView != null) {
                    onHideCustomView();
                    return;
                }
                mCustomView = view;
                mCustomViewCallback = callback;
                mCustomViewContainer.addView(view);
                mCustomViewContainer.setVisibility(View.VISIBLE);
                findViewById(R.id.headerContainer).setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                if (mCustomView == null) return;
                mCustomViewContainer.removeView(mCustomView);
                mCustomView = null;
                mCustomViewContainer.setVisibility(View.GONE);
                findViewById(R.id.headerContainer).setVisibility(View.VISIBLE);
                if (mCustomViewCallback != null) {
                    mCustomViewCallback.onCustomViewHidden();
                    mCustomViewCallback = null;
                }
            }
        });

        return webView;
    }

    // =========================================================================
    // 🎙️ GLASOVNO UPRAVLJANJE & AI INTENTI
    // =========================================================================
    private void startVoiceSearch() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt));
            startActivityForResult(intent, REQ_VOICE_SEARCH);
        } catch (Exception e) {
            Toast.makeText(this, "Glasovno prepoznavanje ni na voljo: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VOICE_SEARCH && resultCode == RESULT_OK && data != null) {
            ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) {
                String spoken = matches.get(0);
                Toast.makeText(this, "🎙️ '" + spoken + "'", Toast.LENGTH_SHORT).show();
                executeVoiceCommand(spoken);
            }
        }
    }

    public void executeVoiceCommand(String spokenText) {
        VoiceCommandEngine.CommandResult result = VoiceCommandEngine.parse(spokenText);

        switch (result.type) {
            case OPEN_URL:
                loadUrl(result.payload);
                break;
            case SEARCH:
                mEditUrl.setText(result.payload);
                handleUrlSubmit();
                break;
            case NEW_TAB:
                createAndSelectTab("https://www.google.com", "Nov zavihek");
                break;
            case CLOSE_TAB:
                if (mTabManager.getCount() > 1) {
                    closeTab(mTabManager.getActiveIndex());
                }
                break;
            case OPEN_BOOKMARKS:
                showBookmarksPanel();
                break;
            case OPEN_DOWNLOADS:
                showDownloadsPanel();
                break;
            case OPEN_SETTINGS:
                showSettingsPanel();
                break;
            case TOGGLE_CURSOR:
                toggleCursorMode();
                break;
            case RELOAD:
                WebView active = getActiveWebView();
                if (active != null) active.reload();
                break;
        }
    }

    private void unmuteAudioHardware() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.setStreamMute(AudioManager.STREAM_MUSIC, false);
                am.setMode(AudioManager.MODE_NORMAL);
            }
        } catch (Exception ignored) {}
    }

    private void handleUrlSubmit() {
        String input = mEditUrl.getText().toString().trim();
        if (input.isEmpty()) return;

        hideAllPanels();

        if (input.startsWith("http://") || input.startsWith("https://")) {
            loadUrl(input);
        } else if (input.contains(".") && !input.contains(" ")) {
            loadUrl("https://" + input);
        } else {
            loadUrl(mCurrentSearchEngine + Uri.encode(input));
        }
    }

    private void loadUrl(String url) {
        hideAllPanels();
        WebView active = getActiveWebView();
        if (active != null) {
            active.loadUrl(url);
            active.requestFocus();
        }
    }

    private void toggleCursorMode() {
        mCursorMode = !mCursorMode;
        mCursorOverlay.setVisibility(mCursorMode ? View.VISIBLE : View.GONE);
        Button btn = findViewById(R.id.btnToggleCursor);
        btn.setText(mCursorMode ? "🖱️ Kurzor: VKLOP" : "🖱️ Kurzor");
        btn.setTextColor(mCursorMode ? Color.parseColor("#00d2ff") : Color.WHITE);
        Toast.makeText(this, mCursorMode ? "Virtualni kurzor: VKLOPLJEN (Krmilite z D-Padom ali gobico)" : "Virtualni kurzor: IZKLOPLJEN", Toast.LENGTH_SHORT).show();
    }

    private void showBookmarksPanel() {
        hideAllPanels();
        mWebViewContainer.setVisibility(View.GONE);
        mBookmarksPanel.setVisibility(View.VISIBLE);
        mBookmarksPanel.bringToFront();
        renderBookmarksGrid();
    }

    private void showDownloadsPanel() {
        hideAllPanels();
        mWebViewContainer.setVisibility(View.GONE);
        mDownloadsPanel.setVisibility(View.VISIBLE);
        mDownloadsPanel.bringToFront();
        renderDownloadsList();
    }

    private void showSettingsPanel() {
        hideAllPanels();
        mWebViewContainer.setVisibility(View.GONE);
        mSettingsPanel.setVisibility(View.VISIBLE);
        mSettingsPanel.bringToFront();
    }

    private void hideAllPanels() {
        mBookmarksPanel.setVisibility(View.GONE);
        mDownloadsPanel.setVisibility(View.GONE);
        mSettingsPanel.setVisibility(View.GONE);
        mWebViewContainer.setVisibility(View.VISIBLE);
    }

    private void renderBookmarksGrid() {
        mBookmarksGrid.removeAllViews();
        List<BookmarkManager.BookmarkItem> list = mBookmarkManager.getAllBookmarks();

        for (final BookmarkManager.BookmarkItem item : list) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setBackgroundResource(R.drawable.bg_card);
            card.setFocusable(true);
            card.setPadding(24, 24, 24, 24);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(12, 12, 12, 12);
            card.setLayoutParams(params);

            TextView iconView = new TextView(this);
            iconView.setText(item.icon != null ? item.icon : "⭐");
            iconView.setTextSize(32);
            iconView.setGravity(Gravity.CENTER);

            TextView titleView = new TextView(this);
            titleView.setText(item.title);
            titleView.setTextColor(Color.WHITE);
            titleView.setTextSize(16);
            titleView.setTypeface(null, Typeface.BOLD);
            titleView.setGravity(Gravity.CENTER);
            titleView.setSingleLine(true);
            titleView.setPadding(0, 8, 0, 4);

            TextView urlView = new TextView(this);
            urlView.setText(item.url);
            urlView.setTextColor(Color.parseColor("#9ca3af"));
            urlView.setTextSize(12);
            urlView.setGravity(Gravity.CENTER);
            urlView.setSingleLine(true);

            card.addView(iconView);
            card.addView(titleView);
            card.addView(urlView);

            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    loadUrl(item.url);
                }
            });

            card.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Izbris zaznamka")
                            .setMessage("Ali želite izbrisati zaznamek '" + item.title + "'?")
                            .setPositiveButton("Izbriši", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    mBookmarkManager.deleteBookmark(item.id);
                                    renderBookmarksGrid();
                                }
                            })
                            .setNegativeButton("Prekliči", null)
                            .show();
                    return true;
                }
            });

            mBookmarksGrid.addView(card);
        }
    }

    private void renderDownloadsList() {
        mDownloadsListContainer.removeAllViews();
        List<DownloadHandler.DownloadItem> list = DownloadHandler.getDownloadList(this);
        TextView noDownloads = findViewById(R.id.textNoDownloads);

        if (list.isEmpty()) {
            noDownloads.setVisibility(View.VISIBLE);
            return;
        }
        noDownloads.setVisibility(View.GONE);

        for (final DownloadHandler.DownloadItem item : list) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_card);
            row.setFocusable(true);
            row.setPadding(20, 16, 20, 16);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, 12);
            row.setLayoutParams(params);

            TextView title = new TextView(this);
            title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            title.setText("📄 " + item.title + "\nStatus: " + item.statusText);
            title.setTextColor(Color.WHITE);
            title.setTextSize(15);

            Button openBtn = new Button(this);
            openBtn.setBackgroundResource(R.drawable.bg_nav_button);
            openBtn.setText(item.title.endsWith(".apk") ? "🚀 Namesti APK" : "▶ Odpri");
            openBtn.setTextColor(Color.parseColor("#00d2ff"));
            openBtn.setFocusable(true);
            openBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    DownloadHandler.openFile(MainActivity.this, item);
                }
            });

            row.addView(title);
            row.addView(openBtn);
            mDownloadsListContainer.addView(row);
        }
    }

    private void showAddBookmarkDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Dodaj nov zaznamek");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText titleInput = new EditText(this);
        titleInput.setHint("Naslov zaznamka (npr. Moj Portal)");
        titleInput.setTextColor(Color.WHITE);
        WebView active = getActiveWebView();
        titleInput.setText(active != null ? active.getTitle() : "");

        final EditText urlInput = new EditText(this);
        urlInput.setHint("URL povezava (https://...)");
        urlInput.setTextColor(Color.WHITE);
        urlInput.setText(active != null && active.getUrl() != null ? active.getUrl() : "https://");

        layout.addView(titleInput);
        layout.addView(urlInput);
        builder.setView(layout);

        builder.setPositiveButton("Shrani", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String t = titleInput.getText().toString().trim();
                String u = urlInput.getText().toString().trim();
                if (!u.isEmpty()) {
                    mBookmarkManager.addBookmark(t, u, "⭐");
                    renderBookmarksGrid();
                    Toast.makeText(MainActivity.this, "Zaznamek dodan!", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("Prekliči", null);
        builder.show();
    }

    private void hideSoftKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            View focus = getCurrentFocus();
            if (imm != null && focus != null) {
                imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();

            // 🟡 Rumeni gumb (185) ali MENU (82) -> Hitri preklop virtualnega kurzorja!
            if (keyCode == KeyEvent.KEYCODE_PROG_YELLOW || keyCode == 185 || keyCode == KeyEvent.KEYCODE_MENU) {
                toggleCursorMode();
                return true;
            }

            // 🔴 Rdeči gumb (183) -> Glasovno iskanje!
            if (keyCode == KeyEvent.KEYCODE_PROG_RED || keyCode == 183) {
                startVoiceSearch();
                return true;
            }

            // Če je kurzorski način vklopljen in smo na spletni strani
            if (mCursorMode && mBookmarksPanel.getVisibility() != View.VISIBLE &&
                    mDownloadsPanel.getVisibility() != View.VISIBLE &&
                    mSettingsPanel.getVisibility() != View.VISIBLE &&
                    mCustomView == null) {

                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    mCursorOverlay.moveCursor(0, -mCursorSpeed);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    mCursorOverlay.moveCursor(0, mCursorSpeed);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    mCursorOverlay.moveCursor(-mCursorSpeed, 0);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    mCursorOverlay.moveCursor(mCursorSpeed, 0);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                    WebView active = getActiveWebView();
                    if (active != null) {
                        mCursorOverlay.clickAtCursor(active);
                    }
                    return true;
                }
            }

            // Tipka Nazaj (Back)
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (mCustomView != null) {
                    WebView active = getActiveWebView();
                    if (active != null && active.getWebChromeClient() != null) {
                        active.getWebChromeClient().onHideCustomView();
                    }
                    return true;
                }
                if (mBookmarksPanel.getVisibility() == View.VISIBLE ||
                        mDownloadsPanel.getVisibility() == View.VISIBLE ||
                        mSettingsPanel.getVisibility() == View.VISIBLE) {
                    hideAllPanels();
                    return true;
                }
                WebView active = getActiveWebView();
                if (active != null && active.canGoBack()) {
                    active.goBack();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        unmuteAudioHardware();
        WebView active = getActiveWebView();
        if (active != null) active.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        WebView active = getActiveWebView();
        if (active != null) active.onPause();
    }

    @Override
    protected void onDestroy() {
        for (TabManager.TabItem item : mTabManager.getTabs()) {
            if (item.webView != null) {
                item.webView.destroy();
            }
        }
        super.onDestroy();
    }
}

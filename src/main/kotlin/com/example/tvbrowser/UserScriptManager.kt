package com.example.tvbrowser

import android.webkit.WebView

object UserScriptManager {

    // 🛡️ 1. Anti-Anti-AdBlock, GPC Privacy Control & Anti-Tracker (DOCUMENT_START)
    private const val ANTI_ANTI_ADBLOCK_JS = """
        (function() {
            if (window._tvAntiAntiDone) return;
            window._tvAntiAntiDone = true;
            try {
                // 1. Global Privacy Control (GPC) & Do Not Track Signal
                try {
                    navigator.globalPrivacyControl = true;
                    Object.defineProperty(navigator, 'globalPrivacyControl', { value: true, writable: false });
                    Object.defineProperty(navigator, 'doNotTrack', { value: "1", writable: false });
                } catch(e) {}

                // 2. Google Ad Status Spoofing
                window.google_ad_status = 1;
                window.canRunAds = true;
                window.google_ads_status = 1;
                window.adsBlocked = false;
                window.adblock = false;
                window.adBlockEnabled = false;
                window.isAdBlockActive = false;

                // 3. Override common Anti-AdBlock detection libraries
                function DummyDetector() {
                    this.onDetected = function(){ return this; };
                    this.onNotDetected = function(cb){ if (typeof cb === 'function') cb(); return this; };
                    this.on = function(detected, cb){ if (!detected && typeof cb === 'function') cb(); return this; };
                    this.check = function(){ return false; };
                    this.emitEvent = function(){};
                    this.clearEvent = function(){};
                }

                window.FuckAdBlock = DummyDetector;
                window.BlockAdBlock = DummyDetector;
                window.fuckAdBlock = new DummyDetector();
                window.blockAdBlock = new DummyDetector();

                // 4. MutationObserver to safely remove anti-adblock modal backdrops and annoyance walls
                const observer = new MutationObserver(() => {
                    window.google_ad_status = 1;
                    window.canRunAds = true;
                    window.adblock = false;

                    const badOverlays = document.querySelectorAll(
                        '.fc-ab-root, .adblock-overlay, [class*="adblock-modal"], [class*="open-in-app"], .smartbanner, #app-banner, .app-download-banner, .download-app-banner'
                    );
                    badOverlays.forEach(el => {
                        try { el.remove(); } catch(e) {}
                    });
                });

                if (document.documentElement) {
                    observer.observe(document.documentElement, { childList: true, subtree: true });
                } else {
                    document.addEventListener('DOMContentLoaded', () => {
                        if (document.documentElement) {
                            observer.observe(document.documentElement, { childList: true, subtree: true });
                        }
                    });
                }
            } catch(err) {}
        })();
    """

    // 🎨 2. Cosmetic Filtering & CMP Annoyances CSS (Cookie walls, App banners, Shorts, Endscreens)
    private const val COSMETIC_FILTER_CSS = """
        (function() {
            var cssId = 'tv_browser_cosmetic_filter';
            if (!document.getElementById(cssId)) {
                var style = document.createElement('style');
                style.id = cssId;
                style.innerHTML = `
                    #ad, #ads, .ad, .ads, .ad-banner, .advertisement, .ad-container,
                    .adsbygoogle, [id^="google_ads_"], [id^="div-gpt-ad"], [class*="sponsored-post"],
                    .ytp-ad-module, .ytp-ad-overlay-container, .video-ads,
                    iframe[src*="doubleclick"], iframe[src*="googleads"], iframe[src*="adservice"],
                    #app-banner, .smartbanner, [class*="open-in-app"], [class*="app-promo"],
                    .banner-open-app, a[href*="market://"], a[href*="play.google.com/store"],
                    .ytp-ce-element, .ytp-ce-covering-overlay,
                    #onetrust-banner-sdk, .fc-consent-root, .didomi-popup-container,
                    ytd-rich-section-renderer[is-shorts], ytd-reel-shelf-renderer {
                        display: none !important;
                        visibility: hidden !important;
                        height: 0 !important;
                        max-height: 0 !important;
                        pointer-events: none !important;
                        opacity: 0 !important;
                    }
                `;
                if (document.head) {
                    document.head.appendChild(style);
                } else if (document.documentElement) {
                    document.documentElement.appendChild(style);
                }
            }
        })();
    """

    // ⚡ 3. YouTube Freedom: SponsorBlock & Return YouTube Dislike (RYD)
    private const val YOUTUBE_FREEDOM_JS = """
        (function initYouTubeFreedom() {
            if (window.location.href.indexOf('youtube.com') === -1) return;
            if (window._tvYtFreedomDone) return;
            window._tvYtFreedomDone = true;

            function getVideoId() {
                var match = window.location.href.match(/[?&]v=([a-zA-Z0-9_-]{11})/);
                return match ? match[1] : null;
            }

            var currentVideoId = null;
            var segments = [];
            var boundVideoElement = null;

            function fetchSponsorSegments(vId) {
                if (!vId) return;
                currentVideoId = vId;
                segments = [];
                var url = 'https://sponsor.ajay.app/api/skipSegments?videoID=' + vId + '&categories=["sponsor","selfpromo","interaction","intro","outro","preview"]';
                fetch(url).then(function(res) {
                    if (res.ok) return res.json();
                    return [];
                }).then(function(data) {
                    segments = data || [];
                }).catch(function(){});
            }

            function fetchDislikes(vId) {
                if (!vId) return;
                var url = 'https://returnyoutubedislikeapi.com/votes?videoId=' + vId;
                fetch(url).then(function(res) {
                    if (res.ok) return res.json();
                    return null;
                }).then(function(data) {
                    if (data && data.dislikes !== undefined) {
                        renderDislikeBadge(data.dislikes, data.likes);
                    }
                }).catch(function(){});
            }

            function renderDislikeBadge(dislikes, likes) {
                var badge = document.getElementById('freenet_dislike_badge');
                if (!badge) {
                    badge = document.createElement('div');
                    badge.id = 'freenet_dislike_badge';
                    badge.style.cssText = 'position:fixed;top:80px;right:40px;background:rgba(15,23,42,0.9);border:1px solid rgba(56,189,248,0.4);color:#fff;padding:8px 16px;border-radius:10px;font-size:13px;font-weight:bold;z-index:999999;box-shadow:0 8px 24px rgba(0,0,0,0.6);pointer-events:none;';
                    document.body.appendChild(badge);
                }
                var dislikeText = Number(dislikes).toLocaleString();
                var likeText = likes ? Number(likes).toLocaleString() : '';
                badge.innerHTML = '👍 ' + likeText + ' &nbsp;|&nbsp; 👎 ' + dislikeText;
            }

            function onVideoTimeUpdate() {
                if (!boundVideoElement) return;
                var cur = boundVideoElement.currentTime;
                for (var i = 0; i < segments.length; i++) {
                    var seg = segments[i].segment;
                    if (cur >= seg[0] && cur < seg[1]) {
                        boundVideoElement.currentTime = seg[1];
                        showSkipToast('⚡ FreeNet: Preskočen segment (' + (segments[i].category || 'sponzor') + ')');
                        break;
                    }
                }
            }

            function attachPlayer() {
                var v = document.querySelector('video');
                if (v && v !== boundVideoElement) {
                    if (boundVideoElement) {
                        boundVideoElement.removeEventListener('timeupdate', onVideoTimeUpdate);
                    }
                    boundVideoElement = v;
                    boundVideoElement.addEventListener('timeupdate', onVideoTimeUpdate);
                }
            }

            function showSkipToast(msg) {
                var t = document.getElementById('freenet_skip_toast');
                if (!t) {
                    t = document.createElement('div');
                    t.id = 'freenet_skip_toast';
                    t.style.cssText = 'position:fixed;bottom:80px;right:40px;background:rgba(2,132,199,0.92);color:#fff;padding:10px 20px;border-radius:12px;font-size:14px;font-weight:bold;z-index:999999;box-shadow:0 8px 30px rgba(0,0,0,0.8);pointer-events:none;transition:opacity 0.3s;';
                    document.body.appendChild(t);
                }
                t.textContent = msg;
                t.style.opacity = '1';
                setTimeout(function(){ if (t) t.style.opacity = '0'; }, 3000);
            }

            setInterval(function() {
                var vId = getVideoId();
                if (vId && vId !== currentVideoId) {
                    fetchSponsorSegments(vId);
                    fetchDislikes(vId);
                }
                attachPlayer();
            }, 1200);
        })();
    """

    // 🌟 4. Focus Outlines, Direct Playback & Reject Tracking CMP
    private const val OPTIMIZATIONS_JS = """
        (function autoUnmuteAndFocus() {
            if (window._tvOptDone) return;
            window._tvOptDone = true;

            // High-contrast TV Focus Outline
            var style = document.getElementById('tv_browser_focus_style');
            if (!style) {
                style = document.createElement('style');
                style.id = 'tv_browser_focus_style';
                style.innerHTML = ':focus, a:focus, button:focus, input:focus, [tabindex]:focus, button.search-button:focus, [aria-label*="Iskanje"]:focus, [aria-label*="Search"]:focus, c3-icon:focus { outline: 3px solid #38bdf8 !important; outline-offset: 3px !important; box-shadow: 0 0 15px rgba(56, 189, 248, 0.6) !important; }';
                if (document.head) document.head.appendChild(style);
                else if (document.documentElement) document.documentElement.appendChild(style);
            }

            // Direct Media Audio Unmute (Only on initial load/play, allows user to mute later)
            function ensureUnmute() {
                document.querySelectorAll('video, audio').forEach(function(v) {
                    if (v.muted) v.muted = false;
                    if (v.volume < 1.0) v.volume = 1.0;
                });
            }
            ensureUnmute();
            document.addEventListener('play', ensureUnmute, { once: true, capture: true });
            document.addEventListener('playing', ensureUnmute, { once: true, capture: true });

            // YouTube Auto-Play trigger via JS directly
            if (window.location.href.indexOf('youtube.com/watch') !== -1) {
                setTimeout(function() {
                    var v = document.querySelector('video');
                    if (v && v.paused) {
                        v.muted = false;
                        v.volume = 1.0;
                        v.play().catch(function(){});
                    }
                }, 600);
            }

            // Privacy-First CMP: Prefer "Reject All" / "Zavrni vse" over blind acceptance!
            function handleConsentPrivacy() {
                // 1. Check for explicit Reject / Decline buttons first
                var rejectBtns = document.querySelectorAll('button, a, [role="button"]');
                for (var i = 0; i < rejectBtns.length; i++) {
                    var el = rejectBtns[i];
                    var txt = (el.innerText || el.textContent || el.getAttribute('aria-label') || '').trim().toLowerCase();
                    if (txt === 'zavrni vse' || txt === 'zavrni' || txt === 'reject all' || txt === 'reject' || txt === 'decline' || txt === 'samo nujni' || txt === 'necessary only') {
                        el.click();
                        return true;
                    }
                }

                // 2. Fallback to dismiss Google/YouTube popup if no reject button
                var googleBtn = document.querySelector('button#L2AGLb, ytd-consent-bump-v2-lightbox button, .consent-bump-v2 button');
                if (googleBtn) {
                    googleBtn.click();
                    return true;
                }
                return false;
            }

            if (!handleConsentPrivacy()) {
                setTimeout(handleConsentPrivacy, 400);
            }

            // Auto-blur search inputs on results page to hide virtual keyboard
            if (window.location.href.indexOf('/search') !== -1 || window.location.href.indexOf('results?search_query') !== -1) {
                setTimeout(function() {
                    var qInput = document.querySelector('input[name="q"], textarea[name="q"], input[name="search_query"]');
                    if (qInput && document.activeElement === qInput) {
                        qInput.blur();
                    }
                }, 300);
            }
        })();
    """

    fun injectAtDocumentStart(webView: WebView) {
        webView.evaluateJavascript(ANTI_ANTI_ADBLOCK_JS.trimIndent(), null)
    }

    fun injectCosmeticFiltering(webView: WebView) {
        webView.evaluateJavascript(COSMETIC_FILTER_CSS.trimIndent(), null)
    }

    fun injectYouTubeFreedom(webView: WebView) {
        webView.evaluateJavascript(YOUTUBE_FREEDOM_JS.trimIndent(), null)
    }

    fun injectOptimizations(webView: WebView) {
        webView.evaluateJavascript(OPTIMIZATIONS_JS.trimIndent(), null)
    }

    fun injectAll(webView: WebView, antiAntiAdblock: Boolean = true, cosmeticFilter: Boolean = true) {
        if (antiAntiAdblock) injectAtDocumentStart(webView)
        if (cosmeticFilter) injectCosmeticFiltering(webView)
        injectYouTubeFreedom(webView)
        injectOptimizations(webView)
    }
}

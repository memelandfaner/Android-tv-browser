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

                // 4. 🚫 100% Popunder & Unrequested Window Blocker (kon-na-andrid-tv-stream-movie logic)
                try {
                    window.open = function(url, target, features) {
                        console.log("FreeNet: Blocked popup window.open:", url);
                        return null;
                    };
                    window.showModalDialog = function() { return null; };
                } catch(e) {}

                // 5. MutationObserver to safely remove anti-adblock modal backdrops and annoyance walls
                var observer = new MutationObserver(function() {
                    window.google_ad_status = 1;
                    window.canRunAds = true;
                    window.adblock = false;

                    var badOverlays = document.querySelectorAll(
                        '.fc-ab-root, .adblock-overlay, [class*="adblock-modal"], [class*="open-in-app"], .smartbanner, #app-banner, .app-download-banner, .download-app-banner'
                    );
                    badOverlays.forEach(function(el) {
                        try { el.remove(); } catch(e) {}
                    });
                });

                if (document.documentElement) {
                    observer.observe(document.documentElement, { childList: true, subtree: true });
                } else {
                    document.addEventListener('DOMContentLoaded', function() {
                        if (document.documentElement) {
                            observer.observe(document.documentElement, { childList: true, subtree: true });
                        }
                    });
                }
            } catch(err) {}
        })();
    """

    // 🎮 1b. Android TV Keyboard & D-Pad Event Normalizer
    private const val KEY_EVENTS_PATCH_JS = """
        (function() {
            if (window.__androidKeyboardCodePatchInstalled) return;
            window.__androidKeyboardCodePatchInstalled = true;

            function codeFromKeyInfo(e) {
                if (e.code) return e.code;
                switch (e.key) {
                    case " ": case "Spacebar": return "Space";
                    case "ArrowUp": return "ArrowUp";
                    case "ArrowDown": return "ArrowDown";
                    case "ArrowLeft": return "ArrowLeft";
                    case "ArrowRight": return "ArrowRight";
                    case "Enter": return "Enter";
                    case "Escape": return "Escape";
                    case "Tab": return "Tab";
                    case "Backspace": return "Backspace";
                    case "f": case "F": return "KeyF";
                }
                switch (e.keyCode || e.which) {
                    case 32: return "Space";
                    case 13: return "Enter";
                    case 27: return "Escape";
                    case 9:  return "Tab";
                    case 8:  return "Backspace";
                    case 37: return "ArrowLeft";
                    case 38: return "ArrowUp";
                    case 39: return "ArrowRight";
                    case 40: return "ArrowDown";
                    case 70: return "KeyF";
                    default: return "";
                }
            }

            function keyFromCode(code, currentKey) {
                if (currentKey && currentKey !== "Unidentified") return currentKey;
                switch (code) {
                    case "Space": return " ";
                    case "Enter": return "Enter";
                    case "Escape": return "Escape";
                    case "Tab": return "Tab";
                    case "Backspace": return "Backspace";
                    case "ArrowLeft": return "ArrowLeft";
                    case "ArrowUp": return "ArrowUp";
                    case "ArrowRight": return "ArrowRight";
                    case "ArrowDown": return "ArrowDown";
                    case "KeyF": return "f";
                    default: return currentKey || "";
                }
            }

            function patchKeyboardEvent(e) {
                if (!(e instanceof KeyboardEvent)) return;
                var code = codeFromKeyInfo(e);
                if (code && !e.code) {
                    try { Object.defineProperty(e, "code", { configurable: true, get: function() { return code; } }); } catch (_) {}
                }
                var key = keyFromCode(code, e.key);
                if (key && (!e.key || e.key === "Unidentified")) {
                    try { Object.defineProperty(e, "key", { configurable: true, get: function() { return key; } }); } catch (_) {}
                }
            }

            window.addEventListener("keydown", patchKeyboardEvent, true);
            window.addEventListener("keyup", patchKeyboardEvent, true);
            window.addEventListener("keypress", patchKeyboardEvent, true);
        })();
    """

    // 🎮 1c. Universal D-Pad Spatial Navigation & SVG Controller for TV
    private const val TV_DPAD_SPATIAL_NAVIGATION_JS = """
        (function() {
            // Naredi vse div-e, a-je, button-e, input-e, svg-je in iframe-e focusable
            function setupFocusableElements() {
                try {
                    document.querySelectorAll('div, a, button, input, iframe, svg, [role="button"], [onclick], .jw-icon, .plyr__control, [class*="control"], [class*="fullscreen"]').forEach(function(el) {
                        if (!el.hasAttribute('tabindex')) el.tabIndex = 0;
                        if (el.tagName === 'SVG' || el.tagName === 'svg') el.setAttribute('focusable', 'true');
                    });
                } catch(e) {}
            }

            // Injected styling za fokus (rumena obroba / yellow outline)
            var styleId = 'freenet-dpad-focus-style';
            if (!document.getElementById(styleId)) {
                var style = document.createElement('style');
                style.id = styleId;
                style.textContent = `
                    :focus, [tabindex="0"]:focus, [data-tv-focused="true"] {
                        outline: 3.5px solid #ffd700 !important;
                        outline-offset: 2px !important;
                        box-shadow: 0 0 18px rgba(255, 215, 0, 0.95) !important;
                        border-radius: 6px !important;
                        z-index: 999999 !important;
                    }
                `;
                (document.head || document.documentElement).appendChild(style);
            }

            // Funkcija za premik fokusa
            window.focusNextElement = function(direction) {
                setupFocusableElements();
                let current = document.activeElement;
                let focusable = Array.from(document.querySelectorAll('[tabindex]:not([tabindex="-1"])')).filter(function(el) {
                    if (el.offsetParent === null && el.offsetWidth === 0 && el.offsetHeight === 0) return false;
                    return true;
                });
                if (focusable.length === 0) return;
                let index = focusable.indexOf(current);
                if (direction === 'right' || direction === 'down') index++;
                if (direction === 'left' || direction === 'up') index--;
                if (index < 0) index = focusable.length - 1;
                if (index >= focusable.length) index = 0;
                let nextEl = focusable[index];
                if (nextEl) {
                    nextEl.focus();
                    if (nextEl.scrollIntoViewIfNeeded) nextEl.scrollIntoViewIfNeeded();
                    else nextEl.scrollIntoView({ block: 'nearest', inline: 'nearest' });
                    // Če je fokus na iframe, pojdi noter
                    if (nextEl.tagName === 'IFRAME') {
                        try { nextEl.contentWindow.focus(); } catch(e){}
                    }
                }
            };

            window.clickActiveElement = function() {
                var act = document.activeElement;
                if (!act) return;
                var isFs = (act.className && typeof act.className === 'string' && act.className.indexOf('fullscreen') !== -1) ||
                           (act.getAttribute('aria-label') && act.getAttribute('aria-label').toLowerCase().indexOf('fullscreen') !== -1) ||
                           (act.getAttribute('title') && act.getAttribute('title').toLowerCase().indexOf('fullscreen') !== -1) ||
                           (act.id && act.id.indexOf('fullscreen') !== -1);
                if (isFs && window.AndroidNativeBridge && typeof window.AndroidNativeBridge.toggleTvFullscreen === 'function') {
                    window.AndroidNativeBridge.toggleTvFullscreen();
                }
                try { act.click(); } catch(e) {}
                try {
                    act.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
                } catch(e) {}
            };

            // ESC gre ven iz iframe
            window.addEventListener('keydown', function(e) {
                if (e.key === 'Escape' || e.keyCode === 27) { window.focus(); }
            });

            document.addEventListener('keydown', function(e) {
                e.stopPropagation();
            }, true);

            setupFocusableElements();
            // Set prvi fokus
            document.querySelector('[tabindex]')?.focus();
        })();
    """

    // 🛡️ 1d. Google Search & Interstitial Warning Auto-Bypass
    private const val GOOGLE_INTERSTITIAL_BYPASS_JS = """
        (function() {
            try {
                var h = (window.location.hostname || '').toLowerCase();
                var p = (window.location.pathname || '').toLowerCase();
                var href = (window.location.href || '').toLowerCase();
                var isGoogle = h.indexOf('google.') !== -1;
                var isWarning = p.indexOf('interstitial') !== -1 || href.indexOf('url?') !== -1 || document.title.indexOf('Opozorilo') !== -1 || document.title.indexOf('Warning') !== -1 || (document.body && document.body.innerText.indexOf('škodi vašemu') !== -1);
                if (isGoogle && isWarning) {
                    var links = document.querySelectorAll('a');
                    for (var i = 0; i < links.length; i++) {
                        var target = links[i].href;
                        if (target && target.indexOf('google.') === -1 && (target.startsWith('http://') || target.startsWith('https://'))) {
                            console.log("FreeNet: Auto-bypassing Google warning to:", target);
                            window.location.replace(target);
                            return;
                        }
                    }
                }
            } catch(e) {}
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
                    .ytp-ad-module, .ytp-ad-overlay-container, .video-ads, #player-ads,
                    iframe[src*="doubleclick"], iframe[src*="googleads"], iframe[src*="adservice"],
                    #app-banner, .smartbanner, [class*="open-in-app"], [class*="app-promo"],
                    .banner-open-app, a[href*="market://"], a[href*="play.google.com/store"],
                    .ytp-ce-element, .ytp-ce-covering-overlay,
                    #onetrust-banner-sdk, .fc-consent-root, .didomi-popup-container,
                    ytd-rich-section-renderer[is-shorts], ytd-reel-shelf-renderer,
                    ytd-promoted-sparkles-web-renderer, ytd-promoted-video-renderer,
                    ytd-ad-slot-renderer, ytd-in-feed-ad-layout-renderer,
                    ytd-banner-promo-renderer, ytd-statement-banner-renderer,
                    ytd-brand-video-singleton-renderer, ytd-merch-shelf-renderer,
                    ytd-rich-item-renderer:has(ytd-ad-slot-renderer),
                    ytd-rich-item-renderer:has(ytd-in-feed-ad-layout-renderer),
                    ytd-rich-section-renderer:has(ytd-ad-slot-renderer),
                    ytd-engagement-panel-section-list-renderer[target-id="engagement-panel-ads"],
                    ytm-promoted-sparkles-web-renderer, ytm-promoted-video-renderer,
                    ytm-ad-slot-renderer, ytm-companion-ad-renderer,
                    ytm-in-feed-ad-layout-renderer, ytm-statement-banner-renderer,
                    #masthead-ad, .ytd-search-pyv-renderer {
                        display: none !important;
                        visibility: hidden !important;
                        height: 0 !important;
                        max-height: 0 !important;
                        pointer-events: none !important;
                        opacity: 0 !important;
                    }

                    /* 🎯 TV Spatial Focus Ring & Neon Halo for Philips TV Remote Navigation */
                    :focus, :focus-visible, a:focus, button:focus, input:focus, select:focus, textarea:focus, [tabindex]:focus, [role="button"]:focus {
                        outline: 3px solid #00e5ff !important;
                        outline-offset: 2px !important;
                        box-shadow: 0 0 18px rgba(0, 229, 255, 0.95), 0 0 35px rgba(0, 229, 255, 0.5) !important;
                        border-radius: 6px !important;
                        transition: outline 0.12s ease-out, box-shadow 0.12s ease-out, transform 0.12s ease-out !important;
                    }

                    /* 🎬 TV Video Player Control Bar & Focus Accessibility Enhancement */
                    .jw-controlbar, .plyr__controls, .vjs-control-bar, .ytp-chrome-bottom, [class*="controls-bar"] {
                        transition: opacity 0.25s ease, visibility 0.25s ease !important;
                    }
                    .tv-controls-active .jw-controlbar,
                    .tv-controls-active .plyr__controls,
                    .tv-controls-active .vjs-control-bar,
                    .tv-controls-active .ytp-chrome-bottom,
                    .jwplayer:focus-within .jw-controlbar,
                    .jw-controlbar:focus-within,
                    .plyr:focus-within .plyr__controls,
                    .vjs-control-bar:focus-within {
                        opacity: 1 !important;
                        visibility: visible !important;
                        pointer-events: auto !important;
                        display: flex !important;
                        z-index: 100000 !important;
                    }
                    .jw-icon, .jw-button-color, .plyr__controls button, .vjs-control, .ytp-button,
                    .jw-slider-horizontal, .plyr__progress, .vjs-progress-control {
                        min-width: 44px !important;
                        min-height: 44px !important;
                        margin: 0 4px !important;
                        cursor: pointer !important;
                    }
                    .jw-icon:focus, .jw-button-color:focus, .plyr__controls button:focus, .vjs-control:focus, .ytp-button:focus,
                    .jw-slider-horizontal:focus, [class*="server"]:focus, .server-btn:focus, .tv-hud-btn:focus {
                        outline: 3px solid #00e5ff !important;
                        outline-offset: 3px !important;
                        box-shadow: 0 0 20px rgba(0, 229, 255, 0.95), 0 0 40px rgba(0, 229, 255, 0.5) !important;
                        background: rgba(0, 229, 255, 0.28) !important;
                        transform: scale(1.18) !important;
                        border-radius: 8px !important;
                        z-index: 100001 !important;
                    }

                    /* 🎛️ TV Quick Player Floating HUD */
                    .tv-player-quick-hud {
                        position: fixed !important;
                        bottom: 24px !important;
                        left: 50% !important;
                        transform: translateX(-50%) !important;
                        display: flex !important;
                        align-items: center !important;
                        gap: 8px !important;
                        background: rgba(15, 23, 42, 0.92) !important;
                        backdrop-filter: blur(16px) !important;
                        -webkit-backdrop-filter: blur(16px) !important;
                        border: 1.5px solid rgba(56, 189, 248, 0.45) !important;
                        box-shadow: 0 12px 36px rgba(0, 0, 0, 0.75), 0 0 24px rgba(56, 189, 248, 0.25) !important;
                        border-radius: 50px !important;
                        padding: 8px 16px !important;
                        z-index: 1000000 !important;
                        transition: opacity 0.3s cubic-bezier(0.4, 0, 0.2, 1), transform 0.3s cubic-bezier(0.4, 0, 0.2, 1), visibility 0.3s !important;
                        opacity: 1;
                        pointer-events: auto;
                    }
                    .tv-player-quick-hud.tv-hud-hidden {
                        opacity: 0 !important;
                        visibility: hidden !important;
                        pointer-events: none !important;
                        transform: translateX(-50%) translateY(16px) !important;
                    }
                    .tv-hud-btn {
                        display: inline-flex !important;
                        align-items: center !important;
                        justify-content: center !important;
                        background: rgba(30, 41, 59, 0.85) !important;
                        color: #f8fafc !important;
                        border: 1px solid rgba(255, 255, 255, 0.15) !important;
                        border-radius: 30px !important;
                        padding: 8px 16px !important;
                        font-size: 14px !important;
                        font-weight: 600 !important;
                        cursor: pointer !important;
                        white-space: nowrap !important;
                        transition: all 0.15s ease !important;
                    }
                    .tv-hud-btn-primary {
                        background: linear-gradient(135deg, #0ea5e9, #0284c7) !important;
                        border-color: #38bdf8 !important;
                        color: #ffffff !important;
                    }
                    .tv-hud-btn:focus, .tv-hud-btn:hover {
                        outline: 3px solid #00e5ff !important;
                        outline-offset: 2px !important;
                        background: #0284c7 !important;
                        color: #ffffff !important;
                        box-shadow: 0 0 18px rgba(0, 229, 255, 0.9) !important;
                        transform: scale(1.12) !important;
                    }

                    html, body {
                        scroll-behavior: smooth !important;
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
                var url = 'https://sponsor.ajay.app/api/skipSegments?videoID=' + vId + '&categories=["sponsor","selfpromo","interaction","intro","outro","preview","music_offtopic","filler"]';
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

            // 🛡️ SmartTube-Style YouTube Video Ad Stopper & Sponsored Post Stripper
            function blockYouTubeAds() {
                var video = document.querySelector('video');
                var adElement = document.querySelector('.ad-showing, .ad-interrupting, .video-ads, .ytp-ad-player-overlay');
                if (adElement && video) {
                    video.muted = true;
                    video.playbackRate = 16.0;
                    if (isFinite(video.duration) && video.duration > 0) {
                        video.currentTime = video.duration;
                    }
                }

                // Auto-click all YouTube Skip Ad buttons
                var skipButtons = document.querySelectorAll(
                    '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytp-ad-overlay-close-button, button.ytp-ad-skip-button-text, .ytp-ad-skip-button-slot button'
                );
                skipButtons.forEach(function(btn) {
                    try { btn.click(); } catch(e) {}
                });

                // Remove YouTube DOM ad banners, promoted shelves, sponsored cards (SmartTube logic)
                var adDomSelectors = [
                    '#player-ads', '.ytp-ad-module', '.ytp-ad-overlay-container',
                    'ytd-promoted-sparkles-web-renderer', 'ytd-display-ad-renderer',
                    'ytd-ad-slot-renderer', 'ytd-banner-promo-renderer', 'ytd-in-feed-ad-layout-renderer',
                    'ytd-promoted-video-renderer', 'ytd-action-companion-ad-renderer',
                    '#masthead-ad', 'ytd-rich-item-renderer:has(ytd-ad-slot-renderer)',
                    'ytd-rich-item-renderer:has(ytd-in-feed-ad-layout-renderer)',
                    'ytd-rich-section-renderer:has(ytd-ad-slot-renderer)',
                    'ytd-statement-banner-renderer', 'ytd-brand-video-singleton-renderer',
                    'ytd-merch-shelf-renderer', 'ytm-promoted-sparkles-web-renderer',
                    'ytm-promoted-video-renderer', 'ytm-ad-slot-renderer', 'ytm-companion-ad-renderer',
                    'ytm-in-feed-ad-layout-renderer', 'ytm-statement-banner-renderer',
                    'ytd-engagement-panel-section-list-renderer[target-id="engagement-panel-ads"]'
                ];
                var adElements = document.querySelectorAll(adDomSelectors.join(', '));
                adElements.forEach(function(el) {
                    try { el.remove(); } catch(e) {}
                });
            }

            setInterval(function() {
                var vId = getVideoId();
                if (vId && vId !== currentVideoId) {
                    fetchSponsorSegments(vId);
                    fetchDislikes(vId);
                }
                attachPlayer();
                blockYouTubeAds();
            }, 100);
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

            // Privacy-First CMP: Auto-dismiss Cookie & Consent Walls
            function handleConsentPrivacy() {
                // 1. Check for explicit Reject / Decline buttons first
                var btns = document.querySelectorAll('button, a, [role="button"]');
                for (var i = 0; i < btns.length; i++) {
                    var el = btns[i];
                    var txt = (el.innerText || el.textContent || el.getAttribute('aria-label') || '').trim().toLowerCase();
                    if (txt === 'zavrni vse' || txt === 'zavrni' || txt === 'reject all' || txt === 'reject' || txt === 'decline' || txt === 'samo nujni' || txt === 'necessary only') {
                        el.click();
                        return true;
                    }
                }

                // 2. Accept fallback for portals (RTV SLO, 24ur, Siol, etc.)
                for (var j = 0; j < btns.length; j++) {
                    var el2 = btns[j];
                    var txt2 = (el2.innerText || el2.textContent || el2.getAttribute('aria-label') || '').trim().toLowerCase();
                    if (txt2 === 'sprejmi vse' || txt2 === 'sprejmi' || txt2 === 'strinjam se' || txt2 === 'v redu' || txt2 === 'potrdi' || txt2 === 'accept all' || txt2 === 'agree') {
                        el2.click();
                        return true;
                    }
                }

                // 3. Fallback to dismiss Google/YouTube popup if no reject button
                var googleBtn = document.querySelector('button#L2AGLb, ytd-consent-bump-v2-lightbox button, .consent-bump-v2 button');
                if (googleBtn) {
                    googleBtn.click();
                    return true;
                }
                return false;
            }

            handleConsentPrivacy();
            setTimeout(handleConsentPrivacy, 400);
            setTimeout(handleConsentPrivacy, 1200);

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

    // 🎬 4. High-Performance Bidirectional Video Communication Bridge (Freenet Player Bridge)
    private const val VIDEO_AUTOPLAY_HELPER_JS = """
        (function initFreenetPlayerBridge() {
            if (window._freenetBridgeInitialized) return;
            window._freenetBridgeInitialized = true;

            // --- State & Optimistic Seeking Management ---
            var playerState = {
                isPlaying: false,
                currentTime: 0,
                duration: 0,
                isMuted: false,
                volume: 1.0,
                isFullscreen: false,
                isBuffering: false,
                isSeeking: false,
                title: document.title || ''
            };

            var lastBroadcastTime = 0;
            var rafPending = false;
            var seekDebounceTimer = null;

            // 1. Throttled State Broadcast (requestAnimationFrame + 250ms interval)
            function queueStateBroadcast(forceImmediate) {
                var now = performance.now();
                if (forceImmediate || (now - lastBroadcastTime > 250)) {
                    if (!rafPending) {
                        rafPending = true;
                        requestAnimationFrame(function() {
                            rafPending = false;
                            lastBroadcastTime = performance.now();
                            broadcastState();
                        });
                    }
                }
            }

            function broadcastState() {
                var activeVideo = getPrimaryVideo();
                if (activeVideo) {
                    if (!playerState.isSeeking) {
                        playerState.currentTime = activeVideo.currentTime || 0;
                    }
                    playerState.duration = activeVideo.duration || 0;
                    playerState.isPlaying = !activeVideo.paused && !activeVideo.ended;
                    playerState.isMuted = activeVideo.muted;
                    playerState.volume = activeVideo.volume;
                    playerState.isBuffering = activeVideo.readyState < 3 && !activeVideo.paused;
                }
                playerState.isFullscreen = !!(document.fullscreenElement || document.webkitFullscreenElement || document.body.classList.contains('tv-fullscreen-mode'));
                playerState.title = document.title || '';

                var payload = JSON.stringify(playerState);

                // Send to native Android bridge
                try {
                    if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.onPlayerStateChanged === 'function') {
                        window.AndroidNativeBridge.onPlayerStateChanged(payload);
                    }
                } catch(e) {}

                // Broadcast postMessage to parent / iframes
                try {
                    var msg = { type: 'FREENET_PLAYER_STATE', state: playerState };
                    if (window.parent && window.parent !== window) {
                        window.parent.postMessage(msg, '*');
                    }
                    var iframes = document.querySelectorAll('iframe');
                    for (var i = 0; i < iframes.length; i++) {
                        try {
                            if (iframes[i].contentWindow) {
                                iframes[i].contentWindow.postMessage(msg, '*');
                            }
                        } catch(e) {}
                    }
                } catch(e) {}
            }

            // Helper to find the most relevant playing or visible video
            function getPrimaryVideo() {
                var videos = document.querySelectorAll('video');
                if (!videos || videos.length === 0) return null;
                for (var i = 0; i < videos.length; i++) {
                    if (!videos[i].paused) return videos[i];
                }
                return videos[0];
            }

            // 2. Universal Command Dispatcher (Handles PLAY, PAUSE, TOGGLE_PLAY, SEEK, etc.)
            function handlePlayerCommand(action, value) {
                var v = getPrimaryVideo();
                switch (action) {
                    case 'PLAY':
                        if (v) {
                            v.muted = false;
                            v.play().catch(function() {
                                v.muted = true;
                                v.play().then(function() { setTimeout(function(){ v.muted = false; }, 300); }).catch(function(){});
                            });
                        }
                        triggerPlayButtons();
                        break;
                    case 'PAUSE':
                        if (v) v.pause();
                        break;
                    case 'TOGGLE_PLAY':
                        if (v) {
                            if (v.paused) handlePlayerCommand('PLAY');
                            else handlePlayerCommand('PAUSE');
                        } else {
                            triggerPlayButtons();
                        }
                        break;
                    case 'SEEK':
                        if (v && typeof value === 'number') {
                            playerState.isSeeking = true;
                            playerState.currentTime = value;
                            v.currentTime = value;
                            clearTimeout(seekDebounceTimer);
                            seekDebounceTimer = setTimeout(function() { playerState.isSeeking = false; }, 400);
                        }
                        break;
                    case 'SEEK_RELATIVE':
                        if (v) {
                            var delta = (typeof value === 'number') ? value : 10;
                            var target = Math.max(0, Math.min(v.duration || 999999, v.currentTime + delta));
                            handlePlayerCommand('SEEK', target);
                        }
                        break;
                    case 'MUTE':
                        if (v) v.muted = true;
                        break;
                    case 'UNMUTE':
                        if (v) { v.muted = false; v.volume = Math.max(v.volume, 0.5); }
                        break;
                    case 'TOGGLE_MUTE':
                        if (v) v.muted = !v.muted;
                        break;
                    case 'SET_VOLUME':
                        if (v && typeof value === 'number') {
                            v.volume = Math.max(0, Math.min(1, value));
                            v.muted = (v.volume === 0);
                        }
                        break;
                    case 'FULLSCREEN':
                        if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.requestTvFullscreen === 'function') {
                            window.AndroidNativeBridge.requestTvFullscreen();
                        }
                        break;
                    case 'EXIT_FULLSCREEN':
                        if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.exitTvFullscreen === 'function') {
                            window.AndroidNativeBridge.exitTvFullscreen();
                        }
                        break;
                    case 'TOGGLE_FULLSCREEN':
                        if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.toggleTvFullscreen === 'function') {
                            window.AndroidNativeBridge.toggleTvFullscreen();
                        }
                        break;
                    case 'SUBTITLES':
                        var ccBtn = document.querySelector('.jw-icon-cc, .jw-icon-subtitles, [aria-label*="Captions" i], [aria-label*="Subtitles" i], [data-plyr="captions"], .vjs-subs-caps-button, .ytp-subtitles-button, button[title*="Subtitles" i], button[title*="Captions" i]');
                        if (ccBtn) {
                            try { ccBtn.click(); ccBtn.focus(); } catch(e) {}
                        }
                        showTvHud();
                        break;
                    case 'SETTINGS':
                        var setBtn = document.querySelector('.jw-icon-settings, [aria-label*="Settings" i], [data-plyr="settings"], .vjs-settings-sub-menu, .ytp-settings-button, button[title*="Settings" i]');
                        if (setBtn) {
                            try { setBtn.click(); setBtn.focus(); } catch(e) {}
                        }
                        showTvHud();
                        break;
                    case 'SERVERS':
                        var srvBtn = document.querySelector('.server-btn, [class*="server"], [data-server], #server, .server-select, [class*="server-selector"]');
                        if (srvBtn) {
                            try { srvBtn.focus(); srvBtn.click(); } catch(e) {}
                        }
                        break;
                    case 'SHOW_CONTROLS':
                        showTvHud();
                        var pb = document.getElementById('hudBtnPlay') || document.querySelector('.jw-icon-playback, .plyr__control--play, button[aria-label*="Play" i]');
                        if (pb) { try { pb.focus(); } catch(e) {} }
                        break;
                }
                queueStateBroadcast(true);
            }

            // Expose global bridge for native Android / Web scripts
            window.FreenetPlayerBridge = {
                sendCommand: handlePlayerCommand,
                getState: function() { return playerState; },
                broadcastState: function() { queueStateBroadcast(true); }
            };

            // 3. postMessage Communication Protocol
            window.addEventListener('message', function(event) {
                if (!event.data) return;
                if (event.data.type === 'FREENET_PLAYER_CMD') {
                    handlePlayerCommand(event.data.action, event.data.value);
                    // Forward to nested iframes
                    var iframes = document.querySelectorAll('iframe');
                    for (var i = 0; i < iframes.length; i++) {
                        try {
                            if (iframes[i].contentWindow && iframes[i].contentWindow !== event.source) {
                                iframes[i].contentWindow.postMessage(event.data, '*');
                            }
                        } catch(e) {}
                    }
                } else if (event.data.type === 'FREENET_PLAYER_STATE') {
                    // Merge child state if playing
                    if (event.data.state && event.data.state.isPlaying) {
                        playerState = event.data.state;
                        queueStateBroadcast(false);
                    }
                }
            });

            // 4. MediaSession API Integration (Hardware & Bluetooth Media Key support)
            function setupMediaSession(video) {
                if ('mediaSession' in navigator) {
                    try {
                        navigator.mediaSession.metadata = new MediaMetadata({
                            title: document.title || 'Predvajanje videa',
                            artist: window.location.hostname || 'TV Browser'
                        });

                        navigator.mediaSession.setActionHandler('play', function() { handlePlayerCommand('PLAY'); });
                        navigator.mediaSession.setActionHandler('pause', function() { handlePlayerCommand('PAUSE'); });
                        navigator.mediaSession.setActionHandler('seekbackward', function(details) {
                            var seekOffset = (details && details.seekOffset) || 10;
                            handlePlayerCommand('SEEK_RELATIVE', -seekOffset);
                        });
                        navigator.mediaSession.setActionHandler('seekforward', function(details) {
                            var seekOffset = (details && details.seekOffset) || 10;
                            handlePlayerCommand('SEEK_RELATIVE', seekOffset);
                        });
                        navigator.mediaSession.setActionHandler('stop', function() { handlePlayerCommand('PAUSE'); });
                    } catch(e) {}
                }
            }

            // 5. Detection: Check if current page is an active watch / movie playback page
            function isWatchPage() {
                var p = (window.location.pathname || '').toLowerCase();
                var h = (window.location.href || '').toLowerCase();
                // Check if on homepage / catalog index
                var isHome = p === '/' || p === '' || p === '/index.html' || p === '/home' || p.indexOf('/#') !== -1;
                if (isHome && !window.location.search && !window.location.hash) return false;

                // Explicit watch indicators in URL
                var isWatchUrl = p.indexOf('/watch') !== -1 ||
                                 p.indexOf('/movie/') !== -1 ||
                                 p.indexOf('/series/') !== -1 ||
                                 p.indexOf('/tv/') !== -1 ||
                                 p.indexOf('/episode/') !== -1 ||
                                 p.indexOf('/stream/') !== -1 ||
                                 p.indexOf('/play/') !== -1 ||
                                 p.indexOf('/embed/') !== -1 ||
                                 p.indexOf('watchseries') !== -1 ||
                                 p.indexOf('/v/') !== -1 ||
                                 h.indexOf('youtube.com/watch') !== -1 ||
                                 h.indexOf('piped.video/watch') !== -1;

                if (isWatchUrl) return true;
                if (isHome) return false;

                // Only consider watch page if explicit player container exists and is NOT a trailer modal
                var playerEl = document.querySelector('#player, #iframe-player, .player-wrapper, iframe[src*="embed"], iframe[src*="vid"], iframe[src*="player"]');
                if (playerEl && !playerEl.closest('#trailer, .trailer, [class*="trailer"], [id*="trailer"]')) {
                    return true;
                }
                return false;
            }

            // Suppress and close annoying homepage trailer modals
            function suppressHomepageTrailers() {
                if (!isWatchPage()) {
                    var trailerModals = document.querySelectorAll('#trailerModal, .trailer-modal, #modalTrailer, .modal-trailer, [id*="trailer-modal"], [class*="trailer-modal"]');
                    trailerModals.forEach(function(m) {
                        if (m.style) m.style.display = 'none';
                        var closeBtn = m.querySelector('.close, .btn-close, [data-dismiss="modal"], .modal-close');
                        if (closeBtn) { try { closeBtn.click(); } catch(e) {} }
                    });
                }
            }

            // 6. Video Element Listeners & Setup
            function setupVideoElement(video) {
                if (!video) return;
                try {
                    // Check if this video is a trailer
                    var isTrailer = (video.src && video.src.toLowerCase().indexOf('trailer') !== -1) ||
                                    video.closest('#trailer, .trailer, [class*="trailer"], [id*="trailer"], .trailer-modal') !== null;
                    if (isTrailer || !isWatchPage()) {
                        return; // DO NOT auto-play or unmute trailers or homepage videos!
                    }

                    video.muted = false;
                    video.volume = 1.0;
                    if (!video.hasAttribute('tabindex')) video.setAttribute('tabindex', '0');

                    if (!video._freenetListenersAttached) {
                        video._freenetListenersAttached = true;

                        video.addEventListener('play', function() {
                            video.muted = false;
                            video.volume = 1.0;
                            setupMediaSession(video);
                            if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.requestTvFullscreen === 'function') {
                                window.AndroidNativeBridge.requestTvFullscreen();
                            }
                            queueStateBroadcast(true);
                        });

                        video.addEventListener('pause', function() { queueStateBroadcast(true); });
                        video.addEventListener('ended', function() { queueStateBroadcast(true); });
                        video.addEventListener('timeupdate', function() { queueStateBroadcast(false); });
                        video.addEventListener('seeking', function() { playerState.isSeeking = true; queueStateBroadcast(false); });
                        video.addEventListener('seeked', function() { playerState.isSeeking = false; queueStateBroadcast(true); });
                        video.addEventListener('volumechange', function() { queueStateBroadcast(true); });
                        video.addEventListener('waiting', function() { queueStateBroadcast(true); });
                        video.addEventListener('playing', function() { queueStateBroadcast(true); });
                    }

                    if (video.paused && !video._freenetAutoplayAttempted && isWatchPage()) {
                        video._freenetAutoplayAttempted = true;
                        var p = video.play();
                        if (p !== undefined) {
                            p.catch(function() {
                                video.muted = true;
                                video.play().then(function() {
                                    setTimeout(function() { video.muted = false; }, 400);
                                }).catch(function(){});
                            });
                        }
                    }
                } catch(e) {}
            }

            // 7. Iframe Setup & Allowfullscreen
            function setupIframes() {
                var iframes = document.querySelectorAll('iframe');
                iframes.forEach(function(ifr) {
                    try {
                        ifr.setAttribute('allowfullscreen', 'true');
                        ifr.setAttribute('webkitallowfullscreen', 'true');
                        ifr.setAttribute('mozallowfullscreen', 'true');
                        ifr.setAttribute('allow', 'fullscreen; autoplay; encrypted-media; picture-in-picture');
                        if (!ifr.hasAttribute('tabindex')) ifr.setAttribute('tabindex', '0');
                    } catch(e) {}
                });
            }

            // 8. Make Player Control Buttons Accessible & Bind Fullscreen Clicks
            function setupControlBarButtons() {
                if (!isWatchPage()) return;
                var controlSelectors = [
                    '.jw-controls button', '.jw-icon', '.jw-button-color',
                    '.plyr__controls button', '.plyr__controls input',
                    '.vjs-control-bar button', '.vjs-control',
                    '.ytp-chrome-bottom button', '.ytp-button',
                    '[class*="control"] button', '[class*="controls"] button',
                    '[class*="control"] svg', '[class*="controls"] svg',
                    '[aria-label*="Play" i]', '[aria-label*="Pause" i]',
                    '[aria-label*="Fullscreen" i]', '[aria-label*="Exit Fullscreen" i]',
                    '[aria-label*="Celozaslonski" i]', '[aria-label*="Settings" i]',
                    '[aria-label*="Mute" i]', '[aria-label*="Volume" i]',
                    '[aria-label*="Subtitles" i]', '[aria-label*="Captions" i]',
                    '.fullscreen-btn', '[data-plyr="fullscreen"]', '.jw-icon-fullscreen',
                    '.vjs-fullscreen-control', '.ytp-fullscreen-button', '[class*="fullscreen"]',
                    'svg[class*="fullscreen"]', 'button[title*="fullscreen" i]', 'button[title*="celozaslonski" i]',
                    'div[title*="fullscreen" i]', 'span[title*="fullscreen" i]'
                ];
                var buttons = document.querySelectorAll(controlSelectors.join(', '));
                buttons.forEach(function(b) {
                    if (!b.hasAttribute('tabindex')) b.setAttribute('tabindex', '0');
                    if (!b._tvCtrlBound) {
                        b._tvCtrlBound = true;
                        var isFsBtn = (b.className && typeof b.className === 'string' && b.className.indexOf('fullscreen') !== -1) ||
                                      (b.getAttribute('aria-label') && b.getAttribute('aria-label').toLowerCase().indexOf('fullscreen') !== -1) ||
                                      (b.getAttribute('data-plyr') === 'fullscreen') ||
                                      (b.getAttribute('title') && b.getAttribute('title').toLowerCase().indexOf('fullscreen') !== -1) ||
                                      (b.id && b.id.indexOf('fullscreen') !== -1);
                        if (isFsBtn) {
                            b.addEventListener('click', function(ev) {
                                ev.stopPropagation();
                                handlePlayerCommand('TOGGLE_FULLSCREEN');
                            });
                        }
                        b.addEventListener('focus', function() {
                            var bar = b.closest('.jw-controlbar, .plyr__controls, .vjs-control-bar, .ytp-chrome-bottom, [class*="controls"]');
                            if (bar) {
                                bar.style.opacity = '1';
                                bar.style.visibility = 'visible';
                                bar.style.pointerEvents = 'auto';
                            }
                        });
                    }
                });
            }

            // 9. Player Containers Focus & 1-Click Play / 2-Click (Double-Click) Fullscreen Toggle
            function setupPlayerFocusAndClicks() {
                if (!isWatchPage()) return;
                var playerSelectors = [
                    'video', 'iframe', '#player', '#iframe-player', '.player-wrapper', '.video-player',
                    '.jwplayer', '.plyr', '.video-js', '[class*="player-container"]',
                    '[id*="player"]', '.html5-video-player', 'iframe[src*="vid"]', 'iframe[src*="embed"]', 'iframe[src*="stream"]'
                ];
                var containers = document.querySelectorAll(playerSelectors.join(', '));
                containers.forEach(function(el) {
                    if (!el.hasAttribute('tabindex')) el.setAttribute('tabindex', '0');
                    if (!el._tvClickBound) {
                        el._tvClickBound = true;

                        var lastClickTime = 0;
                        var singleClickTimer = null;

                        function triggerFullscreenOrNativePlayer() {
                            var v = getPrimaryVideo();
                            var vSrc = v ? (v.currentSrc || v.src || '') : '';
                            var pageTitle = document.title || 'Video Predvajalnik';
                            if (vSrc && (vSrc.indexOf('.m3u8') !== -1 || vSrc.indexOf('.mp4') !== -1 || vSrc.indexOf('.webm') !== -1)) {
                                if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.launchNativeVideo === 'function') {
                                    window.AndroidNativeBridge.launchNativeVideo(vSrc, pageTitle);
                                    return;
                                }
                            }
                            handlePlayerCommand('TOGGLE_FULLSCREEN');
                        }

                        // Double click event
                        el.addEventListener('dblclick', function(e) {
                            e.preventDefault();
                            e.stopPropagation();
                            clearTimeout(singleClickTimer);
                            triggerFullscreenOrNativePlayer();
                        });

                        // Double tap & single click detector
                        el.addEventListener('click', function(e) {
                            // If clicked directly on control buttons or sliders, let them handle themselves
                            if (e.target && e.target.closest('button, input, a, .jw-controlbar, .plyr__controls, .vjs-control-bar, .ytp-chrome-bottom')) {
                                return;
                            }

                            var now = Date.now();
                            if (now - lastClickTime < 340) {
                                // Double click detected!
                                clearTimeout(singleClickTimer);
                                lastClickTime = 0;
                                e.preventDefault();
                                e.stopPropagation();
                                triggerFullscreenOrNativePlayer();
                            } else {
                                lastClickTime = now;
                                singleClickTimer = setTimeout(function() {
                                    handlePlayerCommand('TOGGLE_PLAY');
                                }, 300);
                            }
                        });

                        el.addEventListener('keydown', function(evt) {
                            if (evt.keyCode === 13 || evt.keyCode === 23 || evt.key === 'Enter' || evt.key === ' ') {
                                evt.preventDefault();
                                handlePlayerCommand('TOGGLE_PLAY');
                            }
                        });
                    }
                });
            }

            function checkAndAutoClickResume() {
                if (!isWatchPage()) return false;
                try {
                    var btns = document.querySelectorAll('button, [role="button"], a, div');
                    for (var i = 0; i < btns.length; i++) {
                        var b = btns[i];
                        var t = (b.innerText || b.textContent || '').trim().toLowerCase();
                        if (t === 'resume' || t === 'continue' || t === 'continue watching' || t === 'nadaljuj' || t.startsWith('resume from')) {
                            b.click();
                            console.log("FreeNet: Auto-clicked Resume prompt successfully");
                            return true;
                        }
                    }
                } catch(e) {}
                return false;
            }

            function triggerPlayButtons() {
                if (!isWatchPage()) return; // Never auto-trigger buttons on homepage
                checkAndAutoClickResume();
                var playButtons = document.querySelectorAll(
                    '.video-play-button, .vjs-big-play-button, .plyr__control--overlaid, .play-state-indicator, [data-embed-url], .jw-display-icon-container, [class*="play-button"]'
                );
                playButtons.forEach(function(btn) {
                    var btnText = (btn.innerText || btn.textContent || '').toLowerCase();
                    var btnCls = (btn.className || '').toLowerCase();
                    var btnId = (btn.id || '').toLowerCase();
                    var btnAria = (btn.getAttribute('aria-label') || '').toLowerCase();

                    // Skip any trailer or preview buttons
                    if (btnText.indexOf('trailer') !== -1 ||
                        btnCls.indexOf('trailer') !== -1 ||
                        btnId.indexOf('trailer') !== -1 ||
                        btnAria.indexOf('trailer') !== -1 ||
                        btnText.indexOf('napovednik') !== -1) {
                        return;
                    }
                    try { btn.click(); } catch(e) {}
                });
            }

            function setupServerSwitchListeners() {
                if (!isWatchPage()) return;
                var serverBtns = document.querySelectorAll('[class*="server"], [data-server], [id*="server"], .server-item, .server-btn');
                serverBtns.forEach(function(sBtn) {
                    if (!sBtn.hasAttribute('tabindex')) sBtn.setAttribute('tabindex', '0');
                    if (!sBtn._tvServerBound) {
                        sBtn._tvServerBound = true;
                        sBtn.addEventListener('click', function() {
                            setTimeout(function() {
                                setupIframes();
                                document.querySelectorAll('video').forEach(function(v) {
                                    v._freenetAutoplayAttempted = false;
                                    setupVideoElement(v);
                                });
                                triggerPlayButtons();
                                setupPlayerFocusAndClicks();
                                setupControlBarButtons();
                            }, 400);
                        });
                    }
                });
            }

            // HTML5 Fullscreen API synchronization
            document.addEventListener('fullscreenchange', function() {
                if (document.fullscreenElement) {
                    if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.requestTvFullscreen === 'function') {
                        window.AndroidNativeBridge.requestTvFullscreen();
                    }
                } else {
                    if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.exitTvFullscreen === 'function') {
                        window.AndroidNativeBridge.exitTvFullscreen();
                    }
                }
                queueStateBroadcast(true);
            });

            // 10. 🚫 Popunder & Unrequested Window Click Neutralizer (kon-na-andrid-tv-stream-movie logic)
            document.addEventListener('click', function(e) {
                var target = e.target;
                if (!target) return;
                var link = target.closest('a');
                if (link) {
                    var href = (link.getAttribute('href') || '').toLowerCase();
                    var trg = (link.getAttribute('target') || '').toLowerCase();
                    if (trg === '_blank' && link.closest('#player, .player-wrapper, .jwplayer, .plyr, .video-js, [class*="player"]')) {
                        e.preventDefault();
                        e.stopPropagation();
                        return false;
                    }
                    if (href.indexOf('20bet') !== -1 || href.indexOf('1xbet') !== -1 || href.indexOf('monetag') !== -1 ||
                        href.indexOf('popads') !== -1 || href.indexOf('clickadu') !== -1 || href.indexOf('adsterra') !== -1 ||
                        href.indexOf('exoclick') !== -1 || href.indexOf('juicyads') !== -1 || href.indexOf('trafficjunky') !== -1) {
                        e.preventDefault();
                        e.stopPropagation();
                        return false;
                    }
                }
            }, true);

            // Run passes
            suppressHomepageTrailers();
            setupIframes();
            document.querySelectorAll('video').forEach(setupVideoElement);
            setupPlayerFocusAndClicks();
            setupControlBarButtons();
            setupServerSwitchListeners();
            triggerPlayButtons();

            // Observe dynamic changes
            var obs = new MutationObserver(function() {
                suppressHomepageTrailers();
                setupIframes();
                document.querySelectorAll('video').forEach(setupVideoElement);
                setupPlayerFocusAndClicks();
                setupControlBarButtons();
                setupServerSwitchListeners();
                checkAndAutoClickResume();
            });
            if (document.documentElement) {
                obs.observe(document.documentElement, { childList: true, subtree: true });
            }

            // Periodic watchdog for resume dialogs & video playback
            setInterval(function() {
                if (isWatchPage()) {
                    checkAndAutoClickResume();
                }
            }, 800);
        })();
    """

    fun injectAtDocumentStart(webView: WebView) {
        webView.evaluateJavascript(ANTI_ANTI_ADBLOCK_JS.trimIndent(), null)
        webView.evaluateJavascript(KEY_EVENTS_PATCH_JS.trimIndent(), null)
        webView.evaluateJavascript(TV_DPAD_SPATIAL_NAVIGATION_JS.trimIndent(), null)
        webView.evaluateJavascript(GOOGLE_INTERSTITIAL_BYPASS_JS.trimIndent(), null)
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

    fun injectVideoAutoplayHelper(webView: WebView) {
        webView.evaluateJavascript(VIDEO_AUTOPLAY_HELPER_JS.trimIndent(), null)
    }

    fun injectAll(
        webView: WebView,
        antiAntiAdblock: Boolean = true,
        cosmeticFilter: Boolean = true,
        ytFreedom: Boolean = true
    ) {
        if (antiAntiAdblock) injectAtDocumentStart(webView)
        if (cosmeticFilter) injectCosmeticFiltering(webView)
        if (ytFreedom) injectYouTubeFreedom(webView)
        injectOptimizations(webView)
        injectVideoAutoplayHelper(webView)
    }
}

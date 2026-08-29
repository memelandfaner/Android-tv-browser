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
                var _lastObserverRun = 0;
                var observer = new MutationObserver(function() {
                    var now = Date.now();
                    if (now - _lastObserverRun < 1000) return;
                    _lastObserverRun = now;

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

    // 🎮 1c. Universal High-Precision D-Pad Spatial Navigation & Search/Button Priority
    private const val TV_DPAD_SPATIAL_NAVIGATION_JS = """
        (function() {
            var INTERACTIVE_SELECTORS = [
                'textarea',
                'input:not([type="hidden"])',
                'input#search',
                '#search-input input',
                'ytd-searchbox input',
                'input[name="search_query"]',
                'textarea',
                'select',
                'button',
                'a[href]',
                '[role="button"]',
                '[role="searchbox"]',
                '[role="textbox"]',
                '[role="link"]',
                '[role="tab"]',
                '[role="menuitem"]',
                '[contenteditable="true"]',
                'iframe',
                '.jw-icon',
                '.plyr__control',
                '.vjs-control',
                '.gLFyf',
                '#APjFqb',
                '.topbar-search-button',
                '.search-btn',
                'button.search-button',
                '#search-icon-legacy',
                'button[aria-label*="Iskanje"]',
                'button[aria-label*="Search"]',
                'button[aria-label*="search"]',
                'a[aria-label*="Iskanje"]',
                'a[aria-label*="Search"]',
                'c3-icon[type="search"]',
                'ytm-searchbox',
                'ytd-searchbox',
                '.mobile-topbar-header-endpoint',
                // 🔍 Google Search Results, AI Cards & Web Articles
                'div.g a[href]',
                'div.yuRUbf a',
                'div#search a[href]',
                'div.MjjYud a[href]',
                'a[jsname]',
                'h3 > a',
                'a:has(h3)',
                'a[data-ved]',
                // 📺 YouTube Video Cards, Polymer items & Player Controls
                'ytd-rich-item-renderer',
                'ytd-video-renderer',
                'ytd-grid-video-renderer',
                'ytd-compact-video-renderer',
                'yt-chip-cloud-chip-renderer',
                'ytm-media-item',
                'ytm-video-with-context-renderer',
                'ytm-compact-video-renderer',
                'ytm-rich-item-renderer',
                'a#thumbnail',
                'a#video-title-link',
                'a.media-item-thumbnail-container',
                'a.compact-media-item-image',
                'a.media-item-headline',
                'a.compact-media-item-headline',
                'a[href*="/watch"]',
                'a[href*="watch?v="]',
                '.ytp-play-button',
                '.ytp-fullscreen-button',
                '.ytp-settings-button',
                '.ytp-subtitles-button',
                '.player-control-play-pause-icon'
            ].join(', ');

            // Naredi izključno interaktivne elemente focusable (NE vseh generic div-ov!)
            function setupFocusableElements() {
                try {
                    // Odstrani napačne tabindex=0 iz navadnih div-ov in svg-jev
                    var bogus = document.querySelectorAll('div[tabindex="0"]:not([role]):not([onclick]), svg[tabindex="0"]');
                    bogus.forEach(function(b) { b.removeAttribute('tabindex'); });

                    document.querySelectorAll(INTERACTIVE_SELECTORS).forEach(function(el) {
                        if (!el.hasAttribute('tabindex')) {
                            var r = el.getBoundingClientRect();
                            if (r.width >= 12 && r.height >= 12) {
                                el.tabIndex = 0;
                            }
                        }
                    });
                } catch(e) {}
            }

            // Injected styling za fokus (čista, nemoteča obroba)
            var styleId = 'freenet-dpad-focus-style';
            if (!document.getElementById(styleId)) {
                var style = document.createElement('style');
                style.id = styleId;
                style.textContent = `
                    a[href]:focus,
                    button:focus,
                    input:focus,
                    textarea:focus,
                    select:focus,
                    [role="button"]:focus,
                    [role="link"]:focus,
                    [role="tab"]:focus,
                    [role="menuitem"]:focus,
                    #search:focus-within,
                    ytd-searchbox:focus-within,
                    ytd-rich-item-renderer:focus,
                    ytd-rich-item-renderer:focus-within,
                    ytd-video-renderer:focus,
                    ytd-video-renderer:focus-within,
                    ytd-grid-video-renderer:focus,
                    ytd-grid-video-renderer:focus-within,
                    ytd-compact-video-renderer:focus,
                    ytd-compact-video-renderer:focus-within,
                    ytm-media-item:focus,
                    ytm-video-with-context-renderer:focus,
                    yt-chip-cloud-chip-renderer:focus,
                    yt-chip-cloud-chip-renderer:focus-within,
                    [data-tv-focused="true"] {
                        outline: 4px solid #00d2ff !important;
                        outline-offset: 3px !important;
                        box-shadow: 0 0 24px rgba(0, 210, 255, 0.85) !important;
                        border-radius: 12px !important;
                    }
                    ytd-rich-item-renderer[data-tv-focused="true"],
                    ytd-video-renderer[data-tv-focused="true"],
                    ytm-media-item[data-tv-focused="true"] {
                        outline: 4px solid #00d2ff !important;
                        outline-offset: 4px !important;
                        box-shadow: 0 0 28px rgba(0, 210, 255, 0.9) !important;
                        border-radius: 14px !important;
                        transform: scale(1.025) !important;
                        transition: transform 0.12s ease-out !important;
                        z-index: 100 !important;
                    }
                    div:focus:not([role="button"]), span:focus, [aria-hidden="true"]:focus, .RNNXgb:focus, .a4bIc:focus {
                        outline: none !important;
                        box-shadow: none !important;
                    }
                    /* 🚫 Trajno skrij Google zasebnost, pogoje in odvečne noge */
                    #fbar, #footcnt, footer, .fbar, [aria-label="Noga"], [role="contentinfo"],
                    a[href*="policies.google.com"], a[href*="privacy"], a[href*="terms"],
                    a[href*="zasebnost"], a[href*="pogoji"], .fbar-content, #swml, #W5egbf {
                        display: none !important;
                        visibility: hidden !important;
                        height: 0 !important;
                        opacity: 0 !important;
                        pointer-events: none !important;
                    }
                `;
                (document.head || document.documentElement).appendChild(style);
            }

            function getFocusableElements() {
                setupFocusableElements();
                var all = Array.from(document.querySelectorAll(INTERACTIVE_SELECTORS));
                var vpWidth = window.innerWidth || document.documentElement.clientWidth;
                var vpHeight = window.innerHeight || document.documentElement.clientHeight;

                return all.filter(function(el) {
                    if (!el) return false;
                    if (el.disabled) return false;
                    if (el.getAttribute('aria-hidden') === 'true') return false;
                    
                    // Izloči gumba Zasebnost, Pogoji, Skok na glavno vsebino in elemente v nogi
                    var href = (el.getAttribute('href') || '').toLowerCase();
                    var text = (el.textContent || '').trim().toLowerCase();
                    if (href.indexOf('policies.google.com') !== -1 || href.indexOf('privacy') !== -1 || href.indexOf('terms') !== -1) return false;
                    if (text === 'zasebnost' || text === 'pogoji' || text === 'privacy' || text === 'terms') return false;
                    if (href.indexOf('#main') !== -1 || href.indexOf('skip') !== -1 || text.indexOf('skok na glavno vsebino') !== -1 || text.indexOf('skip to content') !== -1) return false;
                    if (el.classList.contains('skip-to-content') || el.classList.contains('skip-link')) return false;
                    if (el.closest('#fbar, #footcnt, footer, .fbar, [role="contentinfo"]')) return false;

                    var style = window.getComputedStyle(el);
                    if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
                    
                    var rect = el.getBoundingClientRect();
                    if (rect.width <= 0 || rect.height <= 0) return false;
                    
                    // Izloči elemente, ki so popolnoma zunaj vidnega polja
                    if (rect.bottom < -100 || rect.top > vpHeight * 2.0 || rect.right < -100 || rect.left > vpWidth * 2.0) {
                        return false;
                    }
                    return true;
                });
            }

            // 🎯 Samodejno usmerjanje na iskalno polje na Google / prvi YouTube video
            function autoFocusSearch() {
                var isSearchHome = (location.hostname.indexOf('google') !== -1 ||
                                    location.hostname.indexOf('duckduckgo') !== -1 ||
                                    location.hostname.indexOf('bing') !== -1) &&
                                   (location.pathname === '/' || location.pathname === '') &&
                                   (!location.search || location.search.indexOf('q=') === -1);
                if (isSearchHome) {
                    var searchInput = document.querySelector('textarea[name="q"], input[name="q"], #APjFqb, .gLFyf, input[type="search"], input[type="text"]');
                    if (searchInput && document.activeElement !== searchInput) {
                        searchInput.focus();
                        return true;
                    }
                }

                // Na YouTube rezultatih iskanja samodejno fokusiraj prvi video rezultat
                if (location.hostname.indexOf('youtube.com') !== -1 && location.pathname.indexOf('/watch') === -1) {
                    var firstVideo = document.querySelector('ytm-video-with-context-renderer, ytm-compact-video-renderer, ytm-media-item, a[href*="watch?v="], ytd-video-renderer');
                    if (firstVideo && document.activeElement !== firstVideo) {
                        firstVideo.focus();
                        return true;
                    }
                }
                return false;
            }

            // 🧭 2D Geometrijska Navigacija (Spatial Navigation Beam)
            window.focusNextElement = function(direction) {
                try {
                    // Počisti morebiten moder tekstovni izbor
                    if (window.getSelection) {
                        window.getSelection().removeAllRanges();
                    }
                } catch(_) {}

                var focusables = getFocusableElements();
                if (focusables.length === 0) {
                    if (direction === 'up') {
                        if (window.scrollY > 30) {
                            window.scrollBy({ top: -320, behavior: 'smooth' });
                        } else if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.focusToolbar === 'function') {
                            window.AndroidNativeBridge.focusToolbar();
                        }
                    } else if (direction === 'down') {
                        window.scrollBy({ top: 320, behavior: 'smooth' });
                    }
                    return;
                }

                var current = document.activeElement;
                
                // Če ni nič izbrano ali smo na vrhu, izberi prvi vidni element
                if (!current || current === document.body || current === document.documentElement || focusables.indexOf(current) === -1) {
                    if (autoFocusSearch()) return;
                    if (focusables.length > 0) {
                        var sorted = focusables.slice().sort(function(a, b) {
                            return a.getBoundingClientRect().top - b.getBoundingClientRect().top;
                        });
                        sorted[0].focus();
                        try { sorted[0].scrollIntoView({ behavior: 'smooth', block: 'center' }); } catch(_) {}
                        return;
                    }
                }

                var curRect = current.getBoundingClientRect();
                var curCx = curRect.left + curRect.width / 2;
                var curCy = curRect.top + curRect.height / 2;

                var bestCandidate = null;
                var bestDistance = Infinity;

                for (var i = 0; i < focusables.length; i++) {
                    var el = focusables[i];
                    if (el === current) continue;

                    var r = el.getBoundingClientRect();
                    var cx = r.left + r.width / 2;
                    var cy = r.top + r.height / 2;

                    var dx = cx - curCx;
                    var dy = cy - curCy;

                    var isInDirection = false;
                    var primaryDist = 0;
                    var orthogonalDist = 0;
                    var isSameRow = Math.abs(curCy - cy) <= Math.max(curRect.height, r.height, 45);
                    var isSameCol = Math.abs(curCx - cx) <= Math.max(curRect.width, r.width, 45);
                    var rowBonus = 0;

                    if (direction === 'right') {
                        if (cx > curCx + 4) {
                            isInDirection = true;
                            primaryDist = Math.max(0, r.left - curRect.right);
                            orthogonalDist = Math.abs(dy);
                            if (isSameRow) rowBonus = -400;
                        }
                    } else if (direction === 'left') {
                        if (cx < curCx - 4) {
                            isInDirection = true;
                            primaryDist = Math.max(0, curRect.left - r.right);
                            orthogonalDist = Math.abs(dy);
                            if (isSameRow) rowBonus = -400;
                        }
                    } else if (direction === 'down') {
                        if (cy > curCy + 4) {
                            isInDirection = true;
                            primaryDist = Math.max(0, r.top - curRect.bottom);
                            orthogonalDist = Math.abs(dx);
                            if (isSameCol) rowBonus = -300;
                        }
                    } else if (direction === 'up') {
                        if (cy < curCy - 4) {
                            isInDirection = true;
                            primaryDist = Math.max(0, curRect.top - r.bottom);
                            orthogonalDist = Math.abs(dx);
                            if (isSameCol) rowBonus = -300;
                        }
                    }

                    if (isInDirection) {
                        var isInput = (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT' || el.id === 'search' || el.classList.contains('ytd-searchbox'));
                        var isSearchBtn = el.classList.contains('topbar-search-button') ||
                                          el.classList.contains('search-btn') ||
                                          el.id === 'search-icon-legacy' ||
                                          (el.getAttribute('aria-label') && el.getAttribute('aria-label').toLowerCase().indexOf('iskanje') !== -1) ||
                                          (el.getAttribute('aria-label') && el.getAttribute('aria-label').toLowerCase().indexOf('search') !== -1);
                        var isYtCard = el.tagName.toLowerCase().indexOf('ytm-') !== -1 || el.tagName.toLowerCase().indexOf('ytd-') !== -1;
                        var priorityBonus = isInput ? -350 : (isSearchBtn ? -250 : (isYtCard ? -80 : 0));
                        
                        var dist = primaryDist * 1.0 + orthogonalDist * 0.45 + rowBonus + priorityBonus;
                        if (dist < bestDistance) {
                            bestDistance = dist;
                            bestCandidate = el;
                        }
                    }
                }

                if (bestCandidate) {
                    document.querySelectorAll('[data-tv-focused="true"]').forEach(function(e) {
                        e.removeAttribute('data-tv-focused');
                    });
                    bestCandidate.setAttribute('data-tv-focused', 'true');
                    bestCandidate.focus();
                    try {
                        bestCandidate.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'nearest' });
                    } catch(e) {
                        if (bestCandidate.scrollIntoViewIfNeeded) bestCandidate.scrollIntoViewIfNeeded();
                    }
                    if (bestCandidate.tagName === 'IFRAME') {
                        try { bestCandidate.contentWindow.focus(); } catch(e){}
                    }
                } else if (direction === 'down') {
                    window.scrollBy({ top: 320, behavior: 'smooth' });
                    setTimeout(function() {
                        var focusablesAfter = getFocusableElements();
                        var nextEl = focusablesAfter.find(function(e) {
                            var r = e.getBoundingClientRect();
                            return r.top > 80 && r.top < window.innerHeight - 50;
                        });
                        if (nextEl) {
                            document.querySelectorAll('[data-tv-focused="true"]').forEach(function(e) {
                                e.removeAttribute('data-tv-focused');
                            });
                            nextEl.setAttribute('data-tv-focused', 'true');
                            nextEl.focus();
                        }
                    }, 180);
                } else if (direction === 'up') {
                    if (window.scrollY > 40) {
                        window.scrollBy({ top: -320, behavior: 'smooth' });
                    } else {
                        if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.focusToolbar === 'function') {
                            window.AndroidNativeBridge.focusToolbar();
                        }
                    }
                }
            };

            window.clickActiveElement = function() {
                var act = document.activeElement;
                if (!act) return;

                // Če je fokusirana YouTube kartica, najdi in klikni notranjo video povezavo
                var isYtCard = act.tagName.toLowerCase().indexOf('ytm-') !== -1 ||
                               act.tagName.toLowerCase().indexOf('ytd-') !== -1 ||
                               act.classList.contains('media-item') ||
                               act.classList.contains('video-card') ||
                               act.classList.contains('compact-media-item') ||
                               (act.closest && act.closest('ytd-rich-item-renderer, ytd-video-renderer, ytm-media-item'));

                if (isYtCard) {
                    var card = (act.closest && act.closest('ytd-rich-item-renderer, ytd-video-renderer, ytm-media-item')) || act;
                    var link = card.querySelector('a#thumbnail, a#video-title-link, a[href*="watch"], a.media-item-thumbnail-container, a.compact-media-item-image, a[href]');
                    if (link && link.href) {
                        try { link.focus(); } catch(_) {}
                        try { link.click(); } catch(_) {}
                        try {
                            link.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
                        } catch(_) {}
                        setTimeout(function() {
                            if (window.location.href.indexOf('watch') === -1 && link.href.indexOf('watch') !== -1) {
                                window.location.href = link.href;
                            }
                        }, 50);
                        return;
                    }
                }

                var isSearchTrigger = act.classList.contains('topbar-search-button') ||
                                      act.classList.contains('search-btn') ||
                                      (act.getAttribute('aria-label') && act.getAttribute('aria-label').toLowerCase().indexOf('iskanje') !== -1) ||
                                      (act.getAttribute('aria-label') && act.getAttribute('aria-label').toLowerCase().indexOf('search') !== -1);

                if (act.tagName === 'INPUT' || act.tagName === 'TEXTAREA') {
                    act.focus();
                    try {
                        var len = (act.value || '').length;
                        if (act.setSelectionRange) act.setSelectionRange(len, len);
                    } catch(e) {}
                    if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.showKeyboard === 'function') {
                        window.AndroidNativeBridge.showKeyboard();
                    }
                }

                var isFs = (act.className && typeof act.className === 'string' && act.className.indexOf('fullscreen') !== -1) ||
                           (act.getAttribute('aria-label') && act.getAttribute('aria-label').toLowerCase().indexOf('fullscreen') !== -1) ||
                           (act.getAttribute('title') && act.getAttribute('title').toLowerCase().indexOf('fullscreen') !== -1) ||
                           (act.id && act.id.indexOf('fullscreen') !== -1);
                if (isFs && window.AndroidNativeBridge && typeof window.AndroidNativeBridge.toggleTvFullscreen === 'function') {
                    window.AndroidNativeBridge.toggleTvFullscreen();
                }

                try { act.focus(); } catch(e) {}
                try { act.click(); } catch(e) {}
                try {
                    act.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, cancelable: true, composed: true }));
                    act.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, composed: true }));
                    act.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, cancelable: true, composed: true }));
                    act.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true, composed: true }));
                    act.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, composed: true }));
                } catch(e) {}

                // Če kliknemo na lupo na YouTube/straneh, samodejno preusmeri fokus v odprto iskalno polje
                if (isSearchTrigger) {
                    setTimeout(function() {
                        var input = document.querySelector('input[name="search_query"], input#search, input.search-input, input[type="search"], input[type="text"]');
                        if (input) {
                            input.focus();
                            input.click();
                        }
                    }, 120);
                }
            };

            // ⌨️ Sync Input Focus with Android Native Bridge
            document.addEventListener('focusin', function(e) {
                var t = e.target;
                if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable)) {
                    if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.onInputFocusChanged === 'function') {
                        window.AndroidNativeBridge.onInputFocusChanged(true);
                    }
                }
            }, true);

            document.addEventListener('focusout', function(e) {
                var t = e.target;
                if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable)) {
                    if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.onInputFocusChanged === 'function') {
                        window.AndroidNativeBridge.onInputFocusChanged(false);
                    }
                }
            }, true);

            // 🕹️ TV Remote D-Pad Navigation Event Listener
            window.addEventListener('keydown', function(e) {
                var k = e.keyCode || e.which;
                var key = e.key || '';
                var isTyping = document.activeElement && (document.activeElement.tagName === 'INPUT' || document.activeElement.tagName === 'TEXTAREA' || document.activeElement.isContentEditable);

                // Če uporabnik tipka v vnosnem polju ali iskalniku, NE blokiraj tipk!
                if (isTyping) {
                    if (k === 13 || k === 66 || key === 'Enter') {
                        // Ob pritisku ENTER pošlji obrazec / iskanje
                        if (document.activeElement.form) {
                            try { document.activeElement.form.submit(); } catch(_) {}
                        }
                    }
                    return;
                }

                // DPAD_LEFT (21 / 37 / ArrowLeft)
                if (k === 37 || k === 21 || key === 'ArrowLeft') {
                    e.preventDefault();
                    e.stopImmediatePropagation();
                    window.focusNextElement('left');
                }
                // DPAD_UP (19 / 38 / ArrowUp)
                else if (k === 38 || k === 19 || key === 'ArrowUp') {
                    e.preventDefault();
                    e.stopImmediatePropagation();
                    window.focusNextElement('up');
                }
                // DPAD_RIGHT (22 / 39 / ArrowRight)
                else if (k === 39 || k === 22 || key === 'ArrowRight') {
                    e.preventDefault();
                    e.stopImmediatePropagation();
                    window.focusNextElement('right');
                }
                // DPAD_DOWN (20 / 40 / ArrowDown)
                else if (k === 40 || k === 20 || key === 'ArrowDown') {
                    e.preventDefault();
                    e.stopImmediatePropagation();
                    window.focusNextElement('down');
                }
                // DPAD_CENTER / ENTER (13 / 23 / 66 / Enter / Select)
                else if (k === 13 || k === 23 || k === 66 || key === 'Enter' || key === 'Select') {
                    e.preventDefault();
                    e.stopImmediatePropagation();
                    window.clickActiveElement();
                }
                else if (k === 27 || key === 'Escape') {
                    window.focus();
                }
            }, true);

            // Samodejni fokus ob nalaganju strani
            if (document.readyState === 'complete' || document.readyState === 'interactive') {
                setTimeout(autoFocusSearch, 300);
            } else {
                document.addEventListener('DOMContentLoaded', function() { setTimeout(autoFocusSearch, 300); });
            }
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
                style.textContent = `
                    #ad, #ads, .ad, .ads, .ad-banner, .advertisement, .ad-container,
                    .adsbygoogle, [id^="google_ads_"], [id^="div-gpt-ad"], [class*="sponsored-post"],
                    .ytp-ad-module, .ytp-ad-overlay-container, .video-ads, #player-ads,
                    iframe[src*="doubleclick"], iframe[src*="googleads"], iframe[src*="adservice"],
                    #app-banner, .smartbanner, [class*="open-in-app"], [class*="app-promo"],
                    .banner-open-app,
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

                    /* 🎯 Clean TV Focus Styling (Eye-friendly Soft Sky Blue) */
                    :focus:not(body):not(html), :focus-visible {
                        outline: 2.5px solid #38bdf8 !important;
                        outline-offset: 2px !important;
                        box-shadow: 0 0 10px rgba(56, 189, 248, 0.35) !important;
                        border-radius: 8px !important;
                    }
                    /* Remove any intrusive focus on Google chips/suggestions */
                    .gws-output-html, .sbfl_b, [data-async-context*="query"] :focus {
                        box-shadow: none !important;
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
                    .jw-icon:focus, .jw-button-color:focus, .plyr__controls button:focus, .vjs-control:focus, .ytp-button:focus,
                    .jw-slider-horizontal:focus, [class*="server"]:focus, .server-btn:focus, .tv-hud-btn:focus {
                        outline: 3px solid #00e5ff !important;
                        outline-offset: 3px !important;
                        box-shadow: 0 0 20px rgba(0, 229, 255, 0.95), 0 0 40px rgba(0, 229, 255, 0.5) !important;
                        background: rgba(0, 229, 255, 0.28) !important;
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

    // ⚡ 3. YouTube Freedom: SmartTube & Brave-Style JSON AdBlock, SponsorBlock & RYD
    private const val YOUTUBE_FREEDOM_JS = """
        (function initYouTubeFreedom() {
            if (window.location.href.indexOf('youtube.com') === -1) return;

            // 🛡️ 1. Inject Instant Anti-Ad, Sponsor Hiding & SmartTube TV Grid CSS
            function injectSmartTubeStyle() {
                var style = document.getElementById('tv_yt_smarttube_css');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'tv_yt_smarttube_css';
                    style.textContent = `
                        /* 🛡️ Pure YouTube Ad & Promo Blocking */
                        #player-ads, .ytp-ad-module, .ytp-ad-overlay-container, .video-ads,
                        .ytp-ad-player-overlay, .ytp-ad-button-vm,
                        ytd-ad-slot-renderer, ytm-ad-slot-renderer,
                        ytd-in-feed-ad-layout-renderer, ytm-in-feed-ad-layout-renderer,
                        ytd-promoted-sparkles-web-renderer, ytm-promoted-sparkles-web-renderer,
                        ytd-banner-promo-renderer, ytd-statement-banner-renderer,
                        #masthead-ad, #offer-module, #clarify-box, #about-this-result,
                        ytm-promoted-video-renderer, ytd-brand-video-singleton-renderer {
                            display: none !important;
                            visibility: hidden !important;
                            height: 0px !important;
                            width: 0px !important;
                            margin: 0 !important;
                            padding: 0 !important;
                            opacity: 0 !important;
                            pointer-events: none !important;
                        }

                        /* 📺 Clean YouTube Dark Background */
                        body, ytm-app, ytd-app, #app, .page-container, ytm-browse, #content, #page-manager, #primary, html {
                            background: #07090e !important;
                            color: #f1f5f9 !important;
                        }

                        /* 📺 Hide Annoying Shorts Shelves */
                        ytm-reel-shelf-renderer,
                        ytd-reel-shelf-renderer,
                        ytm-shorts-lockup-view-model,
                        ytm-reel-item-renderer {
                            display: none !important;
                            visibility: hidden !important;
                            height: 0px !important;
                            margin: 0 !important;
                            padding: 0 !important;
                        }

                        /* 📺 Smart TV D-Pad Focus Glow for YouTube Video Items, Chips & Buttons */
                        ytd-rich-item-renderer:focus-within,
                        ytd-video-renderer:focus-within,
                        ytd-compact-video-renderer:focus-within,
                        yt-chip-cloud-chip-renderer:focus-within,
                        #chips yt-chip-cloud-chip-renderer:focus,
                        ytm-media-item:focus-within,
                        ytm-video-with-context-renderer:focus-within,
                        ytm-compact-video-renderer:focus-within,
                        a#thumbnail:focus {
                            outline: 3px solid #00d2ff !important;
                            outline-offset: 4px !important;
                            border-radius: 12px !important;
                            box-shadow: 0 0 16px rgba(0, 210, 255, 0.6) !important;
                            transition: outline 0.15s ease, box-shadow 0.15s ease !important;
                        }

                        /* 📺 16:9 Smooth Rounded Thumbnails */
                        ytd-thumbnail, ytd-thumbnail img, #thumbnail,
                        .media-item-thumbnail-container, ytm-thumbnail-cover, .cover-container, .thumbnail-container,
                        .video-thumbnail-container, img.video-thumbnail, ytm-thumbnail-cover img {
                            border-radius: 10px !important;
                        }
                    `;
                    (document.head || document.documentElement).appendChild(style);
                }
            }
            injectSmartTubeStyle();



            // 🛡️ 2. SmartTube Video Ad Fast-Forward & Instaskip (Natančno brez spreminjanja JSON ali vpliva na normalne videe)
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
                    if (document.body) {
                        document.body.appendChild(badge);
                    } else if (document.documentElement) {
                        document.documentElement.appendChild(badge);
                    }
                }
                var dislikeText = Number(dislikes).toLocaleString();
                var likeText = likes ? Number(likes).toLocaleString() : '';
                badge.textContent = '👍 ' + likeText + '  |  👎 ' + dislikeText;
            }

            function onVideoTimeUpdate() {
                if (!boundVideoElement || boundVideoElement.paused || boundVideoElement.seeking || boundVideoElement.readyState < 3) return;
                var cur = boundVideoElement.currentTime;
                if (cur < 1.5) return;
                for (var i = 0; i < segments.length; i++) {
                    var seg = segments[i].segment;
                    if (cur >= seg[0] && cur < seg[1]) {
                        boundVideoElement.currentTime = seg[1];
                        showSkipToast('⚡ FreeNet: Preskočen segment (' + (segments[i].category || 'sponzor') + ')');
                        break;
                    }
                }
            }

            function triggerInstantYouTubePlay(video) {
                if (!video) return;
                if (video.muted) video.muted = false;
                if (video.volume < 1.0) video.volume = 1.0;

                try {
                    video.play().catch(function(){});
                } catch(e) {}

                var playSelectors = [
                    '.player-control-play-pause-icon',
                    '.ytp-large-play-button',
                    '.ytp-play-button',
                    'button[aria-label*="Play"]',
                    'button[aria-label*="Predvajaj"]'
                ];
                var btns = document.querySelectorAll(playSelectors.join(','));
                btns.forEach(function(b) {
                    try {
                        b.click();
                        var evt = new MouseEvent('click', { bubbles: true, cancelable: true, view: window });
                        b.dispatchEvent(evt);
                    } catch(e) {}
                });
            }

            function attachPlayer() {
                var v = document.querySelector('video');
                if (v) {
                    if (v !== boundVideoElement) {
                        if (boundVideoElement) {
                            boundVideoElement.removeEventListener('timeupdate', onVideoTimeUpdate);
                        }
                        boundVideoElement = v;
                        boundVideoElement.addEventListener('timeupdate', onVideoTimeUpdate);
                    }
                    triggerInstantYouTubePlay(v);
                }
            }

            function showSkipToast(msg) {
                // Tihi način brez motenja celozaslonskega filma
            }

            // 🛡️ 3. SmartTube Video Ad Fast-Forward & Instaskip
            function blockYouTubeAds() {
                var video = document.querySelector('video');
                var isAdActive = document.querySelector('.ad-showing, .ad-interrupting');
                var moviePlayer = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                
                if (isAdActive && video) {
                    video.muted = true;
                    video.playbackRate = 16.0;
                    if (isFinite(video.duration) && video.duration > 0) {
                        video.currentTime = video.duration;
                    }
                    if (moviePlayer && typeof moviePlayer.skipAd === 'function') {
                        try { moviePlayer.skipAd(); } catch(e) {}
                    }
                } else if (!isAdActive && video) {
                    if (video.playbackRate > 2.0) {
                        video.playbackRate = 1.0;
                        video.muted = false;
                    }
                }

                // Instant click on YouTube Skip Ad buttons
                var skipButtons = document.querySelectorAll(
                    '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytp-ad-overlay-close-button, button.ytp-ad-skip-button-text, .ytp-ad-skip-button-slot button, .ytp-ad-preview-container, button[aria-label*="Preskoči"], button[aria-label*="Skip"]'
                );
                skipButtons.forEach(function(btn) {
                    try { btn.click(); } catch(e) {}
                });

                // Remove sponsored rich items, clarification boxes and promo banners
                var badSelectors = [
                    '.badge-style-type-ad',
                    'ytd-ad-slot-renderer',
                    'ytm-ad-slot-renderer',
                    'ytd-in-feed-ad-layout-renderer',
                    'ytd-promoted-sparkles-web-renderer',
                    'ytd-promoted-video-renderer',
                    'ytd-search-pyv-renderer',
                    'ytd-clarification-renderer',
                    '#about-this-ad',
                    '#clarify-box',
                    '#about-this-result'
                ];
                document.querySelectorAll(badSelectors.join(',')).forEach(function(el) {
                    var container = el.closest('ytd-rich-item-renderer, ytm-rich-item-renderer, ytd-video-renderer, ytd-rich-section-renderer, ytd-search-pyv-renderer, ytd-clarification-renderer, ytd-item-section-renderer');
                    if (container) {
                        try { container.remove(); } catch(e) {}
                    } else {
                        try { el.remove(); } catch(e) {}
                    }
                });

                // Deep scan for text containing "Sponzorirano" or "Sponsored"
                var allSpans = document.querySelectorAll('span, div, p');
                allSpans.forEach(function(el) {
                    if (el.children.length === 0) {
                        var txt = (el.textContent || '').trim().toLowerCase();
                        if (txt === 'sponzorirano' || txt === 'sponsored' || txt === 'sponzor' || txt === 'ad') {
                            var item = el.closest('ytd-rich-item-renderer, ytm-rich-item-renderer, ytd-video-renderer, ytd-item-section-renderer, ytd-rich-section-renderer');
                            if (item) {
                                try { item.remove(); } catch(e) {}
                            }
                        }
                // Ad cleanup only

                // 🎮 SmartTube D-Pad Traversal: Make video items seamlessly focusable
                var items = document.querySelectorAll('ytm-media-item, ytm-rich-item-renderer, ytm-compact-video-renderer, ytm-video-with-context-renderer, ytd-rich-item-renderer, ytd-video-renderer');
                items.forEach(function(el) {
                    if (!el.hasAttribute('tabindex')) {
                        el.setAttribute('tabindex', '0');
                        el.addEventListener('keydown', function(e) {
                            if (e.key === 'Enter' || e.keyCode === 13) {
                                var a = el.querySelector('a');
                                if (a) a.click();
                                else el.click();
                            }
                        });
                    }
                });
            }

            var lastTrackedUrl = window.location.href;
            setInterval(function() {
                var currentHref = window.location.href;
                if (currentHref !== lastTrackedUrl) {
                    lastTrackedUrl = currentHref;
                    var newVId = getVideoId();
                    if (newVId) {
                        fetchSponsorSegments(newVId);
                        fetchDislikes(newVId);
                    }
                }
                var vId = getVideoId();
                if (vId && vId !== currentVideoId) {
                    fetchSponsorSegments(vId);
                    fetchDislikes(vId);
                }
                attachPlayer();
                blockYouTubeAds();
            }, 60);
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
                style.textContent = ':focus:not(body):not(html) { outline: 2.5px solid #38bdf8 !important; outline-offset: 2px !important; box-shadow: 0 0 10px rgba(56, 189, 248, 0.35) !important; border-radius: 8px !important; }';
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

            // YouTube / Video Auto-Play & Unmute Booster (0ms instant playback trigger)
            function instantMediaPlay() {
                var videos = document.querySelectorAll('video');
                videos.forEach(function(v) {
                    if (v.muted) v.muted = false;
                    if (v.volume < 1.0) v.volume = 1.0;
                    if (v.paused && !v.ended) {
                        var p = v.play();
                        if (p !== undefined) {
                            p.catch(function() {
                                var btns = document.querySelectorAll('.player-control-play-pause-icon, .ytp-large-play-button, .ytp-play-button, button[aria-label*="Play"], button[aria-label*="Predvajaj"], .player-container');
                                btns.forEach(function(b) { try { b.click(); } catch(e) {} });
                            });
                        }
                    }
                });
            }
            instantMediaPlay();

            // 🎮 TV D-Pad: Pressing ArrowUp at top of webpage escapes to top toolbar
            document.addEventListener('keydown', function(e) {
                if (e.key === 'ArrowUp' || e.keyCode === 38) {
                    var scrollTop = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0;
                    var activeEl = document.activeElement;
                    var isAtTop = scrollTop <= 20;
                    if (isAtTop) {
                        var rect = activeEl ? activeEl.getBoundingClientRect() : null;
                        if (!rect || rect.top <= 140 || activeEl === document.body || activeEl === document.documentElement) {
                            if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.focusToolbar === 'function') {
                                window.AndroidNativeBridge.focusToolbar();
                            }
                        }
                    }
                }
            }, true);

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

            function applyCinemaFullscreen(enable) {
                if (!isWatchPage()) {
                    var oldStyle = document.getElementById('freenet_tv_cinema_fullscreen_css');
                    if (oldStyle) oldStyle.remove();
                    document.documentElement.classList.remove('freenet-tv-fullscreen');
                    document.body.classList.remove('freenet-tv-fullscreen');
                    return;
                }
                var cssId = 'freenet_tv_cinema_fullscreen_css';
                if (enable) {
                    if (!document.getElementById(cssId)) {
                        var s = document.createElement('style');
                        s.id = cssId;
                        s.textContent = `
                            html.freenet-tv-fullscreen, body.freenet-tv-fullscreen {
                                overflow: hidden !important;
                                background: #000 !important;
                                margin: 0 !important;
                                padding: 0 !important;
                            }
                            .freenet-tv-fullscreen .col-md-3,
                            .freenet-tv-fullscreen [class*="similar"],
                            .freenet-tv-fullscreen [class*="sidebar"],
                            .freenet-tv-fullscreen [class*="related"],
                            .freenet-tv-fullscreen header,
                            .freenet-tv-fullscreen nav,
                            .freenet-tv-fullscreen footer,
                            .freenet-tv-fullscreen .navbar,
                            .freenet-tv-fullscreen .header,
                            .freenet-tv-fullscreen .info-section,
                            .freenet-tv-fullscreen .movie-details {
                                display: none !important;
                            }
                            .freenet-tv-fullscreen .col-md-9,
                            .freenet-tv-fullscreen .col-lg-9,
                            .freenet-tv-fullscreen .col-12,
                            .freenet-tv-fullscreen .main-content,
                            .freenet-tv-fullscreen #player,
                            .freenet-tv-fullscreen #iframe-player,
                            .freenet-tv-fullscreen #player-wrapper,
                            .freenet-tv-fullscreen .player-wrapper,
                            .freenet-tv-fullscreen .player-container,
                            .freenet-tv-fullscreen .video-container,
                            .freenet-tv-fullscreen iframe,
                            .freenet-tv-fullscreen .jwplayer,
                            .freenet-tv-fullscreen .video-js,
                            .freenet-tv-fullscreen .plyr,
                            .freenet-tv-fullscreen video {
                                position: fixed !important;
                                top: 0 !important;
                                left: 0 !important;
                                width: 100vw !important;
                                height: 100vh !important;
                                max-width: 100vw !important;
                                max-height: 100vh !important;
                                min-width: 100vw !important;
                                min-height: 100vh !important;
                                z-index: 2147483647 !important;
                                background: #000 !important;
                                margin: 0 !important;
                                padding: 0 !important;
                                border: none !important;
                            }
                        `;
                        (document.head || document.documentElement).appendChild(s);
                    }
                    document.documentElement.classList.add('freenet-tv-fullscreen');
                    document.body.classList.add('freenet-tv-fullscreen');

                    try {
                        var pEl = document.querySelector('#iframe-player, #player, iframe[src*="embed"], iframe[src*="vid"], iframe[src*="stream"], iframe[src*="hydra"], iframe[src*="player"], video');
                        if (pEl) {
                            pEl.style.setProperty('position', 'fixed', 'important');
                            pEl.style.setProperty('top', '0', 'important');
                            pEl.style.setProperty('left', '0', 'important');
                            pEl.style.setProperty('width', '100vw', 'important');
                            pEl.style.setProperty('height', '100vh', 'important');
                            pEl.style.setProperty('z-index', '2147483647', 'important');
                            pEl.style.setProperty('background', '#000', 'important');
                        }
                    } catch(e) {}
                } else {
                    document.documentElement.classList.remove('freenet-tv-fullscreen');
                    document.body.classList.remove('freenet-tv-fullscreen');
                    try {
                        var pEl2 = document.querySelector('#iframe-player, #player, iframe, video');
                        if (pEl2) {
                            pEl2.style.removeProperty('position');
                            pEl2.style.removeProperty('top');
                            pEl2.style.removeProperty('left');
                            pEl2.style.removeProperty('width');
                            pEl2.style.removeProperty('height');
                            pEl2.style.removeProperty('z-index');
                        }
                    } catch(e) {}
                }
            }

            // 2. Universal Command Dispatcher (Handles PLAY, PAUSE, TOGGLE_PLAY, SEEK, etc.)
            function handlePlayerCommand(action, value) {
                var v = getPrimaryVideo();
                switch (action) {
                    case 'PLAY':
                        if (v) {
                            v.muted = false;
                            v.volume = 1.0;
                            v.play().catch(function(){});
                        }
                        triggerPlayButtons();
                        if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.forceUnmuteAudio === 'function') {
                            window.AndroidNativeBridge.forceUnmuteAudio();
                        }
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
                        if (v) { v.muted = false; v.volume = 1.0; }
                        if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.forceUnmuteAudio === 'function') {
                            window.AndroidNativeBridge.forceUnmuteAudio();
                        }
                        break;
                    case 'TOGGLE_MUTE':
                        if (v) {
                            v.muted = !v.muted;
                            if (!v.muted && window.AndroidNativeBridge && typeof window.AndroidNativeBridge.forceUnmuteAudio === 'function') {
                                window.AndroidNativeBridge.forceUnmuteAudio();
                            }
                        }
                        break;
                    case 'SET_VOLUME':
                        if (v && typeof value === 'number') {
                            v.volume = Math.max(0, Math.min(1, value));
                            v.muted = (v.volume === 0);
                        }
                        break;
                    case 'FULLSCREEN':
                        applyCinemaFullscreen(true);
                        if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.requestTvFullscreen === 'function') {
                            window.AndroidNativeBridge.requestTvFullscreen();
                        }
                        break;
                    case 'EXIT_FULLSCREEN':
                        applyCinemaFullscreen(false);
                        if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.exitTvFullscreen === 'function') {
                            window.AndroidNativeBridge.exitTvFullscreen();
                        }
                        break;
                    case 'TOGGLE_FULLSCREEN':
                        var isFs = document.body.classList.contains('freenet-tv-fullscreen') || document.body.classList.contains('tv-fullscreen-active');
                        applyCinemaFullscreen(!isFs);
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
                        applyCinemaFullscreen(true);
                        if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.requestTvFullscreen === 'function') {
                            window.AndroidNativeBridge.requestTvFullscreen();
                        }
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

            // 5. Detection: Check if current page is an active watch / movie playback page on streaming portals
            function isWatchPage() {
                var h = (window.location.hostname || window.location.href || '').toLowerCase();
                var p = (window.location.pathname || '').toLowerCase();
                if (h.indexOf('youtube.com') !== -1 || h.indexOf('youtu.be') !== -1 || h.indexOf('google.') !== -1 || h.indexOf('bing.com') !== -1 || h.indexOf('duckduckgo.com') !== -1) return false;

                var isStreamingSite = h.indexOf('hydrahd') !== -1 ||
                                      h.indexOf('vidbox') !== -1 ||
                                      h.indexOf('vidsrc') !== -1 ||
                                      h.indexOf('autoembed') !== -1 ||
                                      h.indexOf('multiembed') !== -1 ||
                                      h.indexOf('2embed') !== -1 ||
                                      h.indexOf('111movies') !== -1 ||
                                      h.indexOf('streamnexus') !== -1 ||
                                      h.indexOf('file://') !== -1;

                if (!isStreamingSite) return false;

                var isWatchUrl = p.indexOf('/movie/') !== -1 ||
                                 p.indexOf('/series/') !== -1 ||
                                 p.indexOf('/tv/') !== -1 ||
                                 p.indexOf('/episode/') !== -1 ||
                                 p.indexOf('/watch') !== -1 ||
                                 h.indexOf('/embed/') !== -1 ||
                                 h.indexOf('/stream/') !== -1;
                return isWatchUrl;
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
                try {
                    if (!video._freenetBridgeAttached) {
                        video._freenetBridgeAttached = true;
                        if (isWatchPage()) {
                            applyCinemaFullscreen(true);
                        }

                        video.addEventListener('play', function() {
                            video.muted = false;
                            video.volume = 1.0;
                            setupMediaSession(video);
                            if (isWatchPage()) {
                                applyCinemaFullscreen(true);
                                if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.requestTvFullscreen === 'function') {
                                    window.AndroidNativeBridge.requestTvFullscreen();
                                }
                            }
                            if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.forceUnmuteAudio === 'function') {
                                window.AndroidNativeBridge.forceUnmuteAudio();
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
                        video.addEventListener('click', function() {
                            try {
                                if (isWatchPage()) {
                                    applyCinemaFullscreen(true);
                                    if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.requestTvFullscreen === 'function') {
                                        window.AndroidNativeBridge.requestTvFullscreen();
                                    }
                                }
                                if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.forceUnmuteAudio === 'function') {
                                    window.AndroidNativeBridge.forceUnmuteAudio();
                                }
                            } catch(e) {}
                        });
                    }

                    if (video.paused && !video._freenetAutoplayAttempted && isWatchPage()) {
                        video._freenetAutoplayAttempted = true;
                        video.muted = false;
                        video.volume = 1.0;
                        var p = video.play();
                        if (p !== undefined) {
                            p.catch(function(){});
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
                        if (!ifr._freenetBound) {
                            ifr._freenetBound = true;
                            ifr.addEventListener('load', function() {
                                if (isWatchPage()) {
                                    applyCinemaFullscreen(true);
                                    if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.requestTvFullscreen === 'function') {
                                        window.AndroidNativeBridge.requestTvFullscreen();
                                    }
                                }
                            });
                        }
                    } catch(e) {}
                });
            }

            document.addEventListener('click', function(e) {
                if (isWatchPage()) {
                    var isPlayerClick = e.target && e.target.closest('#player, #iframe-player, .player-wrapper, iframe, .video-player, video, .jwplayer, .plyr, .vjs-control-bar, .ytp-chrome-bottom, [class*="player"]');
                    if (isPlayerClick) {
                        applyCinemaFullscreen(true);
                        if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.requestTvFullscreen === 'function') {
                            window.AndroidNativeBridge.requestTvFullscreen();
                        }
                    }
                }
            }, true);

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
                                    if (isWatchPage()) {
                                        applyCinemaFullscreen(true);
                                        if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.requestTvFullscreen === 'function') {
                                            window.AndroidNativeBridge.requestTvFullscreen();
                                        }
                                    }
                                }, 300);
                            }
                        });

                        el.addEventListener('keydown', function(evt) {
                            if (evt.keyCode === 13 || evt.keyCode === 23 || evt.key === 'Enter' || evt.key === ' ') {
                                evt.preventDefault();
                                handlePlayerCommand('TOGGLE_PLAY');
                                if (isWatchPage()) {
                                    applyCinemaFullscreen(true);
                                    if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.requestTvFullscreen === 'function') {
                                        window.AndroidNativeBridge.requestTvFullscreen();
                                    }
                                }
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
                            applyCinemaFullscreen(true);
                            if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.requestTvFullscreen === 'function') {
                                window.AndroidNativeBridge.requestTvFullscreen();
                            }
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
            var _lastObsRun = 0;
            var obs = new MutationObserver(function() {
                var now = Date.now();
                if (now - _lastObsRun < 1000) return;
                _lastObsRun = now;

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

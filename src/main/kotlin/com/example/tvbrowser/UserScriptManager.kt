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

                    /* 🎯 TV Spatial Focus Ring & Neon Halo for Philips TV Remote Navigation */
                    :focus, :focus-visible, a:focus, button:focus, input:focus, select:focus, textarea:focus, [tabindex]:focus, [role="button"]:focus {
                        outline: 3px solid #38bdf8 !important;
                        outline-offset: 2px !important;
                        box-shadow: 0 0 16px rgba(56, 189, 248, 0.9) !important;
                        border-radius: 4px !important;
                        transition: outline 0.12s ease-out, box-shadow 0.12s ease-out !important;
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

            // 8. Make Player Control Buttons Accessible
            function setupControlBarButtons() {
                if (!isWatchPage()) return;
                var controlSelectors = [
                    '.jw-controls button', '.jw-icon', '.jw-button-color',
                    '.plyr__controls button', '.plyr__controls input',
                    '.vjs-control-bar button', '.vjs-control',
                    '.ytp-chrome-bottom button', '.ytp-button',
                    '[class*="control"] button', '[class*="controls"] button',
                    '[aria-label*="Play" i]', '[aria-label*="Pause" i]',
                    '[aria-label*="Fullscreen" i]', '[aria-label*="Exit Fullscreen" i]',
                    '[aria-label*="Celozaslonski" i]', '[aria-label*="Settings" i]',
                    '[aria-label*="Mute" i]', '[aria-label*="Volume" i]',
                    '[aria-label*="Subtitles" i]', '[aria-label*="Captions" i]',
                    '.fullscreen-btn', '[data-plyr="fullscreen"]', '.jw-icon-fullscreen',
                    '.vjs-fullscreen-control', '.ytp-fullscreen-button'
                ];
                var buttons = document.querySelectorAll(controlSelectors.join(', '));
                buttons.forEach(function(b) {
                    if (!b.hasAttribute('tabindex')) b.setAttribute('tabindex', '0');
                    if (!b._tvCtrlBound) {
                        b._tvCtrlBound = true;
                        var isFsBtn = (b.className && b.className.indexOf('fullscreen') !== -1) ||
                                      (b.getAttribute('aria-label') && b.getAttribute('aria-label').toLowerCase().indexOf('fullscreen') !== -1) ||
                                      (b.getAttribute('data-plyr') === 'fullscreen') ||
                                      (b.id && b.id.indexOf('fullscreen') !== -1);
                        if (isFsBtn) {
                            b.addEventListener('click', function() {
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

            // 9. Player Containers Focus & 1-Click Playable via D-Pad
            function setupPlayerFocusAndClicks() {
                if (!isWatchPage()) return;
                var playerSelectors = [
                    '#player', '#iframe-player', '.player-wrapper', '.video-player',
                    '.jwplayer', '.plyr', '.video-js', '[class*="player-container"]',
                    '[id*="player"]', 'iframe[src*="vid"]', 'iframe[src*="embed"]', 'iframe[src*="stream"]'
                ];
                var containers = document.querySelectorAll(playerSelectors.join(', '));
                containers.forEach(function(el) {
                    if (!el.hasAttribute('tabindex')) el.setAttribute('tabindex', '0');
                    if (!el._tvClickBound) {
                        el._tvClickBound = true;
                        function triggerPlay(e) {
                            try {
                                handlePlayerCommand('TOGGLE_PLAY');
                            } catch(err) {}
                        }
                        el.addEventListener('click', triggerPlay);
                        el.addEventListener('keydown', function(evt) {
                            if (evt.keyCode === 13 || evt.keyCode === 23 || evt.key === 'Enter' || evt.key === ' ') {
                                evt.preventDefault();
                                triggerPlay(evt);
                            }
                        });
                    }
                });
            }

            function triggerPlayButtons() {
                if (!isWatchPage()) return; // Never auto-trigger buttons on homepage
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
            });
            if (document.documentElement) {
                obs.observe(document.documentElement, { childList: true, subtree: true });
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

/**
 * app.js - StreamNexus Client Application Logic
 * Modern Autonomous Hybrid Streaming Frontend with Android TV D-Pad Remote Navigation
 * & Pure Client-Side TMDB / Stream Engine Fallback.
 */

const STANDALONE_TMDB_API_KEY = "844dba0bfd8f3a4f3799f6130ef9e335";
const TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p";

/**
 * Standalone TMDB Engine
 * Handles direct TMDB API calls when server.py is offline or when running in pure client mode on Android TV.
 */
class StandaloneTmdbEngine {
    constructor(apiKey = STANDALONE_TMDB_API_KEY) {
        this.apiKey = apiKey;
        this.baseUrl = "https://api.themoviedb.org/3";
        this.cache = new Map();
    }

    async fetchTmdb(endpoint, params = {}) {
        const cleanParams = {
            api_key: this.apiKey,
            language: params.language || "en-US",
            ...params
        };
        const queryStr = Object.entries(cleanParams)
            .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
            .join("&");
        const url = `${this.baseUrl}${endpoint}?${queryStr}`;

        if (this.cache.has(url)) {
            return this.cache.get(url);
        }

        try {
            const res = await fetch(url);
            if (!res.ok) return null;
            const data = await res.json();
            this.cache.set(url, data);
            return data;
        } catch (e) {
            console.warn('TMDB API fetch exception:', e);
            return null;
        }
    }

    formatItem(item, defaultType = 'movie') {
        const mediaType = item.media_type || defaultType;
        const title = item.title || item.name || item.original_title || item.original_name || 'Neznan naslov';
        const releaseDate = item.release_date || item.first_air_date || '';
        const year = releaseDate ? releaseDate.substring(0, 4) : '';
        const rating = item.vote_average ? Math.round(item.vote_average * 10) / 10 : 0;
        const poster = item.poster_path ? `${TMDB_IMAGE_BASE}/w500${item.poster_path}` : null;
        const backdrop = item.backdrop_path ? `${TMDB_IMAGE_BASE}/original${item.backdrop_path}` : null;

        const qualityInfo = this.classifyMediaQuality(mediaType, title, releaseDate);

        return {
            id: item.id,
            media_type: mediaType,
            title: title,
            original_title: item.original_title || item.original_name || title,
            overview: item.overview || '',
            poster: poster,
            backdrop: backdrop,
            rating: rating,
            vote_count: item.vote_count || 0,
            release_date: releaseDate,
            year: year,
            is_digital_hd: qualityInfo.is_digital_hd,
            is_cam: qualityInfo.is_cam,
            quality_label: qualityInfo.quality_label,
            quality_badge_class: qualityInfo.quality_badge_class
        };
    }

    classifyMediaQuality(mediaType, title, releaseDateStr) {
        const lower = (title || '').toLowerCase();
        if (lower.includes('cam') || lower.includes('hdts') || lower.includes('telesync') || lower.includes('hdcam')) {
            return { is_digital_hd: false, is_cam: true, quality_label: '📹 CAM / KINO', quality_badge_class: 'badge-cam' };
        }
        if (mediaType === 'tv') {
            return { is_digital_hd: true, is_cam: false, quality_label: 'HD 1080p', quality_badge_class: 'badge-quality' };
        }
        if (!releaseDateStr) {
            return { is_digital_hd: false, is_cam: true, quality_label: '📹 CAM / KINO', quality_badge_class: 'badge-cam' };
        }
        try {
            const rel = new Date(releaseDateStr);
            const now = new Date();
            const diffDays = (now - rel) / (1000 * 60 * 60 * 24);
            if (diffDays < 0) {
                return { is_digital_hd: false, is_cam: true, quality_label: `⏳ KMALU (${rel.getFullYear()})`, quality_badge_class: 'badge-unreleased' };
            }
            if (diffDays < 55) {
                return { is_digital_hd: false, is_cam: true, quality_label: '📹 CAM / KINO', quality_badge_class: 'badge-cam' };
            }
            return { is_digital_hd: true, is_cam: false, quality_label: 'HD 1080p', quality_badge_class: 'badge-quality' };
        } catch (e) {
            return { is_digital_hd: true, is_cam: false, quality_label: 'HD 1080p', quality_badge_class: 'badge-quality' };
        }
    }

    isAvailableMedia(item) {
        if (!item) return false;
        const title = item.title || item.name;
        if (!title || title === 'Neznan naslov') return false;
        if (!item.poster || item.poster.includes('placeholder') || item.poster.includes('Ni+Slike')) return false;
        if (item.is_cam === true) return false;
        if (item.quality_badge_class === 'badge-cam' || item.quality_badge_class === 'badge-unreleased') return false;
        if (item.quality_label && (item.quality_label.includes('CAM') || item.quality_label.includes('KMALU'))) return false;

        const relDateStr = item.release_date || item.first_air_date;
        if (!relDateStr) return false;
        const rel = new Date(relDateStr);
        const now = new Date();
        if (isNaN(rel.getTime()) || rel > now) return false;

        if (item.media_type === 'movie') {
            const diffDays = (now - rel) / (1000 * 60 * 60 * 24);
            if (diffDays < 45 && !item.is_digital_hd) return false;
        }
        return true;
    }

    async getTrending(mediaType = 'all', timeWindow = 'week', page = 1) {
        const data = await this.fetchTmdb(`/trending/${mediaType}/${timeWindow}`, { page });
        if (!data) return { results: [], page: 1, total_pages: 1 };
        const available = (data.results || [])
            .map(i => this.formatItem(i))
            .filter(i => this.isAvailableMedia(i));
        return {
            results: available,
            page: data.page || page,
            total_pages: Math.min(data.total_pages || 1, 500)
        };
    }

    async getPopular(mediaType = 'movie', page = 1) {
        const today = new Date().toISOString().split('T')[0];
        const params = {
            page,
            sort_by: 'popularity.desc',
            include_adult: false,
            'vote_count.gte': 10
        };
        if (mediaType === 'movie') {
            params['primary_release_date.lte'] = today;
        } else {
            params['first_air_date.lte'] = today;
        }
        const data = await this.fetchTmdb(`/discover/${mediaType}`, params);
        if (!data) return { results: [], page: 1, total_pages: 1 };
        const available = (data.results || [])
            .map(i => this.formatItem(i, mediaType))
            .filter(i => this.isAvailableMedia(i));
        return {
            results: available,
            page: data.page || page,
            total_pages: Math.min(data.total_pages || 1, 500)
        };
    }

    async getTopRated(mediaType = 'movie', page = 1) {
        const today = new Date().toISOString().split('T')[0];
        const params = {
            page,
            sort_by: 'vote_average.desc',
            'vote_count.gte': 100,
            include_adult: false
        };
        if (mediaType === 'movie') {
            params['primary_release_date.lte'] = today;
        } else {
            params['first_air_date.lte'] = today;
        }
        const data = await this.fetchTmdb(`/discover/${mediaType}`, params);
        if (!data) return { results: [], page: 1, total_pages: 1 };
        const available = (data.results || [])
            .map(i => this.formatItem(i, mediaType))
            .filter(i => this.isAvailableMedia(i));
        return {
            results: available,
            page: data.page || page,
            total_pages: Math.min(data.total_pages || 1, 500)
        };
    }

    async getDiscover(mediaType = 'movie', genreId = null, sortBy = 'popularity.desc', page = 1) {
        const today = new Date().toISOString().split('T')[0];
        const params = { page, sort_by: sortBy, include_adult: false, 'vote_count.gte': 5 };
        if (mediaType === 'movie') {
            params['primary_release_date.lte'] = today;
        } else {
            params['first_air_date.lte'] = today;
        }
        if (genreId && genreId !== 'all') params.with_genres = genreId;
        const data = await this.fetchTmdb(`/discover/${mediaType}`, params);
        if (!data) return { results: [], page: 1, total_pages: 1 };
        const available = (data.results || [])
            .map(i => this.formatItem(i, mediaType))
            .filter(i => this.isAvailableMedia(i));
        return {
            results: available,
            page: data.page || page,
            total_pages: Math.min(data.total_pages || 1, 500)
        };
    }

    async search(query, page = 1) {
        if (!query) return { results: [], page: 1, total_pages: 0 };
        const data = await this.fetchTmdb('/search/multi', { query, page, include_adult: false });
        if (!data) return { results: [], page: 1, total_pages: 0 };
        const filtered = (data.results || []).filter(i => i.media_type === 'movie' || i.media_type === 'tv');
        const available = filtered
            .map(i => this.formatItem(i))
            .filter(i => this.isAvailableMedia(i));
        return {
            results: available,
            page: data.page || page,
            total_pages: Math.min(data.total_pages || 1, 500)
        };
    }

    async getDetails(mediaType, tmdbId) {
        const data = await this.fetchTmdb(`/${mediaType}/${tmdbId}`, {
            append_to_response: 'videos,credits,similar,external_ids'
        });
        if (!data) return null;

        const extIds = data.external_ids || {};
        const imdbId = extIds.imdb_id || null;
        const genres = (data.genres || []).map(g => g.name);
        const backdrop = data.backdrop_path ? `${TMDB_IMAGE_BASE}/original${data.backdrop_path}` : null;
        const poster = data.poster_path ? `${TMDB_IMAGE_BASE}/w500${data.poster_path}` : null;

        let trailerKey = null;
        const vids = (data.videos && data.videos.results) || [];
        for (const vid of vids) {
            if (vid.site === 'YouTube' && (vid.type === 'Trailer' || vid.type === 'Teaser')) {
                trailerKey = vid.key;
                if (vid.type === 'Trailer') break;
            }
        }

        const cast = ((data.credits && data.credits.cast) || []).slice(0, 10).map(c => ({
            name: c.name,
            character: c.character,
            profile_path: c.profile_path ? `${TMDB_IMAGE_BASE}/w185${c.profile_path}` : null
        }));

        const title = data.title || data.name || data.original_title || data.original_name;
        const releaseDate = data.release_date || data.first_air_date || '';
        const year = releaseDate ? releaseDate.substring(0, 4) : '';

        const seasonsInfo = [];
        if (mediaType === 'tv' && data.seasons) {
            for (const s of data.seasons) {
                if (s.season_number > 0) {
                    seasonsInfo.push({
                        season_number: s.season_number,
                        name: s.name,
                        episode_count: s.episode_count,
                        poster_path: s.poster_path ? `${TMDB_IMAGE_BASE}/w300${s.poster_path}` : poster,
                        air_date: s.air_date
                    });
                }
            }
        }

        const qualityInfo = this.classifyMediaQuality(mediaType, title, releaseDate);

        return {
            id: data.id,
            media_type: mediaType,
            imdb_id: imdbId,
            title: title,
            original_title: data.original_title || data.original_name || title,
            overview: data.overview || '',
            tagline: data.tagline || '',
            rating: data.vote_average ? Math.round(data.vote_average * 10) / 10 : 0,
            vote_count: data.vote_count || 0,
            release_date: releaseDate,
            year: year,
            runtime: data.runtime || (data.episode_run_time && data.episode_run_time[0]) || null,
            genres: genres,
            backdrop: backdrop,
            poster: poster,
            trailer: trailerKey,
            cast: cast,
            seasons: seasonsInfo,
            number_of_seasons: data.number_of_seasons || (seasonsInfo.length),
            number_of_episodes: data.number_of_episodes || 0,
            similar: ((data.similar && data.similar.results) || []).map(i => this.formatItem(i, mediaType)),
            is_digital_hd: qualityInfo.is_digital_hd,
            is_cam: qualityInfo.is_cam,
            quality_label: qualityInfo.quality_label,
            quality_badge_class: qualityInfo.quality_badge_class
        };
    }

    async getSeasonEpisodes(tmdbId, seasonNumber) {
        const data = await this.fetchTmdb(`/tv/${tmdbId}/season/${seasonNumber}`);
        if (!data || !data.episodes) return [];
        return data.episodes.map(ep => ({
            episode_number: ep.episode_number,
            title: ep.name || `Epizoda ${ep.episode_number}`,
            overview: ep.overview || '',
            still_path: ep.still_path ? `${TMDB_IMAGE_BASE}/w500${ep.still_path}` : null,
            air_date: ep.air_date,
            rating: ep.vote_average ? Math.round(ep.vote_average * 10) / 10 : 0,
            runtime: ep.runtime
        }));
    }
}

/**
 * Client-Side Stream Resolver
 * Generates stream provider URLs directly in browser without server dependency.
 */
class ClientStreamResolver {
    getStreams(mediaType, tmdbId, imdbId = null, season = null, episode = null) {
        const isTv = (mediaType === 'tv');
        const s = season || 1;
        const e = episode || 1;
        const sources = [];

        // 1. VidSrc ME (#1 Glavni Privzeti Server - 1080p Full HD brez podnapisov)
        const vidsrcMeUrl = isTv
            ? `https://vidsrcme.ru/embed/tv?tmdb=${tmdbId}&season=${s}&episode=${e}&sub=0&autoplay=1&quality=1080&res=1080`
            : `https://vidsrcme.ru/embed/movie?tmdb=${tmdbId}&sub=0&autoplay=1&quality=1080&res=1080`;
        sources.push({
            id: "vidsrc_me",
            name: "VidSrc ME (Glavni 1080p)",
            provider_type: "free",
            quality: "1080p",
            quality_badge: "FREE 1080p",
            url: vidsrcMeUrl,
            is_embed: true,
            priority_score: 100,
            description: "Glavni 1080p Full HD strežnik z instantnim samodejnim predvajanjem brez podnapisov"
        });

        // 2. VidLink Pro
        const vidlinkUrl = isTv
            ? `https://vidlink.pro/tv/${tmdbId}/${s}/${e}?primaryColor=00e5ff&autoplay=true&sub=0&defaultQuality=1080&resolution=1080p&quality=1080`
            : `https://vidlink.pro/movie/${tmdbId}?primaryColor=00e5ff&autoplay=true&sub=0&defaultQuality=1080&resolution=1080p&quality=1080`;
        sources.push({
            id: "vidlink",
            name: "VidLink Pro (Optimalni 1080p HD)",
            provider_type: "free",
            quality: "1080p",
            quality_badge: "FREE 1080p",
            url: vidlinkUrl,
            is_embed: true,
            priority_score: 95,
            description: "Hitri alternativni 1080p strežnik"
        });

        // 3. VidSrc IN
        const vidsrcInUrl = isTv
            ? `https://vidsrc.in/embed/tv/${tmdbId}/${s}/${e}?sub=0&autoplay=1&quality=1080`
            : `https://vidsrc.in/embed/movie/${tmdbId}?sub=0&autoplay=1&quality=1080`;
        sources.push({
            id: "vidsrc_in",
            name: "VidSrc IN (Cloud CDN 1080p)",
            provider_type: "free",
            quality: "1080p",
            quality_badge: "FREE 1080p",
            url: vidsrcInUrl,
            is_embed: true,
            priority_score: 93,
            description: "Hitro globalno omrežje strežnikov"
        });

        // 4. VidSrc PM
        const vidsrcPmUrl = isTv
            ? `https://vidsrc.pm/embed/tv/${tmdbId}/${s}/${e}?sub=0&autoplay=1&quality=1080`
            : `https://vidsrc.pm/embed/movie/${tmdbId}?sub=0&autoplay=1&quality=1080`;
        sources.push({
            id: "vidsrc_pm",
            name: "VidSrc PM (1080p)",
            provider_type: "free",
            quality: "1080p",
            quality_badge: "FREE 1080p",
            url: vidsrcPmUrl,
            is_embed: true,
            priority_score: 91,
            description: "Alternativno VidSrc 1080p ogledalo"
        });

        // 5. AutoEmbed CO
        const autoembedUrl = isTv
            ? (imdbId ? `https://autoembed.co/tv/imdb/${imdbId}-${s}-${e}?quality=1080&autoplay=1` : `https://autoembed.co/tv/tmdb/${tmdbId}-${s}-${e}?quality=1080&autoplay=1`)
            : `https://autoembed.co/movie/tmdb/${tmdbId}?quality=1080&autoplay=1`;
        sources.push({
            id: "autoembed_co",
            name: "AutoEmbed (Multi-Server 1080p)",
            provider_type: "free",
            quality: "1080p",
            quality_badge: "FREE 1080p",
            url: autoembedUrl,
            is_embed: true,
            priority_score: 89,
            description: "Samodejni preklopnik med več 1080p video viri"
        });

        // 6. MultiEmbed MOV
        const multiembedUrl = isTv
            ? `https://multiembed.mov/?video_id=${tmdbId}&tmdb=1&s=${s}&e=${e}&quality=1080&autoplay=1`
            : `https://multiembed.mov/?video_id=${tmdbId}&tmdb=1&quality=1080&autoplay=1`;
        sources.push({
            id: "multiembed",
            name: "MultiEmbed VIP (1080p)",
            provider_type: "free",
            quality: "1080p",
            quality_badge: "FREE 1080p",
            url: multiembedUrl,
            is_embed: true,
            priority_score: 87,
            description: "Zelo stabilen strežnik za vse vsebine v 1080p"
        });

        // 7. 111Movies
        const movies111Url = isTv
            ? `https://111movies.com/tv/${tmdbId}/${s}/${e}?quality=1080&autoplay=1`
            : `https://111movies.com/movie/${tmdbId}?quality=1080&autoplay=1`;
        sources.push({
            id: "111movies",
            name: "111Movies HD (1080p)",
            provider_type: "free",
            quality: "1080p",
            quality_badge: "FREE 1080p",
            url: movies111Url,
            is_embed: true,
            priority_score: 85,
            description: "Čisto predvajanje v 1080p ločljivosti"
        });

        // 8. 2Embed CC
        const twoembedUrl = isTv
            ? `https://www.2embed.cc/embedtv/${tmdbId}&s=${s}&e=${e}&sub=0&quality=1080`
            : `https://www.2embed.cc/embed/${tmdbId}?sub=0&quality=1080`;
        sources.push({
            id: "twoembed_cc",
            name: "2Embed CC",
            provider_type: "free",
            quality: "1080p",
            quality_badge: "FREE 1080p",
            url: twoembedUrl,
            is_embed: true,
            priority_score: 78,
            description: "Zanesljivo klasično ogledalo v 1080p"
        });

        // 9. 2Embed Skin
        const twoembedSkinUrl = isTv
            ? `https://2embed.skin/embedtv/${tmdbId}&s=${s}&e=${e}&sub=0&quality=1080`
            : `https://2embed.skin/embed/${tmdbId}?sub=0&quality=1080`;
        sources.push({
            id: "twoembed_skin",
            name: "2Embed Skin",
            provider_type: "free",
            quality: "1080p",
            quality_badge: "FREE 1080p",
            url: twoembedSkinUrl,
            is_embed: true,
            priority_score: 75,
            description: "Alternativno 2Embed 1080p ogledalo"
        });

        // 10. VidFast Pro
        const vidfastUrl = isTv
            ? `https://vidfast.pro/tv/${tmdbId}/${s}/${e}?sub=0&quality=1080`
            : `https://vidfast.pro/movie/${tmdbId}?sub=0&quality=1080`;
        sources.push({
            id: "vidfast",
            name: "VidFast Pro",
            provider_type: "free",
            quality: "1080p",
            quality_badge: "FREE 1080p",
            url: vidfastUrl,
            is_embed: true,
            priority_score: 70,
            description: "Hitro alternativno 1080p ogledalo"
        });

        return {
            recommended: sources[0],
            all_sources: sources
        };
    }
}

/**
 * TV Remote Navigation Engine
 * Provides 100% smooth D-Pad (Up/Down/Left/Right/OK/Back) spatial navigation on Philips Android TV.
 */
class TvRemoteEngine {
    constructor(app) {
        this.app = app;
        this.isTvMode = false;
        this.currentFocusElement = null;
        this.lastActiveCard = null;
        this.init();
    }

    init() {
        if (this.detectTvDevice()) {
            this.enableTvMode();
        }

        window.addEventListener('keydown', (e) => this.handleKeyDown(e), { capture: true });

        document.addEventListener('focusin', (e) => {
            if (e.target && e.target !== document.body && !e.target.classList.contains('ambient-glow')) {
                this.currentFocusElement = e.target;
            }
        });
    }

    detectTvDevice() {
        const ua = (navigator.userAgent || '').toLowerCase();
        return ua.includes('android tv') || 
               ua.includes('googletv') || 
               ua.includes('smart-tv') || 
               ua.includes('smarttv') || 
               ua.includes('philips') || 
               ua.includes('tizen') || 
               ua.includes('webos') || 
               ua.includes('tv bro') ||
               ua.includes('downloaddot') ||
               ua.includes('aftmm') ||
               window.innerWidth >= 1920;
    }

    enableTvMode() {
        this.isTvMode = true;
        document.body.classList.add('tv-mode');
        console.log('🎮 Android TV Mode Enabled for Philips TV');
    }

    handleKeyDown(e) {
        const key = e.key;
        const code = e.keyCode || e.which;

        // D-Pad or Remote keys activate TV mode visual indicator
        if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Enter', 'Select', 'Escape', 'Back'].includes(key) || 
            [19, 20, 21, 22, 23, 66, 4, 85, 126, 127, 183, 184, 185, 186].includes(code)) {
            if (!this.isTvMode) this.enableTvMode();
            document.body.classList.add('tv-remote-active');
        }

        const tvSearchInput = document.getElementById('tvSearchInput');
        const activeSearch = (document.activeElement === tvSearchInput) ? tvSearchInput : null;

        if (activeSearch) {
            if (key === 'ArrowUp' || code === 19) {
                e.preventDefault();
                this.app.hideKeyboard(); // Native + JS Dismiss virtual keyboard on TV
                const searchTab = document.querySelector('.nav-btn[data-tab="search"]') || document.querySelector('.nav-btn.active') || document.querySelector('.nav-btn');
                if (searchTab) this.setFocus(searchTab);
                return;
            } else if (key === 'ArrowLeft' || code === 21) {
                e.preventDefault();
                this.app.hideKeyboard();
                const tvTab = document.querySelector('.nav-btn[data-tab="tv"]') || document.querySelector('.nav-btn[data-tab="movies"]') || document.querySelector('.nav-btn');
                if (tvTab) this.setFocus(tvTab);
                return;
            } else if (key === 'ArrowRight' || code === 22) {
                e.preventDefault();
                this.app.hideKeyboard();
                const clearBtn = document.getElementById('tvSearchClearBtn');
                const closeBtn = document.getElementById('tvSearchCloseBtn');
                if (clearBtn && clearBtn.offsetParent !== null && clearBtn.style.display !== 'none') {
                    this.setFocus(clearBtn);
                } else if (closeBtn && closeBtn.offsetParent !== null && closeBtn.style.display !== 'none') {
                    this.setFocus(closeBtn);
                } else {
                    const settingsBtn = document.getElementById('settingsBtn');
                    if (settingsBtn) this.setFocus(settingsBtn);
                }
                return;
            } else if (key === 'ArrowDown' || code === 20) {
                e.preventDefault();
                this.app.hideKeyboard(); // Native + JS Dismiss virtual keyboard on TV
                const firstLivePill = document.querySelector('#tvLiveMatchesBar .tv-live-match-pill');
                const firstChip = document.querySelector('.tv-search-chip.active') || document.querySelector('.tv-search-chip');
                const firstCard = document.querySelector('#searchResultsGrid .media-card');
                
                if (firstLivePill && firstLivePill.offsetParent !== null) {
                    this.setFocus(firstLivePill);
                } else if (firstChip && firstChip.offsetParent !== null) {
                    this.setFocus(firstChip);
                } else if (firstCard) {
                    this.setFocus(firstCard);
                } else {
                    this.navigateSpatial('down');
                }
                return;
            } else if (key === 'Enter' || code === 13 || code === 66 || code === 23) {
                e.preventDefault();
                this.app.hideKeyboard();
                const q = activeSearch.value.trim();
                this.app.performSearch(q);
                setTimeout(() => {
                    const firstLivePill = document.querySelector('#tvLiveMatchesBar .tv-live-match-pill');
                    const firstCard = document.querySelector('#searchResultsGrid .media-card');
                    if (firstLivePill && firstLivePill.offsetParent !== null) {
                        this.setFocus(firstLivePill);
                    } else if (firstCard) {
                        this.setFocus(firstCard);
                    }
                }, 150);
                return;
            } else if (key === 'Escape' || code === 4) {
                e.preventDefault();
                this.app.closeSearch();
                return;
            }
            return;
        }

        // Remote Back button (Escape, Back, BrowserBack, Android keycode 4)
        if (key === 'Escape' || key === 'Back' || key === 'BrowserBack' || code === 4) {
            e.preventDefault();
            e.stopPropagation();
            this.handleBackAction();
            return;
        }

        // Remote Stop button (⏹ / code 86)
        if (key === 'MediaStop' || code === 86) {
            e.preventDefault();
            this.handleStop();
            return;
        }

        // Remote Play/Pause button (⏸ / ▶ / code 85, 126, 127)
        if (key === 'MediaPlayPause' || key === 'MediaPlay' || key === 'MediaPause' || code === 85 || code === 126 || code === 127) {
            e.preventDefault();
            this.handlePlayPause();
            return;
        }

        // Remote Record / Favorite button (⏺ / code 130, 183 / 🔴 Rdeča tipka)
        if (key === 'MediaRecord' || code === 130 || code === 183 || key === 'ColorF0Red') {
            e.preventDefault();
            this.handleRecordOrFavorite();
            return;
        }

        // 🟢 Zelena tipka na daljincu -> Takojšnje iskanje
        if (code === 184 || key === 'ColorF1Green') {
            e.preventDefault();
            this.handleSearchTrigger();
            return;
        }

        // 🟡 Rumena tipka na daljincu -> Menjava strežnika
        if (code === 185 || key === 'ColorF2Yellow') {
            e.preventDefault();
            this.handleCycleServer();
            return;
        }

        // 🔵 Modra tipka na daljincu -> Razmerje slike 16:9
        if (code === 186 || key === 'ColorF3Blue') {
            e.preventDefault();
            this.handleToggleAspectRatio();
            return;
        }

        // Remote Rewind / Prev button (▲▲ / code 88, 89, 273, 92)
        if (key === 'MediaRewind' || key === 'MediaTrackPrevious' || code === 88 || code === 89 || code === 273 || code === 92) {
            e.preventDefault();
            this.handlePrevSeasonOrSeek();
            return;
        }

        // Remote Fast Forward / Next button (▼▼ / code 87, 90, 272, 93)
        if (key === 'MediaFastForward' || key === 'MediaTrackNext' || code === 87 || code === 90 || code === 272 || code === 93) {
            e.preventDefault();
            this.handleNextSeasonOrSeek();
            return;
        }

        // Remote List / Guide button (LIST / TV GUIDE / code 172, 174, 171, 82)
        if (key === 'Guide' || key === 'TVGuide' || code === 172 || code === 174 || code === 171 || code === 82) {
            e.preventDefault();
            this.handleListGuide();
            return;
        }

        // D-Pad Directional Navigation
        const playerOverlay = document.getElementById('playerOverlay');
        if (playerOverlay && playerOverlay.style.display !== 'none') {
            this.app.resetPlayerHudTimer();

            // V predvajalniku:
            // LEVO / DESNO: previjanje nazaj / naprej (-10s / +10s) za daljinec in krmilnik!
            if (key === 'ArrowRight' || code === 22) {
                e.preventDefault();
                this.handleSeekForward();
                return;
            } else if (key === 'ArrowLeft' || code === 21) {
                e.preventDefault();
                this.handleSeekBackward();
                return;
            } else if (key === 'ArrowUp' || code === 19) {
                // GOR: prikaže orodno vrstico in fokusira gumb Nazaj / Razmerje
                e.preventDefault();
                const backBtn = playerOverlay.querySelector('.btn-player-back') || playerOverlay.querySelector('button');
                if (backBtn) this.setFocus(backBtn);
                return;
            } else if (key === 'ArrowDown' || code === 20) {
                // DOL: fokusira video vsebnik za čisto celozaslonsko sliko
                e.preventDefault();
                const videoCont = document.getElementById('playerVideoContainer');
                if (videoCont) this.setFocus(videoCont);
                return;
            }
        }

        // Search Section navigation
        const searchSection = document.getElementById('searchSection');
        if (searchSection && searchSection.style.display !== 'none') {
            if (key === 'ArrowUp' || code === 19) {
                if (this.currentFocusElement && this.currentFocusElement.classList.contains('media-card')) {
                    const cards = Array.from(document.querySelectorAll('#searchResultsGrid .media-card'));
                    const idx = cards.indexOf(this.currentFocusElement);
                    if (idx < 6) { // Top row of search results -> jump to chips or live pills or search input
                        e.preventDefault();
                        const activeChip = document.querySelector('.tv-search-chip.active') || document.querySelector('.tv-search-chip');
                        const livePill = document.querySelector('.tv-live-match-pill');
                        if (activeChip && activeChip.offsetParent !== null) {
                            this.setFocus(activeChip);
                        } else if (livePill && livePill.offsetParent !== null) {
                            this.setFocus(livePill);
                        } else if (tvSearchInput) {
                            this.setFocus(tvSearchInput);
                        }
                        return;
                    }
                } else if (this.currentFocusElement && this.currentFocusElement.classList.contains('tv-search-chip')) {
                    e.preventDefault();
                    const livePill = document.querySelector('.tv-live-match-pill');
                    if (livePill && livePill.offsetParent !== null) {
                        this.setFocus(livePill);
                    } else if (tvSearchInput) {
                        this.setFocus(tvSearchInput);
                    } else {
                        const navBtn = document.querySelector('.nav-btn[data-tab="search"]') || document.querySelector('.nav-btn.active') || document.querySelector('.nav-btn');
                        if (navBtn) this.setFocus(navBtn);
                    }
                    return;
                } else if (this.currentFocusElement && this.currentFocusElement.classList.contains('tv-live-match-pill')) {
                    e.preventDefault();
                    if (tvSearchInput) {
                        this.setFocus(tvSearchInput);
                    }
                    return;
                } else if (this.currentFocusElement && (this.currentFocusElement.id === 'tvSearchClearBtn' || this.currentFocusElement.id === 'tvSearchCloseBtn')) {
                    e.preventDefault();
                    const activeNav = document.querySelector('.nav-btn[data-tab="search"]') || document.querySelector('.nav-btn.active') || document.querySelector('.nav-btn');
                    if (activeNav) this.setFocus(activeNav);
                    return;
                }
            } else if (key === 'ArrowDown' || code === 20) {
                if (this.currentFocusElement && this.currentFocusElement.classList.contains('tv-live-match-pill')) {
                    e.preventDefault();
                    const activeChip = document.querySelector('.tv-search-chip.active') || document.querySelector('.tv-search-chip');
                    const firstCard = document.querySelector('#searchResultsGrid .media-card');
                    if (activeChip && activeChip.offsetParent !== null) {
                        this.setFocus(activeChip);
                    } else if (firstCard) {
                        this.setFocus(firstCard);
                    }
                    return;
                } else if (this.currentFocusElement && this.currentFocusElement.classList.contains('tv-search-chip')) {
                    e.preventDefault();
                    const firstCard = document.querySelector('#searchResultsGrid .media-card');
                    if (firstCard) {
                        this.setFocus(firstCard);
                    }
                    return;
                } else if (this.currentFocusElement && (this.currentFocusElement.id === 'tvSearchClearBtn' || this.currentFocusElement.id === 'tvSearchCloseBtn')) {
                    e.preventDefault();
                    const firstChip = document.querySelector('.tv-search-chip.active') || document.querySelector('.tv-search-chip');
                    if (firstChip) this.setFocus(firstChip);
                    return;
                }
            } else if (key === 'ArrowLeft' || code === 21) {
                if (this.currentFocusElement && this.currentFocusElement.classList.contains('tv-search-chip')) {
                    const prev = this.currentFocusElement.previousElementSibling;
                    if (prev && prev.classList.contains('tv-search-chip')) {
                        e.preventDefault();
                        this.setFocus(prev);
                        return;
                    }
                } else if (this.currentFocusElement && this.currentFocusElement.id === 'tvSearchCloseBtn') {
                    e.preventDefault();
                    const clearBtn = document.getElementById('tvSearchClearBtn');
                    if (clearBtn && clearBtn.offsetParent !== null && clearBtn.style.display !== 'none') {
                        this.setFocus(clearBtn);
                    } else if (tvSearchInput) {
                        this.setFocus(tvSearchInput);
                    }
                    return;
                } else if (this.currentFocusElement && this.currentFocusElement.id === 'tvSearchClearBtn') {
                    e.preventDefault();
                    if (tvSearchInput) this.setFocus(tvSearchInput);
                    return;
                }
            } else if (key === 'ArrowRight' || code === 22) {
                if (this.currentFocusElement && this.currentFocusElement.classList.contains('tv-search-chip')) {
                    const next = this.currentFocusElement.nextElementSibling;
                    if (next && next.classList.contains('tv-search-chip')) {
                        e.preventDefault();
                        this.setFocus(next);
                        return;
                    }
                } else if (this.currentFocusElement && this.currentFocusElement.id === 'tvSearchClearBtn') {
                    e.preventDefault();
                    const closeBtn = document.getElementById('tvSearchCloseBtn');
                    if (closeBtn && closeBtn.offsetParent !== null && closeBtn.style.display !== 'none') {
                        this.setFocus(closeBtn);
                    }
                    return;
                } else if (this.currentFocusElement && this.currentFocusElement.id === 'tvSearchCloseBtn') {
                    e.preventDefault();
                    const settingsBtn = document.getElementById('settingsBtn');
                    if (settingsBtn) this.setFocus(settingsBtn);
                    return;
                }
            }
        }

        // Nav button / Genre chip adjacent handling
        if (this.currentFocusElement && this.currentFocusElement.classList.contains('nav-btn')) {
            if (key === 'ArrowLeft' || code === 21) {
                const prev = this.currentFocusElement.previousElementSibling;
                if (prev && prev.classList.contains('nav-btn')) {
                    e.preventDefault();
                    this.setFocus(prev);
                    return;
                } else {
                    const brand = document.getElementById('brandLogo');
                    if (brand) { e.preventDefault(); this.setFocus(brand); return; }
                }
            } else if (key === 'ArrowRight' || code === 22) {
                const next = this.currentFocusElement.nextElementSibling;
                if (next && next.classList.contains('nav-btn')) {
                    e.preventDefault();
                    this.setFocus(next);
                    return;
                } else {
                    const sBox = document.getElementById('searchBox');
                    const setBtn = document.getElementById('settingsBtn');
                    if (sBox) { e.preventDefault(); this.setFocus(sBox); return; }
                    else if (setBtn) { e.preventDefault(); this.setFocus(setBtn); return; }
                }
            } else if (key === 'ArrowDown' || code === 20) {
                e.preventDefault();
                if (searchSection && searchSection.style.display !== 'none') {
                    if (tvSearchInput) { this.setFocus(tvSearchInput); return; }
                }
                const activeGenre = document.querySelector('.genre-chip.active') || document.querySelector('.genre-chip');
                const firstCard = document.querySelector('#mediaGrid .media-card');
                if (activeGenre && activeGenre.offsetParent !== null) {
                    this.setFocus(activeGenre);
                    return;
                } else if (firstCard) {
                    this.setFocus(firstCard);
                    return;
                }
            }
        } else if (this.currentFocusElement && this.currentFocusElement.classList.contains('genre-chip')) {
            if (key === 'ArrowLeft' || code === 21) {
                const prev = this.currentFocusElement.previousElementSibling;
                if (prev && prev.classList.contains('genre-chip')) {
                    e.preventDefault();
                    this.setFocus(prev);
                    return;
                }
            } else if (key === 'ArrowRight' || code === 22) {
                const next = this.currentFocusElement.nextElementSibling;
                if (next && next.classList.contains('genre-chip')) {
                    e.preventDefault();
                    this.setFocus(next);
                    return;
                }
            } else if (key === 'ArrowUp' || code === 19) {
                e.preventDefault();
                const activeNav = document.querySelector('.nav-btn.active') || document.querySelector('.nav-btn');
                if (activeNav) {
                    this.setFocus(activeNav);
                    return;
                }
            } else if (key === 'ArrowDown' || code === 20) {
                e.preventDefault();
                const firstCard = document.querySelector('#mediaGrid .media-card');
                if (firstCard) {
                    this.setFocus(firstCard);
                    return;
                }
            }
        } else if (this.currentFocusElement && this.currentFocusElement.classList.contains('media-card')) {
            if (key === 'ArrowUp' || code === 19) {
                const cards = Array.from(document.querySelectorAll('#mediaGrid .media-card'));
                const idx = cards.indexOf(this.currentFocusElement);
                if (idx >= 0 && idx < 6) { // Top row of feed grid
                    e.preventDefault();
                    const activeGenre = document.querySelector('.genre-chip.active') || document.querySelector('.genre-chip');
                    const activeNav = document.querySelector('.nav-btn.active') || document.querySelector('.nav-btn');
                    if (activeGenre && activeGenre.offsetParent !== null) {
                        this.setFocus(activeGenre);
                    } else if (activeNav) {
                        this.setFocus(activeNav);
                    }
                    return;
                }
            }
        }

        if (key === 'ArrowUp' || code === 19) {
            e.preventDefault();
            this.navigateSpatial('up');
        } else if (key === 'ArrowDown' || code === 20) {
            e.preventDefault();
            this.navigateSpatial('down');
        } else if (key === 'ArrowLeft' || code === 21) {
            e.preventDefault();
            this.navigateSpatial('left');
        } else if (key === 'ArrowRight' || code === 22) {
            e.preventDefault();
            this.navigateSpatial('right');
        } else if (key === 'Enter' || key === 'Select' || code === 23 || code === 66 || key === ' ') {
            if (playerOverlay && playerOverlay.style.display !== 'none') {
                if (this.currentFocusElement && this.currentFocusElement !== document.body && this.currentFocusElement !== playerOverlay && this.currentFocusElement.tagName === 'BUTTON') {
                    e.preventDefault();
                    this.currentFocusElement.click();
                    return;
                }
                e.preventDefault();
                this.handlePlayPause();
                return;
            }
            if (this.currentFocusElement && this.currentFocusElement !== document.body) {
                if (this.currentFocusElement.tagName !== 'INPUT' || this.currentFocusElement.type === 'button') {
                    e.preventDefault();
                    this.currentFocusElement.click();
                }
            }
        }
    }

    handleStop() {
        const playerOverlay = document.getElementById('playerOverlay');
        if (playerOverlay && playerOverlay.style.display !== 'none') {
            this.app.closePlayer();
            this.app.showToast('⏹ Predvajanje ustavljeno');
            return true;
        }
        const detailsModal = document.getElementById('detailsModal');
        if (detailsModal && detailsModal.classList.contains('active')) {
            this.app.closeModal('detailsModal');
            return true;
        }
        return false;
    }

    handleRecordOrFavorite() {
        if (this.app.activeDetailsItem) {
            this.app.toggleFavorite(this.app.activeDetailsItem);
            return true;
        }
        if (this.currentFocusElement && this.currentFocusElement.dataset && this.currentFocusElement.dataset.id) {
            const id = parseInt(this.currentFocusElement.dataset.id);
            const item = (this.app.currentItems || []).find(x => x.id === id);
            if (item) {
                this.app.toggleFavorite(item);
                return true;
            }
        }
        this.app.setTab('watchlist');
        this.app.showToast('⭐ Moja Lista / Priljubljeni');
        return true;
    }

    handleSeekBackward() {
        const playerOverlay = document.getElementById('playerOverlay');
        if (playerOverlay && playerOverlay.style.display !== 'none') {
            const videoEl = playerOverlay.querySelector('video');
            if (videoEl) {
                videoEl.currentTime = Math.max(0, videoEl.currentTime - 10);
            }
            const iframe = document.getElementById('activeStreamIframe');
            if (iframe && iframe.contentWindow) {
                try {
                    iframe.contentWindow.postMessage({ type: 'seek', offset: -10 }, '*');
                } catch(e) {}
            }
            this.app.showToast('⏪ -10s Nazaj');
            return true;
        }
        if (this.app.activeDetailsItem && this.app.activeDetailsItem.media_type === 'tv' && this.app.activeEpisode > 1) {
            this.app.playEpisode(this.app.activeSeason, this.app.activeEpisode - 1);
            return true;
        }
        window.scrollBy({ top: -window.innerHeight * 0.7, behavior: 'smooth' });
        return true;
    }

    handleSeekForward() {
        const playerOverlay = document.getElementById('playerOverlay');
        if (playerOverlay && playerOverlay.style.display !== 'none') {
            const videoEl = playerOverlay.querySelector('video');
            if (videoEl) {
                videoEl.currentTime += 10;
            }
            const iframe = document.getElementById('activeStreamIframe');
            if (iframe && iframe.contentWindow) {
                try {
                    iframe.contentWindow.postMessage({ type: 'seek', offset: 10 }, '*');
                } catch(e) {}
            }
            this.app.showToast('⏩ +10s Naprej');
            return true;
        }
        if (this.app.activeDetailsItem && this.app.activeDetailsItem.media_type === 'tv') {
            this.app.playEpisode(this.app.activeSeason, this.app.activeEpisode + 1);
            return true;
        }
        window.scrollBy({ top: window.innerHeight * 0.7, behavior: 'smooth' });
        return true;
    }

    handleSearchTrigger() {
        const playerOverlay = document.getElementById('playerOverlay');
        if (playerOverlay && playerOverlay.style.display !== 'none') {
            this.app.closePlayer();
        }
        const detailsModal = document.getElementById('detailsModal');
        if (detailsModal && detailsModal.classList.contains('active')) {
            this.app.closeModal('detailsModal');
        }

        this.app.openSearch();
        return true;
    }

    handleSeekBigBackward() {
        const playerOverlay = document.getElementById('playerOverlay');
        if (playerOverlay && playerOverlay.style.display !== 'none') {
            const videoEl = playerOverlay.querySelector('video');
            if (videoEl) {
                videoEl.currentTime = Math.max(0, videoEl.currentTime - 30);
            }
            const iframe = document.getElementById('activeStreamIframe');
            if (iframe && iframe.contentWindow) {
                try { iframe.contentWindow.postMessage({ type: 'seek', offset: -30 }, '*'); } catch(e) {}
            }
            this.app.showToast('⏮ -30s Nazaj (L2)');
            return true;
        }
        window.scrollBy({ top: -window.innerHeight * 0.85, behavior: 'smooth' });
        return true;
    }

    handleSeekBigForward() {
        const playerOverlay = document.getElementById('playerOverlay');
        if (playerOverlay && playerOverlay.style.display !== 'none') {
            const videoEl = playerOverlay.querySelector('video');
            if (videoEl) {
                videoEl.currentTime += 30;
            }
            const iframe = document.getElementById('activeStreamIframe');
            if (iframe && iframe.contentWindow) {
                try { iframe.contentWindow.postMessage({ type: 'seek', offset: 30 }, '*'); } catch(e) {}
            }
            this.app.showToast('⏭ +30s Naprej (R2)');
            return true;
        }
        window.scrollBy({ top: window.innerHeight * 0.85, behavior: 'smooth' });
        return true;
    }

    handleToggleAspectRatio() {
        const playerOverlay = document.getElementById('playerOverlay');
        if (playerOverlay && playerOverlay.style.display !== 'none') {
            const aspectBtn = document.getElementById('playerAspectBtn') || document.querySelector('.aspect-btn');
            if (aspectBtn) {
                aspectBtn.click();
            } else if (this.app.toggleAspectRatio) {
                this.app.toggleAspectRatio();
            }
            this.app.showToast('📐 Razmerje slike (16:9 / Full)');
            return true;
        }
        return false;
    }

    handleCycleServer() {
        const playerOverlay = document.getElementById('playerOverlay');
        if (playerOverlay && playerOverlay.style.display !== 'none') {
            const pills = Array.from(document.querySelectorAll('#playerServerPills .server-pill-btn'));
            if (pills.length > 0) {
                let activeIdx = pills.findIndex(p => p.classList.contains('active'));
                if (activeIdx === -1) activeIdx = 0;
                const nextIdx = (activeIdx + 1) % pills.length;
                pills[nextIdx].click();
                this.app.showToast(`🔄 Preklop strežnika: ${pills[nextIdx].textContent || ''}`);
                return true;
            }
        }
        return false;
    }

    handleNextServer() {
        const playerOverlay = document.getElementById('playerOverlay');
        if (playerOverlay && playerOverlay.style.display !== 'none') {
            const pills = Array.from(document.querySelectorAll('#playerServerPills .server-pill-btn'));
            if (pills.length > 0) {
                let activeIdx = pills.findIndex(p => p.classList.contains('active'));
                if (activeIdx === -1) activeIdx = 0;
                const nextIdx = (activeIdx + 1) % pills.length;
                pills[nextIdx].click();
                this.app.showToast(`🔄 ${pills[nextIdx].textContent || 'Naslednji strežnik'}`);
                return true;
            }
        }
        return false;
    }

    handlePrevServer() {
        const playerOverlay = document.getElementById('playerOverlay');
        if (playerOverlay && playerOverlay.style.display !== 'none') {
            const pills = Array.from(document.querySelectorAll('#playerServerPills .server-pill-btn'));
            if (pills.length > 0) {
                let activeIdx = pills.findIndex(p => p.classList.contains('active'));
                if (activeIdx === -1) activeIdx = 0;
                const prevIdx = (activeIdx - 1 + pills.length) % pills.length;
                pills[prevIdx].click();
                this.app.showToast(`🔄 ${pills[prevIdx].textContent || 'Prejšnji strežnik'}`);
                return true;
            }
        }
        return false;
    }

    handlePrevSeason() {
        const detailsModal = document.getElementById('detailsModal');
        if (detailsModal && detailsModal.classList.contains('active') && this.app.activeDetailsItem && this.app.activeDetailsItem.media_type === 'tv') {
            const pills = Array.from(document.querySelectorAll('#seasonPillsBar .season-pill'));
            if (pills.length > 0) {
                const activeIdx = pills.findIndex(p => p.classList.contains('active'));
                if (activeIdx > 0) {
                    pills[activeIdx - 1].click();
                    this.app.showToast(`⏮ ${pills[activeIdx - 1].textContent || 'Prejšnja sezona'}`);
                    return true;
                }
            }
        }
        return false;
    }

    handleNextSeason() {
        const detailsModal = document.getElementById('detailsModal');
        if (detailsModal && detailsModal.classList.contains('active') && this.app.activeDetailsItem && this.app.activeDetailsItem.media_type === 'tv') {
            const pills = Array.from(document.querySelectorAll('#seasonPillsBar .season-pill'));
            if (pills.length > 0) {
                const activeIdx = pills.findIndex(p => p.classList.contains('active'));
                if (activeIdx >= 0 && activeIdx < pills.length - 1) {
                    pills[activeIdx + 1].click();
                    this.app.showToast(`⏭ ${pills[activeIdx + 1].textContent || 'Naslednja sezona'}`);
                    return true;
                }
            }
        }
        return false;
    }

    handlePrevSeasonOrSeek() {
        const playerOverlay = document.getElementById('playerOverlay');
        if (playerOverlay && playerOverlay.style.display !== 'none') {
            if (this.app.activeDetailsItem && this.app.activeDetailsItem.media_type === 'tv' && this.app.activeEpisode > 1) {
                this.app.playEpisode(this.app.activeSeason, this.app.activeEpisode - 1);
                return true;
            }
            return this.handleSeekBackward();
        }
        const detailsModal = document.getElementById('detailsModal');
        if (detailsModal && detailsModal.classList.contains('active')) {
            if (this.handlePrevSeason()) return true;
        }
        return this.handleSeekBackward();
    }

    handleNextSeasonOrSeek() {
        const playerOverlay = document.getElementById('playerOverlay');
        if (playerOverlay && playerOverlay.style.display !== 'none') {
            if (this.app.activeDetailsItem && this.app.activeDetailsItem.media_type === 'tv') {
                this.app.playEpisode(this.app.activeSeason, this.app.activeEpisode + 1);
                return true;
            }
            return this.handleSeekForward();
        }
        const detailsModal = document.getElementById('detailsModal');
        if (detailsModal && detailsModal.classList.contains('active')) {
            if (this.handleNextSeason()) return true;
        }
        return this.handleSeekForward();
    }

    handleAnalogScroll(deltaY) {
        const playerOverlay = document.getElementById('playerOverlay');
        if (playerOverlay && playerOverlay.style.display !== 'none') {
            return;
        }

        // 1. Če smo v opisu serije ali filma (detailsModal)
        const detailsModal = document.getElementById('detailsModal');
        if (detailsModal && detailsModal.classList.contains('active')) {
            const scrollableBody = detailsModal.querySelector('.details-scrollable-content');
            if (scrollableBody) {
                scrollableBody.scrollTop += deltaY * 1.5;
            }
            detailsModal.scrollTop += deltaY * 1.5;

            if (this.scrollDebounceTimer) clearTimeout(this.scrollDebounceTimer);
            this.scrollDebounceTimer = setTimeout(() => {
                this.syncFocusToCenter();
            }, 60);
            return;
        }

        // 2. Glavni katalog: zvezno in masleno-gladko 60fps drsenje strani
        window.scrollBy({
            top: deltaY * 1.3,
            left: 0,
            behavior: 'auto'
        });

        // Samodejno pripne fokus na kartico na sredini zaslona brez prekinitve drsenja
        if (this.scrollDebounceTimer) clearTimeout(this.scrollDebounceTimer);
        this.scrollDebounceTimer = setTimeout(() => {
            this.syncFocusToCenter();
        }, 60);
    }

    syncFocusToCenter() {
        const detailsModal = document.getElementById('detailsModal');
        if (detailsModal && detailsModal.classList.contains('active')) {
            const epCards = Array.from(detailsModal.querySelectorAll('.episode-card, .btn-vidbox-play, .season-pill'));
            const centerY = window.innerHeight / 2;
            let closest = null;
            let minDiff = Infinity;
            epCards.forEach(c => {
                const rect = c.getBoundingClientRect();
                if (rect.height > 0 && rect.top >= 20 && rect.bottom <= window.innerHeight - 20) {
                    const cardCenter = rect.top + rect.height / 2;
                    const diff = Math.abs(cardCenter - centerY);
                    if (diff < minDiff) {
                        minDiff = diff;
                        closest = c;
                    }
                }
            });
            if (closest && closest !== this.currentFocusElement) {
                this.setFocus(closest, false);
            }
            return;
        }

        const cards = Array.from(document.querySelectorAll('.media-card, .compact-continue-card, .genre-chip, .tv-search-chip'));
        if (cards.length === 0) return;
        const centerY = window.innerHeight / 2;
        let closest = null;
        let minDiff = Infinity;
        cards.forEach(c => {
            const rect = c.getBoundingClientRect();
            if (rect.top >= -50 && rect.bottom <= window.innerHeight + 50) {
                const cardCenter = rect.top + rect.height / 2;
                const diff = Math.abs(cardCenter - centerY);
                if (diff < minDiff) {
                    minDiff = diff;
                    closest = c;
                }
            }
        });
        if (closest && closest !== this.currentFocusElement) {
            this.setFocus(closest, false);
        }
    }

    handleListGuide() {
        const playerOverlay = document.getElementById('playerOverlay');
        if (playerOverlay && playerOverlay.style.display !== 'none') {
            this.app.closePlayer();
        }
        const detailsModal = document.getElementById('detailsModal');
        if (detailsModal && detailsModal.classList.contains('active')) {
            this.app.closeModal('detailsModal');
        }

        if (this.app.currentMediaType === 'movie') {
            this.app.switchMediaType('tv');
            this.app.showToast('📺 Serije');
        } else {
            this.app.switchMediaType('movie');
            this.app.showToast('🎬 Filmi');
        }
        return true;
    }

    handleBackAction() {
        const now = Date.now();
        const isDoubleBack = (this.lastBackTime && (now - this.lastBackTime) < 550);
        this.lastBackTime = now;

        const playerOverlay = document.getElementById('playerOverlay');
        const detailsModal = document.getElementById('detailsModal');
        const settingsModal = document.getElementById('settingsModal');
        const searchSection = document.getElementById('searchSection');
        const searchDropdown = document.getElementById('searchDropdown');
        const tvSearchInput = document.getElementById('tvSearchInput');

        if (isDoubleBack) {
            // Dva hitra klika za nazaj: takojšnja vrnitev na začetni meni aplikacije (Domov)
            if (playerOverlay && playerOverlay.style.display !== 'none') {
                this.app.closePlayer();
            }
            if (detailsModal && detailsModal.classList.contains('active')) {
                this.app.closeModal('detailsModal');
            }
            if (settingsModal && settingsModal.classList.contains('active')) {
                this.app.closeModal('settingsModal');
            }
            if (searchSection && searchSection.style.display !== 'none') {
                this.app.closeSearch();
            }
            if (searchDropdown && searchDropdown.classList.contains('active')) {
                searchDropdown.classList.remove('active');
            }
            this.app.showToast('🏠 Začetni meni');
            if (this.lastActiveCard) {
                this.setFocus(this.lastActiveCard);
            } else {
                this.focusFirstVisible();
            }
            return;
        }

        // En klik za nazaj: 1 korak nazaj v aplikaciji
        if (playerOverlay && playerOverlay.style.display !== 'none') {
            this.app.closePlayer();
            // Vrnitev na opis filma ali seznam epizod serije, če je bil odprt detailsModal
            if (detailsModal && detailsModal.classList.contains('active')) {
                if (this.app.activeDetailsItem && this.app.activeDetailsItem.media_type === 'tv') {
                    // Serija: ohrani odprt seznam epizod in fokusiraj aktivno epizodo
                    const epCard = document.getElementById(`ep-card-${this.app.activeEpisode}`) ||
                                   document.querySelector('#episodesList .episode-card.active') ||
                                   document.querySelector('#episodesList .episode-card') ||
                                   document.querySelector('#seasonPillsBar .season-pill.active');
                    if (epCard) {
                        this.setFocus(epCard);
                    } else {
                        this.setFocus(detailsModal);
                    }
                } else {
                    // Film: vrnitev na opis filma
                    const playBtn = document.getElementById('modalMainPlayBtn');
                    if (playBtn) {
                        this.setFocus(playBtn);
                    } else {
                        this.setFocus(detailsModal);
                    }
                }
            } else {
                if (this.lastActiveCard) {
                    this.setFocus(this.lastActiveCard);
                } else {
                    this.focusFirstVisible();
                }
            }
            return;
        } else if (detailsModal && detailsModal.classList.contains('active')) {
            // En korak nazaj iz opisa filma / serije -> zapri podrobnosti in fokusiraj kartico v katalogu
            this.app.closeModal('detailsModal');
            if (this.lastActiveCard) {
                this.setFocus(this.lastActiveCard);
            } else {
                this.focusFirstVisible();
            }
        } else if (searchSection && searchSection.style.display !== 'none') {
            if (tvSearchInput && tvSearchInput.value.trim() !== '') {
                this.app.clearSearch();
            } else {
                this.app.closeSearch();
            }
            return;
        } else if (settingsModal && settingsModal.classList.contains('active')) {
            this.app.closeModal('settingsModal');
            this.focusFirstVisible();
            return;
        } else if (searchDropdown && searchDropdown.classList.contains('active')) {
            searchDropdown.classList.remove('active');
            if (searchInput) {
                searchInput.value = '';
                searchInput.blur();
            }
            this.focusFirstVisible();
            return;
        } else if (document.activeElement && document.activeElement.tagName === 'INPUT') {
            document.activeElement.blur();
            const activeNav = document.querySelector('.nav-btn.active') || document.querySelector('.nav-btn');
            if (activeNav) this.setFocus(activeNav);
            return;
        } else {
            const activeNav = document.querySelector('.nav-btn.active') || document.querySelector('.nav-btn');
            if (activeNav) this.setFocus(activeNav);
        }
    }

    handleCenterKeyDirect() {
        const playerOverlay = document.getElementById('playerOverlay');
        if (playerOverlay && playerOverlay.style.display !== 'none') {
            if (this.currentFocusElement && this.currentFocusElement !== document.body && this.currentFocusElement !== playerOverlay && this.currentFocusElement.tagName === 'BUTTON') {
                this.currentFocusElement.click();
                return;
            }
            this.handlePlayPause();
            return;
        }

        const detailsModal = document.getElementById('detailsModal');
        if (detailsModal && detailsModal.classList.contains('active')) {
            if (this.currentFocusElement && this.currentFocusElement !== document.body && this.currentFocusElement !== detailsModal) {
                this.currentFocusElement.click();
            } else {
                const playBtn = document.getElementById('modalMainPlayBtn');
                if (playBtn) playBtn.click();
            }
            return;
        }

        const searchSection = document.getElementById('searchSection');
        if (searchSection && searchSection.style.display !== 'none') {
            const tvSearchInput = document.getElementById('tvSearchInput');
            if (document.activeElement === tvSearchInput) {
                this.app.performSearch(tvSearchInput.value);
                setTimeout(() => {
                    const firstCard = document.querySelector('#searchResultsGrid .media-card');
                    if (firstCard) this.setFocus(firstCard);
                }, 250);
                return;
            }
        }

        if (this.currentFocusElement && this.currentFocusElement !== document.body) {
            this.currentFocusElement.click();
        } else {
            this.focusFirstVisible();
        }
    }

    handlePlayPause() {
        const now = Date.now();
        if (this.lastPlayPauseTime && (now - this.lastPlayPauseTime) < 320) return;
        this.lastPlayPauseTime = now;

        const playerOverlay = document.getElementById('playerOverlay');
        if (playerOverlay && playerOverlay.style.display !== 'none') {
            this.app.resetPlayerHudTimer();
            
            // 1. Če obstaja lokalni <video> element
            const videos = document.querySelectorAll('video');
            let hasHtmlVideo = false;
            videos.forEach(v => {
                try {
                    hasHtmlVideo = true;
                    if (v.paused) {
                        v.play().catch(() => {});
                        this.app.showToast('▶ Predvajanje');
                    } else {
                        v.pause();
                        this.app.showToast('⏸ Pavza');
                    }
                } catch(e) {}
            });

            // 2. Če je predvajalnik iframe (VidLink / VidSrc / AutoEmbed)
            const iframe = document.getElementById('activeStreamIframe');
            if (iframe && iframe.contentWindow) {
                try {
                    iframe.contentWindow.postMessage({ type: 'player:toggle' }, '*');
                    iframe.contentWindow.postMessage({ type: 'toggle' }, '*');
                    iframe.contentWindow.postMessage('{"event":"command","func":"togglePlay","args":""}', '*');
                } catch (e) {}
            }

            // 3. Strojni Center Tap sprožilec za 100% zanesljiv preklop vseh vgrajenih predvajalnikov
            if (!hasHtmlVideo && window.AndroidNativeBridge && window.AndroidNativeBridge.triggerCenterTap) {
                window.AndroidNativeBridge.triggerCenterTap();
            }

            this.app.showToast('⏯ Pavza / Predvajanje');
            return true;
        }
        return false;
    }

    getFocusableElements() {
        const playerOverlay = document.getElementById('playerOverlay');
        const detailsModal = document.getElementById('detailsModal');
        const settingsModal = document.getElementById('settingsModal');
        const searchSection = document.getElementById('searchSection');
        const searchDropdown = document.getElementById('searchDropdown');

        let root = document;
        if (playerOverlay && playerOverlay.style.display !== 'none') {
            root = playerOverlay;
        } else if (detailsModal && detailsModal.classList.contains('active')) {
            root = detailsModal;
        } else if (settingsModal && settingsModal.classList.contains('active')) {
            root = settingsModal;
        } else if (searchSection && searchSection.style.display !== 'none') {
            root = searchSection;
        } else if (searchDropdown && searchDropdown.classList.contains('active')) {
            root = searchDropdown;
        }

        const selector = [
            '.nav-btn',
            '.nav-search-trigger-btn',
            '.tv-live-match-pill',
            '.tv-search-chip',
            '.genre-chip',
            '.media-card',
            '.compact-continue-card',
            '.page-btn',
            '.btn',
            '.btn-vidbox-play',
            '.btn-player-back',
            '.server-pill-btn',
            '#playerVideoContainer',
            '#activeStreamIframe',
            '.season-pill',
            '.episode-card',
            '.modal-close-btn',
            '#tvSearchInput',
            '#tvSearchClearBtn',
            '#tvSearchCloseBtn',
            '#settingsBtn',
            'select',
            'input[type="checkbox"]',
            'input[type="password"]',
            'input[type="text"]'
        ].join(', ');

        const all = Array.from(root.querySelectorAll(selector));
        return all.filter(el => {
            if (el.offsetParent === null && el.style.display === 'none') return false;
            const rect = el.getBoundingClientRect();
            return rect.width > 0 && rect.height > 0 && window.getComputedStyle(el).visibility !== 'hidden';
        });
    }

    navigateSpatial(direction) {
        const focusables = this.getFocusableElements();
        if (focusables.length === 0) return;

        if (!this.currentFocusElement || !document.contains(this.currentFocusElement) || !focusables.includes(this.currentFocusElement)) {
            this.setFocus(focusables[0]);
            return;
        }

        const curRect = this.currentFocusElement.getBoundingClientRect();
        const curCenter = {
            x: curRect.left + curRect.width / 2,
            y: curRect.top + curRect.height / 2
        };

        let bestCandidate = null;
        let minScore = Infinity;

        for (const el of focusables) {
            if (el === this.currentFocusElement) continue;
            const rect = el.getBoundingClientRect();
            const center = {
                x: rect.left + rect.width / 2,
                y: rect.top + rect.height / 2
            };

            const dx = center.x - curCenter.x;
            const dy = center.y - curCenter.y;

            let isDirectionValid = false;
            let primaryDist = 0;
            let secondaryDist = 0;

            if (direction === 'up' && dy < -5) {
                isDirectionValid = true;
                primaryDist = Math.abs(dy);
                secondaryDist = Math.abs(dx);
            } else if (direction === 'down' && dy > 5) {
                isDirectionValid = true;
                primaryDist = Math.abs(dy);
                secondaryDist = Math.abs(dx);
            } else if (direction === 'left' && dx < -5) {
                isDirectionValid = true;
                primaryDist = Math.abs(dx);
                secondaryDist = Math.abs(dy);
            } else if (direction === 'right' && dx > 5) {
                isDirectionValid = true;
                primaryDist = Math.abs(dx);
                secondaryDist = Math.abs(dy);
            }

            if (isDirectionValid) {
                const score = primaryDist + (secondaryDist * 2.2);
                if (score < minScore) {
                    minScore = score;
                    bestCandidate = el;
                }
            }
        }

        if (bestCandidate) {
            this.setFocus(bestCandidate);
        }
    }

    setFocus(element, shouldScroll = true) {
        if (!element) return;
        
        if (this.currentFocusElement) {
            this.currentFocusElement.classList.remove('tv-focused');
        }

        this.currentFocusElement = element;
        element.classList.add('tv-focused');
        element.focus({ preventScroll: true });

        // Če je fokusirana kartica epizode, odstrani .active iz vseh ostalih epizod in jo dodaj izključno tej!
        if (element.classList.contains('episode-card')) {
            document.querySelectorAll('.episode-card').forEach(c => c.classList.remove('active'));
            element.classList.add('active');
        }

        if (shouldScroll) {
            element.scrollIntoView({
                behavior: 'smooth',
                block: 'nearest',
                inline: 'nearest'
            });
        }

        if (element.classList.contains('media-card')) {
            this.lastActiveCard = element;
        }
    }

    focusFirstVisible() {
        const focusables = this.getFocusableElements();
        if (focusables.length > 0) {
            this.setFocus(focusables[0]);
        }
    }
}

/**
 * Main StreamNexus Application
 */
class StreamNexusApp {
    constructor() {
        this.currentTab = 'trending';
        this.currentHeroItem = null;
        this.heroItems = [];
        this.heroIndex = 0;
        this.heroTimer = null;
        this.activeDetailsItem = null;
        this.activeSeason = 1;
        this.activeEpisode = 1;
        this.activeStreams = [];
        this.currentPlayingStream = null;
        this.settings = {};
        this.watchlist = JSON.parse(localStorage.getItem('streamnexus_watchlist') || '[]');
        this.continueWatching = JSON.parse(localStorage.getItem('streamnexus_history') || '[]');
        this.searchCache = new Map();

        // Autonomous Client-Side Engines
        this.standaloneTmdb = new StandaloneTmdbEngine();
        this.clientStreamResolver = new ClientStreamResolver();
        this.isStandaloneMode = (window.location.hostname !== '127.0.0.1' && window.location.hostname !== 'localhost') || window.location.protocol === 'file:';

        // TV Remote Navigation
        this.tvRemote = new TvRemoteEngine(this);

        this.init();
    }

    async init() {
        this.bindEvents();
        this.initAiEngine();
        await this.loadSettings();
        this.navigateHome();
        this.renderContinueWatching();
    }

    bindEvents() {
        // TV Search Input with fast debounce & real-time live echo
        const tvSearchInput = document.getElementById('tvSearchInput');
        const updateLiveQueryEcho = (val) => {
            const echoBox = document.getElementById('tvSearchLiveQueryDisplay');
            const echoText = document.getElementById('tvSearchLiveQueryText');
            const echoCount = document.getElementById('tvSearchLiveQueryCount');
            const trimmed = (val || '').trim();
            if (echoBox && echoText) {
                if (trimmed.length > 0) {
                    echoText.textContent = `"${trimmed}"`;
                    if (echoCount) {
                        const len = trimmed.length;
                        const word = len === 1 ? 'črka' : (len === 2 ? 'črki' : (len <= 4 ? 'črke' : 'črk'));
                        echoCount.textContent = `(${len} ${word})`;
                    }
                    echoBox.style.display = 'flex';
                } else {
                    echoBox.style.display = 'none';
                }
            }
        };

        let tvSearchTimeout = null;
        if (tvSearchInput) {
            tvSearchInput.addEventListener('input', (e) => {
                const query = e.target.value;
                updateLiveQueryEcho(query);
                clearTimeout(tvSearchTimeout);
                tvSearchTimeout = setTimeout(() => this.performSearch(query), 150);
            });
            tvSearchInput.addEventListener('keyup', (e) => {
                updateLiveQueryEcho(e.target.value);
            });
            tvSearchInput.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' || e.keyCode === 13) {
                    e.preventDefault();
                    clearTimeout(tvSearchTimeout);
                    updateLiveQueryEcho(tvSearchInput.value);
                    this.performSearch(tvSearchInput.value);
                    setTimeout(() => {
                        const firstCard = document.querySelector('#searchResultsGrid .media-card');
                        if (firstCard) this.tvRemote.setFocus(firstCard);
                    }, 200);
                }
            });
        }

        // Global Keyboard / Remote Shortcuts
        document.addEventListener('keydown', (e) => {
            if (e.key === '/' && document.activeElement !== tvSearchInput) {
                e.preventDefault();
                this.openSearch();
            } else if ((e.key === 'f' || e.key === 'F') && document.activeElement.tagName !== 'INPUT') {
                e.preventDefault();
                e.stopPropagation();
                if (document.getElementById('playerOverlay').style.display !== 'none') {
                    this.toggleFullscreen();
                }
            }
        });
    }

    async fetchWithFallback(endpoint, fallbackFn) {
        if (this.isStandaloneMode) {
            return await fallbackFn();
        }
        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 1800);
            const res = await fetch(endpoint, { signal: controller.signal });
            clearTimeout(timeoutId);
            if (res.ok) {
                const data = await res.json();
                return data;
            }
        } catch (e) {
            console.warn(`Local server ${endpoint} not responding, switching to Standalone Engine.`);
            this.isStandaloneMode = true;
        }
        return await fallbackFn();
    }

    async loadSettings() {
        const localSettings = JSON.parse(localStorage.getItem('streamnexus_settings') || '{}');
        
        let loaded = null;
        if (!this.isStandaloneMode) {
            try {
                const res = await fetch('/api/settings');
                if (res.ok) loaded = await res.json();
            } catch (e) {
                this.isStandaloneMode = true;
            }
        }

        this.settings = {
            real_debrid_key: localSettings.real_debrid_key || '',
            prefer_free_if_equal: true,
            default_player: 'in_app',
            subtitles_enabled: false,
            subtitle_lang: 'none'
        };

        const rdKeyEl = document.getElementById('settingRdKey');
        if (rdKeyEl) rdKeyEl.value = this.settings.real_debrid_key || '';

        const subToggle = document.getElementById('settingSubEnabled');
        if (subToggle) subToggle.checked = this.settings.subtitles_enabled === true;

        const subLang = document.getElementById('settingSubLang');
        if (subLang) subLang.value = this.settings.subtitle_lang || 'sl';

        const defPlayer = document.getElementById('settingDefaultPlayer');
        if (defPlayer) defPlayer.value = this.settings.default_player || 'in_app';
    }

    async saveSettings() {
        const subToggle = document.getElementById('settingSubEnabled');
        const subLang = document.getElementById('settingSubLang');
        const defPlayer = document.getElementById('settingDefaultPlayer');
        const rdKeyEl = document.getElementById('settingRdKey');

        const payload = {
            real_debrid_key: rdKeyEl ? rdKeyEl.value.trim() : '',
            prefer_free_if_equal: true,
            default_player: defPlayer ? defPlayer.value : 'in_app',
            subtitles_enabled: subToggle ? subToggle.checked : false,
            subtitle_lang: subLang ? subLang.value : 'sl'
        };

        this.settings = payload;
        localStorage.setItem('streamnexus_settings', JSON.stringify(payload));

        if (!this.isStandaloneMode) {
            try {
                await fetch('/api/settings', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
            } catch (e) {
                this.isStandaloneMode = true;
            }
        }

        this.closeModal('settingsModal');
        this.showToast('✅ Nastavitve shranjene (Podnapisi: ' + (payload.subtitles_enabled ? 'Vklopljeni' : 'Izklopljeni') + ', Jezik: ' + payload.subtitle_lang.toUpperCase() + ')');
        
        if (this.activeDetailsItem) {
            this.loadStreams(this.activeDetailsItem, this.activeSeason, this.activeEpisode);
        }
    }

    async setTab(tabName) {
        this.hideKeyboard();

        if (tabName === 'search') {
            await this.openSearch();
            return;
        }

        const searchSection = document.getElementById('searchSection');
        const feedSection = document.getElementById('feedSection');
        const continueSection = document.getElementById('continueSection');

        if (searchSection) searchSection.style.display = 'none';
        if (feedSection) feedSection.style.display = 'block';
        if (continueSection && this.continueWatching && this.continueWatching.length > 0) {
            continueSection.style.display = 'block';
        }

        this.currentTab = tabName;
        document.querySelectorAll('.nav-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.tab === tabName);
        });

        if (tabName === 'watchlist') {
            this.renderWatchlist();
        } else {
            await this.loadFeed(tabName);
        }
    }

    async openSearch() {
        this.currentTab = 'search';
        document.querySelectorAll('.nav-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.tab === 'search');
        });

        const searchSection = document.getElementById('searchSection');
        const feedSection = document.getElementById('feedSection');
        const continueSection = document.getElementById('continueSection');
        const searchDropdown = document.getElementById('searchDropdown');

        if (searchDropdown) searchDropdown.classList.remove('active');
        if (feedSection) feedSection.style.display = 'none';
        if (continueSection) continueSection.style.display = 'none';
        if (searchSection) {
            searchSection.style.display = 'block';
            window.scrollTo({ top: 0, behavior: 'smooth' });
        }

        const input = document.getElementById('tvSearchInput');
        if (input && !input.value.trim()) {
            await this.searchByTopic('trending', '🔥 Priporočeno za ogled');
        }

        const firstChip = document.querySelector('.tv-search-chip.active') || document.querySelector('.tv-search-chip');
        if (firstChip) {
            this.tvRemote.setFocus(firstChip);
        } else if (input) {
            this.tvRemote.setFocus(input);
        }
        this.showToast('🔍 Iskalnik (Izberite predlog spodaj ali vpišite naslov)');
    }

    hideKeyboard() {
        if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.hideKeyboard === 'function') {
            window.AndroidNativeBridge.hideKeyboard();
        }
        if (document.activeElement) {
            document.activeElement.blur();
        }
    }

    closeSearch() {
        this.hideKeyboard();

        const searchSection = document.getElementById('searchSection');
        const feedSection = document.getElementById('feedSection');
        const continueSection = document.getElementById('continueSection');
        const echoBox = document.getElementById('tvSearchLiveQueryDisplay');

        if (echoBox) echoBox.style.display = 'none';
        if (searchSection) searchSection.style.display = 'none';
        if (feedSection) feedSection.style.display = 'block';
        if (continueSection && this.continueWatching && this.continueWatching.length > 0) {
            continueSection.style.display = 'block';
        }

        this.setTab('trending');
        const activeNav = document.querySelector('.nav-btn.active') || document.querySelector('.nav-btn');
        if (activeNav) this.tvRemote.setFocus(activeNav);
    }

    async clearSearch() {
        const input = document.getElementById('tvSearchInput');
        const badge = document.getElementById('tvSearchCounterBadge');
        const liveContainer = document.getElementById('tvLiveMatchesContainer');
        const echoBox = document.getElementById('tvSearchLiveQueryDisplay');
        if (input) {
            input.value = '';
            input.focus();
            this.tvRemote.setFocus(input);
        }
        if (badge) badge.style.display = 'none';
        if (liveContainer) liveContainer.style.display = 'none';
        if (echoBox) echoBox.style.display = 'none';

        await this.searchByTopic('trending', '🔥 Priporočeno za ogled');
    }

    async searchByTopic(topic, title) {
        document.querySelectorAll('.tv-search-chip').forEach(c => {
            c.classList.toggle('active', c.dataset.topic === topic);
        });

        const titleEl = document.getElementById('searchResultsTitle');
        if (titleEl) titleEl.innerHTML = title;

        const liveContainer = document.getElementById('tvLiveMatchesContainer');
        const badge = document.getElementById('tvSearchCounterBadge');
        if (liveContainer) liveContainer.style.display = 'none';
        if (badge) badge.style.display = 'none';

        const grid = document.getElementById('searchResultsGrid');
        if (!grid) return;
        grid.innerHTML = '<div class="loading-spinner">Nalagam predloge...</div>';

        try {
            let data;
            if (topic === 'top_rated') {
                data = await this.fetchWithFallback('/api/top_rated?media_type=movie&page=1', () => this.standaloneTmdb.getTopRated('movie', 1));
            } else {
                data = await this.fetchWithFallback('/api/trending?media_type=all&time_window=week&page=1', () => this.standaloneTmdb.getTrending('all', 'week', 1));
            }
            const items = (data.results || []).filter(item => this.isMediaAvailable(item));
            this.renderSearchResults(items);
        } catch(e) {
            grid.innerHTML = '<div class="error-msg">Napaka pri nalaganju predlogov.</div>';
        }
    }

    async searchByQuery(query, title) {
        const input = document.getElementById('tvSearchInput');
        if (input) input.value = query;
        document.querySelectorAll('.tv-search-chip').forEach(c => {
            c.classList.toggle('active', c.dataset.query === query);
        });
        const titleEl = document.getElementById('searchResultsTitle');
        if (titleEl) titleEl.innerHTML = title || `Rezultati za "${query}"`;
        await this.performSearch(query);
    }

    async searchByGenre(genreId, title) {
        document.querySelectorAll('.tv-search-chip').forEach(c => {
            c.classList.toggle('active', c.dataset.genre == genreId);
        });

        const titleEl = document.getElementById('searchResultsTitle');
        if (titleEl) titleEl.innerHTML = title;

        const liveContainer = document.getElementById('tvLiveMatchesContainer');
        const badge = document.getElementById('tvSearchCounterBadge');
        if (liveContainer) liveContainer.style.display = 'none';
        if (badge) badge.style.display = 'none';

        const grid = document.getElementById('searchResultsGrid');
        if (!grid) return;
        grid.innerHTML = '<div class="loading-spinner">Nalagam žanr...</div>';

        try {
            const data = await this.fetchWithFallback(
                `/api/discover?with_genres=${genreId}`,
                () => this.standaloneTmdb.getDiscover('movie', genreId, 'popularity.desc', 1)
            );
            const items = (data.results || []).filter(item => this.isMediaAvailable(item));
            this.renderSearchResults(items);
        } catch(e) {
            grid.innerHTML = '<div class="error-msg">Napaka pri nalaganju žanra.</div>';
        }
    }

    async performSearch(query) {
        const q = (query || '').trim();
        const icon = document.getElementById('tvSearchIcon');
        const badge = document.getElementById('tvSearchCounterBadge');
        const liveContainer = document.getElementById('tvLiveMatchesContainer');
        const liveBar = document.getElementById('tvLiveMatchesBar');

        if (q.length < 2) {
            if (badge) badge.style.display = 'none';
            if (liveContainer) liveContainer.style.display = 'none';
            if (icon) icon.classList.remove('spin');
            await this.searchByTopic('trending', '🔥 Priporočeno za ogled');
            return;
        }

        const titleEl = document.getElementById('searchResultsTitle');
        if (titleEl) titleEl.innerHTML = `🔍 Rezultati za: "${q}"`;

        const grid = document.getElementById('searchResultsGrid');
        if (!grid) return;

        if (icon) icon.classList.add('spin');

        // 0ms Instant Local Cache Lookup
        const cacheKey = q.toLowerCase();
        let items;
        if (this.searchCache && this.searchCache.has(cacheKey)) {
            items = this.searchCache.get(cacheKey);
        } else {
            try {
                const data = await this.fetchWithFallback(
                    `/api/search?q=${encodeURIComponent(q)}`,
                    () => this.standaloneTmdb.search(q, 1)
                );
                const rawItems = (data.results || []).filter(item => this.isMediaAvailable(item));
                
                // Prioritize exact or prefix matches
                const exactMatches = [];
                const otherMatches = [];
                const lowerQ = q.toLowerCase();

                rawItems.forEach(it => {
                    const title = (it.title || it.name || '').toLowerCase();
                    if (title.startsWith(lowerQ) || title.includes(lowerQ)) {
                        exactMatches.push(it);
                    } else {
                        otherMatches.push(it);
                    }
                });

                items = [...exactMatches, ...otherMatches];
                if (this.searchCache) this.searchCache.set(cacheKey, items);
            } catch (e) {
                if (grid) grid.innerHTML = '<div class="error-msg">Napaka pri iskanju vsebin.</div>';
                if (icon) icon.classList.remove('spin');
                return;
            }
        }

        if (icon) icon.classList.remove('spin');

        // Update Counter Badge
        if (badge) {
            badge.style.display = 'inline-block';
            badge.innerHTML = `✨ ${items.length} HD zadetkov`;
        }

        // Render Smart Live Quick-Jump Pills (Top 5 items)
        if (liveContainer && liveBar) {
            if (items.length > 0) {
                liveBar.innerHTML = '';
                const topMatches = items.slice(0, 5);
                topMatches.forEach(item => {
                    const pill = document.createElement('button');
                    pill.className = 'tv-live-match-pill';
                    pill.tabIndex = 0;
                    const year = item.year || (item.release_date || item.first_air_date || '').substring(0, 4);
                    const isTv = (item.media_type === 'tv');
                    pill.innerHTML = `${isTv ? '📺' : '▶'} ${item.title || item.name} ${year ? `(${year})` : ''}`;
                    pill.title = `Takojšnji zagon: ${item.title || item.name}`;
                    pill.onclick = () => {
                        this.openDetails(item.media_type, item.id);
                    };
                    liveBar.appendChild(pill);
                });
                liveContainer.style.display = 'flex';
            } else {
                liveContainer.style.display = 'none';
            }
        }

        this.renderSearchResults(items);
    }

    renderSearchResults(items) {
        const grid = document.getElementById('searchResultsGrid');
        if (!grid) return;
        grid.innerHTML = '';

        const availableItems = (items || []).filter(item => this.isMediaAvailable(item));

        if (!availableItems || availableItems.length === 0) {
            grid.innerHTML = '<div style="grid-column: 1/-1; padding: 2.5rem 1rem; color: #94a3b8; text-align: center; font-size: 1.15rem; font-weight: 500;">❌ Ni najdenih HD vsebin za ta izraz.<br><span style="font-size: 0.95rem; color: #64748b; margin-top: 0.5rem; display: inline-block;">Poskusite z drugim naslovom ali izberite med hitrimi žanrskimi predlogi zgoraj.</span></div>';
            return;
        }

        availableItems.forEach((item, index) => {
            const card = document.createElement('div');
            card.className = 'media-card search-stagger';
            card.style.animationDelay = `${Math.min(index * 0.025, 0.3)}s`;
            card.tabIndex = 0;
            card.dataset.id = item.id;
            card.dataset.type = item.media_type;
            card.onclick = () => {
                this.openDetails(item.media_type, item.id);
            };

            const posterUrl = item.poster || 'https://via.placeholder.com/300x450/111827/94a3b8?text=Ni+Slike';
            const isTv = (item.media_type === 'tv');
            const qualityBadgeHtml = `<span class="badge ${isTv ? 'badge-type' : 'badge-quality'}">${isTv ? 'SERIJA' : 'FILM'}</span>
                                      <span class="badge badge-quality" style="margin-left: 4px;">HD 1080p</span>`;

            card.innerHTML = `
                <div class="card-poster-wrapper">
                    <img class="card-poster" src="${posterUrl}" alt="${item.title}" loading="lazy" onerror="this.src='https://via.placeholder.com/300x450/111827/94a3b8?text=Ni+Slike'">
                    <div class="card-overlay">
                        <div class="card-play-icon">▶</div>
                    </div>
                    <div class="card-badge-top">
                        ${qualityBadgeHtml}
                    </div>
                </div>
                <div class="card-info">
                    <div class="card-title" title="${item.title}">${item.title}</div>
                    <div class="card-meta">
                        <span class="card-year">${item.year || ''}</span>
                        <span class="badge badge-rating">⭐ ${item.rating || (item.vote_average ? item.vote_average.toFixed(1) : '7.8')}</span>
                    </div>
                </div>
            `;
            grid.appendChild(card);
        });
    }

    navigateHome() {
        try { this.closePlayer(); } catch (e) {}
        try { this.closeModal('detailsModal'); } catch (e) {}
        try { this.closeModal('settingsModal'); } catch (e) {}
        try { this.closeSearch(); } catch (e) {}
        this.currentTab = 'trending';
        this.setTab('trending');
        const activeNav = document.querySelector('.nav-btn[data-tab="trending"]') || document.querySelector('.nav-btn');
        if (activeNav) this.tvRemote.setFocus(activeNav);
    }

    async loadFeed(tab, page = 1) {
        this.currentFeedTab = tab;
        this.currentFeedPage = page;
        this.activeGenreId = 'all';

        const grid = document.getElementById('mediaGrid');
        const titleEl = document.getElementById('feedTitle');
        grid.innerHTML = '<div class="loading-spinner">Nalagam vsebine...</div>';

        let endpoint = '/api/trending';
        let fallbackFn = () => this.standaloneTmdb.getTrending('all', 'week', page);

        if (tab === 'trending') {
            titleEl.innerHTML = `🔥 Priljubljeno ta teden ${page > 1 ? `(Stran ${page})` : ''}`;
            endpoint = `/api/trending?media_type=all&time_window=week&page=${page}`;
            fallbackFn = () => this.standaloneTmdb.getTrending('all', 'week', page);
            if (page === 1) this.updateGenrePills('all');
        } else if (tab === 'movies') {
            titleEl.innerHTML = `🎬 Filmi v HD ${page > 1 ? `(Stran ${page})` : ''}`;
            endpoint = `/api/popular?media_type=movie&page=${page}`;
            fallbackFn = () => this.standaloneTmdb.getPopular('movie', page);
            if (page === 1) this.updateGenrePills('movie');
        } else if (tab === 'tv') {
            titleEl.innerHTML = `📺 TV Serije v HD ${page > 1 ? `(Stran ${page})` : ''}`;
            endpoint = `/api/popular?media_type=tv&page=${page}`;
            fallbackFn = () => this.standaloneTmdb.getPopular('tv', page);
            if (page === 1) this.updateGenrePills('tv');
        } else if (tab === 'top_rated') {
            titleEl.innerHTML = `⭐ Najvišje Ocenjene Vsebine ${page > 1 ? `(Stran ${page})` : ''}`;
            endpoint = `/api/top_rated?media_type=movie&page=${page}`;
            fallbackFn = () => this.standaloneTmdb.getTopRated('movie', page);
            if (page === 1) this.updateGenrePills('movie');
        }

        try {
            const data = await this.fetchWithFallback(endpoint, fallbackFn);
            const items = data.results || [];
            const currentPage = data.page || page;
            const totalPages = data.total_pages || 1;
            
            this.renderMediaGrid(items);
            this.renderPagination(currentPage, totalPages, (p) => {
                this.loadFeed(tab, p);
                window.scrollTo({ top: 0, behavior: 'smooth' });
            });
        } catch (err) {
            grid.innerHTML = '<div class="error-msg">Prišlo je do napake pri nalaganju vsebin.</div>';
        }
    }

    updateGenrePills(type) {
        const container = document.getElementById('genreFilters');
        if (!container) return;

        let genres = [];
        if (type === 'tv') {
            genres = [
                { id: 'all', name: 'Vse Serije' },
                { id: '10759', name: '💥 Akcija & Avantura' },
                { id: '10765', name: '🚀 Sci-Fi & Fantazija' },
                { id: '18', name: '🎭 Drama' },
                { id: '35', name: '😂 Komedija' },
                { id: '80', name: '🕵️ Kriminalka' },
                { id: '16', name: '🎨 Animirani' },
                { id: '9648', name: '🔍 Skrivnost' }
            ];
        } else {
            genres = [
                { id: 'all', name: 'Vsi Filmi' },
                { id: '28', name: '💥 Akcija' },
                { id: '878', name: '🚀 Sci-Fi' },
                { id: '35', name: '😂 Komedija' },
                { id: '27', name: '😱 Grozljivka' },
                { id: '18', name: '🎭 Drama' },
                { id: '53', name: '🕵️ Triler' },
                { id: '16', name: '🎨 Animirani' },
                { id: '10749', name: '❤️ Romantika' }
            ];
        }

        container.innerHTML = '';
        genres.forEach((g, idx) => {
            const chip = document.createElement('button');
            chip.className = `genre-chip ${idx === 0 ? 'active' : ''}`;
            chip.dataset.genre = g.id;
            chip.tabIndex = 0;
            chip.textContent = g.name;
            chip.onclick = () => this.filterByGenre(g.id, type === 'tv' ? 'tv' : 'movie');
            container.appendChild(chip);
        });
    }

    async filterByGenre(genreId, mediaType = 'movie', page = 1) {
        this.activeGenreId = genreId;
        this.currentMediaType = mediaType;
        this.currentGenrePage = page;

        document.querySelectorAll('.genre-chip').forEach(c => {
            c.classList.toggle('active', c.dataset.genre === String(genreId));
        });

        const grid = document.getElementById('mediaGrid');
        grid.innerHTML = '<div class="loading-spinner">Nalagam žanr...</div>';

        let url = `/api/discover?media_type=${mediaType}&page=${page}`;
        if (genreId !== 'all') {
            url += `&genre_id=${genreId}`;
        }

        try {
            const data = await this.fetchWithFallback(url, () => 
                this.standaloneTmdb.getDiscover(mediaType, genreId, 'popularity.desc', page)
            );
            const items = data.results || [];
            const currentPage = data.page || page;
            const totalPages = data.total_pages || 1;

            this.renderMediaGrid(items);
            this.renderPagination(currentPage, totalPages, (p) => {
                this.filterByGenre(genreId, mediaType, p);
                window.scrollTo({ top: 0, behavior: 'smooth' });
            });
        } catch (err) {
            grid.innerHTML = '<div class="error-msg">Napaka pri filtriranju žanra.</div>';
        }
    }

    renderPagination(currentPage, totalPages, onPageSelect) {
        const container = document.getElementById('paginationContainer');
        if (!container) return;

        container.innerHTML = '';
        if (totalPages <= 1) return;

        const maxPages = Math.min(totalPages, 500);

        // Previous Button
        if (currentPage > 1) {
            const prevBtn = document.createElement('button');
            prevBtn.className = 'page-btn page-nav';
            prevBtn.tabIndex = 0;
            prevBtn.innerHTML = '⬅ Prejšnja';
            prevBtn.title = 'Prejšnja stran';
            prevBtn.onclick = () => onPageSelect(currentPage - 1);
            container.appendChild(prevBtn);
        }

        const maxButtons = 5;
        let startPage = Math.max(1, currentPage - Math.floor(maxButtons / 2));
        let endPage = Math.min(maxPages, startPage + maxButtons - 1);

        if (endPage - startPage + 1 < maxButtons) {
            startPage = Math.max(1, endPage - maxButtons + 1);
        }

        // First page + dots
        if (startPage > 1) {
            const firstBtn = document.createElement('button');
            firstBtn.className = `page-btn ${currentPage === 1 ? 'active' : ''}`;
            firstBtn.tabIndex = 0;
            firstBtn.textContent = '1';
            firstBtn.onclick = () => onPageSelect(1);
            container.appendChild(firstBtn);

            if (startPage > 2) {
                const dots = document.createElement('span');
                dots.className = 'page-dots';
                dots.textContent = '...';
                container.appendChild(dots);
            }
        }

        // Middle numbered buttons
        for (let i = startPage; i <= endPage; i++) {
            const pageBtn = document.createElement('button');
            pageBtn.className = `page-btn ${i === currentPage ? 'active' : ''}`;
            pageBtn.tabIndex = 0;
            pageBtn.textContent = i;
            pageBtn.title = `Stran ${i}`;
            pageBtn.onclick = () => onPageSelect(i);
            container.appendChild(pageBtn);
        }

        // Last page + dots
        if (endPage < maxPages) {
            if (endPage < maxPages - 1) {
                const dots = document.createElement('span');
                dots.className = 'page-dots';
                dots.textContent = '...';
                container.appendChild(dots);
            }

            const lastBtn = document.createElement('button');
            lastBtn.className = `page-btn ${currentPage === maxPages ? 'active' : ''}`;
            lastBtn.tabIndex = 0;
            lastBtn.textContent = maxPages;
            lastBtn.onclick = () => onPageSelect(maxPages);
            container.appendChild(lastBtn);
        }

        // Next Button
        if (currentPage < maxPages) {
            const nextBtn = document.createElement('button');
            nextBtn.className = 'page-btn page-nav';
            nextBtn.tabIndex = 0;
            nextBtn.innerHTML = 'Naslednja ➡';
            nextBtn.title = 'Naslednja stran';
            nextBtn.onclick = () => onPageSelect(currentPage + 1);
            container.appendChild(nextBtn);
        }
    }

    isMediaAvailable(item) {
        if (!item) return false;
        if (this.standaloneTmdb && typeof this.standaloneTmdb.isAvailableMedia === 'function') {
            return this.standaloneTmdb.isAvailableMedia(item);
        }
        const title = item.title || item.name;
        if (!title || title === 'Neznan naslov') return false;
        if (!item.poster || item.poster.includes('placeholder') || item.poster.includes('Ni+Slike')) return false;
        if (item.is_cam === true) return false;
        if (item.quality_badge_class === 'badge-cam' || item.quality_badge_class === 'badge-unreleased') return false;
        if (item.quality_label && (item.quality_label.includes('CAM') || item.quality_label.includes('KMALU'))) return false;
        const relDateStr = item.release_date || item.first_air_date;
        if (!relDateStr) return false;
        const rel = new Date(relDateStr);
        const now = new Date();
        if (isNaN(rel.getTime()) || rel > now) return false;
        return true;
    }

    renderMediaGrid(items) {
        const grid = document.getElementById('mediaGrid');
        grid.innerHTML = '';

        const availableItems = (items || []).filter(item => this.isMediaAvailable(item));

        if (availableItems.length === 0) {
            grid.innerHTML = '<div class="empty-state">Ni razpoložljivih HD vsebin.</div>';
            return;
        }

        availableItems.forEach(item => {
            const card = document.createElement('div');
            card.className = 'media-card';
            card.tabIndex = 0;
            card.onclick = () => {
                this.openDetails(item.media_type, item.id);
            };

            const posterUrl = item.poster || 'https://via.placeholder.com/300x450/111827/94a3b8?text=Ni+Slike';
            const qualityBadgeHtml = `<span class="badge ${item.media_type === 'tv' ? 'badge-type' : 'badge-quality'}">${item.media_type === 'tv' ? 'SERIJA' : 'FILM'}</span>
                                      <span class="badge badge-quality" style="margin-left: 4px;">HD 1080p</span>`;

            card.innerHTML = `
                <div class="card-poster-wrapper">
                    <img class="card-poster" src="${posterUrl}" alt="${item.title}" loading="lazy">
                    <div class="card-overlay">
                        <div class="card-play-icon">▶</div>
                    </div>
                    <div class="card-badge-top">
                        ${qualityBadgeHtml}
                    </div>
                </div>
                <div class="card-info">
                    <div class="card-title" title="${item.title}">${item.title}</div>
                    <div class="card-meta">
                        <span class="card-year">${item.year || ''}</span>
                        <span class="badge badge-rating">⭐ ${item.rating}</span>
                    </div>
                </div>
            `;
            grid.appendChild(card);
        });
    }

    async performLiveSearch(query) {
        const dropdown = document.getElementById('searchDropdown');
        dropdown.classList.add('active');

        try {
            const data = await this.fetchWithFallback(
                `/api/search?q=${encodeURIComponent(query)}`,
                () => this.standaloneTmdb.search(query, 1)
            );
            const items = (data.results || []).filter(item => this.isMediaAvailable(item));
            
            dropdown.innerHTML = '';
            if (items.length === 0) {
                dropdown.innerHTML = '<div style="padding: 1rem; color: #94a3b8;">Ni najdenih razpoložljivih HD vsebin.</div>';
                return;
            }

            items.slice(0, 8).forEach(item => {
                const row = document.createElement('div');
                row.className = 'search-result-item';
                row.tabIndex = 0;
                row.onclick = () => {
                    dropdown.classList.remove('active');
                    this.openDetails(item.media_type, item.id);
                };
                row.innerHTML = `
                    <img src="${item.poster}" alt="${item.title}">
                    <div class="search-result-info">
                        <div class="search-result-title">${item.title}</div>
                        <div class="search-result-meta">
                            <span>${item.year || ''}</span>
                            <span class="badge ${item.media_type === 'tv' ? 'badge-type' : 'badge-quality'}">${item.media_type === 'tv' ? 'SERIJA' : 'FILM'}</span>
                            <span class="badge badge-quality">HD 1080p</span>
                        </div>
                    </div>
                `;
                dropdown.appendChild(row);
            });
        } catch (err) {
            dropdown.innerHTML = '<div style="padding: 1rem; color: #ff0055;">Napaka pri iskanju.</div>';
        }
    }

    async playMovieDirectly(item) {
        this.showToast(`🍿 Pripravljam predvajanje: ${item.title}...`);
        this.activeDetailsItem = item;
        this.activeSeason = null;
        this.activeEpisode = null;

        try {
            const data = this.clientStreamResolver.getStreams('movie', item.id, item.imdb_id);
            const sources = Array.isArray(data) ? data : (data.all_sources || []);
            this.activeStreams = sources;
            const streamToPlay = (data && data.recommended) ? data.recommended : (sources.length > 0 ? sources[0] : null);
            if (streamToPlay) {
                this.playStream(streamToPlay, item);
            } else {
                this.openDetails('movie', item.id);
            }
        } catch(e) {
            this.openDetails('movie', item.id);
        }
    }

    async openDetails(mediaType, tmdbId) {
        this.openModal('detailsModal');
        this.activeDetailsItem = null;
        this.activeStreams = [];

        const topbarTitle = document.getElementById('topbarMediaTitle');
        if (topbarTitle) topbarTitle.textContent = 'Nalagam podrobnosti...';
        const modalTitle = document.getElementById('modalTitle');
        if (modalTitle) modalTitle.textContent = 'Nalagam podrobnosti...';
        const modalOverview = document.getElementById('modalOverview');
        if (modalOverview) modalOverview.textContent = '';
        const modalCast = document.getElementById('modalCast');
        if (modalCast) modalCast.innerHTML = '';

        try {
            const item = await this.fetchWithFallback(
                `/api/details/${mediaType}/${tmdbId}`,
                () => this.standaloneTmdb.getDetails(mediaType, tmdbId)
            );
            
            if (!item) {
                this.showToast('Napaka pri pridobivanju podatkov o vsebini');
                return;
            }

            this.activeDetailsItem = item;
            if (topbarTitle) topbarTitle.textContent = item.title;

            // Check if CAM or unreleased/upcoming
            const relDate = item.year || '';
            const isCam = item.is_cam === true || (item.quality_label && item.quality_label.includes('CAM'));
            const isFuture = !isCam && (relDate && parseInt(relDate) > new Date().getFullYear() || (item.quality_label && item.quality_label.includes('KMALU')));
            const qualityBadge = document.getElementById('modalQualityBadge');
            
            if (qualityBadge) {
                if (isCam) {
                    qualityBadge.className = 'badge badge-cam';
                    qualityBadge.textContent = '📹 CAM / KINO POSNETEK (Ni uradnega HD)';
                } else if (isFuture) {
                    qualityBadge.className = 'badge badge-unreleased';
                    qualityBadge.textContent = `⏳ KMALU V KINU (${relDate})`;
                } else {
                    qualityBadge.className = 'badge badge-quality';
                    qualityBadge.textContent = 'HD 1080p';
                }
            }

            // Populate UI
            if (modalTitle) modalTitle.textContent = item.title;
            const typeBadge = document.getElementById('modalTypeBadge');
            if (typeBadge) typeBadge.textContent = item.media_type === 'tv' ? 'SERIJA' : 'FILM';
            const ratingBadge = document.getElementById('modalRating');
            if (ratingBadge) ratingBadge.textContent = `⭐ ${item.rating}`;
            const yearBadge = document.getElementById('modalYear');
            if (yearBadge) yearBadge.textContent = item.year || '';
            const runtimeBadge = document.getElementById('modalRuntime');
            if (runtimeBadge) runtimeBadge.textContent = item.runtime ? `${item.runtime} min` : '';
            if (modalOverview) modalOverview.textContent = item.overview || 'Za ta naslov ni na voljo opisa.';
            
            const backdropImg = document.getElementById('modalBackdropImg');
            if (backdropImg && item.backdrop) backdropImg.src = item.backdrop;

            // Genres
            const genresContainer = document.getElementById('modalGenres');
            if (genresContainer) {
                genresContainer.innerHTML = '';
                (item.genres || []).forEach(g => {
                    const span = document.createElement('span');
                    span.className = 'genre-tag';
                    span.textContent = g;
                    genresContainer.appendChild(span);
                });
            }

            // Cast
            if (modalCast) {
                modalCast.innerHTML = '';
                (item.cast || []).forEach(c => {
                    const chip = document.createElement('div');
                    chip.className = 'cast-chip';
                    chip.innerHTML = `
                        <img src="${c.profile_path || 'https://via.placeholder.com/40'}" alt="${c.name}">
                        <span><strong>${c.name}</strong> (${c.character || ''})</span>
                    `;
                    modalCast.appendChild(chip);
                });
            }

            // TV Shows vs Movies handling
            const fixedSeasons = document.getElementById('fixedSeasonsHeader');
            const tvSection = document.getElementById('tvEpisodesSection');
            const moviePlayActions = document.getElementById('moviePlayActions');

            if (item.media_type === 'tv') {
                if (fixedSeasons) fixedSeasons.style.display = 'block';
                if (moviePlayActions) moviePlayActions.style.display = 'none';
                if (tvSection) tvSection.style.display = 'flex';

                const pillsBar = document.getElementById('seasonPillsBar');
                if (pillsBar) {
                    pillsBar.innerHTML = '';
                    let seasons = item.seasons || [];
                    if (!seasons || seasons.length === 0) {
                        const count = item.number_of_seasons || 1;
                        seasons = [];
                        for (let i = 1; i <= count; i++) {
                            seasons.push({ season_number: i, name: `Sezona ${i}`, episode_count: 10 });
                        }
                    }

                    this.activeSeason = seasons[0].season_number;
                    seasons.forEach((s, idx) => {
                        const pill = document.createElement('button');
                        pill.className = `season-pill ${idx === 0 ? 'active' : ''}`;
                        pill.tabIndex = 0;
                        pill.textContent = s.name || `Sezona ${s.season_number}`;
                        pill.title = `${s.episode_count || ''} epizod`;
                        pill.onclick = () => {
                            this.activeSeason = s.season_number;
                            document.querySelectorAll('.season-pill').forEach(p => p.classList.remove('active'));
                            pill.classList.add('active');
                            const curTitle = document.getElementById('currentSeasonTitle');
                            if (curTitle) curTitle.textContent = `Epizode (${s.name || 'Sezona ' + s.season_number})`;
                            this.renderSeasonEpisodes(item.id, s.season_number);
                        };
                        pillsBar.appendChild(pill);
                    });

                    const curTitle = document.getElementById('currentSeasonTitle');
                    if (curTitle) curTitle.textContent = `Epizode (${seasons[0].name || 'Sezona ' + seasons[0].season_number})`;
                    this.renderSeasonEpisodes(item.id, seasons[0].season_number);
                }
            } else {
                if (fixedSeasons) fixedSeasons.style.display = 'none';
                if (moviePlayActions) moviePlayActions.style.display = 'flex';
                if (tvSection) tvSection.style.display = 'none';
                const episodesList = document.getElementById('episodesList');
                if (episodesList) episodesList.innerHTML = '';
                const pillsBar = document.getElementById('seasonPillsBar');
                if (pillsBar) pillsBar.innerHTML = '';
                this.loadStreams(item);
            }

            // Focus appropriate control for TV remote
            setTimeout(() => {
                if (item.media_type === 'tv') {
                    const firstSeason = document.querySelector('#seasonPillsBar .season-pill');
                    const firstEp = document.querySelector('#episodesList .episode-card');
                    if (firstSeason) {
                        this.tvRemote.setFocus(firstSeason);
                    } else if (firstEp) {
                        this.tvRemote.setFocus(firstEp);
                    }
                } else {
                    const playBtn = document.getElementById('modalMainPlayBtn');
                    if (playBtn) this.tvRemote.setFocus(playBtn);
                }
            }, 300);

        } catch (err) {
            console.error('Error opening details:', err);
            this.showToast('Napaka pri nalaganju podrobnosti');
        }
    }

    async renderSeasonEpisodes(tmdbId, seasonNumber) {
        const container = document.getElementById('episodesList');
        container.innerHTML = '<div style="color:#94a3b8; padding: 1rem;">Nalagam epizode...</div>';

        try {
            const episodes = await this.fetchWithFallback(
                `/api/season/${tmdbId}/${seasonNumber}`,
                async () => {
                    const eps = await this.standaloneTmdb.getSeasonEpisodes(tmdbId, seasonNumber);
                    return { episodes: eps };
                }
            ).then(res => res.episodes || []);

            container.innerHTML = '';
            if (episodes.length === 0) {
                container.innerHTML = '<div style="color:#94a3b8; padding: 1rem;">Ni najdenih epizod za to sezono.</div>';
                return;
            }

            const today = new Date().toISOString().split('T')[0];

            episodes.forEach((ep, idx) => {
                const isUnreleased = !ep.air_date || ep.air_date > today;
                const card = document.createElement('div');
                card.className = `episode-card ${isUnreleased ? 'unreleased-episode' : ''} ${idx === 0 ? 'active' : ''}`;
                card.id = `ep-card-${ep.episode_number}`;
                card.tabIndex = 0;

                const thumbUrl = ep.still_path || this.activeDetailsItem.backdrop || this.activeDetailsItem.poster || '';
                const unreleasedBadge = isUnreleased 
                    ? `<span class="badge badge-unreleased" style="margin-left: 6px;">⏳ ŠE NI IZŠLO (${ep.air_date || 'Kmalu'})</span>` 
                    : '';

                const playButtonHtml = isUnreleased
                    ? `<button class="btn btn-unreleased btn-sm" title="Epizoda še ni bila predvajana na TV">
                        ⏳ Še ni izšlo (${ep.air_date || 'Kmalu'})
                       </button>`
                    : `<button class="btn btn-primary btn-sm" title="Predvajaj epizodo">
                        ▶ Predvajaj
                       </button>`;

                card.innerHTML = `
                    <div class="episode-thumb-box">
                        <img class="episode-thumb-img" src="${thumbUrl}" alt="E${ep.episode_number}" loading="lazy">
                        <div class="episode-thumb-play">
                            <span>${isUnreleased ? '⏳' : '▶'}</span>
                        </div>
                    </div>
                    <div class="episode-details-box">
                        <div class="episode-title-row">
                            <span class="episode-number-badge">E${ep.episode_number < 10 ? '0' + ep.episode_number : ep.episode_number}</span>
                            <span class="episode-card-title">${ep.title}</span>
                            ${unreleasedBadge}
                        </div>
                        <div class="episode-meta-row">
                            <span>📅 ${ep.air_date || 'Ni datuma'}</span>
                            <span>⏱️ ${ep.runtime ? ep.runtime + ' min' : '45 min'}</span>
                            <span>⭐ ${ep.rating}</span>
                        </div>
                        <p class="episode-overview-snippet">${ep.overview || 'Za to epizodo ni na voljo opisa.'}</p>
                    </div>
                    <div class="episode-actions-row">
                        ${playButtonHtml}
                    </div>
                `;

                card.onclick = () => {
                    document.querySelectorAll('.episode-card').forEach(c => c.classList.remove('active'));
                    card.classList.add('active');
                    if (isUnreleased) {
                        this.showToast(`ℹ️ Epizoda E${ep.episode_number} uradno izide šele ${ep.air_date || 'v prihodnje'}.`);
                    }
                    this.playEpisode(seasonNumber, ep.episode_number);
                };

                const playBtn = card.querySelector('.episode-actions-row button');
                if (playBtn) {
                    playBtn.onclick = (e) => {
                        e.stopPropagation();
                        document.querySelectorAll('.episode-card').forEach(c => c.classList.remove('active'));
                        card.classList.add('active');
                        this.playEpisode(seasonNumber, ep.episode_number);
                    };
                }

                container.appendChild(card);
            });

            this.activeEpisode = episodes[0].episode_number;
            this.loadStreams(this.activeDetailsItem, this.activeSeason, this.activeEpisode);

            // Auto-focus first episode card for instant TV remote control
            setTimeout(() => {
                const firstEp = container.querySelector('.episode-card');
                if (firstEp) {
                    this.tvRemote.setFocus(firstEp);
                }
            }, 100);
        } catch (err) {
            container.innerHTML = '<div style="color:#ff0055; padding: 1rem;">Napaka pri nalaganju epizod.</div>';
        }
    }

    async loadStreams(item, season = null, episode = null) {
        try {
            const data = this.clientStreamResolver.getStreams(item.media_type, item.id, item.imdb_id, season, episode);
            const sources = Array.isArray(data) ? data : (data.all_sources || []);
            this.activeStreams = sources;
            this.updateStreamSelectorUI(data);
        } catch (err) {
            console.error('Error loading streams:', err);
        }
    }

    updateStreamSelectorUI(data) {
        const streamList = document.getElementById('allStreamsList');
        const badgeCount = document.getElementById('streamCountBadge');
        const recName = document.getElementById('recStreamName');
        const altCount = document.getElementById('altStreamsCount');

        if (badgeCount) badgeCount.textContent = `100% Pripravljeno`;
        if (altCount) altCount.textContent = `${this.activeStreams.length} na voljo`;

        const rec = data.recommended || (this.activeStreams.length > 0 ? this.activeStreams[0] : null);
        if (rec && recName) {
            recName.innerHTML = `
                <span class="badge ${rec.provider_type === 'debrid' ? 'badge-type' : 'badge-quality'}">${rec.quality_badge}</span>
                <strong>${rec.name}</strong>
            `;
        }

        if (streamList) {
            streamList.innerHTML = '';
            this.activeStreams.forEach((stream, idx) => {
                const row = document.createElement('div');
                row.className = `stream-row ${idx === 0 ? 'active' : ''}`;
                row.innerHTML = `
                    <div style="display:flex; align-items:center; gap: 0.8rem;">
                        <span class="badge ${stream.provider_type === 'debrid' ? 'badge-type' : 'badge-quality'}">${stream.quality_badge}</span>
                        <span style="font-weight:600; color:#fff;">${stream.name}</span>
                    </div>
                    <button class="btn btn-secondary btn-sm" tabindex="0">▶ Izberi ta vir</button>
                `;
                const btn = row.querySelector('button');
                btn.onclick = () => {
                    this.recordServerSuccess(stream.id);
                    this.playStream(stream, this.activeDetailsItem, this.activeSeason, this.activeEpisode);
                };
                streamList.appendChild(row);
            });
        }
    }

    async playEpisode(seasonNumber, episodeNumber) {
        this.activeSeason = seasonNumber;
        this.activeEpisode = episodeNumber;

        this.showToast(`🍿 Nalagam epizodo S${seasonNumber}E${episodeNumber}...`);

        const item = this.activeDetailsItem;
        if (!item || !item.id) {
            console.error('No activeDetailsItem found in playEpisode');
            this.showToast('❌ Napaka: Podatki o seriji niso na voljo');
            return;
        }

        try {
            const data = this.clientStreamResolver.getStreams('tv', item.id, item.imdb_id, seasonNumber, episodeNumber);
            const sources = Array.isArray(data) ? data : (data.all_sources || []);
            this.activeStreams = sources;
            const streamToPlay = (data && data.recommended) ? data.recommended : (sources.length > 0 ? sources[0] : null);
            if (streamToPlay) {
                this.playStream(streamToPlay, item, seasonNumber, episodeNumber);
            } else {
                this.showToast('❌ Ni na voljo veljavnega vira za to epizodo');
            }
        } catch (err) {
            console.error('Error in playEpisode:', err);
            this.showToast('Napaka pri predvajanju epizode');
        }
    }

    async playActiveSelection() {
        if (!this.activeDetailsItem) return;

        if (this.activeDetailsItem.media_type === 'tv') {
            const s = this.activeSeason || 1;
            const e = this.activeEpisode || 1;
            this.playEpisode(s, e);
        } else {
            if (this.activeStreams && this.activeStreams.length > 0) {
                this.playStream(this.activeStreams[0], this.activeDetailsItem, null, null);
            } else {
                await this.loadStreams(this.activeDetailsItem);
                if (this.activeStreams && this.activeStreams.length > 0) {
                    this.playStream(this.activeStreams[0], this.activeDetailsItem, null, null);
                }
            }
        }
    }

    applySmartServerRanking(data) {
        if (!data || !data.all_sources) return;
        
        data.all_sources = data.all_sources.filter(s => s.id !== 'vidsrc_to');

        let stats = {};
        try {
            stats = JSON.parse(localStorage.getItem('streamnexus_server_stats') || '{}');
            delete stats['vidsrc_to'];
            localStorage.setItem('streamnexus_server_stats', JSON.stringify(stats));
        } catch (e) {
            stats = {};
        }

        data.all_sources.forEach(source => {
            const serverId = source.id;
            const bonus = (stats[serverId] || 0) * 2;
            source.priority_score = (source.priority_score || 50) + bonus;
            if (source.id === 'vidlink') {
                source.priority_score += 500; // Guaranteed #1 primary 1080p server
            }
        });

        data.all_sources.sort((a, b) => (b.priority_score || 0) - (a.priority_score || 0));
        
        if (data.all_sources.length > 0) {
            data.recommended = data.all_sources[0];
        }
    }

    recordServerSuccess(serverId) {
        if (!serverId) return;
        try {
            let stats = JSON.parse(localStorage.getItem('streamnexus_server_stats') || '{}');
            stats[serverId] = (stats[serverId] || 0) + 1;
            localStorage.setItem('streamnexus_server_stats', JSON.stringify(stats));
        } catch (e) {
            console.error('Error saving server stats:', e);
        }
    }

    async playStream(stream, item, season = null, episode = null) {
        const titleStr = `${item.title} ${season ? `S${season}E${episode}` : ''}`;
        this.currentPlayingStream = { stream, item, season, episode, titleStr };

        this.saveHistoryItem(item, season, episode);
        this.recordServerSuccess(stream.id);

        this.showToast(`▶ Odpiram predvajalnik: ${titleStr}`);
        this.openInAppPlayer(stream, titleStr);
    }

    openInAppPlayer(stream, titleStr) {
        const overlay = document.getElementById('playerOverlay');
        const container = document.getElementById('playerVideoContainer');
        const titleEl = document.getElementById('playerMovieTitle');
        const badgeEl = document.getElementById('playerSourceBadge');
        const activeTag = document.getElementById('playerActiveServerTag');
        const pillsContainer = document.getElementById('playerServerPills');
        const detailsModal = document.getElementById('detailsModal');

        if (!overlay || !container) return;

        if (detailsModal) {
            detailsModal.style.visibility = 'hidden';
        }

        overlay.style.display = 'flex';
        overlay.style.zIndex = '100000';

        if (titleEl) titleEl.textContent = titleStr;

        const isCam = this.activeDetailsItem && (this.activeDetailsItem.is_cam === true || (this.activeDetailsItem.quality_label && this.activeDetailsItem.quality_label.includes('CAM')));
        if (isCam) {
            if (badgeEl) {
                badgeEl.className = 'badge badge-unreleased';
                badgeEl.textContent = '📹 KINO POSNETEK (CAM)';
            }
            this.showToast('⚠️ Opozorilo: Za ta film še ni uradne HD digitalne izdaje.');
        } else {
            if (badgeEl) {
                badgeEl.className = 'badge badge-quality';
                badgeEl.textContent = stream.quality_badge || 'FREE 1080P';
            }
        }
        
        if (activeTag) {
            let shortName = stream.name.replace(' (Optimalni Glavni Server)', '').replace(' (Rezerva HD)', '').replace(' (Direct)', '').replace(' (Multi-Server)', '').replace(' (VIP)', '');
            activeTag.innerHTML = `🟢 ${shortName}`;
        }

        // Server switcher pills
        if (pillsContainer && this.activeStreams && this.activeStreams.length > 0) {
            pillsContainer.innerHTML = '';
            pillsContainer.style.display = 'flex';
            
            this.activeStreams.forEach((s, idx) => {
                const btn = document.createElement('button');
                const isCur = (s.url === stream.url || s.id === stream.id);
                btn.className = `server-pill-btn ${isCur ? 'active' : ''}`;
                btn.tabIndex = 0;
                
                let shortName = s.name.replace(' (Optimalni Glavni Server)', '').replace(' (Rezerva HD)', '').replace(' (Direct)', '').replace(' (Cloud CDN)', '').replace(' (Multi-Server)', '').replace(' VIP', '').replace(' HD', '');
                btn.textContent = `S${idx + 1}: ${shortName}`;
                btn.title = `${s.name} - ${s.quality_badge}`;

                btn.onclick = () => {
                    document.querySelectorAll('.server-pill-btn').forEach(b => b.classList.remove('active'));
                    btn.classList.add('active');
                    this.recordServerSuccess(s.id);
                    this.switchPlayerServer(s, titleStr);
                };
                pillsContainer.appendChild(btn);
            });
        }

        if (this.artInstance) {
            try { this.artInstance.destroy(true); } catch(e) {}
            this.artInstance = null;
        }

        const isDirectStream = stream.url.includes('.m3u8') || 
                               stream.url.includes('.mp4') || 
                               stream.is_direct === true || 
                               (stream.provider_type === 'debrid' && !stream.is_embed);

        if (isDirectStream && typeof Artplayer !== 'undefined') {
            this.initArtplayerEngine(container, stream, titleStr);
        } else {
            let finalUrl = stream.url;
            const sep = finalUrl.includes('?') ? '&' : '?';
            if (!finalUrl.includes('autoplay=')) {
                finalUrl += `${sep}autoplay=true`;
            }
            if (!finalUrl.includes('mute=')) {
                finalUrl += `&mute=0&muted=0&muted=false&volume=100`;
            }

            container.innerHTML = `
                <iframe 
                    id="activeStreamIframe"
                    src="${finalUrl}" 
                    allowfullscreen="true" 
                    webkitallowfullscreen="true" 
                    mozallowfullscreen="true"
                    allow="autoplay *; fullscreen *; encrypted-media *; picture-in-picture *; microphone 'none'; camera 'none'"
                    style="width: 100%; height: 100%; border: none;"
                ></iframe>
            `;
        }

        this.applyAiFilterMode();
        this.startStreamWatchdog(stream, titleStr);

        overlay.style.display = 'flex';
        this.resetPlayerHudTimer();

        // Continuous Hardware & Software Audio Force-Unmute Cascade
        const forceUnmuteAll = () => {
            // 1. Android Native Hardware AudioManager
            if (window.AndroidNativeBridge && window.AndroidNativeBridge.forceUnmuteAudio) {
                window.AndroidNativeBridge.forceUnmuteAudio();
            }

            // 2. Direct HTML5 video / audio elements
            document.querySelectorAll('video, audio').forEach(v => {
                try {
                    v.muted = false;
                    v.defaultMuted = false;
                    v.volume = 1.0;
                } catch(e) {}
            });

            // 3. Iframe Un-Mute Multi-Protocol Broadcast
            const iframe = document.getElementById('activeStreamIframe');
            if (iframe) {
                try {
                    iframe.focus();
                    const unMuteMsgs = [
                        '{"event":"command","func":"unMute","args":""}',
                        '{"event":"command","func":"setVolume","args":[100]}',
                        '{"event":"command","func":"playVideo","args":""}',
                        JSON.stringify({ method: 'unMute' }),
                        JSON.stringify({ event: 'unMute' }),
                        JSON.stringify({ type: 'player:unmute' }),
                        JSON.stringify({ type: 'unmute' }),
                        JSON.stringify({ action: 'unmute' }),
                        JSON.stringify({ volume: 1 }),
                        'unmute',
                        'volume:100'
                    ];
                    unMuteMsgs.forEach(m => {
                        try { iframe.contentWindow.postMessage(m, '*'); } catch(e) {}
                    });
                } catch(e) {}
            }
        };

        // Repeated Un-Mute Cascade to guarantee audio kicks in on TV
        setTimeout(forceUnmuteAll, 100);
        setTimeout(forceUnmuteAll, 400);
        setTimeout(forceUnmuteAll, 900);
        setTimeout(forceUnmuteAll, 1600);
        setTimeout(forceUnmuteAll, 2800);
        setTimeout(forceUnmuteAll, 4200);
    }

    resetPlayerHudTimer() {
        const overlay = document.getElementById('playerOverlay');
        if (!overlay) return;
        overlay.classList.remove('player-hud-hidden');

        if (this.playerHudTimeout) clearTimeout(this.playerHudTimeout);
        this.playerHudTimeout = setTimeout(() => {
            if (overlay && overlay.style.display !== 'none') {
                overlay.classList.add('player-hud-hidden');
            }
        }, 3000);
    }

    initArtplayerEngine(container, stream, titleStr) {
        container.innerHTML = '';
        const artWrapper = document.createElement('div');
        artWrapper.id = 'artplayerHolder';
        artWrapper.style.width = '100%';
        artWrapper.style.height = '100%';
        container.appendChild(artWrapper);

        const subUrl = this.settings.subtitles_enabled ? (stream.subtitle_url || '') : '';

        this.artInstance = new Artplayer({
            container: artWrapper,
            url: stream.url,
            title: titleStr,
            theme: '#00e5ff',
            volume: 0.9,
            isLive: false,
            muted: false,
            autoplay: true,
            pip: false,
            autoSize: true,
            autoMini: false,
            screenshot: true,
            setting: true,
            loop: false,
            flip: true,
            playbackRate: true,
            aspectRatio: true,
            fullscreen: true,
            fullscreenWeb: true,
            subtitleOffset: true,
            miniProgressBar: true,
            mutex: true,
            backdrop: true,
            playsInline: true,
            autoPlayback: true,
            airplay: true,
            hotkey: true,
            subtitle: subUrl ? {
                url: subUrl,
                type: 'vtt',
                style: {
                    color: '#ffffff',
                    fontSize: '28px',
                    textShadow: '0 2px 4px rgba(0,0,0,0.9), 0 0 10px rgba(0,0,0,0.8)'
                }
            } : undefined,
            customType: {
                m3u8: function (video, url, art) {
                    if (typeof Hls !== 'undefined' && Hls.isSupported()) {
                        if (art.hls) art.hls.destroy();
                        const hls = new Hls({
                            maxBufferLength: 120,
                            maxMaxBufferLength: 600,
                            enableWorker: true
                        });
                        hls.loadSource(url);
                        hls.attachMedia(video);
                        art.hls = hls;
                        art.on('destroy', () => hls.destroy());
                    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                        video.src = url;
                    } else {
                        art.notice.show = 'Nepodprt HLS format v brskalniku';
                    }
                }
            }
        });
    }

    launchCurrentInVlc() {
        if (!this.currentPlayingStream || !this.currentPlayingStream.stream) {
            this.showToast('⚠️ Ni izbranega aktivnega toka za VLC.');
            return;
        }
        const { stream, item, season, episode, titleStr } = this.currentPlayingStream;
        if (window.AndroidNativeBridge && window.AndroidNativeBridge.launchVlcOrExternal) {
            this.showToast(`🟠 Odpiram zunanji predvajalnik (VLC / ExoPlayer)...`);
            window.AndroidNativeBridge.launchVlcOrExternal(stream.url, titleStr || 'StreamNexus Predvajalnik');
            return;
        }
        if (this.isStandaloneMode) {
            this.showToast('ℹ️ Predvajanje poteka neposredno v TV predvajalniku.');
            return;
        }
        this.showToast(`🟠 Zaganjam sistemski VLC...`);
        
        fetch('/api/play_vlc', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                stream_url: stream.url,
                title: titleStr,
                media_type: item ? item.media_type : 'movie',
                tmdb_id: item ? item.id : null,
                season: season,
                episode: episode
            })
        }).then(res => res.json()).then(data => {
            if (data.success) {
                this.showToast('✅ ' + data.message);
            } else {
                this.showToast('⚠️ ' + (data.message || 'Napaka pri zagonu'));
            }
        }).catch(() => {
            this.showToast('⚠️ Predvajanje se nadaljuje v vgrajenem predvajalniku.');
        });
    }

    toggleServerPills() {
        const pills = document.getElementById('playerServerPills');
        if (pills) {
            const isHidden = (pills.style.display === 'none');
            pills.style.display = isHidden ? 'flex' : 'none';
            this.showToast(isHidden ? '🔄 Trak strežnikov je prikazan' : '🔄 Trak strežnikov je skrit');
        }
    }

    switchPlayerServer(stream, titleStr) {
        this.showToast(`🔄 Preklapljam na: ${stream.name}...`);
        const container = document.getElementById('playerVideoContainer');
        const badgeEl = document.getElementById('playerSourceBadge');
        const activeTag = document.getElementById('playerActiveServerTag');
        
        if (badgeEl) badgeEl.textContent = stream.quality_badge;
        if (activeTag) {
            let shortName = stream.name.replace(' (Optimalni Glavni Server)', '').replace(' (Rezerva HD)', '').replace(' (Direct)', '').replace(' (Multi-Server)', '').replace(' (VIP)', '');
            activeTag.innerHTML = `🟢 ${shortName}`;
        }

        this.currentPlayingStream.stream = stream;

        if (this.artInstance) {
            try { this.artInstance.destroy(true); } catch(e) {}
            this.artInstance = null;
        }

        const isDirectStream = stream.url.includes('.m3u8') || 
                               stream.url.includes('.mp4') || 
                               stream.is_direct === true || 
                               (stream.provider_type === 'debrid' && !stream.is_embed);

        if (isDirectStream && typeof Artplayer !== 'undefined') {
            this.initArtplayerEngine(container, stream, titleStr);
        } else {
            let finalUrl = stream.url;
            if (!this.settings.subtitles_enabled) {
                const sep = finalUrl.includes('?') ? '&' : '?';
                if (!finalUrl.includes('sub=0')) {
                    finalUrl += `${sep}sub=0&subtitles=0&sub_lang=none&subtitles_enabled=false`;
                }
            }

            container.innerHTML = `
                <iframe 
                    id="activeStreamIframe"
                    src="${finalUrl}" 
                    allowfullscreen="true" 
                    webkitallowfullscreen="true" 
                    mozallowfullscreen="true"
                    allow="autoplay; fullscreen; encrypted-media; picture-in-picture; microphone 'none'; camera 'none'"
                    style="width: 100%; height: 100%; border: none;"
                ></iframe>
            `;
        }

        this.applyAiFilterMode();
        this.startStreamWatchdog(stream, titleStr);
    }

    initAiEngine() {
        const cdnDomains = [
            'https://vidsrc.to',
            'https://vidlink.pro',
            'https://vidsrc.me',
            'https://vidsrc.in',
            'https://vidsrc.pm',
            'https://autoembed.co',
            'https://multiembed.mov',
            'https://111movies.com',
            'https://www.2embed.cc',
            'https://image.tmdb.org',
            'https://api.themoviedb.org'
        ];
        cdnDomains.forEach(domain => {
            const link = document.createElement('link');
            link.rel = 'dns-prefetch';
            link.href = domain;
            document.head.appendChild(link);
        });

        this.aiModes = ['1440p', 'hdr', 'vivid', 'off'];
        this.currentAiMode = localStorage.getItem('streamnexus_ai_mode') || '1440p';
        this.updateAiBadgeUI();
    }

    applyAiFilterMode() {
        const overlay = document.getElementById('playerOverlay');
        if (!overlay) return;

        overlay.classList.remove('ai-enhanced-1440p', 'ai-enhanced-hdr', 'ai-enhanced-vivid');

        if (this.currentAiMode === '1440p') {
            overlay.classList.add('ai-enhanced-1440p');
        } else if (this.currentAiMode === 'hdr') {
            overlay.classList.add('ai-enhanced-hdr');
        } else if (this.currentAiMode === 'vivid') {
            overlay.classList.add('ai-enhanced-vivid');
        }
    }

    cycleAiFilterMode() {
        const currentIdx = this.aiModes.indexOf(this.currentAiMode);
        const nextIdx = (currentIdx + 1) % this.aiModes.length;
        this.currentAiMode = this.aiModes[nextIdx];
        localStorage.setItem('streamnexus_ai_mode', this.currentAiMode);

        this.applyAiFilterMode();
        this.updateAiBadgeUI();

        const labels = {
            '1440p': '✨ NexusAI: 1440p Ultra-Sharp (65" TV Optimizacija)',
            'hdr': '🎨 NexusAI: Cinema HDR Vibrance',
            'vivid': '⚡ NexusAI: Vivid Colors',
            'off': '⏸️ NexusAI: Izklopljeno'
        };
        this.showToast(labels[this.currentAiMode]);
    }

    updateAiBadgeUI() {
        const btn = document.getElementById('aiModeBtn');
        if (!btn) return;

        const modeMap = {
            '1440p': '✨ NexusAI: 1440p Ultra-Sharp',
            'hdr': '🎨 NexusAI: Cinema HDR',
            'vivid': '⚡ NexusAI: Vivid Boost',
            'off': '⏸️ NexusAI: Izklopljeno'
        };

        btn.textContent = modeMap[this.currentAiMode] || '✨ NexusAI';
        if (this.currentAiMode === 'off') {
            btn.classList.add('off');
        } else {
            btn.classList.remove('off');
        }
    }

    startStreamWatchdog(stream, titleStr) {
        if (this.streamWatchdogTimer) {
            clearTimeout(this.streamWatchdogTimer);
        }
    }

    closePlayer() {
        if (this.streamWatchdogTimer) {
            clearTimeout(this.streamWatchdogTimer);
        }
        if (this.artInstance) {
            try { this.artInstance.destroy(true); } catch (e) {}
            this.artInstance = null;
        }
        const overlay = document.getElementById('playerOverlay');
        const container = document.getElementById('playerVideoContainer');
        const detailsModal = document.getElementById('detailsModal');
        if (container) container.innerHTML = '';
        if (overlay) overlay.style.display = 'none';
        if (detailsModal) {
            detailsModal.style.visibility = 'visible';
        }
    }

    saveHistoryItem(item, season, episode) {
        const existingIdx = this.continueWatching.findIndex(i => i.id === item.id);
        const mType = item.media_type || (season || episode ? 'tv' : 'movie');
        const historyObj = {
            id: item.id,
            media_type: mType,
            title: item.title,
            poster: item.poster,
            backdrop: item.backdrop,
            rating: item.rating,
            year: item.year,
            season: season,
            episode: episode,
            updatedAt: Date.now()
        };

        if (existingIdx >= 0) {
            this.continueWatching.splice(existingIdx, 1);
        }
        this.continueWatching.unshift(historyObj);
        localStorage.setItem('streamnexus_history', JSON.stringify(this.continueWatching.slice(0, 15)));
        this.renderContinueWatching();
    }

    renderContinueWatching() {
        const section = document.getElementById('continueSection');
        const container = document.getElementById('continueGrid');
        
        if (!section || !container) return;

        if (this.continueWatching.length === 0) {
            section.style.display = 'none';
            return;
        }

        section.style.display = 'block';
        container.innerHTML = '';

        this.continueWatching.forEach(item => {
            const card = document.createElement('div');
            card.className = 'compact-continue-card';
            card.tabIndex = 0;
            const targetType = item.media_type || (item.season ? 'tv' : 'movie');
            card.onclick = () => this.openDetails(targetType, item.id);

            const epLabel = item.season ? `S${item.season}E${item.episode}` : '';

            card.innerHTML = `
                <div class="compact-continue-poster-wrapper">
                    <img class="compact-continue-poster" src="${item.poster || ''}" alt="${item.title}" loading="lazy">
                    <div class="card-overlay"><div class="card-play-icon" style="width:38px; height:38px; font-size:1rem;">▶</div></div>
                    <button class="btn-remove-continue" style="width:26px; height:26px; font-size:13px; top:6px; right:6px;" title="Odstrani iz seznama Nadaljuj z ogledom" onclick="event.stopPropagation(); app.removeFromContinueWatching(${item.id})">
                        &times;
                    </button>
                    ${epLabel ? `<div class="card-badge-top" style="left: 6px; right: auto; top: 6px;"><span class="badge badge-quality" style="font-size:0.7rem; padding:2px 6px;">${epLabel}</span></div>` : ''}
                </div>
                <div class="compact-continue-info">
                    <div class="compact-continue-title" title="${item.title}">${item.title}</div>
                    <div class="compact-continue-meta">
                        <span>Nadaljuj</span>
                        <span style="color:#ef4444; font-weight:800; cursor:pointer;" onclick="event.stopPropagation(); app.removeFromContinueWatching(${item.id})" title="Odstrani">✕</span>
                    </div>
                </div>
            `;
            container.appendChild(card);
        });
    }

    removeFromContinueWatching(itemId) {
        this.continueWatching = this.continueWatching.filter(i => i.id !== itemId);
        localStorage.setItem('streamnexus_history', JSON.stringify(this.continueWatching));
        this.renderContinueWatching();
        this.showToast('🗑️ Odstranjeno iz seznama Nadaljuj z ogledom');
    }

    clearAllContinueWatching() {
        if (confirm('Ali želite počistiti celoten seznam Nadaljuj z ogledom?')) {
            this.continueWatching = [];
            localStorage.removeItem('streamnexus_history');
            this.renderContinueWatching();
            this.showToast('🗑️ Seznam Nadaljuj z ogledom je počiščen');
        }
    }

    toggleAspectRatio() {
        const overlay = document.getElementById('playerOverlay');
        const aspectModes = ['aspect-16-9', 'aspect-21-9', 'aspect-fill'];
        const labels = { 'aspect-16-9': '16:9', 'aspect-21-9': '21:9 Kino', 'aspect-fill': 'Polno' };
        
        let currentIdx = 0;
        for (let i = 0; i < aspectModes.length; i++) {
            if (overlay.classList.contains(aspectModes[i])) {
                currentIdx = (i + 1) % aspectModes.length;
                break;
            }
        }
        
        aspectModes.forEach(m => overlay.classList.remove(m));
        overlay.classList.add(aspectModes[currentIdx]);
        
        const labelEl = document.getElementById('aspectLabel');
        if (labelEl) labelEl.textContent = labels[aspectModes[currentIdx]];
        this.showToast(`📐 Razmerje stranic: ${labels[aspectModes[currentIdx]]}`);
    }

    toggleFullscreen() {
        const container = document.getElementById('playerVideoContainer');
        const overlay = document.getElementById('playerOverlay');
        const target = container || overlay;

        if (!document.fullscreenElement && !document.webkitFullscreenElement && !document.mozFullScreenElement) {
            if (target.requestFullscreen) {
                target.requestFullscreen();
            } else if (target.webkitRequestFullscreen) {
                target.webkitRequestFullscreen();
            } else if (target.mozRequestFullScreen) {
                target.mozRequestFullScreen();
            }
            this.showToast('⛶ Celozaslonski način');
        } else {
            if (document.exitFullscreen) {
                document.exitFullscreen();
            } else if (document.webkitExitFullscreen) {
                document.webkitExitFullscreen();
            } else if (document.mozCancelFullScreen) {
                document.mozCancelFullScreen();
            }
            this.showToast('Nazaj v okno');
        }
    }

    renderWatchlist() {
        const titleEl = document.getElementById('feedTitle');
        titleEl.innerHTML = '❤️ Moja Lista Priljubljenih';
        this.renderMediaGrid(this.watchlist);
    }

    openModal(modalId) {
        const modal = document.getElementById(modalId);
        if (modal) modal.classList.add('active');
    }

    closeModal(modalId) {
        const modal = document.getElementById(modalId);
        if (modal) modal.classList.remove('active');
    }

    openSettings() {
        this.openModal('settingsModal');
        setTimeout(() => {
            const firstInput = document.getElementById('settingDefaultPlayer') || document.getElementById('settingSubEnabled');
            if (firstInput) this.tvRemote.setFocus(firstInput);
        }, 150);
    }

    showToast(message) {
        const toast = document.getElementById('toast');
        if (!toast) return;
        toast.textContent = message;
        toast.classList.add('show');
        setTimeout(() => toast.classList.remove('show'), 3500);
    }
}

// Initialize App on DOM Ready
document.addEventListener('DOMContentLoaded', () => {
    window.app = new StreamNexusApp();
});

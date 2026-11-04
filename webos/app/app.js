// Torrento Drive for LG webOS: the same screens as the Android TV app (ui/App.kt, ui/SettingsScreen.kt,
// ui/PlayerScreen.kt), with the same words (strings.js is generated from the Android string resources).
'use strict';

const VERSION = '0.3.0';
const SERVICE = 'luna://io.github.nindzyago.torrentodrive.webos.service';
const DEFAULT_SERVER = 'http://192.168.1.2:8090';
const METADATA_WAIT_S = 180;
const VIDEO_EXT = ['mkv', 'mp4', 'avi', 'm4v', 'mov', 'ts', 'm2ts', 'webm', 'wmv', 'mpg', 'mpeg'];
const NEXT_CARD_S = 20;
const PRELOAD_NEXT_S = 120;
const HIDE_CONTROLS_MS = 5000;
const SEEK_STEP_S = 10;

const KEY = {
    LEFT: 37, UP: 38, RIGHT: 39, DOWN: 40, ENTER: 13, BACK: 461, ESC: 27,
    PLAY: 415, PAUSE: 19, PLAY_PAUSE: 10252, STOP: 413, FF: 417, RW: 412, RED: 403,
    CH_UP: 33, CH_DOWN: 34,
};

const $ = (id) => document.getElementById(id);

// ---------- storage ----------

function load(key, fallback) {
    try {
        const v = localStorage.getItem(key);
        return v === null ? fallback : JSON.parse(v);
    } catch (e) {
        return fallback;
    }
}

function save(key, value) {
    try { localStorage.setItem(key, JSON.stringify(value)); } catch (e) { /* not critical */ }
}

function serverUrl() {
    return String(load('server', DEFAULT_SERVER)).replace(/\/+$/, '');
}

// ---------- words: the Android app's strings plus a few that only the webOS app needs ----------

const EXTRA = {
    en: {
        speak_hint: 'Press the microphone button on the remote and say a title',
        server_setup_webos: 'TorrServer at %1$s doesn\'t answer. Check that it is running, or set another address in settings.',
        open_settings: 'Open settings',
        check: 'Check',
        torrserver_address: 'Address',
        torrserver_help_webos: '1. TorrServer has to run in your home network: TorrServe on an Android box, a computer or a NAS.\n2. Enter its address above, for example http://192.168.1.2:8090.\n3. Press «Check»: the dot turns green when it answers.',
        android_only: 'Android app only for now',
        tracker_android_only: 'Signing in on the tracker\'s site isn\'t available on webOS yet',
        about_webos: 'Version for LG webOS TVs',
        no_tracks: 'No other audio tracks',
    },
    ru: {
        speak_hint: 'Нажмите кнопку микрофона на пульте и скажите название',
        server_setup_webos: 'TorrServer по адресу %1$s не отвечает. Проверьте, что он запущен, или укажите другой адрес в настройках.',
        open_settings: 'Открыть настройки',
        check: 'Проверить',
        torrserver_address: 'Адрес',
        torrserver_help_webos: '1. TorrServer должен работать в домашней сети: TorrServe на Android-приставке, компьютер или NAS.\n2. Укажи его адрес выше, например http://192.168.1.2:8090.\n3. Нажми «Проверить»: точка станет зелёной, когда он ответит.',
        android_only: 'пока только в Android-версии',
        tracker_android_only: 'Вход на сайте трекера на webOS пока недоступен',
        about_webos: 'Версия для телевизоров LG webOS',
        no_tracks: 'Других дорожек нет',
    },
};

const LANGUAGES = [
    { code: 'ru', name: 'Русский', flag: 'ru' },
    { code: 'en', name: 'English', flag: 'gb' },
];
const AUTO_LANG = /^ru|^uk|^be|^kk/i.test(navigator.language || '') ? 'ru' : 'en';
const LANG = load('language', null) || AUTO_LANG;

function words(lang) { return Object.assign({}, EXTRA[lang], window.STRINGS[lang]); }
const W = Object.assign({}, words('en'), words(LANG));

/** Android-style format: %s, %d, %.1f, %1$s… */
function format(s, args) {
    let i = 0;
    return s.replace(/%(?:(\d+)\$)?(\.\d+)?([sdf%])/g, (m, pos, prec, type) => {
        if (type === '%') return '%';
        const v = args[pos ? pos - 1 : i++];
        if (type === 'd') return String(Math.round(v));
        if (type === 'f') {
            const n = Number(v).toFixed(prec ? +prec.slice(1) : 6);
            return LANG === 'ru' ? n.replace('.', ',') : n;
        }
        return String(v);
    });
}

function t(key) {
    const s = W[key];
    return s === undefined ? key : format(s, Array.prototype.slice.call(arguments, 1));
}

const pluralRules = new Intl.PluralRules(LANG);
function tp(key, n) {
    const forms = W[key];
    return format(forms[pluralRules.select(n)] || forms.other, [n]);
}

// ---------- icons (ui/Icons.kt, 24x24 strokes) ----------

const ICONS = {
    mic: '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0 0 14 0"/><path d="M12 18v3"/>',
    search: '<circle cx="11" cy="11" r="7"/><path d="M16 16l4 4"/>',
    play: '<path d="M7 4l13 8-13 8z" fill="currentColor" stroke="none"/>',
    pause: '<rect x="6" y="4" width="4" height="16" rx="1" fill="currentColor" stroke="none"/><rect x="14" y="4" width="4" height="16" rx="1" fill="currentColor" stroke="none"/>',
    prev: '<path d="M19 5L8 12l11 7z" fill="currentColor" stroke="none"/><path d="M6 5v14" stroke-width="2.5"/>',
    next: '<path d="M5 5l11 7-11 7z" fill="currentColor" stroke="none"/><path d="M18 5v14" stroke-width="2.5"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    'chevron-up': '<path d="M6 15l6-6 6 6"/>',
    'chevron-down': '<path d="M6 9l6 6 6-6"/>',
    gear: '<path d="M12 2.6l1.5 2.3 2.7-.6.6 2.7 2.3 1.5-1.4 2.4 1.4 2.4-2.3 1.5-.6 2.7-2.7-.6L12 21.4l-1.5-2.3-2.7.6-.6-2.7-2.3-1.5 1.4-2.4-1.4-2.4 2.3-1.5.6-2.7 2.7.6z" stroke-width="1.8" stroke-linejoin="round"/><circle cx="12" cy="12" r="3" stroke-width="1.8"/>',
    globe: '<circle cx="12" cy="12" r="9"/><ellipse cx="12" cy="12" rx="4" ry="9" stroke-width="1.6"/><path d="M3 12h18" stroke-width="1.6"/><path d="M4.6 7.5h14.8M4.6 16.5h14.8" stroke-width="1.4"/>',
    info: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="7.8" r="1.3" fill="currentColor" stroke="none"/><path d="M12 11v6"/>',
};

// flags as pictures: the TV has no color emoji font
const FLAGS = {
    ru: '<svg viewBox="0 0 9 6"><rect width="9" height="2" fill="#fff"/><rect y="2" width="9" height="2" fill="#0039A6"/><rect y="4" width="9" height="2" fill="#D52B1E"/></svg>',
    gb: '<svg viewBox="0 0 60 30"><clipPath id="gbc"><path d="M0 0v30h60V0z"/></clipPath><g clip-path="url(#gbc)"><path d="M0 0v30h60V0z" fill="#012169"/><path d="M0 0l60 30m0-30L0 30" stroke="#fff" stroke-width="6"/><path d="M0 0l60 30m0-30L0 30" stroke="#C8102E" stroke-width="4"/><path d="M30 0v30M0 15h60" stroke="#fff" stroke-width="10"/><path d="M30 0v30M0 15h60" stroke="#C8102E" stroke-width="6"/></g></svg>',
};

function icon(name) {
    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">' + ICONS[name] + '</svg>';
}

function setIcon(el, name) {
    el.dataset.icon = name;
    const old = el.querySelector(':scope > svg');
    if (old) old.remove();
    el.insertAdjacentHTML('afterbegin', icon(name));
}

function setLabel(btn, text) {
    const svg = btn.querySelector(':scope > svg');
    btn.textContent = text;
    if (svg) btn.prepend(svg);
}

// ---------- formatting ----------

const formatSize = (b) => (b >= 1073741824 ? t('size_gb', b / 1073741824) : t('size_mb', b / 1048576));
const formatSpeed = (b) => (b >= 1048576 ? t('speed_mb', b / 1048576) : t('speed_kb', b / 1024));

function formatTime(sec) {
    sec = Math.max(0, Math.floor(sec || 0));
    const h = Math.floor(sec / 3600), m = Math.floor(sec / 60) % 60, s = sec % 60;
    return (h ? h + ':' + String(m).padStart(2, '0') : m) + ':' + String(s).padStart(2, '0');
}

function ago(ts) {
    const minutes = Math.floor((Date.now() - ts) / 60000);
    if (minutes < 1) return t('just_now');
    if (minutes < 60) return t('minutes_ago', minutes);
    if (minutes < 24 * 60) return t('hours_ago', Math.floor(minutes / 60));
    if (minutes < 48 * 60) return t('yesterday');
    return t('days_ago', Math.floor(minutes / 1440));
}

/** "Title / Original Title (Director…) [2010, …]" -> "Title": cards need a short title. */
function shortTitle(title) {
    return title.split(' / ')[0].split(' (')[0].split(' [')[0].trim() || title;
}

function quality(title) {
    if (/2160|\b4k\b|uhd/i.test(title)) return ['2160p', 'var(--warn)', 'rgba(255,181,71,.15)'];
    if (title.indexOf('1080') >= 0) return ['1080p', 'var(--good)', 'rgba(61,220,151,.15)'];
    if (title.indexOf('720') >= 0) return ['720p', 'var(--muted)', 'rgba(163,168,178,.15)'];
    return null;
}

// trackers/Category.kt with the words of every language pack (lang/Russian.kt, lang/English.kt)
const CATEGORIES = [
    ['cat_sport', /\bf1\b|ufc|\bnba\b|\bnhl\b|uefa|спорт|футбол|хоккей|теннис|формула|бокс|олимпи|sport|football|soccer|hockey|tennis|boxing/],
    ['cat_cartoons', /мульт|аниме|anime|cartoon|animation|animated/],
    ['cat_series', /\bs\d{1,2}\b|s\d{1,2}e\d{1,2}|сериал|сезон|серии|эпизод|tv series|episodes|season|episode/],
    ['cat_docs', /bbc|discovery|national geographic|документ|научно|познават|documentary|docu/],
    ['cat_music', /lossless|flac|\bmp3\b|музык|альбом|дискограф|концерт|саундтрек|music|album|discography|concert|soundtrack/],
    ['cat_movies', /hdrip|bdrip|web-?dl|webrip|remux|dvdrip|\b(720|1080|2160)p\b|фильм|кино|movie|film/],
];
function category(title) {
    const s = title.toLowerCase();
    for (const [key, re] of CATEGORIES) if (re.test(s)) return key;
    return 'cat_other';
}

const seedColor = (n) => (n >= 5 ? 'var(--good)' : n >= 1 ? 'var(--warn)' : 'var(--accent)');

// Java's String.hashCode, so a title gets the same placeholder color as on Android
function javaHash(s) {
    let h = 0;
    for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
    return h;
}

const HUES = ['#2B3D5C', '#5C3A2B', '#3B2B5C', '#2B5C4A', '#5C2B45', '#4A5C2B'];
function fillPoster(el, url, title) {
    el.style.backgroundImage = 'linear-gradient(135deg, ' + HUES[(javaHash(title) & 0x7fffffff) % HUES.length] + ', #0F1219)';
    el.textContent = title.slice(0, 1).toUpperCase();
    if (!url) return;
    const img = new Image();
    img.onload = () => { el.textContent = ''; el.style.backgroundImage = 'url("' + url.replace(/"/g, '%22') + '")'; };
    img.src = url;
}

function escapeHtml(s) {
    return String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
}

let toastTimer = null;
function toast(message) {
    const el = $('toast');
    el.textContent = message;
    el.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove('show'), 5000);
}

// ---------- the app's JS service (on a Mac: dev-server.js) ----------

const bridges = new Set();
function luna(uri, params) {
    return new Promise((resolve, reject) => {
        const bridge = new PalmServiceBridge();
        bridges.add(bridge); // keep a reference until the answer comes
        bridge.onservicecallback = (msg) => {
            bridges.delete(bridge);
            let r;
            try { r = JSON.parse(msg); } catch (e) { reject(new Error('Bad answer from the service')); return; }
            if (r.returnValue) resolve(r);
            else reject(new Error(r.errorText || 'Service error'));
        };
        bridge.call(uri, JSON.stringify(params));
    });
}

async function callService(method, params) {
    params = params || {};
    if (typeof PalmServiceBridge !== 'undefined') return luna(SERVICE + '/' + method, params);
    const query = Object.keys(params).map((k) => k + '=' + encodeURIComponent(params[k])).join('&');
    const r = await (await fetch('/api/' + method + '?' + query)).json();
    if (!r.returnValue) throw new Error(r.errorText);
    return r;
}

const posterCache = {};
function posterOf(page) {
    if (!page) return Promise.resolve(null);
    if (!(page in posterCache)) posterCache[page] = callService('poster', { page }).then((r) => r.url || null, () => null);
    return posterCache[page];
}

// ---------- TorrServer (answers with CORS *, so the page calls it directly) ----------

async function ts(body) {
    const resp = await fetch(serverUrl() + '/torrents', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    });
    if (!resp.ok) throw new Error('TorrServer HTTP ' + resp.status);
    return resp.json();
}

async function tsVersion() {
    try {
        const ctl = new AbortController();
        setTimeout(() => ctl.abort(), 4000);
        const resp = await fetch(serverUrl() + '/echo', { signal: ctl.signal });
        return resp.ok ? (await resp.text()).trim() : null;
    } catch (e) {
        return null;
    }
}

function streamUrl(hash, file, mode) {
    return serverUrl() + '/stream/' + encodeURIComponent(fileName(file)) + '?link=' + hash + '&index=' + file.id + '&' + mode;
}

const fileName = (f) => f.path.split('/').pop();

function statsHtml(st) {
    const seeds = st.connected_seeders || 0;
    return '<div class="stats">' +
        '<div class="stat"><b style="color:' + seedColor(seeds) + '">' + seeds + '</b><span>' + t('seeds_label') + '</span></div>' +
        '<div class="stat"><b>' + (st.active_peers || 0) + ' / ' + (st.total_peers || 0) + '</b><span>' + t('peers_label') + '</span></div>' +
        '<div class="stat"><b>' + formatSpeed(st.download_speed || 0) + '</b><span>' + t('speed_label') + '</span></div>' +
        '</div>' + (seeds === 0 ? '<div class="no-seeds">' + t('no_seeds') + '</div>' : '');
}

// ---------- watch history ("Continue watching") ----------

function history() { return load('watch', []); }
const progressKey = (hash, fileId) => hash + ':' + fileId;
function progressOf(hash, fileId) { return load('progress', {})[progressKey(hash, fileId)] || null; }
const resumable = (p) => !!p && !p.finished && p.pos > 30;

function saveProgress(hash, fileId, pos, dur) {
    if (!hash || !isFinite(dur) || dur <= 0) return;
    const all = load('progress', {});
    all[progressKey(hash, fileId)] = { pos: Math.min(pos, dur), dur, finished: pos > dur * 0.95 };
    save('progress', all);
}

function remember(entry) {
    save('watch', [entry].concat(history().filter((e) => e.magnet !== entry.magnet)).slice(0, 20));
}

// ---------- screens ----------

const stack = [];
let current = 'home';

function show(name, focusEl) {
    document.querySelectorAll('.screen').forEach((s) => s.classList.toggle('active', s.id === name));
    current = name;
    document.body.classList.toggle('dense', name !== 'home');
    document.body.classList.toggle('playing', name === 'player');
    const target = focusEl || document.querySelector('#' + name + ' .focusable:not(.hidden)');
    if (target) target.focus();
}

function open(name, focusEl) {
    stack.push({ name: current, focus: document.activeElement });
    show(name, focusEl);
}

function back() {
    if (current === 'home') {
        // Back from anywhere on the home screen returns to the voice button; Back on it leaves the app
        if (document.activeElement !== $('speak')) { $('query').value = ''; $('speak').focus(); return; }
        if (window.webOS && webOS.platformBack) webOS.platformBack();
        else window.close();
        return;
    }
    if (current === 'player') stopPlayer();
    if (current === 'loading') stopLoading();
    if (current === 'files') leaveTorrent();
    const prev = stack.pop() || { name: 'home' };
    show(prev.name, prev.focus && document.contains(prev.focus) ? prev.focus : null);
    if (prev.name === 'home') refreshHome();
}

function card(cls, html, onEnter) {
    const el = document.createElement('div');
    el.className = 'card focusable ' + cls;
    el.tabIndex = -1;
    el.innerHTML = html;
    el.onEnter = onEnter;
    el.addEventListener('click', onEnter);
    return el;
}

function button(label, onEnter, cls, iconName) {
    const b = document.createElement('button');
    b.className = 'btn focusable ' + (cls || '');
    b.textContent = label;
    if (iconName) setIcon(b, iconName);
    b.onEnter = onEnter;
    b.addEventListener('click', onEnter);
    return b;
}

// ---------- home ----------

let serverVersion = null;
let serverAlive = null;
async function checkServer() {
    serverAlive = null;
    paintServer();
    serverVersion = await tsVersion();
    serverAlive = !!serverVersion;
    paintServer();
    if (current === 'settings' && section === 'torrserver') renderSection();
}

function paintServer() {
    const cls = 'dot' + (serverAlive === true ? ' ok' : serverAlive === false ? ' bad' : '');
    $('server-dot').className = cls;
    $('nav-server-dot').className = cls + ' small-dot';
    $('server-label').textContent = serverAlive === null ? t('server_checking') : serverAlive ? 'TorrServer' : t('server_down');
    $('server-setup-text').textContent = t('server_setup_webos', serverUrl());
    $('server-setup').classList.toggle('hidden', serverAlive !== false);
}

function renderSuggestions() {
    const box = $('suggestions');
    box.innerHTML = '';
    const searches = load('searches', { typed: [], voice: [] });
    [['recent_typed', 'search', searches.typed, 'typed'], ['recent_spoken', 'mic', searches.voice, 'voice']].forEach(([label, ico, list, source]) => {
        if (!list.length) return;
        const row = document.createElement('div');
        row.className = 'sugg-row';
        row.innerHTML = '<span class="sugg-label"><span class="ico"></span>' + t(label) + '</span><div class="sugg-items"></div>';
        setIcon(row.querySelector('.ico'), ico);
        list.forEach((q) => row.querySelector('.sugg-items').appendChild(button(q, () => runSearch(q, source))));
        box.appendChild(row);
    });
}

let voiceMode = false;
function updateSuggestionsVisibility() {
    const el = document.activeElement;
    $('suggestions').classList.toggle('hidden', !(el === $('query') || $('suggestions').contains(el)));
    $('voice-hint').classList.toggle('hidden', !(el === $('query') && voiceMode));
}

function renderShelf() {
    const body = $('shelf-body');
    body.innerHTML = '';
    const list = history();
    if (!list.length) {
        body.innerHTML = '<div class="empty">' + t('history_empty') + '</div>';
        return;
    }
    list.forEach((e) => {
        const p = progressOf(e.hash, e.fileId);
        const html = '<div class="poster"></div>' +
            (p ? '<div class="progress"><div style="width:' + (p.finished ? 100 : Math.round(p.pos / p.dur * 100)) + '%"></div></div>' : '') +
            '<div class="info"><div class="t ellipsis">' + escapeHtml(shortTitle(e.title)) + '</div>' +
            '<div class="s ellipsis">' + escapeHtml(e.fileName) + ' · ' + ago(e.watchedAt) + '</div></div>';
        const c = card('hcard', html, () => openTorrent(e, e.fileId));
        fillPoster(c.querySelector('.poster'), e.poster, shortTitle(e.title));
        body.appendChild(c);
    });
}

function updateShelf() {
    const isOpen = $('shelf').contains(document.activeElement);
    $('shelf').classList.toggle('open', isOpen);
    setIcon($('shelf-handle').querySelector('.ico'), isOpen ? 'chevron-down' : 'chevron-up');
}

function refreshHome() {
    renderSuggestions();
    renderShelf();
    updateShelf();
    checkServer();
}

let searching = false;
async function runSearch(query, source) {
    query = (query || '').trim();
    if (!query || searching) return;
    searching = true;
    const searches = load('searches', { typed: [], voice: [] });
    const key = source === 'voice' ? 'voice' : 'typed';
    searches[key] = [query].concat(searches[key].filter((q) => q !== query)).slice(0, 6);
    save('searches', searches);

    $('results-query').textContent = query;
    $('results-count').textContent = t('searching');
    $('chips').innerHTML = '';
    $('results-list').innerHTML = '';
    open('results', $('results-query'));
    try {
        const items = (await callService('search', { query })).items;
        if (current === 'results') showResults(items);
    } catch (e) {
        $('results-count').textContent = t('tracker_error', 'Rutor', e.message);
    } finally {
        searching = false;
    }
}

// ---------- results ----------

function showResults(items) {
    items.forEach((it) => { it.category = category(it.title); });
    const trackers = items.map((it) => it.tracker).filter((x, i, a) => a.indexOf(x) === i).join(', ');
    $('results-count').textContent = items.length ? tp('results_count', items.length) + ' · ' + trackers : t('nothing_found');
    const counts = {};
    items.forEach((it) => { counts[it.category] = (counts[it.category] || 0) + 1; });
    const cats = Object.keys(counts).sort((a, b) => counts[b] - counts[a]);
    let selected = null;

    function renderList() {
        const list = $('results-list');
        list.innerHTML = '';
        items.filter((it) => !selected || it.category === selected).forEach((it) => {
            const q = quality(it.title);
            const html = '<div class="main"><div class="t">' + escapeHtml(it.title) + '</div><div class="tags">' +
                (q ? '<span class="tag" style="color:' + q[1] + ';background:' + q[2] + '">' + q[0] + '</span>' : '') +
                '<span class="tag" style="background:rgba(163,168,178,.15)">' + t(it.category) + '</span>' +
                '<span>' + escapeHtml(it.size) + ' · ' + it.tracker + '</span></div></div>' +
                '<div class="seeds"><b style="color:' + seedColor(it.seeds) + '">' + it.seeds + '</b><span>' + t('seeds_label') + '</span></div>';
            list.appendChild(card('rcard', html, () => openTorrent(it)));
        });
    }

    const chips = $('chips');
    chips.innerHTML = '';
    if (cats.length > 1) {
        const all = button(t('all_count', items.length), () => pick(null, all), 'selected');
        chips.appendChild(all);
        cats.forEach((c) => {
            const b = button(t(c) + ' · ' + counts[c], () => pick(c, b));
            chips.appendChild(b);
        });
    }
    function pick(c, b) {
        selected = c;
        chips.querySelectorAll('.btn').forEach((x) => x.classList.toggle('selected', x === b));
        renderList();
    }
    renderList();
    const first = $('results-list').firstChild;
    if (first) first.focus();
}

// ---------- files ----------

let torrent = null; // { item, hash, files, poster }
let openToken = 0;

async function openTorrent(item, resumeFileId) {
    const token = ++openToken;
    torrent = { item, hash: null, files: [], poster: item.poster || null };
    fillPoster($('files-poster'), torrent.poster, shortTitle(item.title));
    if (!torrent.poster) posterOf(item.page).then((url) => {
        if (!url) return;
        save('watch', history().map((e) => (e.magnet === item.magnet ? Object.assign(e, { poster: url }) : e)));
        if (torrent && torrent.item === item) { torrent.poster = url; fillPoster($('files-poster'), url, shortTitle(item.title)); }
    });
    $('files-title').textContent = shortTitle(item.title);
    $('files-meta').textContent = [quality(item.title) && quality(item.title)[0], item.size].filter(Boolean).join(' · ');
    paintSeeds(item.seeds || 0);
    $('files-tracker').textContent = item.tracker || 'Rutor';
    $('files-status').textContent = t('waiting_files');
    $('files-stats').innerHTML = '';
    $('files-wait').classList.remove('hidden');
    $('files-ready').classList.add('hidden');
    open('files', $('files-title'));
    try {
        const added = await ts({ action: 'add', link: item.magnet, title: item.title, save_to_db: false });
        torrent.hash = added.hash;
        for (let i = 0; i < METADATA_WAIT_S; i++) {
            if (token !== openToken) return;
            const st = await ts({ action: 'get', hash: added.hash });
            $('files-stats').innerHTML = statsHtml(st);
            paintSeeds(st.connected_seeders || 0);
            if (st.file_stats && st.file_stats.length) {
                showFiles(st.file_stats, resumeFileId);
                return;
            }
            await new Promise((r) => setTimeout(r, 1000));
        }
        $('files-status').innerHTML = '<span style="color:var(--accent)">' + escapeHtml(t('open_failed', t('no_seeds'))) + '</span>';
    } catch (e) {
        if (token === openToken) $('files-status').innerHTML = '<span style="color:var(--accent)">' + escapeHtml(t('open_failed', e.message)) + '</span>';
    }
}

function paintSeeds(n) {
    $('files-seeds').textContent = t('seeds_dot', n);
    $('files-seeds').style.color = seedColor(n);
}

function showFiles(fileStats, resumeFileId) {
    const files = torrent.files = fileStats
        .filter((f) => VIDEO_EXT.indexOf(f.path.split('.').pop().toLowerCase()) >= 0)
        .sort((a, b) => a.path.localeCompare(b.path, undefined, { numeric: true }));
    const total = files.reduce((s, f) => s + f.length, 0);
    $('files-meta').textContent = [tp('files_count', files.length), quality(torrent.item.title) && quality(torrent.item.title)[0],
        total ? formatSize(total) : torrent.item.size].filter(Boolean).join(' · ');
    if (!files.length) {
        $('files-status').innerHTML = '<span style="color:var(--accent)">' + t('no_video') + '</span>';
        return;
    }
    $('files-wait').classList.add('hidden');
    $('files-ready').classList.remove('hidden');
    $('files-kind').textContent = t(files.length > 1 ? 'episodes' : 'file');

    // continue the last episode if it was left midway, otherwise offer the one after it
    const watched = history().find((e) => e.magnet === torrent.item.magnet);
    const lastIdx = watched ? files.findIndex((f) => f.id === watched.fileId) : -1;
    const lastUnfinished = lastIdx >= 0 && resumable(progressOf(torrent.hash, files[lastIdx].id)) ? files[lastIdx] : null;
    const next = lastUnfinished || (lastIdx >= 0 ? files[lastIdx + 1] : null);

    const cont = $('files-continue');
    cont.classList.toggle('hidden', !next);
    if (next) {
        setLabel(cont, t('continue_n', files.indexOf(next) + 1));
        cont.onEnter = () => play(files.indexOf(next));
    }

    const list = $('files-list');
    list.innerHTML = '';
    const focusFile = next || files[lastIdx] || files[0];
    let focusEl = null;
    files.forEach((f, i) => {
        const p = progressOf(torrent.hash, f.id);
        const seen = (p && p.finished) || (lastIdx >= 0 && i < lastIdx);
        const isLast = i === lastIdx;
        const html = (resumable(p) ? '<div class="progress"><div style="width:' + Math.round(p.pos / p.dur * 100) + '%"></div></div>' : '') +
            '<span class="n' + (isLast ? ' last' : '') + '">' + String(i + 1).padStart(2, '0') + '</span>' +
            '<span class="name' + (seen ? ' seen' : '') + '">' + escapeHtml(fileName(f)) + '</span>' +
            (isLast ? '<span class="tag x" style="color:var(--accent-soft);background:rgba(255,138,102,.15)">' + t('watched_last') + '</span>' : '') +
            (resumable(p) ? '<span class="x soft">' + formatTime(p.pos) + '</span>' : seen ? '<span class="x">' + t('watched') + '</span>' : '') +
            '<span class="x">' + formatSize(f.length) + '</span>';
        const c = card('fcard', html, () => play(i));
        list.appendChild(c);
        if (f === focusFile) focusEl = c;
    });
    focusEl.focus();

    if (resumeFileId !== undefined) {
        const i = files.findIndex((f) => f.id === resumeFileId);
        if (i >= 0) play(i);
    } else if (files.length === 1) {
        play(0);
    }
}

function leaveTorrent() {
    openToken++;
    if (torrent && torrent.hash) ts({ action: 'drop', hash: torrent.hash }).catch(() => {});
    torrent = null;
}

// ---------- loading: fill the TorrServer cache with the start of the file ----------

let fileIndex = 0;
let loadingTimer = null;
let loadingToken = 0;

function play(index) {
    fileIndex = index;
    const file = torrent.files[index];
    remember({
        title: torrent.item.title, magnet: torrent.item.magnet, page: torrent.item.page, poster: torrent.poster,
        tracker: torrent.item.tracker, size: torrent.item.size, seeds: torrent.item.seeds,
        hash: torrent.hash, fileId: file.id, fileName: fileName(file), watchedAt: Date.now(),
    });
    // resuming midway or switching episodes in the player: the player shows its own "Loading…"
    if (current === 'player' || resumable(progressOf(torrent.hash, file.id))) { startPlayer(); return; }

    const token = ++loadingToken;
    $('loading-title').textContent = shortTitle(torrent.item.title);
    $('loading-file').textContent = fileName(file);
    $('loading-buffer').textContent = t('connecting');
    $('loading-fill').style.width = '0';
    $('loading-stats').innerHTML = '';
    open('loading', $('watch-now'));
    fetch(streamUrl(torrent.hash, file, 'preload')).then(() => { if (token === loadingToken && current === 'loading') startPlayer(); }, () => {});
    clearInterval(loadingTimer);
    loadingTimer = setInterval(async () => {
        try {
            const st = await ts({ action: 'get', hash: torrent.hash });
            if (token !== loadingToken) return;
            const size = st.preload_size || 0, done = st.preloaded_bytes || 0;
            if (size > 0) {
                $('loading-buffer').textContent = t('buffer_of', formatSize(done), formatSize(size));
                $('loading-fill').style.width = Math.min(100, done / size * 100) + '%';
                if (done >= size && current === 'loading') startPlayer();
            }
            $('loading-stats').innerHTML = statsHtml(st);
        } catch (e) { /* next tick */ }
    }, 1000);
}

function stopLoading() {
    loadingToken++;
    clearInterval(loadingTimer);
}

// ---------- player ----------

const video = $('video');
let controls = true;
let hideTimer = null;
let tickTimer = null;
let resumedFrom = null;
let nextDismissed = false;
let preloadedNext = false;
let lastSave = 0;
let seekRepeat = 0;

const playerFile = () => torrent && torrent.files[fileIndex];
const nextFile = () => torrent && torrent.files[fileIndex + 1];

function startPlayer() {
    stopLoading();
    const file = playerFile();
    const saved = progressOf(torrent.hash, file.id);
    resumedFrom = resumable(saved) ? saved.pos : null;
    nextDismissed = false;
    preloadedNext = false;
    const count = torrent.files.length;
    $('p-title').textContent = torrent.item.title;
    $('p-episode').textContent = t('player_episode_of', fileIndex + 1, count);
    $('p-episode').classList.toggle('hidden', count < 2);
    $('p-file').textContent = fileName(file);
    $('p-prev').classList.toggle('hidden', fileIndex === 0);
    $('p-next').classList.toggle('hidden', !nextFile());
    $('p-error').classList.add('hidden');
    $('p-next-card').classList.add('hidden');
    $('p-buffering').classList.remove('hidden');
    $('p-buffer-stats').textContent = '';
    paintResumed();
    paintTracks();
    video.src = streamUrl(torrent.hash, file, 'play');
    video.play().catch(() => {});
    if (current === 'loading') show('player', $('p-play'));
    else if (current !== 'player') open('player', $('p-play'));
    setControls(true);
    clearInterval(tickTimer);
    tickTimer = setInterval(tick, 1000);
}

function stopPlayer() {
    clearInterval(tickTimer);
    savePosition();
    video.pause();
    video.removeAttribute('src');
    video.load();
}

function savePosition() {
    const f = playerFile();
    if (f && video.getAttribute('src')) saveProgress(torrent.hash, f.id, video.currentTime, video.duration);
}

function switchTo(index) {
    if (!torrent || index < 0 || index >= torrent.files.length) return;
    savePosition();
    play(index);
}

function paintResumed() {
    $('p-resumed').classList.toggle('hidden', resumedFrom === null);
    $('p-from-start').classList.toggle('hidden', resumedFrom === null);
    if (resumedFrom !== null) $('p-resumed').textContent = t('player_resumed', formatTime(resumedFrom));
}

function paintPlay() {
    setIcon($('p-play'), video.paused ? 'play' : 'pause');
    setLabel($('p-play'), t(video.paused ? 'player_play' : 'player_pause'));
}

// audio and subtitle tracks, if the TV's engine exposes them
function audioTracks() {
    const list = video.audioTracks;
    return list ? Array.from({ length: list.length }, (_, i) => list[i]) : [];
}
function textTracks() {
    const list = video.textTracks;
    return list ? Array.from({ length: list.length }, (_, i) => list[i]).filter((x) => x.kind === 'subtitles' || x.kind === 'captions') : [];
}
function trackLabel(x, i) {
    let language = x.language && x.language !== 'und' ? x.language : '';
    try { if (language) language = new Intl.DisplayNames([LANG], { type: 'language' }).of(language); } catch (e) { /* keep the code */ }
    return [x.label, language].filter((v, k, a) => v && a.indexOf(v) === k).join(' · ') || t('track_n', i + 1);
}

function paintTracks() {
    const audio = audioTracks();
    $('p-audio').classList.toggle('hidden', audio.length < 2);
    if (audio.length > 1) {
        const i = Math.max(0, audio.findIndex((x) => x.enabled));
        setLabel($('p-audio'), t('player_audio', trackLabel(audio[i], i)));
    }
    const subs = textTracks();
    $('p-subs').classList.toggle('hidden', !subs.length);
    if (subs.length) {
        const i = subs.findIndex((x) => x.mode === 'showing');
        setLabel($('p-subs'), t('player_subs', i >= 0 ? trackLabel(subs[i], i) : t('player_off')));
    }
}

function cycleAudio() {
    const audio = audioTracks();
    if (audio.length < 2) { toast(t('no_tracks')); return; }
    const next = (Math.max(0, audio.findIndex((x) => x.enabled)) + 1) % audio.length;
    audio.forEach((x, i) => { x.enabled = i === next; });
    paintTracks();
}

// subtitles go round in a loop with an "off" step
function cycleSubs() {
    const subs = textTracks();
    if (!subs.length) return;
    const next = subs.findIndex((x) => x.mode === 'showing') + 1;
    subs.forEach((x, i) => { x.mode = i === next ? 'showing' : 'disabled'; });
    paintTracks();
}

function setControls(on) {
    controls = on;
    $('p-overlay').classList.toggle('off', !on);
    $('p-next-card').classList.toggle('raised', on);
    clearTimeout(hideTimer);
    if (on) hideTimer = setTimeout(() => { if (!video.paused) setControls(false); }, HIDE_CONTROLS_MS);
    placeFocus();
}

const nextCardShown = () => !$('p-next-card').classList.contains('hidden');
const errorShown = () => !$('p-error').classList.contains('hidden');

function placeFocus() {
    if (errorShown()) $('p-error-back').focus();
    else if (nextCardShown() && !controls) $('p-next-play').focus();
    else if (controls) { if (!$('p-overlay').contains(document.activeElement)) $('p-play').focus(); }
    else $('player').focus();
}

function seekBy(sec) {
    if (!isFinite(video.duration)) return;
    video.currentTime = Math.max(0, Math.min(video.duration - 1, video.currentTime + sec));
    paintTime();
}

function togglePlay() {
    if (video.paused) video.play().catch(() => {}); else video.pause();
}

function paintTime() {
    const d = video.duration, pos = video.currentTime;
    $('p-time').textContent = formatTime(pos);
    $('p-left').textContent = isFinite(d) && d > 0 ? '−' + formatTime(d - pos) + ' · ' + formatTime(d) : '';
    $('p-pos').style.width = (isFinite(d) && d > 0 ? pos / d * 100 : 0) + '%';
    if (video.buffered.length && isFinite(d) && d > 0) $('p-buffered').style.width = video.buffered.end(video.buffered.length - 1) / d * 100 + '%';
}

async function tick() {
    if (!torrent || current !== 'player') return;
    const f = playerFile();
    if (!video.paused && Date.now() - lastSave > 5000) {
        lastSave = Date.now();
        saveProgress(torrent.hash, f.id, video.currentTime, video.duration);
    }
    const d = video.duration;
    const remaining = isFinite(d) && d > 0 ? d - video.currentTime : Infinity;
    const next = nextFile();
    // warm up the next episode in TorrServer shortly before the end
    if (next && !preloadedNext && remaining < PRELOAD_NEXT_S) {
        preloadedNext = true;
        fetch(streamUrl(torrent.hash, next, 'preload')).catch(() => {});
    }
    const showNext = !!next && !nextDismissed && !errorShown() && remaining <= NEXT_CARD_S;
    if (showNext) {
        $('p-next-in').textContent = t('next_in', Math.ceil(remaining));
        $('p-next-label').textContent = t('next_label', fileIndex + 2, fileName(next));
    }
    if (showNext !== nextCardShown()) {
        $('p-next-card').classList.toggle('hidden', !showNext);
        placeFocus();
    }
    if (!$('p-buffering').classList.contains('hidden')) {
        try {
            const st = await ts({ action: 'get', hash: torrent.hash });
            const seeds = st.connected_seeders || 0;
            $('p-buffer-stats').textContent = t('seeds_dot', seeds) + ' · ' + formatSpeed(st.download_speed || 0);
            $('p-buffer-stats').style.color = seedColor(seeds);
        } catch (e) { /* next tick */ }
    }
}

video.addEventListener('loadedmetadata', () => {
    if (resumedFrom !== null && resumedFrom < video.duration - 30) video.currentTime = resumedFrom;
    paintTracks();
    paintTime();
});
video.addEventListener('timeupdate', paintTime);
video.addEventListener('playing', () => { $('p-buffering').classList.add('hidden'); paintPlay(); setControls(controls); });
video.addEventListener('waiting', () => $('p-buffering').classList.remove('hidden'));
video.addEventListener('pause', () => { paintPlay(); if (video.getAttribute('src') && !video.ended) setControls(true); });
video.addEventListener('play', paintPlay);
if (video.audioTracks) video.audioTracks.addEventListener('change', paintTracks);
video.addEventListener('ended', () => {
    savePosition();
    if (nextFile() && !nextDismissed) switchTo(fileIndex + 1);
    else back();
});
video.addEventListener('error', () => {
    if (!video.getAttribute('src')) return;
    const err = video.error;
    $('p-buffering').classList.add('hidden');
    $('p-error-text').textContent = t('player_error', (err && err.message) || 'MEDIA_ERR ' + (err ? err.code : '?'));
    $('p-error').classList.remove('hidden');
    setControls(false);
});

// the first OK only brings up the overlay; pause is the second press, on the focused button
function playerKey(e) {
    const code = e.keyCode;
    switch (code) {
        case KEY.PLAY_PAUSE: togglePlay(); setControls(true); return true;
        case KEY.PLAY: video.play().catch(() => {}); return true;
        case KEY.PAUSE: video.pause(); return true;
        case KEY.CH_UP: switchTo(fileIndex + 1); return true;
        case KEY.CH_DOWN: switchTo(fileIndex - 1); return true;
        case KEY.FF: seekBy(SEEK_STEP_S * 3); setControls(true); return true;
        case KEY.RW: seekBy(-SEEK_STEP_S * 3); setControls(true); return true;
        case KEY.RED: cycleAudio(); return true;
        case KEY.STOP: back(); return true;
    }
    if (!controls && !nextCardShown() && !errorShown()) {
        switch (code) {
            case KEY.ENTER: setControls(true); return true;
            case KEY.LEFT: seekBy(-SEEK_STEP_S); setControls(true); return true;
            case KEY.RIGHT: seekBy(SEEK_STEP_S); setControls(true); return true;
            case KEY.UP: case KEY.DOWN: setControls(true); return true;
        }
        return false;
    }
    if (controls) setControls(true); // any key keeps the overlay up a little longer
    const el = document.activeElement;
    if (el === $('p-bar') && (code === KEY.LEFT || code === KEY.RIGHT)) {
        // holding the key seeks faster, like on Android
        seekRepeat = e.repeat ? seekRepeat + 1 : 0;
        const step = SEEK_STEP_S * Math.min(6, 1 + Math.floor(seekRepeat / 5));
        seekBy(code === KEY.LEFT ? -step : step);
        return true;
    }
    if (code >= KEY.LEFT && code <= KEY.DOWN) { moveFocus(code); return true; }
    if (code === KEY.ENTER && el && el.onEnter) { el.onEnter(); return true; }
    return false;
}

function playerBack() {
    if (controls && !errorShown()) setControls(false); else back();
}

$('p-play').onEnter = togglePlay;
$('p-prev').onEnter = () => switchTo(fileIndex - 1);
$('p-next').onEnter = () => switchTo(fileIndex + 1);
$('p-from-start').onEnter = () => { video.currentTime = 0; resumedFrom = null; paintResumed(); $('p-play').focus(); };
$('p-audio').onEnter = cycleAudio;
$('p-subs').onEnter = cycleSubs;
$('p-next-play').onEnter = () => switchTo(fileIndex + 1);
$('p-next-cancel').onEnter = () => { nextDismissed = true; $('p-next-card').classList.add('hidden'); placeFocus(); };
$('p-error-back').onEnter = back;
['p-play', 'p-prev', 'p-next', 'p-from-start', 'p-audio', 'p-subs', 'p-next-play', 'p-next-cancel', 'p-error-back']
    .forEach((id) => $(id).addEventListener('click', () => $(id).onEnter()));
$('player').tabIndex = -1;

// ---------- settings ----------

let section = 'connection';

function openSettings() {
    open('settings', $('settings-nav').querySelector('[data-section="' + section + '"]'));
    renderSection();
}

function selectSection(name) {
    if (section === name && $('settings-body').childElementCount) return;
    section = name;
    renderSection();
}

function sectionHeader(title, subtitle) {
    return '<div class="s-head">' + escapeHtml(title) + '</div>' + (subtitle ? '<div class="s-sub">' + escapeHtml(subtitle) + '</div>' : '');
}

function radioCard(titleHtml, description, selected, badge, onEnter, disabled) {
    return card('radio' + (selected ? ' on' : '') + (disabled ? ' disabled' : ''),
        '<span class="mark"></span><div><div class="rt">' + titleHtml +
        (badge ? '<span class="tag" style="color:' + (selected ? 'var(--accent-soft)' : 'var(--muted)') + ';background:' +
            (selected ? 'rgba(255,138,102,.15)' : 'rgba(163,168,178,.15)') + '">' + escapeHtml(badge) + '</span>' : '') +
        '</div>' + (description ? '<div class="rd">' + escapeHtml(description) + '</div>' : '') + '</div>', onEnter);
}

let reach = null;      // last connection check: [{ name, millis }]
let checking = false;

async function checkTrackers() {
    if (checking) return;
    checking = true;
    renderSection();
    try { reach = (await callService('reach')).trackers; } catch (e) { reach = [{ name: 'Rutor', millis: null }]; }
    checking = false;
    if (current === 'settings' && section === 'connection') renderSection();
}

function renderSection() {
    const body = $('settings-body');
    const focusedIndex = Array.prototype.indexOf.call(body.querySelectorAll('.focusable'), document.activeElement);
    body.innerHTML = '';
    $('settings-nav').querySelectorAll('.nav-item').forEach((el) => el.classList.toggle('selected', el.dataset.section === section));
    const add = (html) => { const d = document.createElement('div'); d.innerHTML = html; while (d.firstChild) body.appendChild(d.firstChild); };

    if (section === 'connection') {
        const chosen = load('connection', 'auto');
        const choose = (id) => { save('connection', id); renderSection(); };
        add(sectionHeader(t('connection_title'), t('connection_subtitle')));
        body.appendChild(radioCard(escapeHtml(t('connection_auto')), t('connection_auto_hint'), chosen === 'auto', t('connection_recommended'), () => choose('auto')));
        body.appendChild(radioCard(escapeHtml(t('connection_direct')), t('connection_direct_hint'), chosen === 'direct', null, () => choose('direct')));
        body.appendChild(radioCard(escapeHtml(t('connection_byedpi')), t('connection_byedpi_hint') + ' (' + t('android_only') + ')', false, 'byedpi', () => {}, true));
        const panel = document.createElement('div');
        panel.className = 'panel';
        const caption = checking ? t('connection_checking')
            : reach ? t('connection_check') + ' · ' + t('connection_in_use', t('connection_direct')) : t('connection_check');
        panel.innerHTML = '<div class="panel-head"><span class="caps">' + escapeHtml(caption) + '</span><span class="grow"></span></div>';
        panel.firstChild.appendChild(button(t('connection_check_again'), checkTrackers));
        (reach || [{ name: 'Rutor' }]).forEach((r) => {
            const [color, text] = !reach ? ['var(--muted)', t('reach_unknown')]
                : r.millis === null ? ['var(--accent)', t('reach_failed')]
                    : ['var(--good)', t('reach_ok', t('connection_direct'), r.millis)];
            panel.insertAdjacentHTML('beforeend', '<div class="reach"><span class="dot" style="background:' + color + '"></span><b>' +
                escapeHtml(r.name) + '</b><span class="muted">' + escapeHtml(text) + '</span></div>');
        });
        body.appendChild(panel);
        if (!reach && !checking) checkTrackers();
    } else if (section === 'trackers') {
        add(sectionHeader(t('trackers'), t('trackers_subtitle')));
        const row = (name, sub, on, onToggle, disabled) => {
            const r = document.createElement('div');
            r.className = 'row-card' + (disabled ? ' disabled' : '');
            const sw = document.createElement('div');
            sw.className = 'switch focusable' + (on ? ' on' : '');
            sw.tabIndex = -1;
            sw.innerHTML = '<div><div></div></div>';
            sw.onEnter = onToggle;
            sw.addEventListener('click', onToggle);
            r.appendChild(sw);
            r.insertAdjacentHTML('beforeend', '<div class="main"><div class="rt"' + (on ? '' : ' style="color:var(--muted)"') + '>' + name +
                '</div><div class="rd">' + escapeHtml(sub) + '</div></div>');
            body.appendChild(r);
        };
        const rutorOn = load('rutor', true);
        row('Rutor', rutorOn ? t('tracker_no_login') : t('tracker_off'), rutorOn, () => { save('rutor', !rutorOn); renderSection(); });
        row('RuTracker', t('tracker_android_only'), false, () => toast(t('tracker_android_only')), true);
        add('<div class="add-row"><span class="ico" data-icon="plus"></span><div><div class="rt">' + escapeHtml(t('add_tracker')) +
            '</div><div class="rd">' + escapeHtml(t('add_tracker_hint')) + '</div></div></div>');
    } else if (section === 'torrserver') {
        add(sectionHeader('TorrServer'));
        const ok = !!serverVersion;
        add('<div class="row-card"><span class="dot" style="width:18px;height:18px;margin:0 4px;background:' + (ok ? 'var(--good)' : 'var(--accent)') + '"></span>' +
            '<div class="main"><div class="rt">' + escapeHtml(t(ok ? 'torrserver_running' : 'server_down')) + '</div>' +
            '<div class="rd">' + escapeHtml([serverVersion, serverUrl().replace(/^https?:\/\//, '')].filter(Boolean).join(' · ')) + '</div></div></div>');
        const fieldRow = document.createElement('div');
        fieldRow.className = 'settings-field';
        fieldRow.innerHTML = '<label class="field"><span class="muted small">' + escapeHtml(t('torrserver_address')) + '</span><input id="server" class="focusable" type="text" autocomplete="off"></label>';
        const input = fieldRow.querySelector('input');
        input.value = serverUrl();
        const apply = () => { save('server', input.value.trim() || DEFAULT_SERVER); checkServer(); };
        input.addEventListener('keydown', (e) => { if (e.keyCode === KEY.ENTER) { e.preventDefault(); apply(); } });
        input.addEventListener('change', apply);
        fieldRow.appendChild(button(t('check'), apply, 'primary'));
        body.appendChild(fieldRow);
        add('<div class="caps">' + escapeHtml(t('torrserver_help_title')) + '</div><div class="help">' + escapeHtml(t('torrserver_help_webos')) + '</div>');
    } else if (section === 'language') {
        const chosen = load('language', null);
        const pick = (code) => {
            if (code === chosen) return;
            save('language', code);
            location.reload(); // start over in the new language, as if just opened
        };
        const flag = (code) => '<span class="rflag">' + FLAGS[code] + '</span>';
        add(sectionHeader(t('settings_language')));
        const auto = LANGUAGES.find((l) => l.code === AUTO_LANG);
        body.appendChild(radioCard(flag(auto.flag) + escapeHtml(t('language_auto', auto.name)), null, chosen === null, null, () => pick(null)));
        LANGUAGES.forEach((l) => body.appendChild(radioCard(flag(l.flag) + escapeHtml(l.name), null, chosen === l.code, null, () => pick(l.code))));
    } else if (section === 'about') {
        add(sectionHeader('Torrento Drive', t('about_version', VERSION) + ' · ' + t('about_webos')));
        add('<div class="help">' + escapeHtml(t('about_source', 'github.com/nindzyago/torrento-drive')) + '\n' + escapeHtml(t('about_license')) + '</div>');
    }
    body.querySelectorAll('[data-icon]').forEach((el) => setIcon(el, el.dataset.icon));
    const again = body.querySelectorAll('.focusable')[focusedIndex];
    if (focusedIndex >= 0 && again) again.focus();
}

$('settings-nav').querySelectorAll('.nav-item').forEach((el) => {
    // focusing a section is enough to open it
    el.addEventListener('focus', () => selectSection(el.dataset.section));
    el.onEnter = () => { const f = $('settings-body').querySelector('.focusable'); if (f) f.focus(); };
});

// ---------- D-pad spatial navigation ----------

function focusables() {
    return Array.from(document.querySelectorAll('#' + current + ' .focusable'))
        .filter((el) => el.offsetParent !== null && !el.closest('.hidden') && !el.closest('.off'));
}

function moveFocus(code) {
    const items = focusables();
    const cur = document.activeElement;
    if (!items.length) return;
    if (items.indexOf(cur) < 0) { items[0].focus(); return; }
    // coming back to the settings menu lands on the open section, not on whichever item is nearest
    if (current === 'settings' && code === KEY.LEFT && $('settings-body').contains(cur)) {
        $('settings-nav').querySelector('[data-section="' + section + '"]').focus();
        return;
    }
    const a = cur.getBoundingClientRect();
    let best = null, bestScore = Infinity;
    items.forEach((el) => {
        if (el === cur) return;
        const b = el.getBoundingClientRect();
        let along, across;
        if (code === KEY.DOWN) { along = b.top - a.bottom; across = gap(a.left, a.right, b.left, b.right); }
        else if (code === KEY.UP) { along = a.top - b.bottom; across = gap(a.left, a.right, b.left, b.right); }
        else if (code === KEY.RIGHT) { along = b.left - a.right; across = gap(a.top, a.bottom, b.top, b.bottom); }
        else { along = a.left - b.right; across = gap(a.top, a.bottom, b.top, b.bottom); }
        if (along < -8) return;
        const score = Math.max(0, along) + across * 4;
        if (score < bestScore) { bestScore = score; best = el; }
    });
    if (best) {
        best.focus({ preventScroll: true });
        best.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    }
}

// distance between two segments on the cross axis, 0 when they overlap
function gap(a1, a2, b1, b2) {
    return Math.max(0, b1 - a2, a1 - b2);
}

document.addEventListener('keydown', (e) => {
    const code = e.keyCode;
    const el = document.activeElement;
    const inInput = el && el.tagName === 'INPUT';

    if (code === KEY.BACK || (code === KEY.ESC && !inInput)) {
        e.preventDefault();
        if (current === 'player') playerBack(); else back();
        return;
    }
    if (current === 'player') {
        if (playerKey(e)) e.preventDefault();
        return;
    }
    if (code >= KEY.LEFT && code <= KEY.DOWN) {
        if (inInput && (code === KEY.LEFT || code === KEY.RIGHT)) {
            const atEdge = code === KEY.LEFT ? el.selectionStart === 0 : el.selectionEnd === el.value.length;
            if (!atEdge) return;
        }
        e.preventDefault();
        moveFocus(code);
        return;
    }
    if (code === KEY.ENTER && el && el.onEnter) {
        e.preventDefault();
        el.onEnter();
    }
});

document.addEventListener('focusin', () => {
    if (current === 'home') { updateSuggestionsVisibility(); updateShelf(); }
});

// Magic Remote pointer: whatever it hovers gets the focus
document.addEventListener('mouseover', (e) => {
    const el = e.target.closest && e.target.closest('.focusable');
    if (el && document.activeElement !== el && !(document.activeElement && document.activeElement.tagName === 'INPUT')) el.focus({ preventScroll: true });
});
$('player').addEventListener('mousemove', () => { if (current === 'player' && !controls) setControls(true); });

// ---------- start ----------

document.documentElement.lang = LANG;
document.querySelectorAll('[data-t]').forEach((el) => { el.textContent = t(el.dataset.t); });
document.querySelectorAll('[data-icon]').forEach((el) => setIcon(el, el.dataset.icon));
$('nav-flag').innerHTML = FLAGS[LANGUAGES.find((l) => l.code === LANG).flag];
$('query').placeholder = t('search_hint');
$('settings-btn').setAttribute('aria-label', t('settings'));

$('backdrop-img').style.backgroundImage = 'url(img/backdrop_default.jpg)';

// "Speak": web apps get no microphone; the remote's mic button types into a focused text field
$('speak').onEnter = () => { voiceMode = true; $('query').focus(); updateSuggestionsVisibility(); };
$('speak').addEventListener('click', $('speak').onEnter);
$('query').addEventListener('blur', () => { setTimeout(() => { if (document.activeElement !== $('query')) voiceMode = false; }, 0); });
$('query').addEventListener('keydown', (e) => { if (e.keyCode === KEY.ENTER) { e.preventDefault(); runSearch($('query').value, voiceMode ? 'voice' : 'typed'); } });
// voice input fills the whole field at once: search as soon as the text settles
let voiceTimer = null;
$('query').addEventListener('input', (e) => {
    clearTimeout(voiceTimer);
    if (e.data && e.data.length > 1) voiceTimer = setTimeout(() => runSearch($('query').value, 'voice'), 800);
});

$('settings-btn').onEnter = openSettings;
$('settings-btn').addEventListener('click', openSettings);
$('setup-settings').onEnter = () => { section = 'torrserver'; openSettings(); };
$('shelf-handle').onEnter = () => { const c = $('shelf-body').querySelector('.card'); if (c) c.focus(); };
$('watch-now').onEnter = startPlayer;

refreshHome();
$('speak').focus();

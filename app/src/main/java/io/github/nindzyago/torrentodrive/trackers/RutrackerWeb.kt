package io.github.nindzyago.torrentodrive.trackers

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONTokener
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * All RuTracker traffic goes through a real browser engine (WebView), so the site sees an ordinary browser,
 * and if Cloudflare asks to prove you're not a robot, the WebView is shown on screen and a human does it.
 */
@SuppressLint("SetJavaScriptEnabled")
class RutrackerWeb(context: Context) {

    /** true while Cloudflare is waiting for a human: the WebView must be shown over the UI. */
    val challenge = mutableStateOf(false)

    /** true while the rutracker.org sign-in page is on screen. */
    val manualLogin = mutableStateOf(false)

    /** The signed-in username (null if not signed in). */
    val user = mutableStateOf<String?>(null)

    private val prefs = context.getSharedPreferences("rutracker", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var pageLoaded: CompletableDeferred<Unit>? = null
    private val pending = ConcurrentHashMap<String, CompletableDeferred<String>>()

    val webView: WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                pageLoaded?.complete(Unit)
                if (manualLogin.value && url.startsWith(BASE)) checkManualLogin(view)
            }
        }
        addJavascriptInterface(Bridge(), "TvBridge")
    }

    init {
        CookieManager.getInstance().setAcceptCookie(true)
        user.value = prefs.getString(KEY_USER, null)
    }

    suspend fun search(query: String): List<TorrentItem> = mutex.withLock {
        val q = URLEncoder.encode(query, "windows-1251")
        // o=10: sort by seeders, s=2: descending
        load("$BASE/forum/tracker.php?nm=$q&o=10&s=2")
        if (!isLoggedIn()) {
            setUser(null)
            throw LoginRequired(NAME)
        }
        val arr = JSONArray(jsString(SEARCH_JS) ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TorrentItem(
                trackerId = ID,
                tracker = NAME,
                title = o.getString("title"),
                size = o.getString("size").replace(Regex("\\s+"), " "),
                seeds = o.getInt("seeds"),
                peers = o.getInt("peers"),
                link = o.getString("id"),
                forum = o.optString("forum"),
            )
        }
    }

    /**
     * Torrent poster: the first image of the first post. The page is fetched from the RuTracker context
     * and parsed with DOMParser, so the page's images aren't loaded and the WebView doesn't waste memory.
     */
    suspend fun poster(topicId: String): String? = mutex.withLock {
        if (!onRutracker()) load("$BASE/forum/index.php")
        val url = jsAsync(
            "fetch('/forum/viewtopic.php?t=$topicId',{credentials:'include'})" +
                ".then(function(r){return r.arrayBuffer();})" +
                ".then(function(b){var html=new TextDecoder('windows-1251').decode(b);" +
                "var d=new DOMParser().parseFromString(html,'text/html');" +
                "var e=d.querySelector('.post_body var.postImg, .post_body img.postImg, .post_body img');" +
                "return e?(e.getAttribute('title')||e.getAttribute('src')||''):'';})"
        )
        url.takeIf { it.startsWith("http") }
    }

    /** Downloads the .torrent from the RuTracker page context, with cookies, like an ordinary browser. */
    suspend fun torrentFile(topicId: String): ByteArray = mutex.withLock {
        if (!onRutracker()) load("$BASE/forum/index.php")
        val b64 = jsAsync(
            "fetch('/forum/dl.php?t=$topicId',{credentials:'include'})" +
                ".then(function(r){if(!r.ok)throw new Error('HTTP '+r.status);return r.arrayBuffer();})" +
                ".then(function(b){var u=new Uint8Array(b),s='';for(var i=0;i<u.length;i+=32768)" +
                "{s+=String.fromCharCode.apply(null,u.subarray(i,i+32768));}return btoa(s);})"
        )
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        // a .torrent is a bencoded dictionary starting with 'd'; otherwise the site returned HTML (not signed in, rate limit, etc.)
        if (bytes.isEmpty() || bytes[0] != 'd'.code.toByte()) error("RuTracker did not return a .torrent — try signing in again")
        bytes
    }

    /**
     * Opens the real rutracker.org sign-in page full screen. The user signs in directly on the site
     * (with the remote-driven cursor and the system keyboard); the app never sees the username or password,
     * it only notices from the page that sign-in succeeded and keeps the session.
     */
    fun startLogin() {
        manualLogin.value = true
        webView.loadUrl("$BASE/forum/login.php")
    }

    fun cancelLogin() {
        manualLogin.value = false
        webView.loadUrl("about:blank")
    }

    suspend fun logout() = withContext(Dispatchers.Main) {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        webView.loadUrl("about:blank")
        setUser(null)
    }

    /** During sign-in, checks after every page load whether the user has signed in yet. */
    private fun checkManualLogin(view: WebView) {
        view.evaluateJavascript("(function(){var a=document.querySelector('#logged-in-username');return a?a.textContent.trim():null;})()") { raw ->
            val name = JSONTokener(raw ?: "null").nextValue() as? String ?: return@evaluateJavascript
            CookieManager.getInstance().flush()
            setUser(name)
            manualLogin.value = false
            view.loadUrl("about:blank")
        }
    }

    private fun setUser(name: String?) {
        user.value = name
        prefs.edit().apply { if (name == null) remove(KEY_USER) else putString(KEY_USER, name) }.apply()
    }

    private suspend fun load(url: String) {
        val done = CompletableDeferred<Unit>()
        withContext(Dispatchers.Main) {
            pageLoaded = done
            webView.loadUrl(url)
        }
        withTimeout(PAGE_TIMEOUT) { done.await() }
        passChallenge()
    }

    /** If the page shows a Cloudflare check, shows the WebView and waits until it passes (on its own or with human help). */
    private suspend fun passChallenge() {
        if (!isChallenge()) return
        withContext(Dispatchers.Main) {
            challenge.value = true
            webView.requestFocus()
        }
        try {
            withTimeout(CHALLENGE_TIMEOUT) {
                while (isChallenge()) {
                    val next = CompletableDeferred<Unit>()
                    withContext(Dispatchers.Main) { pageLoaded = next }
                    withTimeoutOrNull(2_000) { next.await() }
                }
            }
            delay(500)
        } finally {
            withContext(Dispatchers.Main) { challenge.value = false }
        }
    }

    private suspend fun isChallenge(): Boolean = js(
        "return document.title.indexOf('Just a moment')>=0 || !!document.querySelector('#challenge-form,.cf-turnstile,#cf-challenge-running,[id^=\"cf-chl\"],script[src*=\"challenges.cloudflare.com\"]');"
    ) == "true"

    private suspend fun isLoggedIn(): Boolean =
        js("return !!document.querySelector('#logged-in-username, a[href*=\"logout\"]');") == "true"

    private suspend fun onRutracker(): Boolean = withContext(Dispatchers.Main) {
        webView.url?.startsWith(BASE) == true
    }

    /** Runs a JS function body and returns the result as JSON, the way evaluateJavascript returns it. */
    private suspend fun js(body: String): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            webView.evaluateJavascript("(function(){$body})()") { cont.resume(it ?: "null") }
        }
    }

    private suspend fun jsString(body: String): String? = JSONTokener(js(body)).nextValue() as? String

    /** Runs a JS promise and waits for the result via the TvBridge bridge. */
    private suspend fun jsAsync(promise: String): String {
        val id = UUID.randomUUID().toString()
        val result = CompletableDeferred<String>()
        pending[id] = result
        js("($promise).then(function(r){TvBridge.done('$id',String(r));}).catch(function(e){TvBridge.fail('$id',String(e));});return true;")
        return try {
            withTimeout(PAGE_TIMEOUT) { result.await() }
        } finally {
            pending.remove(id)
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun done(id: String, data: String) {
            pending[id]?.complete(data)
        }

        @JavascriptInterface
        fun fail(id: String, error: String) {
            pending[id]?.completeExceptionally(Exception(error))
        }
    }

    companion object {
        const val ID = "rutracker"
        const val NAME = "RuTracker"
        private const val BASE = "https://rutracker.org"
        private const val KEY_USER = "user"
        private const val PAGE_TIMEOUT = 60_000L
        private const val CHALLENGE_TIMEOUT = 180_000L

        private val SEARCH_JS = """
            var out = [];
            document.querySelectorAll('tr.hl-tr, tr[id^="trs-tr-"]').forEach(function (tr) {
              var a = tr.querySelector('a.tLink, a[href*="viewtopic.php?t="]');
              if (!a) return;
              var m = (a.getAttribute('href') || '').match(/t=(\d+)/);
              var id = a.getAttribute('data-topic_id') || (m && m[1]);
              if (!id) return;
              var size = tr.querySelector('td.tor-size a, td.tor-size');
              var seeds = tr.querySelector('.seedmed');
              var peers = tr.querySelector('.leechmed');
              var forum = tr.querySelector('td.f-name-col a, .f-name a, a.gen.f');
              out.push({
                id: id,
                title: a.textContent.trim(),
                size: size ? size.textContent.replace(/[^\d.,A-Za-zА-Яа-я ]/g, ' ').trim() : '',
                seeds: seeds ? (parseInt(seeds.textContent, 10) || 0) : 0,
                peers: peers ? (parseInt(peers.textContent, 10) || 0) : 0,
                forum: forum ? forum.textContent.trim() : ''
              });
            });
            return JSON.stringify(out);
        """.trimIndent()
    }
}

class RutrackerProvider(private val web: RutrackerWeb) : TrackerProvider {
    override val id = RutrackerWeb.ID
    override val name = RutrackerWeb.NAME

    override suspend fun search(query: String) = web.search(query)

    override suspend fun source(item: TorrentItem) = TorrentSource.File(web.torrentFile(item.link))

    override suspend fun poster(item: TorrentItem) = web.poster(item.link)

    override val requiresSignIn = true
    override val isSignedIn: Boolean get() = web.user.value != null
    override fun startSignIn() = web.startLogin()
    override suspend fun signOut() = web.logout()
}

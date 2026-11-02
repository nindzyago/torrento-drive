package io.github.nindzyago.torrentodrive.connection

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import io.github.nindzyago.torrentodrive.Http
import io.github.nindzyago.torrentodrive.trackers.TrackerProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

/** How a tracker's website answered: through which method, how fast; [method] null = through none of them. */
data class Reach(val method: ConnectionMethod?, val millis: Long = 0)

/**
 * The connection to the trackers: the method chosen in settings ("Automatic" or a specific one), the one in use,
 * and which trackers it reaches. [check] probes the trackers and routes tracker traffic (OkHttp and the
 * WebView) through the chosen method.
 */
object Connection {
    const val AUTO = "auto"

    /** [AUTO] or a [ConnectionMethod.id]. */
    val chosen = mutableStateOf(AUTO)

    /** The method in use; null until the first check is done. */
    val active = mutableStateOf<ConnectionMethod?>(null)

    val checking = mutableStateOf(false)

    /** Last check, by tracker id. */
    val reach = mutableStateMapOf<String, Reach>()

    private val mutex = Mutex()

    fun load(context: Context) {
        chosen.value = prefs(context).getString(KEY, AUTO) ?: AUTO
    }

    suspend fun choose(context: Context, id: String, trackers: List<TrackerProvider>) {
        chosen.value = id
        prefs(context).edit().putString(KEY, id).apply()
        check(context, trackers)
    }

    /**
     * Probes the trackers and picks the route. Automatic: directly if every tracker answers, otherwise the first
     * method that reaches the ones that didn't.
     */
    suspend fun check(context: Context, trackers: List<TrackerProvider>) = mutex.withLock {
        checking.value = true
        try {
            val fixed = ConnectionMethods.byId(chosen.value)
            val candidates = if (fixed != null) listOf(fixed) else ConnectionMethods.all
            val result = mutableMapOf<String, Reach>()
            var use: Pair<ConnectionMethod, InetSocketAddress?>? = null
            for (method in candidates) {
                val proxy = runCatching { method.start(context) }.getOrElse { continue }
                val missing = trackers.filter { result[it.id]?.method == null }
                val found = probe(missing, proxy)
                missing.forEach { t -> result[t.id] = found[t.id]?.let { Reach(method, it) } ?: Reach(null) }
                if (found.isNotEmpty() || use == null) use = method to proxy
                if (result.values.all { it.method != null }) break
            }
            val (method, proxy) = use ?: (Direct to null)
            route(proxy)
            active.value = method
            reach.clear()
            reach.putAll(result)
        } finally {
            checking.value = false
        }
    }

    /** Waits while a check is running, so a search right after start-up already goes the right way. */
    suspend fun awaitReady() {
        withTimeoutOrNull(20_000) { while (checking.value || active.value == null) delay(100) }
    }

    /** Which trackers answer through [proxy], with the time to the first response. */
    private suspend fun probe(trackers: List<TrackerProvider>, proxy: InetSocketAddress?): Map<String, Long> = coroutineScope {
        val client = Http.trackersClient(proxy).newBuilder().callTimeout(PROBE_TIMEOUT, TimeUnit.MILLISECONDS).build()
        trackers.map { t ->
            async(Dispatchers.IO) {
                val start = System.currentTimeMillis()
                // any HTTP answer counts, even an error page: what matters is that the site is reachable
                runCatching { client.newCall(Request.Builder().url(t.site).head().build()).execute().close() }
                    .map { t.id to System.currentTimeMillis() - start }.getOrNull()
            }
        }.awaitAll().filterNotNull().toMap()
    }

    private suspend fun route(proxy: InetSocketAddress?) {
        Http.useTrackerProxy(proxy)
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return
        withContext(Dispatchers.Main) {
            val controller = ProxyController.getInstance()
            if (proxy == null) controller.clearProxyOverride({ it.run() }, {})
            else controller.setProxyOverride(
                ProxyConfig.Builder().addProxyRule("socks5://${proxy.hostString}:${proxy.port}").build(), { it.run() }, {},
            )
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences("connection", Context.MODE_PRIVATE)

    private const val KEY = "method"
    private const val PROBE_TIMEOUT = 8_000L
}

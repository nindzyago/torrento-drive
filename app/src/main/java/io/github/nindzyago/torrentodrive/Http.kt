package io.github.nindzyago.torrentodrive

import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 12; TV) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                    .build()
            )
        }
        .build()

    /** For tracker websites: goes the way the current connection method says (see connection.Connection). */
    @Volatile
    var trackers: OkHttpClient = client
        private set

    fun trackersClient(proxy: InetSocketAddress?): OkHttpClient =
        if (proxy == null) client else client.newBuilder().proxy(Proxy(Proxy.Type.SOCKS, proxy)).build()

    fun useTrackerProxy(proxy: InetSocketAddress?) {
        trackers = trackersClient(proxy)
    }
}

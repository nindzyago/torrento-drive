package io.github.nindzyago.torrentodrive.connection

import android.content.Context
import io.github.nindzyago.torrentodrive.R
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * ByeDPI: changes how the start of each connection is sent (splits it, reorders the pieces…), so that network
 * filters don't recognize the site while the site itself receives everything as usual.
 * Runs inside the app, without a VPN; see [ByeDpi].
 */
object ByeDpiMethod : ConnectionMethod {
    override val id = "byedpi"
    override val title = R.string.connection_byedpi
    override val description = R.string.connection_byedpi_hint
    override val badge = "byedpi ${ByeDpi.VERSION}"
    override suspend fun start(context: Context) = ByeDpi.start()
}

/**
 * The byedpi core (app/src/main/cpp/byedpi) as a SOCKS5 proxy on a random loopback port, on a thread of this
 * process: it starts on first use and lives as long as the app.
 */
object ByeDpi {
    const val VERSION = "0.17.3+"
    private const val TAG = "ByeDpi"

    /**
     * Strategies for byedpi's automatic mode: the first group is used by default, and when a site times out,
     * resets the connection or doesn't answer the TLS hello, the next group is tried and remembered for that site.
     */
    private val STRATEGY = listOf(
        "--disorder", "1", "--tlsrec", "1+s",
        "--auto", "torst,ssl_err", "--split", "1+s", "--disorder", "3+s",
        "--auto", "torst,ssl_err", "--fake", "-1", "--ttl", "8",
        "--auto", "torst,ssl_err", "--oob", "1", "--tlsrec", "3+s",
    )

    private var address: InetSocketAddress? = null

    suspend fun start(): InetSocketAddress = withContext(Dispatchers.IO) {
        synchronized(this@ByeDpi) {
            address ?: run {
                System.loadLibrary("byedpi")
                val port = ServerSocket(0).use { it.localPort }
                val args = arrayOf("byedpi", "--ip", "127.0.0.1", "--port", port.toString()) + STRATEGY
                // returns only if the arguments are wrong or the port is taken
                thread(isDaemon = true, name = "byedpi") { Log.w(TAG, "byedpi stopped: ${run(args)}") }
                InetSocketAddress("127.0.0.1", port).also { address = it }
            }
        }.also { awaitListening(it) }
    }

    private suspend fun awaitListening(address: InetSocketAddress) {
        repeat(30) {
            if (runCatching { Socket().use { it.connect(address, 200) } }.isSuccess) return
            delay(100)
        }
        error("byedpi did not start")
    }

    @JvmStatic
    private external fun run(args: Array<String>): Int
}

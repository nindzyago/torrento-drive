package io.github.nindzyago.torrentodrive.connection

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * The byedpi core (app/src/main/cpp/byedpi) as a SOCKS5 proxy on a loopback port, on a thread of this
 * process: it starts on first use and lives as long as the app. It can be restarted with a single strategy,
 * which is how [Diagnostics] finds out what each one does on this network.
 */
object ByeDpi {
    const val VERSION = "0.17.3+"
    private const val TAG = "ByeDpi"

    /**
     * Ways to send the start of a connection, one byedpi group each. The first [IN_USE] make up the automatic
     * mode: the first is the default, and when a site resets the connection, stays silent or doesn't answer the
     * TLS hello, the next one is tried and remembered for that site. The rest are candidates that only the
     * diagnostics tries, to learn which are worth moving up.
     */
    val STRATEGIES: List<List<String>> = listOf(
        listOf("--disorder", "1", "--tlsrec", "1+s"),
        listOf("--split", "1+s", "--disorder", "3+s"),
        listOf("--fake", "-1", "--ttl", "8"),
        listOf("--oob", "1", "--tlsrec", "3+s"),
        listOf("--fake", "-1", "--md5sig"),
        listOf("--disoob", "1", "--tlsrec", "1+s"),
        listOf("--split", "1", "--disorder", "3+s", "--tlsrec", "1+s", "--fake", "-1", "--md5sig", "--fake-tls-mod", "rand"),
    )
    private const val IN_USE = 4

    /**
     * Filters usually drop the packets silently instead of resetting the connection, so without a timeout byedpi
     * would never notice the failure and never move on to the next strategy. Seconds to wait for the TLS answer.
     */
    private const val TLS_TIMEOUT = "2"

    internal val AUTOMATIC: List<String> = listOf("--tls-timeout", TLS_TIMEOUT) +
        STRATEGIES.take(IN_USE).reduce { all, next -> all + listOf("--auto", "torst,ssl_err") + next }

    private var port = 0
    private var worker: Thread? = null
    private var running: List<String>? = null

    /** The automatic mode; cheap when it is already running. */
    suspend fun start(): InetSocketAddress = launch(AUTOMATIC)

    /** Restarts byedpi with [strategy] alone, with no fallback to the others. */
    suspend fun startOnly(strategy: List<String>): InetSocketAddress = launch(strategy)

    suspend fun stop() = withContext(Dispatchers.IO) { synchronized(this@ByeDpi) { halt() } }

    private suspend fun launch(strategy: List<String>): InetSocketAddress = withContext(Dispatchers.IO) {
        synchronized(this@ByeDpi) {
            if (running != strategy || worker?.isAlive != true) {
                halt()
                System.loadLibrary("byedpi")
                if (port == 0) port = ServerSocket(0).use { it.localPort }
                val args = (listOf("byedpi", "--ip", "127.0.0.1", "--port", port.toString()) + strategy).toTypedArray()
                // returns when stopped, or at once if the arguments are wrong or the port is taken
                worker = thread(isDaemon = true, name = "byedpi") { Log.i(TAG, "byedpi stopped: ${run(args)}") }
                running = strategy
            }
            InetSocketAddress("127.0.0.1", port)
        }.also { awaitListening(it) }
    }

    private fun halt() {
        val thread = worker ?: return
        if (thread.isAlive) {
            stopLoop()
            thread.join(3_000)
            check(!thread.isAlive) { "byedpi did not stop" }
        }
        worker = null
        running = null
    }

    private suspend fun awaitListening(address: InetSocketAddress) {
        repeat(30) {
            if (runCatching { Socket().use { it.connect(address, 200) } }.isSuccess) return
            if (worker?.isAlive != true) error("byedpi did not start")
            delay(100)
        }
        error("byedpi did not start")
    }

    @JvmStatic
    private external fun run(args: Array<String>): Int

    @JvmStatic
    private external fun stopLoop()
}

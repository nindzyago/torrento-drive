package io.github.nindzyago.torrentodrive.connection

import org.json.JSONObject
import java.io.EOFException
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketTimeoutException
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Opens a site's front page by hand, to say exactly how far it got: connection, TLS handshake, answer, page.
 * Plain Java sockets, no Android: [Diagnostics] turns the outcome into words.
 */
internal object Probe {
    enum class Stage { Connect, Tls, Answer, Page }

    enum class Failure { Timeout, Refused, Reset, Closed, Tls, WrongCertificate, Other }

    /** [failure] null = the page came; [status] is the HTTP code, [bytes] how much of the page was read. */
    data class Outcome(
        val stage: Stage,
        val failure: Failure?,
        val status: String = "",
        val bytes: Int = 0,
        val millis: Long = 0,
        val detail: String = "",
    )

    private const val BODY_LIMIT = 64 * 1024

    /**
     * Asks [address] for the front page of [host], optionally through the SOCKS [proxy]. Reads the page too,
     * since some filters let the handshake through and cut the connection after the first kilobytes.
     * [timeout]: milliseconds of silence that count as a failure; the whole page gets twice that.
     */
    fun fetch(
        host: String,
        address: InetAddress,
        proxy: InetSocketAddress? = null,
        port: Int = 443,
        timeout: Int = 7_000,
        sockets: SSLSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory,
        verifier: HostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier(),
    ): Outcome {
        val start = System.currentTimeMillis()
        fun elapsed() = System.currentTimeMillis() - start
        var stage = Stage.Connect
        var status = ""
        var received = 0
        try {
            val socket = if (proxy == null) Socket() else Socket(Proxy(Proxy.Type.SOCKS, proxy))
            socket.use { raw ->
                raw.connect(InetSocketAddress(address, port), timeout)
                raw.soTimeout = timeout
                stage = Stage.Tls
                val tls = sockets.createSocket(raw, host, port, true) as SSLSocket
                tls.startHandshake()
                if (!verifier.verify(host, tls.session)) return Outcome(stage, Failure.WrongCertificate, millis = elapsed())
                stage = Stage.Answer
                tls.outputStream.write(
                    "GET / HTTP/1.1\r\nHost: $host\r\nUser-Agent: Mozilla/5.0\r\nAccept: */*\r\nConnection: close\r\n\r\n".toByteArray()
                )
                val buffer = ByteArray(8192)
                val head = StringBuilder()
                while (received < BODY_LIMIT) {
                    if (elapsed() > timeout * 2) throw SocketTimeoutException()
                    val n = tls.inputStream.read(buffer)
                    if (n < 0) break
                    if (head.length < 64) head.append(String(buffer, 0, minOf(n, 64), Charsets.ISO_8859_1))
                    received += n
                    status = statusOf(head)
                    stage = Stage.Page
                }
            }
            if (status.isEmpty()) throw EOFException()
            return Outcome(stage, null, status, received, elapsed())
        } catch (e: Exception) {
            return Outcome(stage, failureOf(e), status, received, elapsed(), e.message?.take(60) ?: e.javaClass.simpleName)
        }
    }

    /** "HTTP/1.1 200 OK…" → "200"; empty until a whole status line with a numeric code has come. */
    internal fun statusOf(head: CharSequence): String =
        head.lineSequence().first().split(' ').getOrNull(1)?.takeIf { it.length == 3 && it.all(Char::isDigit) }.orEmpty()

    internal fun failureOf(e: Exception): Failure {
        val message = e.message.orEmpty()
        return when {
            e is SocketTimeoutException || message.contains("timed out", ignoreCase = true) -> Failure.Timeout
            e is ConnectException -> Failure.Refused
            message.contains("reset", ignoreCase = true) -> Failure.Reset
            e is EOFException || listOf("closed", "EOF", "terminated").any { message.contains(it, ignoreCase = true) } -> Failure.Closed
            e is SSLException -> Failure.Tls
            else -> Failure.Other
        }
    }

    /** The IPv4 addresses in a DNS-over-HTTPS JSON answer (the format Google and Cloudflare share). */
    fun addressesIn(json: String): List<String> {
        val answers = JSONObject(json).optJSONArray("Answer") ?: return emptyList()
        return (0 until answers.length()).map(answers::getJSONObject).filter { it.optInt("type") == 1 }.map { it.getString("data") }
    }
}

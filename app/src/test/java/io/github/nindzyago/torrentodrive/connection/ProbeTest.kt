package io.github.nindzyago.torrentodrive.connection

import io.github.nindzyago.torrentodrive.connection.Probe.Failure
import io.github.nindzyago.torrentodrive.connection.Probe.Stage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.EOFException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.security.KeyStore
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.TrustManagerFactory
import kotlin.concurrent.thread

/** [Probe] against local servers that behave like a filtered network: silent, cutting off, answering. */
class ProbeTest {
    private val loopback = InetAddress.getLoopbackAddress()
    private val servers = mutableListOf<ServerSocket>()
    private val held = mutableListOf<Socket>()

    @After
    fun close() {
        servers.forEach { runCatching { it.close() } }
        held.forEach { runCatching { it.close() } }
    }

    /** A server on a free port that hands every accepted connection to [serve]. */
    private fun server(socket: ServerSocket = ServerSocket(0, 50, loopback), serve: (Socket) -> Unit): Int {
        servers += socket
        thread(isDaemon = true) {
            while (true) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { serve(client) } }
            }
        }
        return socket.localPort
    }

    /** The test certificate (CN=tracker.test), for both the server and the client that trusts it. */
    private val tls: SSLContext by lazy {
        val keys = KeyStore.getInstance("PKCS12").apply {
            load(checkNotNull(ProbeTest::class.java.getResourceAsStream("/probe-test.p12")), "testtest".toCharArray())
            check(isKeyEntry("probe")) { "aliases: ${aliases().toList()}" }
        }
        val km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keys, "testtest".toCharArray()) }
        val tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(keys) }
        SSLContext.getInstance("TLS").apply { init(km.keyManagers, tm.trustManagers, null) }
    }

    private val sameName = HostnameVerifier { host, session -> session.peerPrincipal.name == "CN=$host" }

    /** An HTTPS server that sends [bodyBytes] of page and closes. */
    private fun site(bodyBytes: Int): Int = server(tls.serverSocketFactory.createServerSocket(0, 50, loopback)) { client ->
        client.use {
            val input = it.getInputStream().bufferedReader()
            while (input.readLine().orEmpty().isNotEmpty()) Unit
            it.getOutputStream().apply {
                write("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n".toByteArray())
                write(ByteArray(bodyBytes) { 'x'.code.toByte() })
                flush()
            }
        }
    }

    @Test
    fun `a server that stays silent is a timeout at the TLS stage`() {
        val port = server { held += it }
        val o = Probe.fetch("tracker.test", loopback, port = port, timeout = 400)
        assertEquals(Stage.Tls, o.stage)
        assertEquals(Failure.Timeout, o.failure)
        assertTrue("waited ${o.millis} ms", o.millis in 350..3000)
    }

    @Test
    fun `a server that hangs up is a cut-off at the TLS stage`() {
        val port = server { it.close() }
        val o = Probe.fetch("tracker.test", loopback, port = port, timeout = 2000)
        assertEquals(Stage.Tls, o.stage)
        assertTrue("${o.failure} ${o.detail}", o.failure == Failure.Closed || o.failure == Failure.Reset)
    }

    @Test
    fun `nothing listening is a refusal at the connection stage`() {
        val port = ServerSocket(0, 1, loopback).use { it.localPort }
        val o = Probe.fetch("tracker.test", loopback, port = port, timeout = 2000)
        assertEquals(Stage.Connect, o.stage)
        assertEquals(Failure.Refused, o.failure)
    }

    @Test
    fun `an answer that is not TLS is a TLS error`() {
        val port = server { it.use { c -> c.getOutputStream().write("HTTP/1.1 403 Forbidden\r\n\r\n".toByteArray()) } }
        val o = Probe.fetch("tracker.test", loopback, port = port, timeout = 2000)
        assertEquals(Stage.Tls, o.stage)
        assertEquals(Failure.Tls, o.failure)
    }

    @Test
    fun `a site that answers gives the status and the size of the page`() {
        val o = Probe.fetch("tracker.test", loopback, port = site(20_000), timeout = 3000, sockets = tls.socketFactory, verifier = sameName)
        assertNull("${o.failure} ${o.detail}", o.failure)
        assertEquals(Stage.Page, o.stage)
        assertEquals("200", o.status)
        assertTrue("read ${o.bytes}", o.bytes in 20_000..20_100)
    }

    @Test
    fun `a long page is read up to the limit only`() {
        val o = Probe.fetch("tracker.test", loopback, port = site(500_000), timeout = 3000, sockets = tls.socketFactory, verifier = sameName)
        assertNull("${o.failure} ${o.detail}", o.failure)
        assertTrue("read ${o.bytes}", o.bytes in 64 * 1024..80 * 1024)
    }

    @Test
    fun `a certificate for another name is reported as such`() {
        val o = Probe.fetch("other.test", loopback, port = site(100), timeout = 3000, sockets = tls.socketFactory, verifier = sameName)
        assertEquals(Failure.WrongCertificate, o.failure)
    }

    @Test
    fun `an untrusted certificate is a TLS error`() {
        val o = Probe.fetch("tracker.test", loopback, port = site(100), timeout = 3000)
        assertEquals(Stage.Tls, o.stage)
        assertEquals(Failure.Tls, o.failure)
    }

    @Test
    fun `failures are told apart by exception`() {
        assertEquals(Failure.Timeout, Probe.failureOf(SocketTimeoutException("Read timed out")))
        assertEquals(Failure.Reset, Probe.failureOf(SocketException("Connection reset")))
        assertEquals(Failure.Reset, Probe.failureOf(SSLHandshakeException("Connection reset by peer")))
        assertEquals(Failure.Closed, Probe.failureOf(EOFException()))
        assertEquals(Failure.Tls, Probe.failureOf(SSLHandshakeException("PKIX path building failed")))
        assertEquals(Failure.Other, Probe.failureOf(SocketException("SOCKS: Host unreachable")))
    }

    @Test
    fun `the status comes from a whole status line`() {
        assertEquals("200", Probe.statusOf("HTTP/1.1 200 OK\r\nServer: x"))
        assertEquals("302", Probe.statusOf("HTTP/1.1 302"))
        assertEquals("", Probe.statusOf("HTTP/1.1 2"))
        assertEquals("", Probe.statusOf("<html>blocked</html>"))
    }

    @Test
    fun `addresses are read from a DNS-over-HTTPS answer`() {
        val json = """{"Status":0,"Answer":[
            {"name":"tracker.test.","type":5,"TTL":60,"data":"cdn.tracker.test."},
            {"name":"cdn.tracker.test.","type":1,"TTL":60,"data":"203.0.113.7"},
            {"name":"cdn.tracker.test.","type":1,"TTL":60,"data":"203.0.113.8"}]}"""
        assertEquals(listOf("203.0.113.7", "203.0.113.8"), Probe.addressesIn(json))
        assertEquals(emptyList<String>(), Probe.addressesIn("""{"Status":3}"""))
    }
}

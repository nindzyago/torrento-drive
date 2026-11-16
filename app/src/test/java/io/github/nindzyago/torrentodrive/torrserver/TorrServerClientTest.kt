package io.github.nindzyago.torrentodrive.torrserver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

class TorrServerClientTest {
    @Test
    fun okHttpConnectionRefused() {
        assertTrue(TorrServerClient.isUnreachable(ConnectException("Failed to connect to /127.0.0.1:8090")))
    }

    @Test
    fun platformMessageWrappedInCause() {
        val cause = ConnectException(
            "failed to connect to /127.0.0.1 (port 8090) from /127.0.0.1 (port 40312) after 10000ms: isConnected failed: ECONNREFUSED (Connection refused)",
        )
        assertTrue(TorrServerClient.isUnreachable(IOException("wrapped", cause)))
    }

    @Test
    fun trackerUnreachableIsNotTorrServer() {
        assertFalse(TorrServerClient.isUnreachable(ConnectException("Failed to connect to rutracker.org/104.21.32.39:443")))
    }

    @Test
    fun otherServerErrorsAreNotUnreachable() {
        assertFalse(TorrServerClient.isUnreachable(IllegalStateException("TorrServer answered HTTP 500")))
        assertFalse(TorrServerClient.isUnreachable(SocketTimeoutException("timeout")))
    }
}

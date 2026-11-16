package io.github.nindzyago.torrentodrive.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ByeDpiTest {
    private val args = ByeDpi.AUTOMATIC

    /** Without it byedpi never notices packets being dropped silently and stays on the first strategy. */
    @Test
    fun `the automatic mode has a TLS timeout`() {
        val seconds = args[args.indexOf("--tls-timeout") + 1].toDouble()
        assertTrue(seconds > 0)
    }

    @Test
    fun `the automatic mode chains the first four strategies`() {
        val groups = args.drop(2).joinToString(" ").split(" --auto torst,ssl_err ")
        assertEquals(ByeDpi.STRATEGIES.take(4).map { it.joinToString(" ") }, groups)
    }

    /** All four must have their turn before the connection check gives up (12 s, see Connection). */
    @Test
    fun `every strategy fits into the connection check`() {
        val seconds = args[args.indexOf("--tls-timeout") + 1].toDouble()
        assertTrue(seconds * 4 < 12)
    }

    @Test
    fun `strategies are distinct`() {
        assertEquals(ByeDpi.STRATEGIES.size, ByeDpi.STRATEGIES.toSet().size)
    }
}

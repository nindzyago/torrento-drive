package io.github.nindzyago.torrentodrive.trackers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The RuTracker session copy: put back only when the WebView lost the session cookie. */
class RutrackerSessionTest {
    private val backup = "bb_guid=abc; bb_ssl=1; bb_session=0-123-xyz; cf_clearance=q"

    @Test fun lostSessionIsRestored() {
        assertEquals(listOf("bb_guid=abc", "bb_ssl=1", "bb_session=0-123-xyz", "cf_clearance=q"), RutrackerWeb.cookiesToRestore(null, backup))
        assertEquals(listOf("bb_guid=abc", "bb_ssl=1", "bb_session=0-123-xyz"), RutrackerWeb.cookiesToRestore("cf_clearance=new", backup))
    }

    @Test fun liveSessionIsLeftAlone() {
        assertTrue(RutrackerWeb.cookiesToRestore("bb_session=0-123-new; bb_ssl=1", backup).isEmpty())
    }

    @Test fun noCopyOrACopyWithoutSessionRestoresNothing() {
        assertTrue(RutrackerWeb.cookiesToRestore(null, null).isEmpty())
        assertTrue(RutrackerWeb.cookiesToRestore("", "bb_guid=abc; cf_clearance=q").isEmpty())
    }
}

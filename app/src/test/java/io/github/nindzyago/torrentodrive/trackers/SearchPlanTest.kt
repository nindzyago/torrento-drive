package io.github.nindzyago.torrentodrive.trackers

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchPlanTest {
    private class Fake(override val id: String, override val requiresSignIn: Boolean = false, override val isSignedIn: Boolean = true) : TrackerProvider {
        override val name = id
        override val site = "https://$id"
        override suspend fun search(query: String) = emptyList<TorrentItem>()
    }

    private val open = Fake("rutor")
    private val signedOut = Fake("rutracker", requiresSignIn = true, isSignedIn = false)
    private val signedIn = Fake("rutracker", requiresSignIn = true, isSignedIn = true)

    @Test fun signedOutTrackerIsSkippedNotAsked() {
        val plan = Trackers.searchPlan(listOf(signedOut, open)) { true }
        assertEquals(listOf("rutor"), plan.ask.map { it.id })
        assertEquals(listOf("rutracker"), plan.needSignIn.map { it.id })
    }

    @Test fun signedInTrackerIsAsked() {
        val plan = Trackers.searchPlan(listOf(signedIn, open)) { true }
        assertEquals(listOf("rutracker", "rutor"), plan.ask.map { it.id })
        assertEquals(emptyList<String>(), plan.needSignIn.map { it.id })
    }

    @Test fun switchedOffTrackerIsNeitherAskedNorNoted() {
        val plan = Trackers.searchPlan(listOf(signedOut, open)) { it.id == "rutor" }
        assertEquals(listOf("rutor"), plan.ask.map { it.id })
        assertEquals(emptyList<String>(), plan.needSignIn.map { it.id })
    }

    @Test fun onlySignedOutTrackerOnAsksNothing() {
        val plan = Trackers.searchPlan(listOf(signedOut, open)) { it.id == "rutracker" }
        assertEquals(emptyList<String>(), plan.ask.map { it.id })
        assertEquals(listOf("rutracker"), plan.needSignIn.map { it.id })
    }
}

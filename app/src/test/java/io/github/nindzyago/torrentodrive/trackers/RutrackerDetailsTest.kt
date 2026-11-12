package io.github.nindzyago.torrentodrive.trackers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the RuTracker page script returns for a topic, turned into release details. */
class RutrackerDetailsTest {
    @Test fun scriptAnswerBecomesDetails() {
        val text = javaClass.getResource("/rutracker/post.txt")!!.readText()
        val json = listOf("https://i.ibb.co/x/poster.jpg", "11-Ноя-24 15:30", text).joinToString(RutrackerWeb.SEP.toString())
        val d = RutrackerWeb.detailsOf(json)!!
        assertEquals("https://i.ibb.co/x/poster.jpg", d.poster)
        assertEquals("Падение империи", d.facts[Fact.Title])
        assertEquals("11.11.2024", d.added)
        assertEquals("IMDb" to "7.4", d.ratings.first())
    }

    @Test fun emptyOrBrokenAnswersGiveNull() {
        assertNull(RutrackerWeb.detailsOf(""))
        assertNull(RutrackerWeb.detailsOf("not json"))
        assertNull(RutrackerWeb.detailsOf("\u0001\u0001"))
        assertEquals("", RutrackerWeb.addedOf("вчера"))
        assertEquals("05.01.2025", RutrackerWeb.addedOf("5-Янв-25 01:02"))
    }
}

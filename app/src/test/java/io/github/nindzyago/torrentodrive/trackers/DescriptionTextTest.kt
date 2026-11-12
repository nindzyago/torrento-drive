package io.github.nindzyago.torrentodrive.trackers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Torrent descriptions: a saved Rutor page (2026-11-12) and a typical RuTracker first post as text. */
class DescriptionTextTest {
    private fun res(name: String) = javaClass.getResource(name)!!.readText()

    @Test fun rutorPageGivesPlotFactsPosterAndDate() {
        val d = RutorPages.details(res("/rutor/topic.html"), "https://rutor.info/torrent/1113977")
        assertNotNull(d)
        d!!
        assertTrue(d.poster!!.startsWith("https://i129.fastpic.org/"))
        assertTrue(d.description, d.description.startsWith("Пастор Айзек неожиданно наследует"))
        assertTrue(d.description.endsWith("надежду на лучшее."))
        assertEquals("Рождественский госпел", d.facts[Fact.Title])
        assertEquals("The Gospel of Christmas", d.facts[Fact.Original])
        assertEquals("2026", d.facts[Fact.Year])
        assertEquals("Драма, мюзикл", d.facts[Fact.Genre])
        assertEquals("США", d.facts[Fact.Country])
        assertEquals("01:44:46", d.facts[Fact.Duration])
        assertEquals("Тайлер Перри", d.facts[Fact.Director])
        assertTrue(d.facts[Fact.Cast]!!.endsWith("…"))
        assertEquals(6, d.facts[Fact.Cast]!!.split(", ").size)
        assertEquals("Профессиональный (дублированный) | Videofilm Int.", d.facts[Fact.Translation])
        assertEquals("WEB-DLRip-AVC | NF", d.facts[Fact.Quality])
        assertEquals("11.11.2026", d.added)
        // MediaInfo is cut off
        assertFalse(d.description.contains("Unique ID"))
        assertFalse(d.facts.values.any { it.contains("Matroska") && it.contains("Version") })
    }

    @Test fun rutrackerPostGivesPlotFactsAndRatings() {
        val p = DescriptionText.parse(res("/rutracker/post.txt"))
        assertEquals("Империя на закате: старый император болен, наследники делят трон, а на границах собираются враги. Молодой офицер получает приказ, который изменит судьбу всей страны.", p.description)
        assertEquals("2024", p.facts[Fact.Year])
        assertEquals("США, Великобритания", p.facts[Fact.Country])
        assertEquals("Иван Иванов", p.facts[Fact.Director])
        assertEquals("Профессиональный (дублированный) | Пифагор", p.facts[Fact.Translation])
        assertEquals("WEB-DL 1080p", p.facts[Fact.Quality])
        assertEquals("AC3, 6 ch, 640 kbps | Дубляж", p.facts[Fact.Audio])
        assertEquals(listOf("IMDb" to "7.4", "КП" to "7.1"), p.ratings)
    }

    @Test fun textWithoutLabelsUsesTheFirstLongParagraph() {
        val plot = "Это история о человеке, который однажды проснулся и понял, что всё вокруг изменилось, а его прошлое стало чужим и далёким воспоминанием."
        val p = DescriptionText.parse("Фильм 2024\n\n$plot\n\nСкриншоты")
        assertEquals(plot, p.description)
        assertTrue(p.facts.isEmpty())
    }

    @Test fun hostileTextGivesNothing() {
        listOf("", "   ", "<html>captcha</html>", "Just a moment…", "General\nUnique ID : 1\nFormat : Matroska").forEach { t ->
            val p = DescriptionText.parse(t)
            assertEquals("", p.description)
            assertTrue(p.facts.isEmpty())
            assertTrue(p.ratings.isEmpty())
        }
        assertNull(RutorPages.details("<html><body>nothing</body></html>", "https://rutor.info/torrent/1"))
    }

    @Test fun longPlotIsShortened() {
        val p = DescriptionText.parse("Описание:\n" + "слово ".repeat(400))
        assertTrue(p.description.length <= 1201)
        assertTrue(p.description.endsWith("…"))
    }
}

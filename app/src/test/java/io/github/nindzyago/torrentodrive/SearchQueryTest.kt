package io.github.nindzyago.torrentodrive

import io.github.nindzyago.torrentodrive.trackers.Category
import io.github.nindzyago.torrentodrive.trackers.TorrentItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryTest {

    @Test fun russianPhrase() = assertEquals(SearchQuery("дюна", "2160p"), SearchQuery.parse("найди мне фильм дюна в 4к"))

    @Test fun englishPhrase() = assertEquals(SearchQuery("Dune", "1080p"), SearchQuery.parse("show me the movie Dune in full hd"))

    @Test fun plainTitle() = assertEquals(SearchQuery("Interstellar", null), SearchQuery.parse("Interstellar"))

    @Test fun russianFullHd() = assertEquals(SearchQuery("дом дракона", "1080p"), SearchQuery.parse("включи сериал дом дракона в фулл эйч ди"))

    private fun category(title: String, forum: String = "") =
        Category.of(TorrentItem(trackerId = "t", tracker = "T", title = title, size = "", seeds = 0, peers = 0, link = "", forum = forum))

    @Test fun categories() {
        assertEquals(Category.Series, category("Дом дракона / House of the Dragon / Сезон 2"))
        assertEquals(Category.Cartoons, category("Головоломка 2 / Inside Out 2 (2024) WEB-DL 1080p", forum = "Мультфильмы"))
        assertEquals(Category.Movies, category("Dune: Part Two (2024) BDRip 2160p"))
        assertEquals(Category.Sport, category("Formula 1. Гран-при Монако", forum = "Формула-1"))
        assertEquals(Category.Other, category("Windows 11"))
    }

    @Test fun versions() {
        assertTrue(Updates.isNewer("0.10.0", "0.9.1"))
        assertTrue(Updates.isNewer("0.2.1", "0.2"))
        assertFalse(Updates.isNewer("0.2.0", "0.2.0"))
        assertFalse(Updates.isNewer("0.1.9", "0.2.0"))
    }
}

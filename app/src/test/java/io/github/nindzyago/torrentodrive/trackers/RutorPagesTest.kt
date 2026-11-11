package io.github.nindzyago.torrentodrive.trackers

import io.github.nindzyago.torrentodrive.ShelfRules
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parsing saved Rutor pages (test/resources/rutor, captured 2026-11-11, trimmed). */
class RutorPagesTest {
    private fun page(name: String) = javaClass.getResource("/rutor/$name")!!.readText()

    @Test fun topHasPerCategoryBlocksUnderTheSitesNames() {
        val top = RutorPages.top(page("top.html"), "https://rutor.info", "rutor", "Rutor")
        // the first block (top of all categories) has no category link and is not a shelf
        // every category on the page maps to a known section, in the site's order
        assertEquals(listOf("1", "5", "12", "4", "16", "6", "7", "10", "17", "2", "8", "9", "13", "15", "14", "11", "3"), top.keys.toList())
        assertEquals(RutorPages.sections.map { it.id }.toSet(), top.keys)
        assertTrue(top.values.all { it.isNotEmpty() })
        val films = top.getValue("1")
        assertEquals(12, films.size)
        assertEquals("Зарубежные фильмы", RutorPages.sections.first { it.id == "1" }.title)
        films.forEach {
            assertTrue(it.link, it.link.startsWith("magnet:"))
            assertTrue(it.page, it.page.startsWith("https://rutor.info/torrent/"))
            assertTrue(it.size, it.size.endsWith("GB") || it.size.endsWith("MB"))
        }
        assertTrue(films.first().seeds > 0)
        // Rutor's top repeats a film in several qualities: the shelf keeps one card per film
        val picked = ShelfRules.pick(films)
        assertEquals(films.map { ShelfRules.titleKey(it.title) }.distinct().size, picked.size)
        assertTrue(picked.size in 5..10)
    }

    @Test fun browseRowsAreNewestFirst() {
        val rows = RutorPages.rows(Jsoup.parse(page("browse-1.html"), "https://rutor.info").body(), "rutor", "Rutor")
        assertEquals(100, rows.size)
        assertTrue(rows.all { it.title.isNotBlank() && it.trackerId == "rutor" })
        assertEquals(10, ShelfRules.pick(rows).size)
    }

    @Test fun hostilePagesGiveNothingAndDoNotThrow() {
        listOf("", "<html>", "<h2>Самые популярные торренты в категории <a href=/kino>X</a></h2>", "<h2><a href=/kino>x</a></h2><table><tr class=gai><td>no links</td></tr></table>", "Just a moment… cf-challenge")
            .forEach { html ->
                assertTrue(RutorPages.top(html, "https://rutor.info", "rutor", "Rutor").values.all { it.isEmpty() })
                assertTrue(RutorPages.rows(Jsoup.parse(html).body(), "rutor", "Rutor").isEmpty())
            }
    }

    @Test fun videoSectionsAreOnByDefault() {
        val on = RutorPages.sections.filter { it.video }.map { it.title }
        assertTrue("Зарубежные фильмы" in on && "Аниме" in on && "Телевизор" in on)
        assertTrue(RutorPages.sections.none { it.video && it.title in setOf("Игры", "Софт", "Книги", "Музыка") })
    }
}

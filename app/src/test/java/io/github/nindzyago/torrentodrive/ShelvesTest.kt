package io.github.nindzyago.torrentodrive

import io.github.nindzyago.torrentodrive.trackers.Browse
import io.github.nindzyago.torrentodrive.trackers.ExampleProvider
import io.github.nindzyago.torrentodrive.trackers.TorrentItem
import io.github.nindzyago.torrentodrive.trackers.TrackerProvider
import io.github.nindzyago.torrentodrive.trackers.TrackerSection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ShelvesTest {
    private val films = TrackerSection("1", "Зарубежные фильмы", video = true)
    private val series = TrackerSection("4", "Зарубежные сериалы", video = true)

    private fun item(title: String, seeds: Int = 10) =
        TorrentItem("fake", "Fake", title, "1.4 GB", seeds, 1, "magnet:?xt=$title", forum = "Кино\tтаб", page = "https://x/$title")

    /** Counts calls; answers with [answer] or throws [fail]. */
    private class FakeTracker(var answer: Map<String, List<TorrentItem>> = emptyMap(), var fail: Exception? = null) : TrackerProvider {
        override val id = "fake"
        override val name = "Fake"
        override val site = "https://fake"
        val asked = mutableListOf<List<String>>()
        override suspend fun search(query: String) = emptyList<TorrentItem>()
        override suspend fun browse(kind: Browse, sections: List<TrackerSection>): Map<String, List<TorrentItem>> {
            asked += sections.map { it.id }
            fail?.let { throw it }
            return answer.filterKeys { k -> sections.any { it.id == k } }
        }
    }

    private fun tempCache() = ShelfCache(Files.createTempDirectory("shelves").toFile())

    @Test fun codecRoundTripKeepsEveryField() {
        val batch = ShelfBatch(123L, linkedMapOf("1" to listOf(item("A\\B\nC")), "empty" to emptyList()))
        assertEquals(batch, ShelfCache.decode(ShelfCache.encode(batch)))
    }

    @Test fun garbageIsNotACache() {
        assertNull(ShelfCache.decode(""))
        assertNull(ShelfCache.decode("<html>captcha</html>"))
        assertNull(ShelfCache.decode("shelves 1\nnot-a-number"))
        // a broken item line is skipped, the rest is kept
        val ok = ShelfCache.decode("shelves 1\n5\n#1\nbroken line\n" + ShelfCache.encode(ShelfBatch(5, mapOf("1" to listOf(item("X"))))).lines().drop(3).joinToString("\n"))
        assertEquals(listOf("X"), ok!!.sections["1"]!!.map { it.title })
    }

    @Test fun pickKeepsTenDistinctTitles() {
        val many = (1..15).flatMap { n -> listOf(item("Фильм $n / Film $n (2026) WEB-DL 1080p"), item("Фильм $n / Film $n (2026) BDRip 2160p")) }
        val picked = ShelfRules.pick(many)
        assertEquals(10, picked.size)
        assertEquals(10, picked.map { ShelfRules.titleKey(it.title) }.distinct().size)
        assertTrue(picked.first().title.contains("1080p"))
    }

    @Test fun freshCacheMeansNoNetwork() = runBlocking {
        val cache = tempCache()
        val t = FakeTracker(mapOf("1" to listOf(item("A"))))
        ShelfRules.load(cache, t, Browse.New, listOf(films), now = 1_000)
        val again = ShelfRules.load(cache, t, Browse.New, listOf(films), now = 1_000 + ShelfRules.TTL_MS - 1)
        assertEquals(1, t.asked.size)
        assertEquals(listOf("A"), again.sections["1"]!!.map { it.title })
    }

    @Test fun staleCacheIsFetchedAgain() = runBlocking {
        val cache = tempCache()
        val t = FakeTracker(mapOf("1" to listOf(item("A"))))
        ShelfRules.load(cache, t, Browse.Popular, listOf(films), now = 0)
        t.answer = mapOf("1" to listOf(item("B")))
        val b = ShelfRules.load(cache, t, Browse.Popular, listOf(films), now = ShelfRules.TTL_MS + 1)
        assertEquals(2, t.asked.size)
        assertEquals(listOf("B"), b.sections["1"]!!.map { it.title })
        assertEquals(ShelfRules.TTL_MS + 1, b.at)
    }

    @Test fun switchedOnSectionIsFetchedAloneWhileCacheIsFresh() = runBlocking {
        val cache = tempCache()
        val t = FakeTracker(mapOf("1" to listOf(item("A")), "4" to listOf(item("S"))))
        ShelfRules.load(cache, t, Browse.New, listOf(films), now = 0)
        val b = ShelfRules.load(cache, t, Browse.New, listOf(films, series), now = 10)
        assertEquals(listOf(listOf("1"), listOf("4")), t.asked)
        assertEquals(0L, b.at)
        assertEquals(setOf("1", "4"), b.sections.keys)
    }

    @Test fun failedFetchKeepsTheOldListOnDisk() = runBlocking {
        val cache = tempCache()
        val t = FakeTracker(mapOf("1" to listOf(item("A"))))
        ShelfRules.load(cache, t, Browse.New, listOf(films), now = 0)
        t.fail = IllegalStateException("HTTP 503")
        val thrown = runCatching { ShelfRules.load(cache, t, Browse.New, listOf(films), now = ShelfRules.TTL_MS * 2) }.exceptionOrNull()
        assertEquals("HTTP 503", thrown?.message)
        assertEquals(listOf("A"), cache.read("fake", Browse.New)!!.sections["1"]!!.map { it.title })
    }

    @Test fun emptyAnswerKeepsTheCachedSection() = runBlocking {
        val cache = tempCache()
        val t = FakeTracker(mapOf("1" to listOf(item("A"))))
        ShelfRules.load(cache, t, Browse.New, listOf(films), now = 0)
        t.answer = emptyMap()
        val b = ShelfRules.load(cache, t, Browse.New, listOf(films), now = ShelfRules.TTL_MS * 2)
        assertEquals(listOf("A"), b.sections["1"]!!.map { it.title })
    }

    @Test fun trackerWithoutSectionsHasNoShelves() = runBlocking {
        val t = ExampleProvider()
        assertTrue(t.sections.isEmpty())
        val b = ShelfRules.load(tempCache(), t, Browse.New, t.sections, now = 0)
        assertTrue(b.sections.isEmpty())
    }

    @Test fun cacheFileIsWrittenWhole() {
        val dir = Files.createTempDirectory("shelves").toFile()
        val cache = ShelfCache(dir)
        cache.write("fake", Browse.New, ShelfBatch(1, mapOf("1" to listOf(item("A")))))
        cache.write("fake", Browse.New, ShelfBatch(2, mapOf("1" to listOf(item("B")))))
        assertEquals(listOf("fake-new.txt"), dir.list()!!.toList())
        assertEquals(2L, cache.read("fake", Browse.New)!!.at)
        assertNull(ShelfCache(File(dir, "missing")).read("fake", Browse.New))
    }
}

package io.github.nindzyago.torrentodrive.trackers

import io.github.nindzyago.torrentodrive.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder

/** Rutor: no sign-in needed, magnet links are right in the search results. */
class RutorProvider(private val baseUrl: String = "https://rutor.info") : TrackerProvider {
    override val id = "rutor"
    override val name = "Rutor"
    override val site = baseUrl

    override suspend fun search(query: String): List<TorrentItem> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        // /search/<page>/<category>/<mode>/<sort>/<query>; sort 2 = by seeders
        RutorPages.rows(Jsoup.parse(get("$baseUrl/search/0/0/100/2/$q"), baseUrl).body(), id, name)
    }

    // the poster is the first image in the torrent description (#details)
    override suspend fun poster(item: TorrentItem): String? = withContext(Dispatchers.IO) {
        if (item.page.isEmpty()) return@withContext null
        val html = Http.trackers.newCall(Request.Builder().url(item.page).build()).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            resp.body.string()
        }
        Jsoup.parse(html, item.page).selectFirst("#details img[src]")?.absUrl("src")?.takeIf { it.isNotEmpty() }
    }

    override val sections = RutorPages.sections

    override suspend fun browse(kind: Browse, sections: List<TrackerSection>): Map<String, List<TorrentItem>> = withContext(Dispatchers.IO) {
        when (kind) {
            // one page has the last 24 hours' top of every category
            Browse.Popular -> RutorPages.top(get("$baseUrl/top"), baseUrl, id, name).filterKeys { k -> sections.any { it.id == k } }
            // /browse/<page>/<category>/<mode>/<sort>: sort 0 = newest first
            Browse.New -> coroutineScope {
                sections.map { s ->
                    async { s.id to runCatching { RutorPages.rows(Jsoup.parse(get("$baseUrl/browse/0/${s.id}/0/0"), baseUrl).body(), id, name) } }
                }.awaitAll()
            }.let { results ->
                // every category failing means the site is down: say so instead of showing nothing
                if (results.all { it.second.isFailure }) throw results.first().second.exceptionOrNull()!!
                results.mapNotNull { (k, r) -> r.getOrNull()?.let { k to it } }.toMap()
            }
        }
    }

    private fun get(url: String): String =
        Http.trackers.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) error("Rutor answered HTTP ${resp.code}")
            resp.body.string()
        }
}

/** Rutor's page layouts, kept apart from the network so tests can feed saved pages. */
internal object RutorPages {
    /** Rutor's categories: number (as in /browse URLs), the /top block's link, the site's own name, playable on a TV. */
    private val categories = listOf(
        Triple("1", "kino", "Зарубежные фильмы") to true,
        Triple("5", "nashe_kino", "Наши фильмы") to true,
        Triple("12", "nauchno_popularnoe", "Научно-популярные фильмы") to true,
        Triple("4", "seriali", "Зарубежные сериалы") to true,
        Triple("16", "nashi_seriali", "Наши сериалы") to true,
        Triple("6", "tv", "Телевизор") to true,
        Triple("7", "multiki", "Мультипликация") to true,
        Triple("10", "anime", "Аниме") to true,
        Triple("17", "inostrannoe", "Иностранные релизы") to false,
        Triple("2", "audio", "Музыка") to false,
        Triple("8", "games", "Игры") to false,
        Triple("9", "soft", "Софт") to false,
        Triple("13", "sport", "Спорт и Здоровье") to true,
        Triple("15", "jumor", "Юмор") to false,
        Triple("14", "byt", "Хозяйство и Быт") to false,
        Triple("11", "knigi", "Книги") to false,
        Triple("3", "other", "Другое") to false,
    )

    val sections = categories.map { (c, video) -> TrackerSection(c.first, c.third, video) }
    private val bySlug = categories.associate { (c, _) -> c.second to c.first }

    /** Torrent rows of a results table (search, browse and top pages share the layout). */
    fun rows(root: Element, trackerId: String, tracker: String): List<TorrentItem> =
        root.select("tr.gai, tr.tum").mapNotNull { row ->
            val magnet = row.selectFirst("a[href^=magnet:]")?.attr("href") ?: return@mapNotNull null
            val topic = row.selectFirst("a[href^=/torrent/]") ?: return@mapNotNull null
            val title = topic.text().trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val size = row.select("td[align=right]").lastOrNull()?.text()?.replace(' ', ' ')?.trim().orEmpty()
            val seeds = row.selectFirst("span.green")?.text()?.filter(Char::isDigit)?.toIntOrNull() ?: 0
            val peers = row.selectFirst("span.red")?.text()?.filter(Char::isDigit)?.toIntOrNull() ?: 0
            TorrentItem(trackerId, tracker, title, size, seeds, peers, magnet, page = topic.absUrl("href"))
        }

    /** /top: "most popular in category <a href=/kino>…</a>" headings, each followed by its table. */
    fun top(html: String, baseUrl: String, trackerId: String, tracker: String): Map<String, List<TorrentItem>> {
        val doc = Jsoup.parse(html, baseUrl)
        return doc.select("h2:has(a[href])").mapNotNull { h2 ->
            val slug = h2.selectFirst("a[href]")!!.attr("href").trim('/').substringAfterLast('/')
            val section = bySlug[slug] ?: return@mapNotNull null
            val table = generateSequence(h2.nextElementSibling()) { it.nextElementSibling() }
                .takeWhile { it.tagName() != "h2" }
                .firstOrNull { it.tagName() == "table" } ?: return@mapNotNull null
            section to rows(table, trackerId, tracker)
        }.toMap()
    }
}

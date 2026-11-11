package io.github.nindzyago.torrentodrive.trackers

import io.github.nindzyago.torrentodrive.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * Template for a new tracker: copy this file, rename the class, fill in the selectors,
 * then register it in [Trackers.all]. This class itself is not registered.
 *
 * This shape fits public trackers whose search page is plain HTML with magnet links.
 * A tracker behind a sign-in or Cloudflare should run its requests through a WebView
 * session instead; see [RutrackerWeb] for how that is done.
 */
@Suppress("unused")
class ExampleProvider(private val baseUrl: String = "https://tracker.example") : TrackerProvider {
    override val id = "example"
    override val name = "Example"
    override val site = baseUrl

    override suspend fun search(query: String): List<TorrentItem> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        val html = Http.trackers.newCall(Request.Builder().url("$baseUrl/search?q=$q").build()).execute().use { resp ->
            if (!resp.isSuccessful) error("$name answered HTTP ${resp.code}")
            resp.body.string()
        }
        Jsoup.parse(html, baseUrl).select("table.results tr").mapNotNull { row ->
            val magnet = row.selectFirst("a[href^=magnet:]")?.attr("href") ?: return@mapNotNull null
            val topic = row.selectFirst("a.title") ?: return@mapNotNull null
            TorrentItem(
                trackerId = id,
                tracker = name,
                title = topic.text().trim(),
                size = row.selectFirst("td.size")?.text().orEmpty(),
                seeds = row.selectFirst("td.seeds")?.text()?.toIntOrNull() ?: 0,
                peers = row.selectFirst("td.peers")?.text()?.toIntOrNull() ?: 0,
                link = magnet,                    // a magnet, or anything your source() understands
                page = topic.absUrl("href"),      // the topic page, used to find the poster
            )
        }
    }

    // Optional: sections for the home screen's New and Popular shelves (see CONTRIBUTING.md).
    // override val sections = listOf(TrackerSection("movies", "Movies", video = true), TrackerSection("games", "Games", video = false))
    // override suspend fun browse(kind: Browse, sections: List<TrackerSection>) = sections.associate { s ->
    //     val sort = if (kind == Browse.New) "date" else "seeders"
    //     s.id to parseRows(get("$baseUrl/browse/${s.id}?sort=$sort"))
    // }

    // Optional: the poster is usually the first picture in the topic description.
    override suspend fun poster(item: TorrentItem): String? = withContext(Dispatchers.IO) {
        if (item.page.isEmpty()) return@withContext null
        val html = Http.trackers.newCall(Request.Builder().url(item.page).build()).execute().use { it.body.string() }
        Jsoup.parse(html, item.page).selectFirst(".description img[src]")?.absUrl("src")
    }
}

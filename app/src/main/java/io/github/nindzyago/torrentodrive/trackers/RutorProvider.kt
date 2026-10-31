package io.github.nindzyago.torrentodrive.trackers

import io.github.nindzyago.torrentodrive.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder

/** Rutor: no sign-in needed, magnet links are right in the search results. */
class RutorProvider(private val baseUrl: String = "https://rutor.info") : TrackerProvider {
    override val id = "rutor"
    override val name = "Rutor"

    override suspend fun search(query: String): List<TorrentItem> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        // /search/<page>/<category>/<mode>/<sort>/<query>; sort 2 = by seeders
        val request = Request.Builder().url("$baseUrl/search/0/0/100/2/$q").build()
        val html = Http.client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("Rutor answered HTTP ${resp.code}")
            resp.body.string()
        }
        Jsoup.parse(html, baseUrl).select("tr.gai, tr.tum").mapNotNull { row ->
            val magnet = row.selectFirst("a[href^=magnet:]")?.attr("href") ?: return@mapNotNull null
            val topic = row.selectFirst("a[href^=/torrent/]") ?: return@mapNotNull null
            val title = topic.text().trim()
            val size = row.select("td[align=right]").lastOrNull()?.text()?.replace(' ', ' ')?.trim().orEmpty()
            val seeds = row.selectFirst("span.green")?.text()?.filter(Char::isDigit)?.toIntOrNull() ?: 0
            val peers = row.selectFirst("span.red")?.text()?.filter(Char::isDigit)?.toIntOrNull() ?: 0
            TorrentItem(id, name, title, size, seeds, peers, magnet, page = topic.absUrl("href"))
        }
    }

    // the poster is the first image in the torrent description (#details)
    override suspend fun poster(item: TorrentItem): String? = withContext(Dispatchers.IO) {
        if (item.page.isEmpty()) return@withContext null
        val html = Http.client.newCall(Request.Builder().url(item.page).build()).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            resp.body.string()
        }
        Jsoup.parse(html, item.page).selectFirst("#details img[src]")?.absUrl("src")?.takeIf { it.isNotEmpty() }
    }
}

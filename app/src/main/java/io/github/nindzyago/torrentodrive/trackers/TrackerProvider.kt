package io.github.nindzyago.torrentodrive.trackers

/** A single torrent from a tracker's search results. */
data class TorrentItem(
    val trackerId: String,
    val tracker: String,
    val title: String,
    val size: String,
    val seeds: Int,
    val peers: Int,
    /** Magnet link or the torrent's id on the tracker; the tracker knows how to turn it into a [TorrentSource]. */
    val link: String,
    /** Tracker forum section ("Foreign TV series"…), if the tracker provides it; helps determine the category. */
    val forum: String = "",
    /** URL of the torrent page (with the description and poster), if the tracker knows it. */
    val page: String = "",
) {
    val category: Category get() = Category.of(this)
}

/** What we hand to TorrServer: a (magnet) link or the contents of a .torrent file. */
sealed interface TorrentSource {
    data class Link(val uri: String) : TorrentSource
    class File(val bytes: ByteArray) : TorrentSource
}

/** What a shelf on the home screen shows: a section's newest releases or its most popular ones. */
enum class Browse { New, Popular }

/**
 * A section of the tracker that fills shelves ("Зарубежные фильмы", "Наши сериалы"…).
 * [title] is the tracker's own name for it; [video] sections are shown by default, the rest
 * (games, software, books) only when switched on in settings.
 */
data class TrackerSection(val id: String, val title: String, val video: Boolean)

/**
 * What the torrent's page on the tracker says about the release: poster, the "about the film" text and
 * the "Label: value" lines the uploader wrote (year, genre, director, cast, translation…), read by [DescriptionText].
 */
data class TorrentDetails(
    val poster: String? = null,
    val description: String = "",
    val facts: Map<Fact, String> = emptyMap(),
    /** Ratings the uploader quoted ("IMDb" to "7.1"), in the page's order. */
    val ratings: List<Pair<String, String>> = emptyList(),
    /** When the torrent was added or registered, as the tracker shows it ("11.11.2026"). */
    val added: String = "",
)

/** A release fact from the description, in the order the release screen shows them. */
enum class Fact { Title, Original, Year, Country, Genre, Duration, Director, Cast, Translation, Subtitles, Quality, Video, Audio }

/** The tracker needs a signed-in session and there is none. */
class LoginRequired(val tracker: String) : Exception("$tracker: sign-in required")

/**
 * Common tracker interface: a new tracker is a new implementation; the rest of the app stays unchanged.
 * Make plain HTTP requests with Http.trackers, so they follow the connection method chosen in settings.
 */
interface TrackerProvider {
    val id: String
    val name: String

    /** The site's home page: the connection check asks it whether the tracker is reachable. */
    val site: String
    suspend fun search(query: String): List<TorrentItem>
    suspend fun source(item: TorrentItem): TorrentSource = TorrentSource.Link(item.link)

    /** URL of the poster image from the torrent description (the post's first image), or null. */
    suspend fun poster(item: TorrentItem): String? = null

    /**
     * The release screen's description: poster, text and facts from the torrent's page, or null if the tracker
     * can't tell. Most trackers turn the description into text and hand it to [DescriptionText.parse].
     * By default only the poster.
     */
    suspend fun details(item: TorrentItem): TorrentDetails? = poster(item)?.let { TorrentDetails(poster = it) }

    /** true if the tracker only searches after sign-in (then implement [isSignedIn], [startSignIn], [signOut]). */
    val requiresSignIn: Boolean get() = false

    /** Whether there is currently a session on the tracker. Backed by Compose state so the screen updates itself. */
    val isSignedIn: Boolean get() = true

    /** Opens the tracker's own sign-in page (full-screen WebView with a cursor). */
    fun startSignIn() {}

    suspend fun signOut() {}

    /**
     * Sections that can fill the home screen's "New" and "Popular" shelves, in the tracker's own order.
     * Empty (the default) means the tracker has no shelves.
     */
    val sections: List<TrackerSection> get() = emptyList()

    /** For trackers that learn their sections from the site: refreshes [sections]. Called before [browse]. */
    suspend fun refreshSections() {}

    /**
     * The newest or most popular releases of each of [sections], by section id, best first.
     * Return what the tracker gives (the app keeps the first 10 distinct titles); a section that failed
     * or is empty may be left out.
     */
    suspend fun browse(kind: Browse, sections: List<TrackerSection>): Map<String, List<TorrentItem>> = emptyMap()
}

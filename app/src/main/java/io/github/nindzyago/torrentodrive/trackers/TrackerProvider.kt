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

/** The tracker needs a signed-in session and there is none. */
class LoginRequired(val tracker: String) : Exception("$tracker: sign-in required")

/** Common tracker interface: a new tracker is a new implementation; the rest of the app stays unchanged. */
interface TrackerProvider {
    val id: String
    val name: String
    suspend fun search(query: String): List<TorrentItem>
    suspend fun source(item: TorrentItem): TorrentSource = TorrentSource.Link(item.link)

    /** URL of the poster image from the torrent description (the post's first image), or null. */
    suspend fun poster(item: TorrentItem): String? = null

    /** true if the tracker only searches after sign-in (then implement [isSignedIn], [startSignIn], [signOut]). */
    val requiresSignIn: Boolean get() = false

    /** Whether there is currently a session on the tracker. Backed by Compose state so the screen updates itself. */
    val isSignedIn: Boolean get() = true

    /** Opens the tracker's own sign-in page (full-screen WebView with a cursor). */
    fun startSignIn() {}

    suspend fun signOut() {}
}

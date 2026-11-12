package io.github.nindzyago.torrentodrive.trackers

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The last torrent pages' details, by link: the shelf card asks for the poster and the release screen for the
 * description, and both come from one page. A failed load is not kept, so it can be asked again.
 */
class DetailsCache(size: Int = 40) {
    private val memory = object : LinkedHashMap<String, TorrentDetails>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TorrentDetails>?) = this.size > size
    }
    private val lock = Mutex()

    suspend fun get(key: String, load: suspend () -> TorrentDetails?): TorrentDetails? {
        lock.withLock { memory[key] }?.let { return it }
        val d = load() ?: return null
        lock.withLock { memory[key] = d }
        return d
    }
}

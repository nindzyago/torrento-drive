package io.github.nindzyago.torrentodrive

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import io.github.nindzyago.torrentodrive.trackers.TorrentItem
import org.json.JSONArray
import org.json.JSONObject

/** What was watched and when: the torrent (to reopen it) and the specific file (episode). */
data class WatchEntry(
    val item: TorrentItem,
    val fileIndex: Int,
    val fileName: String,
    val watchedAt: Long,
    /** Poster from the torrent description, if one was found. */
    val poster: String? = null,
)

/** Watch history: the latest entry per torrent, newest on top, stored in SharedPreferences. */
class WatchHistory(context: Context) {
    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    val entries = mutableStateListOf<WatchEntry>().apply { addAll(load()) }

    fun add(entry: WatchEntry) {
        entries.removeAll { it.item.trackerId == entry.item.trackerId && it.item.link == entry.item.link }
        entries.add(0, entry)
        while (entries.size > LIMIT) entries.removeAt(entries.lastIndex)
        save()
    }

    /** Adds a poster to an entry (for older entries that didn't have one yet). */
    fun setPoster(entry: WatchEntry, poster: String) {
        val i = entries.indexOfFirst { it.item.trackerId == entry.item.trackerId && it.item.link == entry.item.link }
        if (i < 0) return
        entries[i] = entries[i].copy(poster = poster)
        save()
    }

    fun remove(entry: WatchEntry) {
        entries.remove(entry)
        save()
    }

    private fun save() {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("trackerId", e.item.trackerId).put("tracker", e.item.tracker)
                    .put("title", e.item.title).put("size", e.item.size)
                    .put("seeds", e.item.seeds).put("peers", e.item.peers)
                    .put("link", e.item.link).put("forum", e.item.forum).put("page", e.item.page)
                    .put("fileIndex", e.fileIndex).put("fileName", e.fileName).put("watchedAt", e.watchedAt)
                    .put("poster", e.poster ?: "")
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun load(): List<WatchEntry> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            WatchEntry(
                item = TorrentItem(
                    trackerId = o.getString("trackerId"),
                    tracker = o.getString("tracker"),
                    title = o.getString("title"),
                    size = o.optString("size"),
                    seeds = o.optInt("seeds"),
                    peers = o.optInt("peers"),
                    link = o.getString("link"),
                    forum = o.optString("forum"),
                    page = o.optString("page"),
                ),
                fileIndex = o.getInt("fileIndex"),
                fileName = o.getString("fileName"),
                watchedAt = o.getLong("watchedAt"),
                poster = o.optString("poster").ifEmpty { null },
            )
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val KEY = "entries"
        const val LIMIT = 30
    }
}

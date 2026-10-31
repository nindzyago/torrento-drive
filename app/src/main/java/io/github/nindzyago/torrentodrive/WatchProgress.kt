package io.github.nindzyago.torrentodrive

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import io.github.nindzyago.torrentodrive.trackers.TorrentItem
import org.json.JSONObject

/** Where playback stopped in an episode, in milliseconds. */
data class Progress(val positionMs: Long, val durationMs: Long) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    /** Close enough to the end (credits) to count the episode as watched. */
    val finished: Boolean get() = durationMs > 0 && (durationMs - positionMs < FINISH_MARGIN_MS || fraction > 0.95f)
    /** Worth resuming: not just the first seconds and not already finished. */
    val resumable: Boolean get() = positionMs > MIN_RESUME_MS && !finished

    private companion object {
        const val FINISH_MARGIN_MS = 60_000L
        const val MIN_RESUME_MS = 15_000L
    }
}

/** Playback positions per torrent file, stored in SharedPreferences; the oldest are dropped past [LIMIT]. */
class WatchProgress(context: Context) {
    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)

    /** Key → progress; observable so cards and episode lists redraw when it changes. */
    private val all = mutableStateMapOf<String, Progress>().apply { putAll(load()) }
    private val order = ArrayDeque(all.keys)

    fun get(item: TorrentItem, fileIndex: Int): Progress? = all[key(item, fileIndex)]

    fun set(item: TorrentItem, fileIndex: Int, progress: Progress) {
        val k = key(item, fileIndex)
        all[k] = progress
        order.remove(k)
        order.addLast(k)
        while (order.size > LIMIT) all.remove(order.removeFirst())
        save()
    }

    private fun key(item: TorrentItem, fileIndex: Int) = "${item.trackerId}|${item.link}|$fileIndex"

    private fun save() {
        val json = JSONObject()
        order.forEach { k -> all[k]?.let { json.put(k, "${it.positionMs}/${it.durationMs}") } }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private fun load(): Map<String, Progress> = runCatching {
        val json = JSONObject(prefs.getString(KEY, "{}")!!)
        // JSONObject keeps insertion order, so the saved order survives
        json.keys().asSequence().associateWith { k ->
            val (pos, dur) = json.getString(k).split('/').map(String::toLong)
            Progress(pos, dur)
        }
    }.getOrDefault(emptyMap())

    private companion object {
        const val KEY = "positions"
        const val LIMIT = 500
    }
}

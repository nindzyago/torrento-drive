package io.github.nindzyago.torrentodrive

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import io.github.nindzyago.torrentodrive.trackers.Browse
import io.github.nindzyago.torrentodrive.trackers.TorrentItem
import io.github.nindzyago.torrentodrive.trackers.TrackerProvider
import io.github.nindzyago.torrentodrive.trackers.TrackerSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One shelf: a tracker section's releases. */
data class Shelf(val tracker: TrackerProvider, val section: TrackerSection, val items: List<TorrentItem>)

/** What one tracker gave for one kind of shelf, and when. */
data class ShelfBatch(val at: Long, val sections: Map<String, List<TorrentItem>>)

/** Shelves kept on disk, one file per tracker and kind, so the panel shows the last lists right away. */
class ShelfCache(private val dir: File) {
    private fun file(trackerId: String, kind: Browse) = File(dir, "$trackerId-${kind.name.lowercase()}.txt")

    fun read(trackerId: String, kind: Browse): ShelfBatch? =
        runCatching { decode(file(trackerId, kind).readText()) }.getOrNull()

    fun write(trackerId: String, kind: Browse, batch: ShelfBatch) {
        dir.mkdirs()
        val target = file(trackerId, kind)
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText(encode(batch))
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    companion object {
        private const val VERSION = "shelves 1"

        /** Line format: header, "at", then "#<section id>" lines each followed by its items, one per line, tab-separated. */
        fun encode(batch: ShelfBatch): String = buildString {
            appendLine(VERSION)
            appendLine(batch.at)
            batch.sections.forEach { (id, items) ->
                appendLine("#" + esc(id))
                items.forEach { i ->
                    appendLine(listOf(i.trackerId, i.tracker, i.title, i.size, i.seeds.toString(), i.peers.toString(), i.link, i.forum, i.page).joinToString("\t") { esc(it) })
                }
            }
        }

        /** Returns null for anything that isn't a file we wrote; a damaged item line is skipped. */
        fun decode(text: String): ShelfBatch? {
            val lines = text.lines()
            if (lines.firstOrNull() != VERSION) return null
            val at = lines.getOrNull(1)?.toLongOrNull() ?: return null
            val sections = linkedMapOf<String, MutableList<TorrentItem>>()
            var current: MutableList<TorrentItem>? = null
            for (line in lines.drop(2)) {
                if (line.isEmpty()) continue
                if (line.startsWith("#")) {
                    current = sections.getOrPut(unesc(line.substring(1))) { mutableListOf() }
                    continue
                }
                val f = line.split("\t").map(::unesc)
                if (f.size != 9 || current == null) continue
                current += TorrentItem(f[0], f[1], f[2], f[3], f[4].toIntOrNull() ?: 0, f[5].toIntOrNull() ?: 0, f[6], f[7], f[8])
            }
            return ShelfBatch(at, sections)
        }

        private fun esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "")

        private fun unesc(s: String): String {
            val out = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    out.append(when (s[i + 1]) { 't' -> '\t'; 'n' -> '\n'; else -> s[i + 1] })
                    i += 2
                } else {
                    out.append(c)
                    i++
                }
            }
            return out.toString()
        }
    }
}

object ShelfRules {
    /** How many releases a shelf shows. */
    const val SIZE = 10

    /** A shelf list is fetched again once it is older than this. */
    const val TTL_MS = 3 * 60 * 60 * 1000L

    /** The same film in five qualities is one card: keep the first (best) release of each title. */
    fun pick(items: List<TorrentItem>): List<TorrentItem> =
        items.distinctBy { titleKey(it.title) }.take(SIZE)

    fun titleKey(title: String): String =
        title.substringBefore(" / ").substringBefore(" (").substringBefore(" [").substringBefore(" | ")
            .lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().ifEmpty { title }

    fun isFresh(batch: ShelfBatch?, now: Long): Boolean = batch != null && now - batch.at in 0 until TTL_MS

    /**
     * Shelves of one tracker and kind: the cached ones while they are fresh, otherwise fetched again.
     * While the cache is fresh only sections it has never asked for (just switched on) are fetched.
     * A failed fetch keeps the old lists (and rethrows, so the caller can note it); a section that came back
     * empty keeps its cached list.
     */
    suspend fun load(
        cache: ShelfCache,
        tracker: TrackerProvider,
        kind: Browse,
        sections: List<TrackerSection>,
        now: Long,
        force: Boolean = false,
    ): ShelfBatch {
        val cached = withContext(Dispatchers.IO) { cache.read(tracker.id, kind) }
        val fresh = !force && isFresh(cached, now)
        val wanted = if (fresh) sections.filter { it.id !in cached!!.sections } else sections
        if (wanted.isEmpty()) return cached ?: ShelfBatch(now, emptyMap())
        val fetched = tracker.browse(kind, wanted).mapValues { pick(it.value) }
        val old = cached?.sections.orEmpty()
        val merged = old + wanted.associate { s -> s.id to (fetched[s.id]?.takeIf { it.isNotEmpty() } ?: old[s.id].orEmpty()) }
        val batch = ShelfBatch(if (fresh) cached!!.at else now, merged)
        withContext(Dispatchers.IO) { cache.write(tracker.id, kind, batch) }
        return batch
    }
}

/** Sections the user switched on or off in settings; untouched ones follow [TrackerSection.video]. */
class SectionSwitches(context: Context) {
    private val prefs = context.getSharedPreferences("sections", Context.MODE_PRIVATE)
    private val flipped = mutableStateOf(prefs.getStringSet(KEY, emptySet()).orEmpty())

    private fun key(tracker: TrackerProvider, section: TrackerSection) = "${tracker.id}:${section.id}"

    fun isOn(tracker: TrackerProvider, section: TrackerSection) = section.video != (key(tracker, section) in flipped.value)

    fun toggle(tracker: TrackerProvider, section: TrackerSection) {
        val k = key(tracker, section)
        flipped.value = if (k in flipped.value) flipped.value - k else flipped.value + k
        prefs.edit().putStringSet(KEY, flipped.value).apply()
    }

    private companion object {
        const val KEY = "flipped"
    }
}

/** The home panel's "New" and "Popular" shelves of every tracker, loaded once per app start (TTL in [ShelfRules]). */
class Shelves(context: Context, private val trackers: List<TrackerProvider>, private val switches: SectionSwitches) {
    private val cache = ShelfCache(File(context.filesDir, "shelves"))
    private val batches = mutableStateMapOf<Pair<String, Browse>, ShelfBatch>()

    /** Per tracker: why its shelves could not be refreshed (the old ones are still shown). */
    val errors = mutableStateMapOf<String, String>()

    /** true while some tracker's shelves are being fetched. */
    val loading = mutableStateOf(false)

    fun shelves(kind: Browse, isOn: (TrackerProvider) -> Boolean): List<Shelf> = trackers.filter(isOn).flatMap { t ->
        val batch = batches[t.id to kind] ?: return@flatMap emptyList()
        t.sections.filter { switches.isOn(t, it) }.mapNotNull { s -> batch.sections[s.id]?.takeIf { it.isNotEmpty() }?.let { Shelf(t, s, it) } }
    }

    /** Shows what is on disk, then refreshes what is stale. Trackers that run through a WebView go last. */
    suspend fun refresh(isOn: (TrackerProvider) -> Boolean, force: Boolean = false) {
        trackers.forEach { t -> Browse.entries.forEach { k -> withContext(Dispatchers.IO) { cache.read(t.id, k) }?.let { batches[t.id to k] = it } } }
        loading.value = true
        try {
            for (t in trackers.sortedBy { it.requiresSignIn }) {
                if (!isOn(t) || (t.requiresSignIn && !t.isSignedIn)) continue
                try {
                    if (t.sections.isEmpty() || Browse.entries.any { !ShelfRules.isFresh(batches[t.id to it], System.currentTimeMillis()) }) {
                        t.refreshSections()
                    }
                    val on = t.sections.filter { switches.isOn(t, it) }
                    for (k in Browse.entries) batches[t.id to k] = ShelfRules.load(cache, t, k, on, System.currentTimeMillis(), force)
                    errors.remove(t.id)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    errors[t.id] = e.message ?: e.toString()
                }
            }
        } finally {
            loading.value = false
        }
    }
}

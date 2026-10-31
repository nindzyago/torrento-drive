package io.github.nindzyago.torrentodrive.trackers

import androidx.annotation.StringRes
import io.github.nindzyago.torrentodrive.R
import io.github.nindzyago.torrentodrive.lang.Languages

/** Torrent category, derived from the tracker's forum section (if known) and from the title. */
enum class Category(@StringRes val label: Int) {
    Movies(R.string.cat_movies),
    Series(R.string.cat_series),
    Cartoons(R.string.cat_cartoons),
    Docs(R.string.cat_docs),
    Sport(R.string.cat_sport),
    Music(R.string.cat_music),
    Other(R.string.cat_other);

    companion object {
        /** Language-neutral patterns; words of each language come from the language packs. */
        private val common = mapOf(
            Sport to listOf("\\bf1\\b", "ufc", "\\bnba\\b", "\\bnhl\\b", "uefa"),
            Series to listOf("\\bs\\d{1,2}\\b", "s\\d{1,2}e\\d{1,2}"),
            Docs to listOf("bbc", "discovery", "national geographic"),
            Music to listOf("lossless", "flac", "\\bmp3\\b"),
            Movies to listOf("hdrip", "bdrip", "web-?dl", "webrip", "remux", "dvdrip", "\\b(720|1080|2160)p\\b"),
        )

        /** Checked in this order: a sports broadcast in 1080p is sport, not a movie. */
        private val rules by lazy {
            listOf(Sport, Cartoons, Series, Docs, Music, Movies).map { cat ->
                val words = common[cat].orEmpty() + Languages.collect { it.categories[cat].orEmpty() }
                cat to Regex(words.joinToString("|"))
            }
        }

        fun of(item: TorrentItem): Category {
            val text = "${item.forum} ${item.title}".lowercase()
            return rules.firstOrNull { (_, re) -> re.containsMatchIn(text) }?.first ?: Other
        }
    }
}

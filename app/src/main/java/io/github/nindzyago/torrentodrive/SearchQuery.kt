package io.github.nindzyago.torrentodrive

import io.github.nindzyago.torrentodrive.lang.Languages
import io.github.nindzyago.torrentodrive.trackers.TorrentItem

/** A parsed voice phrase: what to search for and in what quality. */
data class SearchQuery(val text: String, val quality: String?) {

    fun matches(item: TorrentItem): Boolean {
        val pattern = when (quality) {
            "2160p" -> Regex("2160|4k|uhd", RegexOption.IGNORE_CASE)
            "1080p" -> Regex("1080")
            "720p" -> Regex("720")
            else -> return true
        }
        return pattern.containsMatchIn(item.title)
    }

    val label: String get() = if (quality == null) "«$text»" else "«$text» · $quality"

    companion object {
        private fun alt(words: List<String>) = words.joinToString("|")

        private val fillers = Regex(
            "^((${alt(Languages.collect { it.commands })})( (${alt(Languages.collect { it.pronouns })}))?\\s+)?" +
                "((${alt(Languages.collect { it.kinds })})\\s+)?",
            RegexOption.IGNORE_CASE,
        )

        /** Language-neutral names of each quality; spoken ones come from the language packs. */
        private val qualityNames = listOf(
            "2160p" to listOf("4\\s?k", "2160p?", "uhd"),
            "1080p" to listOf("1080p?"),
            "720p" to listOf("720p?"),
        )
        private val qualities = qualityNames.map { (q, names) ->
            val all = names + Languages.collect { it.qualities[q].orEmpty() }
            val prepositions = alt(Languages.collect { it.qualityPrepositions })
            q to Regex("\\b(($prepositions)\\s+)?(${alt(all)})\\b", RegexOption.IGNORE_CASE)
        }

        fun parse(phrase: String): SearchQuery {
            var text = phrase.trim().replace(fillers, "")
            var quality: String? = null
            for ((q, re) in qualities) {
                if (re.containsMatchIn(text)) {
                    quality = q
                    text = text.replace(re, " ")
                    break
                }
            }
            return SearchQuery(text.replace(Regex("\\s+"), " ").trim(), quality)
        }
    }
}

package io.github.nindzyago.torrentodrive.lang

import io.github.nindzyago.torrentodrive.trackers.Category

/**
 * Words of one language that the app needs besides the UI strings: to clean up a spoken query
 * ("find me the movie Dune in 4K" → "Dune", 2160p) and to guess a release's category from its
 * title. Every entry is a lowercase regex fragment; a word stem ("мульт") matches longer words.
 *
 * Packs of all languages are applied together, because a Russian tracker shows Russian titles
 * whatever the UI language is. To add a language, copy [English], translate it and register it
 * in [Languages.all]; the UI strings go to `res/values-<lang>/strings.xml`.
 */
interface LanguageWords {
    /** Language code, the same as in `values-<tag>`: "en", "ru". */
    val tag: String

    /** The language's own name for the language menu: "English", "Русский". */
    val name: String

    /** Flag emoji shown on the language button. */
    val flag: String

    /** Commands a spoken query starts with: "find", "play", "i want to watch". */
    val commands: List<String>

    /** Words right after a command: "me" in "show me". */
    val pronouns: List<String>

    /** Kind of content before the title: "the movie", "series". */
    val kinds: List<String>

    /** A word before the quality: "in" in "in 4K". */
    val qualityPrepositions: List<String>

    /** Spoken names of a quality ("2160p", "1080p", "720p") besides the usual "4K" / "1080p". */
    val qualities: Map<String, List<String>>

    /** Keywords of each category in titles and tracker section names. */
    val categories: Map<Category, List<String>>
}

object Languages {
    /** All language packs. Add yours here. */
    val all: List<LanguageWords> = listOf(English, Russian)

    fun <T> collect(pick: (LanguageWords) -> List<T>): List<T> = all.flatMap(pick).distinct()
}

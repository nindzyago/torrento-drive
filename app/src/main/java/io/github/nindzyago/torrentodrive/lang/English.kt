package io.github.nindzyago.torrentodrive.lang

import io.github.nindzyago.torrentodrive.trackers.Category

object English : LanguageWords {
    override val tag = "en"
    override val name = "English"
    override val flag = "🇬🇧"

    override val commands = listOf(
        "i want to watch", "search for", "search", "find", "play", "show", "watch",
    )
    override val pronouns = listOf("me")
    override val kinds = listOf("the movie", "movie", "the series", "series", "tv show")
    override val qualityPrepositions = listOf("in")
    override val qualities = mapOf(
        "2160p" to listOf("ultra\\s?hd"),
        "1080p" to listOf("full\\s?hd"),
    )
    override val categories = mapOf(
        Category.Sport to listOf("sport", "football", "soccer", "hockey", "tennis", "boxing"),
        Category.Cartoons to listOf("anime", "cartoon", "animation", "animated"),
        Category.Series to listOf("tv series", "episodes", "season", "episode"),
        Category.Docs to listOf("documentary", "docu"),
        Category.Music to listOf("music", "album", "discography", "concert", "soundtrack"),
        Category.Movies to listOf("movie", "film"),
    )
}

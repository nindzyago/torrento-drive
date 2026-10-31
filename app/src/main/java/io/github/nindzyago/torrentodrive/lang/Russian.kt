package io.github.nindzyago.torrentodrive.lang

import io.github.nindzyago.torrentodrive.trackers.Category

object Russian : LanguageWords {
    override val tag = "ru"
    override val name = "Русский"
    override val flag = "🇷🇺"

    override val commands = listOf(
        "хочу посмотреть", "посмотреть", "найди", "найти", "включи", "покажи", "поставь",
        "давай", "поищи",
    )
    override val pronouns = listOf("мне")
    override val kinds = listOf("фильм", "сериал", "мультфильм", "мультик", "кино")
    override val qualityPrepositions = listOf("в")
    override val qualities = mapOf(
        "2160p" to listOf("4\\s?к", "ультра\\s?hd"),
        "1080p" to listOf("фулл?\\s?(эйч\\s?ди|hd)"),
    )
    override val categories = mapOf(
        Category.Sport to listOf("спорт", "футбол", "хоккей", "теннис", "формула", "бокс", "олимпи"),
        Category.Cartoons to listOf("мульт", "аниме"),
        Category.Series to listOf("сериал", "сезон", "серии", "эпизод"),
        Category.Docs to listOf("документ", "научно", "познават"),
        Category.Music to listOf("музык", "альбом", "дискограф", "концерт", "саундтрек"),
        Category.Movies to listOf("фильм", "кино"),
    )
}

package io.github.nindzyago.torrentodrive.trackers

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Reads a torrent description written the way Russian trackers write them:
 * "Год выхода: 2026", "Жанр: драма", "В ролях: …", then "О фильме:" (or "Описание:") with the plot,
 * then technical lines and MediaInfo. Works on plain text with line breaks, whatever the tracker's HTML.
 */
object DescriptionText {
    /** Labels as uploaders write them (lowercase, "ё" as "е"), per fact; the first that matches wins. */
    private val labels: List<Pair<Fact, (String) -> Boolean>> = listOf(
        Fact.Original to { l -> l.startsWith("оригинальное название") || l == "original title" },
        Fact.Title to { l -> l == "название" || l == "русское название" },
        Fact.Year to { l -> l == "год" || l.startsWith("год выхода") || l.startsWith("год выпуска") },
        Fact.Country to { l -> l == "страна" || l == "производство" || l == "страна производства" },
        Fact.Genre to { l -> l == "жанр" },
        Fact.Duration to { l -> l == "продолжительность" || l == "длительность" || l == "время" },
        Fact.Director to { l -> l == "режиссер" || l == "режиссеры" || l == "режиссер-постановщик" },
        Fact.Cast to { l -> l == "в ролях" || l == "актеры" || l == "роли озвучивали" },
        Fact.Translation to { l -> l.startsWith("перевод") || l == "озвучивание" || l == "озвучка" },
        Fact.Subtitles to { l -> l.startsWith("субтитры") },
        Fact.Quality to { l -> l == "качество" || l == "качество видео" },
        Fact.Video to { l -> l == "видео" },
        Fact.Audio to { l -> l.startsWith("аудио") },
    )

    /** Labels that start the plot. */
    private val plot = setOf("о фильме", "описание", "сюжет", "о сериале", "о мультфильме", "о мультсериале", "об аниме", "о передаче", "о программе", "краткое описание")

    /** Where the readable part ends: MediaInfo and similar technical dumps. */
    private val tail = listOf("general", "unique id", "complete name", "mediainfo", "media info", "код:", "формат файла:", "скриншоты", "семпл", "сэмпл")

    private val labelLine = Regex("^([\\p{L}\\p{N} .()\\-]{2,40}?)\\s*[:：]\\s*(.*)$")
    private val ratingRe = Regex("(IMDb|IMDB|Кинопоиск|КиноПоиск|KinoPoisk|Kinopoisk|КП)\\s*[:\\-]?\\s*(\\d{1,2}[.,]\\d{1,3})")

    private const val MAX_CAST = 6
    private const val MAX_DESCRIPTION = 1200

    data class Parsed(val description: String, val facts: Map<Fact, String>, val ratings: List<Pair<String, String>>)

    fun parse(text: String): Parsed {
        val lines = text.replace(' ', ' ').lines().map { it.trim() }
        val readable = lines.takeWhile { line -> tail.none { line.lowercase().startsWith(it) } }

        val facts = linkedMapOf<Fact, String>()
        val plotLines = mutableListOf<String>()
        var inPlot = false
        for (line in readable) {
            val m = labelLine.find(line)
            val label = m?.groupValues?.get(1)?.let(::norm)
            val fact = label?.let { l -> labels.firstOrNull { it.second(l) }?.first }
            when {
                label != null && label in plot -> {
                    inPlot = true
                    m.groupValues[2].takeIf { it.isNotBlank() }?.let { plotLines += it }
                }
                fact != null -> {
                    inPlot = false
                    val value = clean(m.groupValues[2])
                    if (value.isNotEmpty() && fact !in facts) facts[fact] = if (fact == Fact.Cast) shortCast(value) else value
                }
                inPlot && line.isEmpty() && plotLines.isNotEmpty() -> inPlot = false
                inPlot && line.isNotEmpty() -> plotLines += line
            }
        }

        var description = plotLines.joinToString(" ").replace(Regex("\\s+"), " ").trim()
        // no "About the film" label: the first real paragraph is most likely the plot
        if (description.isEmpty()) {
            description = paragraphs(readable).firstOrNull { p -> p.length >= 120 && labelLine.find(p)?.groupValues?.get(1)?.let(::norm)?.let { l -> labels.any { it.second(l) } } != true }.orEmpty()
        }
        if (description.length > MAX_DESCRIPTION) description = description.take(MAX_DESCRIPTION).substringBeforeLast(' ') + "…"

        val ratings = ratingRe.findAll(readable.joinToString("\n")).map { r ->
            val name = if (r.groupValues[1].startsWith("I", ignoreCase = true)) "IMDb" else "КП"
            name to r.groupValues[2].replace(',', '.')
        }.distinctBy { it.first }.toList()
        return Parsed(description, facts, ratings)
    }

    /**
     * Text of a description element with its line breaks: `<br>` and block elements become new lines;
     * [skip] elements (spoilers with MediaInfo, scripts) are left out.
     */
    fun of(root: Element, skip: String = "script, style"): String {
        val copy = root.clone()
        copy.select(skip).remove()
        val out = StringBuilder()
        fun walk(n: Node) {
            when (n) {
                is TextNode -> out.append(n.wholeText.replace('\n', ' '))
                is Element -> {
                    val tag = n.normalName()
                    if (tag == "br") { out.append('\n'); return }
                    val block = tag in BLOCKS
                    if (block) out.append('\n')
                    n.childNodes().forEach(::walk)
                    if (block) out.append('\n')
                }
            }
        }
        walk(copy)
        return out.toString()
    }

    private val BLOCKS = setOf("div", "p", "li", "ul", "ol", "tr", "table", "hr", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre")

    private fun norm(label: String) = label.lowercase().replace('ё', 'е').replace(Regex("\\s+"), " ").trim()

    private fun clean(v: String) = v.replace(Regex("\\s+"), " ").trim().trim('|', ' ', ',', ';').trim()

    private fun shortCast(v: String): String {
        val names = v.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return if (names.size <= MAX_CAST) names.joinToString(", ") else names.take(MAX_CAST).joinToString(", ") + "…"
    }

    private fun paragraphs(lines: List<String>): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        for (l in lines) {
            if (l.isEmpty()) {
                if (cur.isNotEmpty()) out += cur.toString().trim()
                cur.clear()
            } else cur.append(l).append(' ')
        }
        if (cur.isNotEmpty()) out += cur.toString().trim()
        return out
    }
}

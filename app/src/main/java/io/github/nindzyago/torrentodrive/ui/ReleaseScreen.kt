package io.github.nindzyago.torrentodrive.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.nindzyago.torrentodrive.Progress
import io.github.nindzyago.torrentodrive.R
import io.github.nindzyago.torrentodrive.WatchEntry
import io.github.nindzyago.torrentodrive.torrserver.TorrentFile
import io.github.nindzyago.torrentodrive.torrserver.TorrentStats
import io.github.nindzyago.torrentodrive.trackers.Fact
import io.github.nindzyago.torrentodrive.trackers.TorrentDetails
import io.github.nindzyago.torrentodrive.trackers.TorrentItem

/** Everything the release screen shows; files, stats and details arrive one by one while it is open. */
internal class Release(
    val item: TorrentItem,
    val poster: String?,
    val details: TorrentDetails?,
    val files: List<TorrentFile>?,
    val error: String?,
    val stats: TorrentStats?,
)

/**
 * The release screen: poster and the torrent's numbers on the left; title, facts, the plot and the Watch button
 * on the right, the episodes below. Nothing starts by itself: focus waits on Watch.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReleaseScreen(r: Release, watched: WatchEntry?, progressOf: (TorrentFile) -> Progress?, onPlay: (TorrentFile) -> Unit) {
    val d = r.details
    val title = d?.facts?.get(Fact.Title) ?: shortTitle(r.item.title)
    val files = r.files
    val watchFocus = remember { FocusRequester() }
    val list = rememberLazyListState()
    LaunchedEffect(Unit) {
        runCatching { watchFocus.requestFocus() }
        // focusing the button scrolls it into view a frame later; then bring the title back on screen
        kotlinx.coroutines.delay(150)
        list.animateScrollToItem(0)
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(30.dp)) {
        // left: poster and the torrent's own numbers
        Column(Modifier.width(210.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PosterImage(r.poster, title, 90.sp, Modifier.fillMaxWidth().height(290.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.height(4.dp))
            val seeds = maxOf(r.item.seeds, r.stats?.seeders ?: 0)
            Text(stringResource(R.string.seeds_peers, seeds, r.item.peers), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = seedColor(seeds))
            val total = files?.sumOf { it.length } ?: 0L
            Text(
                listOfNotNull(
                    if (total > 0) formatSize(total) else r.item.size,
                    files?.takeIf { it.size > 1 }?.let { pluralStringResource(R.plurals.files_count, it.size, it.size) },
                ).joinToString(" · "),
                fontSize = 11.sp, color = Palette.Muted,
            )
            Text(
                listOfNotNull(r.item.tracker, d?.added?.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.added_on, it) }).joinToString(" · "),
                fontSize = 11.sp, color = Palette.Muted,
            )
            r.stats?.takeIf { it.totalPeers > 0 || it.speedBytes > 0 }?.let { st ->
                Text(stringResource(R.string.live_peers, st.activePeers, formatSpeed(st.speedBytes)), fontSize = 11.sp, color = Palette.Muted)
            }
            d?.facts?.get(Fact.Video)?.let { SmallFact(stringResource(R.string.fact_video), it) }
            d?.facts?.get(Fact.Audio)?.let { SmallFact(stringResource(R.string.fact_audio), it) }
            d?.facts?.get(Fact.Subtitles)?.let { SmallFact(stringResource(R.string.fact_subtitles), it) }
        }

        // right: what the film is, the button, the episodes
        LazyColumn(Modifier.weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)) {
            item(key = "head") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val original = listOfNotNull(d?.facts?.get(Fact.Original)?.takeIf { it != title }).joinToString()
                    if (original.isNotEmpty()) Text(original, fontSize = 13.sp, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val line = listOfNotNull(d?.facts?.get(Fact.Year), d?.facts?.get(Fact.Country)?.substringBefore(" / "), d?.facts?.get(Fact.Genre)?.lowercase(), d?.facts?.get(Fact.Duration)).joinToString(" · ")
                    if (line.isNotEmpty()) Text(line, fontSize = 13.sp, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        qualityOf(r.item.title)?.let { (q, c) -> Tag(q, c) }
                        d?.facts?.get(Fact.Quality)?.let { Tag(it.substringBefore('|').trim().take(28)) }
                        d?.facts?.get(Fact.Translation)?.let { Tag(it.substringBefore('|').substringBefore('(').trim().take(28), Palette.AccentSoft) }
                        d?.ratings?.forEach { (name, value) -> Tag("$name $value", Palette.Warn) }
                    }
                }
            }
            if (d != null && d.description.isNotEmpty()) item(key = "plot") { Plot(d.description) }
            if (d != null) item(key = "people") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    d.facts[Fact.Director]?.let { SmallFact(stringResource(R.string.fact_director), it, 1) }
                    d.facts[Fact.Cast]?.let { SmallFact(stringResource(R.string.fact_cast), it, 1) }
                    d.facts[Fact.Translation]?.takeIf { it.length > 28 }?.let { SmallFact(stringResource(R.string.fact_translation), it, 1) }
                }
            }
            item(key = "watch") {
                Column {
                    Spacer(Modifier.height(6.dp))
                    WatchRow(r, watched, progressOf, onPlay, watchFocus)
                }
            }
            if (files != null && files.isNotEmpty()) {
                item(key = "files-title") {
                    Text(
                        stringResource(if (files.size > 1) R.string.episodes else R.string.file),
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text, modifier = Modifier.padding(top = 10.dp),
                    )
                }
                val lastIdx = files.indexOfFirst { it.index == watched?.fileIndex }
                items(files, key = { it.index }) { file ->
                    val i = files.indexOf(file)
                    val p = progressOf(file)
                    val seen = p?.finished == true || (lastIdx >= 0 && i < lastIdx)
                    val isLast = i == lastIdx
                    TvCard(onClick = { onPlay(file) }, modifier = Modifier.fillMaxWidth()) {
                        if (p != null && p.resumable) ProgressLine(p)
                        Row(Modifier.padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("%02d".format(i + 1), fontSize = 12.sp, color = if (isLast) Palette.Accent else Palette.Muted, modifier = Modifier.width(24.dp))
                            Text(file.name, fontSize = 13.sp, color = if (seen) Palette.Muted else Palette.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (isLast) { Tag(stringResource(R.string.watched_last), Palette.AccentSoft); Spacer(Modifier.width(9.dp)) }
                            if (p != null && p.resumable) { Text(formatTime(p.positionMs), fontSize = 10.sp, color = Palette.AccentSoft); Spacer(Modifier.width(9.dp)) }
                            else if (seen) { Text(stringResource(R.string.watched), fontSize = 10.sp, color = Palette.Muted); Spacer(Modifier.width(9.dp)) }
                            Text(formatSize(file.length), fontSize = 10.sp, color = Palette.Muted)
                        }
                    }
                }
            }
        }
    }
}

/** The Watch button: continue where it was left, start from episode 1, or (still opening) wait. */
@Composable
private fun WatchRow(r: Release, watched: WatchEntry?, progressOf: (TorrentFile) -> Progress?, onPlay: (TorrentFile) -> Unit, focus: FocusRequester) {
    val files = r.files
    if (r.error != null) {
        Text(stringResource(R.string.open_failed, r.error), color = Palette.Accent, fontSize = 14.sp)
        return
    }
    if (files != null && files.isEmpty()) {
        Text(stringResource(R.string.no_video), color = Palette.Accent, fontSize = 14.sp)
        return
    }
    val lastIdx = files?.indexOfFirst { it.index == watched?.fileIndex } ?: -1
    // continue the last episode if it was left midway, otherwise the one after it
    val lastUnfinished = files?.getOrNull(lastIdx)?.takeIf { progressOf(it)?.resumable == true }
    val next = lastUnfinished ?: files?.getOrNull(lastIdx + 1)?.takeIf { lastIdx >= 0 }
    val (label, file) = when {
        files == null -> stringResource(R.string.opening_torrent) to null
        next != null && files.size > 1 -> stringResource(R.string.continue_episode, files.indexOf(next) + 1) to next
        next != null -> stringResource(R.string.continue_at, formatTime(progressOf(next)?.positionMs ?: 0)) to next
        files.size > 1 -> stringResource(R.string.watch_first) to files.first()
        else -> stringResource(R.string.watch) to files.first()
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // one button for every state, so focus stays on it when the file list arrives
        TvButton(label, { file?.let(onPlay) }, Modifier.focusRequester(focus), primary = true, large = true, icon = if (file != null) Icon.Play else null)
        if (files == null) r.stats?.let { Text(stringResource(R.string.live_peers, it.activePeers, formatSpeed(it.speedBytes)), fontSize = 11.sp, color = Palette.Muted) }
    }
}

/** The plot: a few lines; OK shows all of it. */
@Composable
private fun Plot(text: String) {
    var open by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .border(1.dp, if (focused) Palette.Accent else Color.Transparent, RoundedCornerShape(8.dp))
            .background(if (focused) Palette.Glass else Color.Transparent)
            .clickable { open = !open }
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Text(text, fontSize = 13.sp, lineHeight = 19.sp, color = Palette.Text.copy(alpha = 0.9f), maxLines = if (open) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SmallFact(label: String, value: String, lines: Int = 2) {
    Text("$label: $value", fontSize = 10.sp, lineHeight = 14.sp, color = Palette.Muted, maxLines = lines, overflow = TextOverflow.Ellipsis)
}

/** Behind the release screen: its poster, blurred and darkened (Android 12+ blurs; older ones only darken). */
@Composable
internal fun PosterBackdrop(url: String?) {
    if (url == null) return
    val context = LocalContext.current
    var image by remember(url) { mutableStateOf<ImageBitmap?>(Posters.cached(url)) }
    LaunchedEffect(url) { if (image == null) image = Posters.load(context, url) }
    val img = image ?: return
    Box(Modifier.fillMaxSize()) {
        Image(img, null, contentScale = ContentScale.Crop, alpha = 0.55f, modifier = Modifier.fillMaxSize().blur(36.dp))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Palette.Bg.copy(alpha = 0.55f), Palette.Bg.copy(alpha = 0.88f)))))
    }
}

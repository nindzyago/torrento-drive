package io.github.nindzyago.torrentodrive.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
    /** The [error] is TorrServer not answering. */
    val serverDown: Boolean = false,
)

/**
 * The release screen: poster and the torrent's numbers on the left; title, facts, the plot and the Watch button
 * on the right, the episodes below. Nothing starts by itself: focus waits on Watch.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReleaseScreen(
    r: Release,
    watched: WatchEntry?,
    progressOf: (TorrentFile) -> Progress?,
    onPlay: (TorrentFile) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val d = r.details
    val title = d?.facts?.get(Fact.Title) ?: shortTitle(r.item.title)
    val files = r.files
    val watchFocus = remember { FocusRequester() }
    val list = rememberLazyListState()
    val context = LocalContext.current
    var torrServe by remember { mutableStateOf(torrServeLaunch(context)) }
    // notice TorrServe being installed while the panel is on screen
    LaunchedEffect(r.serverDown, torrServe == null) {
        while (r.serverDown && torrServe == null) {
            kotlinx.coroutines.delay(2000)
            torrServe = torrServeLaunch(context)
        }
    }
    // whatever stands in the Watch button's place (an error panel, Try again, Back) takes the focus over
    val state = when {
        r.error != null && r.serverDown -> if (torrServe != null) "open-torrserve" else "install-torrserve"
        r.error != null -> "error"
        files != null && files.isEmpty() -> "no-video"
        else -> "watch"
    }
    LaunchedEffect(state) {
        kotlinx.coroutines.delay(50) // let the new button compose first
        runCatching { watchFocus.requestFocus() }
        // focusing the button scrolls it into view a frame later; then bring the title back on screen
        kotlinx.coroutines.delay(150)
        list.animateScrollToItem(0)
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        // left: poster and the torrent's own numbers
        Column(Modifier.width(210.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PosterImage(r.poster, title, 90.sp, Modifier.fillMaxWidth().height(290.dp).clip(Radius.M))
            Spacer(Modifier.height(4.dp))
            val seeds = maxOf(r.item.seeds, r.stats?.seeders ?: 0)
            Text(stringResource(R.string.seeds_peers, pluralStringResource(R.plurals.seeds_count, seeds, seeds), pluralStringResource(R.plurals.peers_count, r.item.peers, r.item.peers)), fontSize = Type.Caption, fontWeight = FontWeight.SemiBold, color = seedColor(seeds))
            val total = files?.sumOf { it.length } ?: 0L
            Text(
                listOfNotNull(
                    if (total > 0) formatSize(total) else trackerSize(r.item.size),
                    files?.takeIf { it.size > 1 }?.let { pluralStringResource(R.plurals.files_count, it.size, it.size) },
                ).joinToString(" · "),
                fontSize = Type.Caption, color = Palette.Muted,
            )
            Text(
                listOfNotNull(r.item.tracker, d?.added?.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.added_on, it) }).joinToString(" · "),
                fontSize = Type.Caption, color = Palette.Muted,
            )
            r.stats?.takeIf { it.totalPeers > 0 || it.speedBytes > 0 }?.let { st ->
                Text(stringResource(R.string.live_peers, pluralStringResource(R.plurals.connections_count, st.activePeers, st.activePeers), formatSpeed(st.speedBytes)), fontSize = Type.Caption, color = Palette.Muted)
            }
            d?.facts?.get(Fact.Video)?.let { SmallFact(stringResource(R.string.fact_video), it) }
            d?.facts?.get(Fact.Audio)?.let { SmallFact(stringResource(R.string.fact_audio), it) }
            d?.facts?.get(Fact.Subtitles)?.let { SmallFact(stringResource(R.string.fact_subtitles), it) }
        }

        // right: what the film is, the button, the episodes
        LazyColumn(Modifier.weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)) {
            item(key = "head") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, fontSize = Type.Headline, fontWeight = FontWeight.Bold, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val original = listOfNotNull(d?.facts?.get(Fact.Original)?.takeIf { it != title }).joinToString()
                    if (original.isNotEmpty()) Text(original, fontSize = Type.Caption, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val line = listOfNotNull(d?.facts?.get(Fact.Year), d?.facts?.get(Fact.Country)?.substringBefore(" / "), d?.facts?.get(Fact.Genre)?.lowercase(), d?.facts?.get(Fact.Duration)).joinToString(" · ")
                    if (line.isNotEmpty()) Text(line, fontSize = Type.Caption, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    Spacer(Modifier.height(8.dp))
                    WatchRow(r, watched, progressOf, onPlay, onRetry, onBack, torrServe, watchFocus)
                }
            }
            if (files != null && files.isNotEmpty()) {
                item(key = "files-title") {
                    Text(
                        stringResource(if (files.size > 1) R.string.episodes else R.string.file),
                        fontSize = Type.Caption, fontWeight = FontWeight.SemiBold, color = Palette.Text, modifier = Modifier.padding(top = 12.dp),
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
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("%02d".format(i + 1), fontSize = Type.Caption, color = if (isLast) Palette.Accent else Palette.Muted, modifier = Modifier.width(24.dp))
                            Text(file.name, fontSize = Type.Caption, color = if (seen) Palette.Muted else Palette.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (isLast) { Tag(stringResource(R.string.watched_last), Palette.AccentSoft); Spacer(Modifier.width(8.dp)) }
                            if (p != null && p.resumable) { Text(formatTime(p.positionMs), fontSize = Type.Caption, color = Palette.AccentSoft); Spacer(Modifier.width(8.dp)) }
                            else if (seen) { Text(stringResource(R.string.watched), fontSize = Type.Caption, color = Palette.Muted); Spacer(Modifier.width(8.dp)) }
                            Text(formatSize(file.length), fontSize = Type.Caption, color = Palette.Muted)
                        }
                    }
                }
            }
        }
    }
}

/** The Watch button: continue where it was left, start from episode 1, or (still opening) wait. */
@Composable
private fun WatchRow(
    r: Release,
    watched: WatchEntry?,
    progressOf: (TorrentFile) -> Progress?,
    onPlay: (TorrentFile) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    torrServe: Intent?,
    focus: FocusRequester,
) {
    val files = r.files
    if (r.error != null && r.serverDown) {
        ServerDownPanel(r.error, torrServe, onRetry, focus)
        return
    }
    // every dead end keeps a button, so focus always has somewhere to be
    if (r.error != null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.open_failed, r.error), color = Palette.Accent, fontSize = Type.Caption, lineHeight = Type.CaptionLine)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvButton(stringResource(R.string.try_again), onRetry, Modifier.focusRequester(focus), primary = true, large = true)
                TvButton(stringResource(R.string.player_back), onBack, large = true)
            }
        }
        return
    }
    if (files != null && files.isEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.no_video), color = Palette.Accent, fontSize = Type.Caption)
            TvButton(stringResource(R.string.player_back), onBack, Modifier.focusRequester(focus), primary = true, large = true)
        }
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
        if (files == null) r.stats?.let { Text(stringResource(R.string.live_peers, pluralStringResource(R.plurals.connections_count, it.activePeers, it.activePeers), formatSpeed(it.speedBytes)), fontSize = Type.Caption, color = Palette.Muted) }
    }
}

/**
 * In the Watch button's place while TorrServer doesn't answer: what is wrong, how to fix it, a button that opens
 * (or installs) TorrServe and Try again; the raw error stays below as one small line.
 */
@Composable
private fun ServerDownPanel(detail: String, torrServe: Intent?, onRetry: () -> Unit, focus: FocusRequester) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            Modifier
                .background(Palette.Surface, Radius.L)
                .border(1.dp, Palette.Line, Radius.L)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(8.dp).background(Palette.Accent, CircleShape))
                Text(stringResource(R.string.server_down), fontSize = Type.Body, fontWeight = FontWeight.SemiBold, color = Palette.Text)
            }
            Text(
                stringResource(if (torrServe != null) R.string.release_server_start else R.string.release_server_install),
                fontSize = Type.Caption, lineHeight = Type.CaptionLine, color = Palette.Muted,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (torrServe != null) {
                    TvButton(stringResource(R.string.open_torrserve), { context.startActivity(torrServe) }, Modifier.focusRequester(focus), primary = true)
                } else {
                    InstallTorrServeButton(reinstall = false, modifier = Modifier.focusRequester(focus))
                }
                TvButton(stringResource(R.string.try_again), onRetry)
            }
        }
        Text(detail, fontSize = Type.Caption, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            .clip(Radius.S)
            .onFocusChanged { focused = it.isFocused }
            .border(1.dp, if (focused) Palette.Accent else Color.Transparent, Radius.S)
            .background(if (focused) Palette.Glass else Color.Transparent)
            .clickable { open = !open }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text, fontSize = Type.Caption, lineHeight = Type.CaptionLine, color = Palette.Text.copy(alpha = 0.9f), maxLines = if (open) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SmallFact(label: String, value: String, lines: Int = 2) {
    Text("$label: $value", fontSize = Type.Caption, lineHeight = Type.CaptionLine, color = Palette.Muted, maxLines = lines, overflow = TextOverflow.Ellipsis)
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

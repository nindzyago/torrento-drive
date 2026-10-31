package io.github.nindzyago.torrentodrive.ui

import android.net.Uri
import android.view.KeyEvent as AndroidKey
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import io.github.nindzyago.torrentodrive.Progress
import io.github.nindzyago.torrentodrive.R
import io.github.nindzyago.torrentodrive.torrserver.TorrentFile
import io.github.nindzyago.torrentodrive.torrserver.TorrentStats
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

/** Show the "next episode" card this long before the end of an episode. */
private const val NEXT_CARD_MS = 20_000L
/** Start buffering the next episode in TorrServer this long before the end. */
private const val PRELOAD_NEXT_MS = 120_000L
private const val HIDE_CONTROLS_MS = 5_000L
private const val SEEK_STEP_MS = 10_000L

/**
 * Built-in player (Media3/ExoPlayer) for a torrent's video files: remembers the position of every episode,
 * switches episodes from the overlay, offers the next one near the end and plays it on its own.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun PlayerScreen(
    title: String,
    episodes: List<TorrentFile>,
    start: TorrentFile,
    streamUrl: (TorrentFile) -> Uri,
    savedProgress: (TorrentFile) -> Progress?,
    onProgress: (TorrentFile, Progress) -> Unit,
    onEpisodeStarted: (TorrentFile) -> Unit,
    preload: suspend (TorrentFile) -> Unit,
    stats: suspend () -> TorrentStats?,
    onExternal: (TorrentFile) -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).setSeekBackIncrementMs(SEEK_STEP_MS).setSeekForwardIncrementMs(SEEK_STEP_MS).build() }

    var file by remember { mutableStateOf(start) }
    val index = episodes.indexOf(file)
    val prev = episodes.getOrNull(index - 1)
    val next = episodes.getOrNull(index + 1)

    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var ended by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var resumedFrom by remember { mutableStateOf<Long?>(null) }
    var torrentStats by remember { mutableStateOf<TorrentStats?>(null) }

    var controls by remember { mutableStateOf(true) }
    /** Bumped on every key press: restarts the timer that hides the overlay. */
    var interaction by remember { mutableIntStateOf(0) }
    /** The viewer dismissed the "next episode" card for the current episode. */
    var nextDismissed by remember { mutableStateOf(false) }

    fun save() {
        val dur = player.duration
        if (dur > 0 && dur != C.TIME_UNSET) onProgress(file, Progress(player.currentPosition.coerceAtMost(dur), dur))
    }

    fun switchTo(target: TorrentFile) {
        save()
        file = target
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                ended = state == Player.STATE_ENDED
            }
            override fun onTracksChanged(t: Tracks) {
                tracks = t
            }
            override fun onPlayerError(e: PlaybackException) {
                error = e.cause?.message ?: e.errorCodeName
            }
        }
        player.addListener(listener)
        onDispose {
            save()
            player.removeListener(listener)
            player.release()
        }
    }

    // a new episode: load it, continue from the saved position, show the overlay for a moment
    LaunchedEffect(file) {
        error = null
        ended = false
        nextDismissed = false
        val saved = savedProgress(file)?.takeIf { it.resumable }
        resumedFrom = saved?.positionMs
        player.setMediaItem(MediaItem.Builder().setUri(streamUrl(file)).setMediaId("${file.index}").build(), saved?.positionMs ?: 0L)
        player.prepare()
        player.play()
        onEpisodeStarted(file)
        controls = true
        interaction++
    }

    // poll the position; save it every few seconds
    LaunchedEffect(Unit) {
        var tick = 0
        while (isActive) {
            position = player.currentPosition
            duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            buffered = player.bufferedPosition
            if (++tick % 10 == 0 && playing) save()
            delay(500)
        }
    }

    // torrent stats only while waiting for data
    LaunchedEffect(buffering) {
        while (buffering && isActive) {
            torrentStats = runCatching { stats() }.getOrNull()
            delay(1000)
        }
    }

    // warm up the next episode in TorrServer shortly before the end
    val remaining = if (duration > 0) duration - position else Long.MAX_VALUE
    val nearEnd = remaining < PRELOAD_NEXT_MS
    LaunchedEffect(file, nearEnd) {
        if (nearEnd && next != null) runCatching { preload(next) }
    }

    val nextCard = next != null && !nextDismissed && error == null && (ended || (duration > 0 && remaining <= NEXT_CARD_MS))
    LaunchedEffect(ended) {
        if (!ended) return@LaunchedEffect
        save()
        when {
            next != null && !nextDismissed -> switchTo(next)
            else -> onExit()
        }
    }

    LaunchedEffect(controls, interaction, playing) {
        if (controls && playing) {
            delay(HIDE_CONTROLS_MS)
            controls = false
        }
    }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }
    val errorFocus = remember { FocusRequester() }
    LaunchedEffect(controls, nextCard, error) {
        delay(50) // let the overlay compose before moving focus into it
        runCatching {
            when {
                error != null -> errorFocus.requestFocus()
                nextCard && !controls -> nextFocus.requestFocus()
                controls -> playFocus.requestFocus()
                else -> rootFocus.requestFocus()
            }
        }
    }

    BackHandler {
        if (controls && error == null) controls = false else onExit()
    }

    fun seekBy(ms: Long) {
        player.seekTo((player.currentPosition + ms).coerceIn(0L, (duration - 1000).coerceAtLeast(0L)))
        position = player.currentPosition
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { ev ->
                // Back is handled here: with a button focused, Compose would spend it on moving focus instead
                if (ev.key.nativeKeyCode == AndroidKey.KEYCODE_BACK) {
                    if (ev.type == KeyEventType.KeyUp) {
                        if (controls && error == null) controls = false else onExit()
                    }
                    return@onPreviewKeyEvent true
                }
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                interaction++
                when (ev.key.nativeKeyCode) {
                    // remote media keys work at any time
                    AndroidKey.KEYCODE_MEDIA_PLAY_PAUSE, AndroidKey.KEYCODE_MEDIA_PLAY, AndroidKey.KEYCODE_MEDIA_PAUSE -> { togglePlay(); true }
                    AndroidKey.KEYCODE_MEDIA_NEXT, AndroidKey.KEYCODE_CHANNEL_UP -> { next?.let(::switchTo); true }
                    AndroidKey.KEYCODE_MEDIA_PREVIOUS, AndroidKey.KEYCODE_CHANNEL_DOWN -> { prev?.let(::switchTo); true }
                    AndroidKey.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(SEEK_STEP_MS * 3); true }
                    AndroidKey.KEYCODE_MEDIA_REWIND -> { seekBy(-SEEK_STEP_MS * 3); true }
                    else -> false
                }
            }
            .focusRequester(rootFocus)
            .onKeyEvent { ev ->
                // with the overlay, the card or an error on screen the arrows move focus between their buttons
                if (ev.type != KeyEventType.KeyDown || controls || nextCard || error != null) return@onKeyEvent false
                when (ev.key.nativeKeyCode) {
                    AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER -> { togglePlay(); controls = true; true }
                    AndroidKey.KEYCODE_DPAD_LEFT -> { seekBy(-SEEK_STEP_MS); controls = true; true }
                    AndroidKey.KEYCODE_DPAD_RIGHT -> { seekBy(SEEK_STEP_MS); controls = true; true }
                    AndroidKey.KEYCODE_DPAD_UP, AndroidKey.KEYCODE_DPAD_DOWN -> { controls = true; true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true
                    isFocusable = false
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering && error == null) BufferingBadge(torrentStats, Modifier.align(Alignment.Center))

        AnimatedVisibility(controls && error == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Overlay(
                title = title,
                file = file,
                index = index,
                count = episodes.size,
                position = position,
                duration = duration,
                buffered = buffered,
                playing = playing,
                hasPrev = prev != null,
                hasNext = next != null,
                resumedFrom = resumedFrom,
                tracks = tracks,
                playFocus = playFocus,
                onSeek = ::seekBy,
                onTogglePlay = ::togglePlay,
                onPrev = { prev?.let(::switchTo) },
                onNext = { next?.let(::switchTo) },
                onFromStart = { player.seekTo(0); resumedFrom = null },
                onAudio = { cycleTrack(player, tracks, C.TRACK_TYPE_AUDIO, allowOff = false) },
                onSubtitles = { cycleTrack(player, tracks, C.TRACK_TYPE_TEXT, allowOff = true) },
                onExternal = { save(); onExternal(file) },
            )
        }

        if (nextCard && next != null) {
            NextEpisodeCard(
                next = next,
                number = index + 2,
                secondsLeft = if (ended) 0 else ((remaining + 999) / 1000).toInt(),
                focus = nextFocus,
                onPlay = { switchTo(next) },
                onCancel = { nextDismissed = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 42.dp, bottom = if (controls) 150.dp else 42.dp),
            )
        }

        error?.let { msg ->
            ErrorPanel(msg, errorFocus, onExternal = { save(); onExternal(file) }, onBack = onExit, modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun Overlay(
    title: String,
    file: TorrentFile,
    index: Int,
    count: Int,
    position: Long,
    duration: Long,
    buffered: Long,
    playing: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    resumedFrom: Long?,
    tracks: Tracks,
    playFocus: FocusRequester,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onFromStart: () -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onExternal: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // top: what is playing
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                .padding(start = 42.dp, end = 42.dp, top = 27.dp, bottom = 40.dp),
        ) {
            Text(title, fontSize = 13.sp, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (count > 1) {
                    Tag(stringResource(R.string.player_episode_of, index + 1, count), Palette.AccentSoft)
                    Spacer(Modifier.width(10.dp))
                }
                Text(file.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // bottom: progress and buttons
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(start = 42.dp, end = 42.dp, top = 50.dp, bottom = 27.dp),
        ) {
            SeekBar(position, duration, buffered, onSeek)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasPrev) TvButton(stringResource(R.string.player_prev), onPrev, icon = Icon.Prev)
                TvButton(
                    stringResource(if (playing) R.string.player_pause else R.string.player_play),
                    onTogglePlay,
                    Modifier.focusRequester(playFocus),
                    primary = true,
                    icon = if (playing) Icon.Pause else Icon.Play,
                )
                if (hasNext) TvButton(stringResource(R.string.player_next), onNext, icon = Icon.Next)
                if (resumedFrom != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.player_resumed, formatTime(resumedFrom)), fontSize = 11.sp, color = Palette.Muted)
                    TvButton(stringResource(R.string.player_from_start), onFromStart)
                }
                Spacer(Modifier.weight(1f))
                val audio = trackOptions(tracks, C.TRACK_TYPE_AUDIO)
                if (audio.size > 1) TvButton(stringResource(R.string.player_audio, audio.firstOrNull { it.selected }?.label ?: "—"), onAudio)
                val subs = trackOptions(tracks, C.TRACK_TYPE_TEXT)
                if (subs.isNotEmpty()) {
                    TvButton(stringResource(R.string.player_subs, subs.firstOrNull { it.selected }?.label ?: stringResource(R.string.player_off)), onSubtitles)
                }
                TvButton(stringResource(R.string.player_external), onExternal)
            }
        }
    }
}

/** Progress bar; when focused, left/right seek and holding the button seeks faster. */
@Composable
private fun SeekBar(position: Long, duration: Long, buffered: Long, onSeek: (Long) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (focused) 8.dp else 5.dp)
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val step = SEEK_STEP_MS * (1 + (ev.nativeKeyEvent.repeatCount / 5)).coerceAtMost(6)
                    when (ev.key.nativeKeyCode) {
                        AndroidKey.KEYCODE_DPAD_LEFT -> { onSeek(-step); true }
                        AndroidKey.KEYCODE_DPAD_RIGHT -> { onSeek(step); true }
                        else -> false
                    }
                }
                .focusable()
                .background(Palette.Glass, RoundedCornerShape(4.dp))
                .then(if (focused) Modifier.border(1.dp, Palette.Accent, RoundedCornerShape(4.dp)) else Modifier),
        ) {
            val total = duration.coerceAtLeast(1L).toFloat()
            Box(Modifier.fillMaxWidth((buffered / total).coerceIn(0f, 1f)).height(if (focused) 8.dp else 5.dp).background(Color(0x33FFFFFF), RoundedCornerShape(4.dp)))
            Box(Modifier.fillMaxWidth((position / total).coerceIn(0f, 1f)).height(if (focused) 8.dp else 5.dp).background(Palette.Accent, RoundedCornerShape(4.dp)))
        }
        Spacer(Modifier.height(6.dp))
        Row {
            Text(formatTime(position), fontSize = 12.sp, color = Palette.Text)
            Spacer(Modifier.weight(1f))
            Text(if (duration > 0) "−" + formatTime(duration - position) + " · " + formatTime(duration) else "", fontSize = 12.sp, color = Palette.Muted)
        }
    }
}

@Composable
private fun NextEpisodeCard(
    next: TorrentFile,
    number: Int,
    secondsLeft: Int,
    focus: FocusRequester,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(340.dp)
            .background(Color(0xF0181A20), RoundedCornerShape(12.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.next_in, secondsLeft), fontSize = 12.sp, color = Palette.AccentSoft)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.next_label, number, next.name), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TvButton(stringResource(R.string.next_play), onPlay, Modifier.focusRequester(focus), primary = true, icon = Icon.Next)
            TvButton(stringResource(R.string.next_cancel), onCancel)
        }
    }
}

@Composable
private fun BufferingBadge(stats: TorrentStats?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Color(0xCC0B0C10), RoundedCornerShape(12.dp))
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.player_buffering), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
        stats?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                "${stringResource(R.string.seeds_dot, it.seeders)} · ${formatSpeed(it.speedBytes)}",
                fontSize = 11.sp,
                color = seedColor(it.seeders),
            )
        }
    }
}

@Composable
private fun ErrorPanel(message: String, focus: FocusRequester, onExternal: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .width(520.dp)
            .background(Palette.Surface, RoundedCornerShape(12.dp))
            .padding(22.dp),
    ) {
        Text(stringResource(R.string.player_error, message), fontSize = 15.sp, color = Palette.Text)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TvButton(stringResource(R.string.player_external), onExternal, Modifier.focusRequester(focus), primary = true)
            TvButton(stringResource(R.string.player_back), onBack)
        }
    }
}

/** One selectable audio or subtitle track. */
private data class TrackOption(val group: Tracks.Group, val track: Int, val label: String, val selected: Boolean)

@Composable
private fun trackOptions(tracks: Tracks, type: Int): List<TrackOption> {
    val fallback = stringResource(R.string.track_n)
    var n = 0
    return tracks.groups.filter { it.type == type }.flatMap { g ->
        (0 until g.length).filter(g::isTrackSupported).map { i ->
            n++
            val f = g.getTrackFormat(i)
            val language = f.language?.takeIf { it != "und" }?.let { Locale.forLanguageTag(it).displayLanguage }
            val label = listOfNotNull(f.label, language).distinct().joinToString(" · ").ifEmpty { fallback.format(n) }
            TrackOption(g, i, label, g.isTrackSelected(i))
        }
    }
}

/** Switches to the next audio/subtitle track in a loop; subtitles also have an "off" step. */
private fun cycleTrack(player: Player, tracks: Tracks, type: Int, allowOff: Boolean) {
    val all = tracks.groups.filter { it.type == type }.flatMap { g -> (0 until g.length).filter(g::isTrackSupported).map { g to it } }
    if (all.isEmpty()) return
    val disabled = type in player.trackSelectionParameters.disabledTrackTypes
    val current = if (disabled) -1 else all.indexOfFirst { (g, i) -> g.isTrackSelected(i) }
    val nextIdx = current + 1
    val params = player.trackSelectionParameters.buildUpon()
    if (nextIdx >= all.size && allowOff) {
        params.setTrackTypeDisabled(type, true)
    } else {
        val (g, i) = all[nextIdx % all.size]
        params.setTrackTypeDisabled(type, false).setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
    }
    player.trackSelectionParameters = params.build()
}

internal fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

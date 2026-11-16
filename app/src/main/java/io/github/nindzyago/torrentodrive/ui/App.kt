package io.github.nindzyago.torrentodrive.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import io.github.nindzyago.torrentodrive.QuerySource
import io.github.nindzyago.torrentodrive.SearchHistory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import io.github.nindzyago.torrentodrive.R
import io.github.nindzyago.torrentodrive.SearchQuery
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.nindzyago.torrentodrive.trackers.TrackerProvider
import io.github.nindzyago.torrentodrive.trackers.Trackers
import io.github.nindzyago.torrentodrive.trackers.TrackerSwitches
import io.github.nindzyago.torrentodrive.connection.Connection
import io.github.nindzyago.torrentodrive.VoiceWarmup
import io.github.nindzyago.torrentodrive.WatchEntry
import io.github.nindzyago.torrentodrive.WatchHistory
import io.github.nindzyago.torrentodrive.Progress
import io.github.nindzyago.torrentodrive.WatchProgress
import io.github.nindzyago.torrentodrive.ApkInstaller
import io.github.nindzyago.torrentodrive.Update
import io.github.nindzyago.torrentodrive.Updates
import io.github.nindzyago.torrentodrive.torrserver.TorrServerClient
import io.github.nindzyago.torrentodrive.torrserver.TorrentFile
import io.github.nindzyago.torrentodrive.torrserver.TorrentStats
import io.github.nindzyago.torrentodrive.trackers.Category
import io.github.nindzyago.torrentodrive.trackers.LoginRequired
import io.github.nindzyago.torrentodrive.trackers.RutrackerWeb
import io.github.nindzyago.torrentodrive.trackers.TorrentDetails
import io.github.nindzyago.torrentodrive.trackers.TorrentItem
import io.github.nindzyago.torrentodrive.SectionSwitches
import io.github.nindzyago.torrentodrive.Shelves
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data object Listening : Screen
    data class Results(
        val query: SearchQuery,
        val items: List<TorrentItem>? = null,
        /** Errors from individual trackers: the remaining results are still shown. */
        val notes: List<String> = emptyList(),
        /** Names of the trackers being asked, for the loading state. */
        val searching: List<String> = emptyList(),
    ) : Screen
    data class Files(
        /** Where we came from: search results, or (null) the home screen's "Continue watching". */
        val from: Results?,
        val item: TorrentItem,
        val hash: String? = null,
        val files: List<TorrentFile>? = null,
        val error: String? = null,
        /** The [error] is TorrServer not answering: offer to start or install TorrServe. */
        val serverDown: Boolean = false,
        val stats: TorrentStats? = null,
        /** Poster from the torrent description. */
        val poster: String? = null,
        /** The torrent page's description and facts, once the tracker answered. */
        val details: TorrentDetails? = null,
    ) : Screen
    /** Buffering the selected file before launching the player. */
    data class Loading(val from: Files, val file: TorrentFile, val stats: TorrentStats? = null) : Screen
    /** The built-in player; it switches episodes of [from] on its own. */
    data class Player(val from: Files, val file: TorrentFile) : Screen
}

/** Hash of the demo series (debug builds only): its files are plain URLs, no TorrServer involved. */
private const val DEMO_HASH = "demo"

@Composable
fun App(
    rutracker: RutrackerWeb,
    cursor: WebCursor,
    openListening: Int,
    incomingQuery: String?,
    onIncomingQueryHandled: () -> Unit,
    /** Hands a file to an external player (NOVA or the system chooser). */
    playExternal: (Uri, TorrentFile) -> Unit,
    /** Debug builds: video URLs to open as a fake series in the player. */
    demoEpisodes: List<String>? = null,
) {
    val context = LocalContext.current
    val server = remember { TorrServerClient() }
    val trackers = remember { Trackers.all(rutracker) }
    val switches = remember { TrackerSwitches(context) }
    val history = remember { WatchHistory(context) }
    val progress = remember { WatchProgress(context) }
    val searches = remember { SearchHistory(context) }
    val warmup = remember { VoiceWarmup() }
    val sectionSwitches = remember { SectionSwitches(context) }
    val shelves = remember { Shelves(context, trackers, sectionSwitches) }
    /** Set when a release was opened from a shelf: coming back home puts focus on that card again. */
    var panelReturn by remember { mutableStateOf<PanelReturn?>(null) }
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var serverAlive by remember { mutableStateOf<Boolean?>(null) }
    var serverVersion by remember { mutableStateOf<String?>(null) }
    val updates = remember { Updates(context.applicationContext) }
    var update by remember { mutableStateOf<UpdateState?>(null) }
    val challenge by rutracker.challenge
    val loginOpen by rutracker.manualLogin

    fun search(phrase: String, source: QuerySource = QuerySource.Typed) {
        val query = SearchQuery.parse(phrase)
        if (query.text.isBlank()) return
        searches.add(source, phrase)
        // trackers that need a sign-in that isn't there are not asked at all, only noted
        val plan = Trackers.searchPlan(trackers, switches::isOn)
        val signInNotes = plan.needSignIn.map { context.getString(R.string.login_required, it.name) }
        val results = Screen.Results(query, notes = signInNotes, searching = plan.ask.map { it.name })
        screen = results
        scope.launch {
            Connection.awaitReady()
            val found = coroutineScope {
                plan.ask.map { t -> async { t.name to runCatching { t.search(query.text) } } }.awaitAll()
            }
            val items = found.flatMap { it.second.getOrDefault(emptyList()) }
            val notes = signInNotes + found.mapNotNull { (name, r) ->
                r.exceptionOrNull()?.let { e ->
                    if (e is LoginRequired) context.getString(R.string.login_required, name)
                    else context.getString(R.string.tracker_error, name, e.message ?: e.toString())
                }
            }
            if (screen == results) {
                screen = results.copy(items = items.filter(query::matches).sortedByDescending { it.seeds }, notes = notes)
            }
        }
    }

    fun installUpdate() {
        val u = update ?: return
        if (u.busy) return
        update = u.copy(busy = true, failed = false)
        scope.launch {
            val ok = runCatching { updates.install(u.update) }.isSuccess
            update = u.copy(busy = false, failed = !ok)
        }
    }

    fun streamUrl(hash: String, file: TorrentFile): Uri =
        if (hash == DEMO_HASH) Uri.parse(file.path) else server.streamUrl(hash, file)

    fun startPlayback(files: Screen.Files, file: TorrentFile) {
        if (files.hash == null || files.files == null) return
        screen = Screen.Player(files, file)
    }

    /** Buffers the start of the file and shows progress; the player starts on its own once the buffer is full. */
    fun load(from: Screen.Files, file: TorrentFile) {
        val hash = from.hash ?: return
        // preloading the start is useless when resuming from the middle: the player buffers from there itself
        if (progress.get(from.item, file.index)?.resumable == true) return startPlayback(from, file)
        screen = Screen.Loading(from, file)
        scope.launch {
            val preload = launch { runCatching { server.preload(hash, file) } }
            while (preload.isActive && (screen as? Screen.Loading)?.file == file) {
                runCatching { server.stats(hash) }.getOrNull()?.let { st ->
                    (screen as? Screen.Loading)?.takeIf { it.file == file }?.let { screen = it.copy(stats = st) }
                }
                delay(1000)
            }
            if ((screen as? Screen.Loading)?.file == file) {
                startPlayback(from, file)
            } else {
                preload.cancel()
            }
        }
    }

    /** Opens a torrent; [resumeIndex] starts that file right away (resuming from history); [retry] keeps its poster and details. */
    fun open(from: Screen.Results?, item: TorrentItem, resumeIndex: Int? = null, retry: Screen.Files? = null) {
        var files = Screen.Files(from, item, poster = retry?.poster, details = retry?.details)
        screen = files
        fun update(next: Screen.Files) {
            if ((screen as? Screen.Files)?.item == item) screen = next
            files = next
        }
        val provider = trackers.first { it.id == item.trackerId }
        // look up the poster while the torrent opens; from history, reuse the known one
        val known = history.entries.firstOrNull { it.item.trackerId == item.trackerId && it.item.link == item.link }?.poster
        if (known != null) update(files.copy(poster = known))
        // description, facts and (if not known yet) the poster, from the torrent's page
        scope.launch {
            runCatching { provider.details(item) }.getOrNull()?.let { d -> update(files.copy(details = d, poster = files.poster ?: d.poster)) }
        }
        scope.launch {
            try {
                val source = provider.source(item)
                val hash = server.add(source, item.title)
                update(files.copy(hash = hash))
                val all = server.awaitFiles(hash) { st -> scope.launch { update(files.copy(stats = st)) } }
                val videos = all.filter { it.isVideo }.sortedBy { it.path }
                update(files.copy(files = videos))
                // "Continue watching": the same episode if it isn't finished, otherwise the one after it
                val resumed = resumeIndex?.let { i -> videos.indexOfFirst { it.index == i } }?.takeIf { it >= 0 }?.let { i ->
                    if (progress.get(item, videos[i].index)?.finished == true) videos.getOrNull(i + 1) ?: videos[i] else videos[i]
                }
                // only "Continue watching" starts by itself; anything else waits on the Watch button
                if (resumed != null && (screen as? Screen.Files)?.item == item) load(files, resumed)
            } catch (e: Exception) {
                // a tracker that can't be reached is only TorrServer's fault if TorrServer is silent too
                val serverDown = TorrServerClient.isUnreachable(e) || !server.isAlive()
                update(files.copy(error = e.message ?: e.toString(), serverDown = serverDown))
            }
        }
    }

    LaunchedEffect(Unit) { updates.check()?.let { update = UpdateState(it) } }
    // new and popular shelves: from disk at once, then refreshed if older than three hours; again after a sign-in
    LaunchedEffect(rutracker.user.value) {
        Connection.awaitReady()
        shelves.refresh(switches::isOn)
    }
    // sections or trackers switched on in settings: fetch their shelves on the way back home
    var settingsVisited by remember { mutableStateOf(false) }
    LaunchedEffect(screen) {
        if (screen == Screen.Settings) settingsVisited = true
        else if (screen == Screen.Home && settingsVisited) {
            settingsVisited = false
            scope.launch { shelves.refresh(switches::isOn) }
        }
    }
    LaunchedEffect(Unit) {
        Connection.load(context)
        Connection.check(context, trackers)
    }
    // keep checking until TorrServer answers: the user may be installing or starting it right now
    LaunchedEffect(Unit) {
        while (true) {
            serverVersion = server.version()
            serverAlive = serverVersion != null
            if (serverAlive == true) break
            delay(3000)
        }
    }
    // fetch posters one by one for older history entries that don't have one yet
    LaunchedEffect(Unit) {
        history.entries.filter { it.poster == null }.forEach { e ->
            val provider = trackers.firstOrNull { it.id == e.item.trackerId } ?: return@forEach
            runCatching { provider.poster(e.item) }.getOrNull()?.let { history.setPoster(e, it) }
        }
    }
    LaunchedEffect(incomingQuery) {
        if (incomingQuery != null) {
            search(incomingQuery, QuerySource.Voice)
            onIncomingQueryHandled()
        }
    }
    LaunchedEffect(openListening) { if (openListening > 0) screen = Screen.Listening }
    LaunchedEffect(demoEpisodes) {
        val urls = demoEpisodes ?: return@LaunchedEffect
        val item = TorrentItem("demo", "Demo", "Demo series", "", 0, 0, link = urls.first())
        val files = urls.mapIndexed { i, url -> TorrentFile(i, url, 0) }
        screen = Screen.Player(Screen.Files(null, item, hash = DEMO_HASH, files = files), files.first())
    }
    // without the permission there's no warmup, but we still try recognition
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { screen = Screen.Listening }

    BackHandler(enabled = loginOpen) { rutracker.cancelLogin() }
    BackHandler(enabled = screen != Screen.Home && !challenge && !loginOpen) {
        screen = when (val s = screen) {
            is Screen.Files -> s.from ?: Screen.Home
            is Screen.Loading -> s.from
            is Screen.Player -> s.from
            else -> Screen.Home
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Palette.Accent, background = Palette.Bg, surface = Palette.Surface)) {
        Box(Modifier.fillMaxSize()) {
            // the RuTracker WebView lives beneath the UI and comes to the front only for sign-in and Cloudflare checks
            AndroidView(
                factory = { rutracker.webView.also { (it.parent as? ViewGroup)?.removeView(it) } },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (loginOpen) 56.dp else 0.dp)
                    .zIndex(if (challenge || loginOpen) 2f else 0f),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .background(Palette.Bg)
            ) {
              val playing = screen as? Screen.Player
              if (playing != null) {
                  val from = playing.from
                  val hash = from.hash!!
                  PlayerScreen(
                      title = shortTitle(from.item.title),
                      episodes = from.files.orEmpty(),
                      start = playing.file,
                      streamUrl = { streamUrl(hash, it) },
                      savedProgress = { progress.get(from.item, it.index) },
                      onProgress = { f, p -> progress.set(from.item, f.index, p) },
                      onEpisodeStarted = { f ->
                          if (hash != DEMO_HASH) history.add(WatchEntry(from.item, f.index, f.name, System.currentTimeMillis(), from.poster))
                      },
                      preload = { f -> if (hash != DEMO_HASH) server.preload(hash, f) },
                      stats = { if (hash != DEMO_HASH) server.stats(hash) else null },
                      onExternal = { f -> playExternal(streamUrl(hash, f), f) },
                      onExit = { screen = from },
                  )
              } else {
              Backdrop(dense = screen != Screen.Home && screen != Screen.Listening)
              when (val s = screen) {
                  is Screen.Files -> PosterBackdrop(s.poster)
                  is Screen.Loading -> PosterBackdrop(s.from.poster)
                  else -> Unit
              }
              Box(Modifier.fillMaxSize().padding(horizontal = ScreenGutter, vertical = 28.dp)) {
                when (val s = screen) {
                    Screen.Home -> HomeScreen(
                        serverAlive = serverAlive,
                        update = update,
                        onUpdate = { installUpdate() },
                        signedIn = trackers.filter { it.requiresSignIn && it.isSignedIn }.map { it.name },
                        history = history.entries,
                        onVoice = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                screen = Screen.Listening
                            } else {
                                micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        searches = searches,
                        onSearch = { q, src -> search(q, src) },
                        unreachable = trackers.filter { switches.isOn(it) && Connection.reach[it.id]?.let { r -> r.method == null } == true }.map { it.name },
                        onSettings = { screen = Screen.Settings },
                        onResume = { e -> open(null, e.item, e.fileIndex) },
                        progressOf = { e -> progress.get(e.item, e.fileIndex) },
                        backEnabled = !loginOpen && !challenge,
                        shelfView = ShelfView(
                            shelves = { k -> shelves.shelves(k, switches::isOn) },
                            loading = shelves.loading.value,
                            notes = trackers.filter(switches::isOn).mapNotNull { t ->
                                when {
                                    t.requiresSignIn && !t.isSignedIn -> stringResource(R.string.shelves_sign_in, t.name)
                                    shelves.errors[t.id] != null -> stringResource(R.string.shelves_error, t.name, shelves.errors[t.id]!!)
                                    else -> null
                                }
                            },
                            poster = { item -> shelves.posters[itemKey(item)] },
                            askPoster = { item -> shelves.poster(item) },
                        ),
                        panelReturn = panelReturn,
                        onPanelReturnUsed = { panelReturn = null },
                        onOpenShelf = { item, ret ->
                            panelReturn = ret
                            open(null, item)
                        },
                    )
                    Screen.Listening -> ListeningScreen(warmup, onResult = { search(it, QuerySource.Voice) }, onCancel = { screen = Screen.Home })
                    Screen.Settings -> SettingsScreen(
                        trackers = trackers,
                        switches = switches,
                        sectionSwitches = sectionSwitches,
                        serverVersion = serverVersion,
                        onSignOut = { t -> scope.launch { t.signOut() } },
                        update = update,
                        onUpdate = { installUpdate() },
                        onCheckUpdates = { updates.check()?.also { update = UpdateState(it) } != null },
                    )
                    is Screen.Results -> ResultsScreen(s, onOpen = { open(s, it) })
                    is Screen.Files -> ReleaseScreen(
                        Release(s.item, s.poster, s.details, s.files, s.error, s.stats, s.serverDown),
                        watched = history.entries.firstOrNull { it.item.trackerId == s.item.trackerId && it.item.link == s.item.link },
                        progressOf = { progress.get(s.item, it.index) },
                        onPlay = { file -> load(s, file) },
                        onRetry = { open(s.from, s.item, retry = s) },
                        onBack = { screen = s.from ?: Screen.Home },
                    )
                    is Screen.Loading -> LoadingScreen(s, onPlayNow = { startPlayback(s.from, s.file) })
                    is Screen.Player -> Unit
                }
              }
              }
            }
            if (loginOpen) {
                Row(
                    Modifier
                        .zIndex(3f)
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(Palette.Bg)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TvIcon(Icon.Lock, Palette.Good, 14.dp)
                    Text("rutracker.org", fontSize = Type.Caption, fontWeight = FontWeight.SemiBold, color = Palette.Text)
                    Text(stringResource(R.string.login_bar_hint), fontSize = Type.Caption, color = Palette.Muted)
                    Spacer(Modifier.weight(1f))
                    listOf(stringResource(R.string.key_arrows), stringResource(R.string.key_ok), stringResource(R.string.key_back)).forEach {
                        Text(it, fontSize = Type.Caption, color = Palette.Muted, modifier = Modifier.border(1.dp, Palette.Line, Radius.S).padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
            if (loginOpen || challenge) {
                CursorPointer(cursor, Modifier.zIndex(4f))
            }
            if (challenge) {
                Text(
                    stringResource(R.string.challenge_hint, "RuTracker"),
                    color = Palette.Text,
                    fontSize = Type.Title,
                    modifier = Modifier
                        .zIndex(3f)
                        .align(Alignment.BottomCenter)
                        .background(Color(0xCC000000))
                        .padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    serverAlive: Boolean?,
    update: UpdateState?,
    onUpdate: () -> Unit,
    signedIn: List<String>,
    unreachable: List<String>,
    history: List<WatchEntry>,
    searches: SearchHistory,
    onVoice: () -> Unit,
    onSearch: (String, QuerySource) -> Unit,
    onSettings: () -> Unit,
    onResume: (WatchEntry) -> Unit,
    progressOf: (WatchEntry) -> Progress?,
    backEnabled: Boolean,
    shelfView: ShelfView,
    panelReturn: PanelReturn?,
    onPanelReturnUsed: () -> Unit,
    onOpenShelf: (TorrentItem, PanelReturn) -> Unit,
) {
    val voiceFocus = remember { FocusRequester() }
    val setupFocus = remember { FocusRequester() }
    val updateFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    var typed by remember { mutableStateOf("") }
    var voiceFocused by remember { mutableStateOf(false) }
    var setupFocused by remember { mutableStateOf(false) }
    // the open panel needs the room; the setup card comes back when focus leaves it
    var panelOpen by remember { mutableStateOf(false) }
    val setupShown = serverAlive == false && !panelOpen
    // focus goes to the setup card's button as soon as the card is on screen
    var wantSetupFocus by remember { mutableStateOf(false) }
    val restore = remember { panelReturn }
    val serverAtStart = remember { serverAlive }

    /** The home screen's initial focus: the setup card's button while TorrServer is missing, otherwise Speak. */
    fun focusHome() {
        if (serverAlive == false) {
            wantSetupFocus = true
            // the card is hidden while the panel is open: leaving the panel brings it back
            if (panelOpen) runCatching { voiceFocus.requestFocus() }
        } else {
            runCatching { voiceFocus.requestFocus() }
        }
    }

    LaunchedEffect(Unit) {
        if (restore == null) focusHome() else onPanelReturnUsed()
    }
    LaunchedEffect(setupShown, wantSetupFocus) {
        if (setupShown && wantSetupFocus) {
            runCatching { setupFocus.requestFocus() }
            wantSetupFocus = false
        }
    }
    LaunchedEffect(serverAlive) {
        when {
            // the first check found no TorrServer while focus still sits where the screen put it
            serverAlive == false && serverAtStart == null && restore == null && voiceFocused -> focusHome()
            // TorrServer came up: the card and its focused button are gone
            serverAlive == true && setupFocused -> runCatching { voiceFocus.requestFocus() }
        }
    }
    val homeFocused = if (serverAlive == false) setupFocused else voiceFocused
    // Back from anywhere else on the home screen returns to the initial state; Back on the initial element exits the app
    BackHandler(enabled = backEnabled && !homeFocused) {
        typed = ""
        focusHome()
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Logo(36.dp)
            Spacer(Modifier.weight(1f))
            val (dot, label) = when (serverAlive) {
                null -> Palette.Muted to stringResource(R.string.server_checking)
                true -> Palette.Good to (listOf("TorrServer") + signedIn).joinToString(" · ")
                false -> Palette.Accent to stringResource(R.string.server_down)
            }
            Box(Modifier.size(6.dp).background(dot, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = Type.Caption, color = Palette.Muted)
            if (unreachable.isNotEmpty()) {
                Spacer(Modifier.width(16.dp))
                Box(Modifier.size(6.dp).background(Palette.Warn, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.trackers_unreachable, unreachable.joinToString(", ")), fontSize = Type.Caption, color = Palette.Warn)
            }
            Spacer(Modifier.width(20.dp))
            if (update != null) {
                val label = when {
                    update.busy -> stringResource(R.string.update_downloading)
                    update.failed -> stringResource(R.string.update_failed)
                    else -> stringResource(R.string.update_available, update.update.version)
                }
                TvButton(label, onUpdate, Modifier.focusRequester(updateFocus), icon = Icon.Download)
                Spacer(Modifier.width(12.dp))
            }
            TvIconButton(Icon.Gear, stringResource(R.string.settings), onSettings, Modifier.focusRequester(settingsFocus))
        }
        Spacer(Modifier.height(48.dp))
        Text(stringResource(R.string.home_title), fontSize = Type.Hero, fontWeight = FontWeight.Bold, color = Palette.Text, letterSpacing = (-0.5).sp)
        Spacer(Modifier.height(20.dp))
        // search history shows only while the text field or the suggestions themselves have focus, not the voice button
        var fieldFocused by remember { mutableStateOf(false) }
        var suggestionsFocused by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvButton(
                stringResource(R.string.speak), onVoice,
                Modifier.focusRequester(voiceFocus).onFocusChanged { voiceFocused = it.isFocused },
                primary = true, large = true, icon = Icon.Mic,
            )
            Spacer(Modifier.width(12.dp))
            TvTextField(
                typed, { typed = it }, stringResource(R.string.search_hint),
                Modifier
                    .width(345.dp)
                    .onFocusChanged { fieldFocused = it.hasFocus }
                    // Right goes up to the header buttons, not to a search suggestion that happens to stick out below
                    .focusProperties { right = if (update != null) updateFocus else settingsFocus },
                imeAction = ImeAction.Search,
                onImeAction = { onSearch(typed, QuerySource.Typed) },
            )
        }
        if (fieldFocused || suggestionsFocused) {
            Column(Modifier.onFocusChanged { suggestionsFocused = it.hasFocus }) {
                RecentQueries(stringResource(R.string.recent_typed), Icon.Search, searches.typed) { onSearch(it, QuerySource.Typed) }
                RecentQueries(stringResource(R.string.recent_spoken), Icon.Mic, searches.voice) { onSearch(it, QuerySource.Voice) }
            }
        }
        if (setupShown) {
            ServerSetup(Modifier.focusRequester(setupFocus).onFocusChanged { setupFocused = it.isFocused })
        }
        Spacer(Modifier.weight(1f))
        HomePanel(
            shelfView = shelfView,
            restore = restore,
            onOpen = onOpenShelf,
            continueEmpty = history.isEmpty(),
            onRestoreFailed = { focusHome() },
            onOpenChange = { panelOpen = it },
            continueContent = { firstCard ->
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 6.dp, bottom = 6.dp, start = 4.dp, end = ScreenGutter)) {
                    itemsIndexed(history, key = { _, e -> "${e.item.trackerId}:${e.item.link}" }) { i, e ->
                        HistoryCard(e, progressOf(e), if (i == 0) Modifier.focusRequester(firstCard) else Modifier, onClick = { onResume(e) })
                    }
                }
            },
        )
    }
}

/** A newer release found on GitHub and where its installation stands. */
internal data class UpdateState(val update: Update, val busy: Boolean = false, val failed: Boolean = false)

/**
 * Shown while TorrServer doesn't answer: how to install or start TorrServe.
 * [buttonModifier] goes on the card's button (the home screen's initial focus while the card is shown).
 */
@Composable
private fun ServerSetup(buttonModifier: Modifier) {
    val context = LocalContext.current
    var launch by remember { mutableStateOf(torrServeLaunch(context)) }
    var buttonFocused by remember { mutableStateOf(false) }
    val buttonFocus = remember { FocusRequester() }
    // notice TorrServe being installed while the card is on screen
    LaunchedEffect(Unit) {
        while (launch == null) {
            delay(2000)
            val found = torrServeLaunch(context) ?: continue
            // Install turns into Open: keep focus on the button if it was there
            val hadFocus = buttonFocused
            launch = found
            if (hadFocus) {
                withFrameNanos { }
                runCatching { buttonFocus.requestFocus() }
            }
        }
    }
    val button = buttonModifier.focusRequester(buttonFocus).onFocusChanged { buttonFocused = it.isFocused }
    Column(
        Modifier
            .padding(top = 24.dp)
            .width(560.dp)
            .background(Palette.Bg.copy(alpha = 0.82f), Radius.L)
            .padding(20.dp),
    ) {
        Text(stringResource(R.string.server_setup_title), fontSize = Type.Body, fontWeight = FontWeight.Bold, color = Palette.Text)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(if (launch != null) R.string.server_setup_start else R.string.server_setup_install, TORRSERVE_CODE),
            fontSize = Type.Caption, color = Palette.Muted, lineHeight = Type.CaptionLine,
        )
        Spacer(Modifier.height(16.dp))
        val open = launch
        if (open != null) {
            TvButton(stringResource(R.string.open_torrserve), { context.startActivity(open) }, button, primary = true, large = true)
        } else {
            InstallTorrServeButton(reinstall = false, modifier = button, large = true)
        }
    }
}

/** A row of recent queries: label on the left, then suggestion buttons; pressing one searches right away. */
@Composable
private fun RecentQueries(label: String, icon: Icon, queries: List<String>, onPick: (String) -> Unit) {
    if (queries.isEmpty()) return
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        TvIcon(icon, Palette.Muted, 12.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = Type.Caption, color = Palette.Muted, modifier = Modifier.width(62.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp, horizontal = 2.dp)) {
            items(queries, key = { it }) { q -> TvButton(q, { onPick(q) }) }
        }
    }
}

@Composable
private fun HistoryCard(e: WatchEntry, progress: Progress?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TvCard(onClick, modifier.width(202.dp)) {
        PosterImage(e.poster, shortTitle(e.item.title), 34.sp, Modifier.fillMaxWidth().height(90.dp))
        if (progress != null) ProgressLine(progress)
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(shortTitle(e.item.title), fontSize = Type.Caption, fontWeight = FontWeight.SemiBold, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text("${e.fileName} · ${ago(e.watchedAt)}", fontSize = Type.Caption, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Thin bar showing how much of an episode was watched. */
@Composable
internal fun ProgressLine(progress: Progress, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).background(Palette.Glass)) {
        Box(Modifier.fillMaxWidth(if (progress.finished) 1f else progress.fraction).height(3.dp).background(Palette.Accent))
    }
}

/** "Title / Original Title (Director…) [2010, …]" → "Title": cards need a short title. */
internal fun shortTitle(title: String): String =
    title.substringBefore(" / ").substringBefore(" (").substringBefore(" [").trim().ifEmpty { title }

/** Quality parsed from the torrent title, for the colored badge. */
internal fun qualityOf(title: String): Pair<String, Color>? = when {
    Regex("2160|\\b4k\\b|uhd", RegexOption.IGNORE_CASE).containsMatchIn(title) -> "2160p" to Palette.Warn
    title.contains("1080") -> "1080p" to Palette.Good
    title.contains("720") -> "720p" to Palette.Muted
    else -> null
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ResultsScreen(s: Screen.Results, onOpen: (TorrentItem) -> Unit) {
    val items = s.items.orEmpty()
    var category by remember(s.items) { mutableStateOf<Category?>(null) }
    val counts = remember(s.items) { items.groupingBy { it.category }.eachCount().toList().sortedByDescending { it.second } }
    val shown = if (category == null) items else items.filter { it.category == category }
    val trackers = remember(s.items) { items.map { it.tracker }.distinct().joinToString(", ") }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(s.query.label, fontSize = Type.Headline, fontWeight = FontWeight.Bold, color = Palette.Text)
            Spacer(Modifier.width(12.dp))
            Text(
                when {
                    s.items == null && s.searching.isNotEmpty() -> stringResource(R.string.searching_trackers, s.searching.joinToString(", "))
                    s.items == null -> stringResource(R.string.searching)
                    items.isEmpty() -> stringResource(R.string.nothing_found)
                    else -> pluralStringResource(R.plurals.results_count, items.size, items.size) + " · $trackers"
                },
                fontSize = Type.Caption,
                color = Palette.Muted,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        s.notes.forEach { Text(it, fontSize = Type.Caption, color = Palette.Warn) }
        // one requester per chip: Up from the list lands on the selected chip, not the geometrically nearest one
        val allChip = remember(s.items) { FocusRequester() }
        val chipFocus = remember(s.items) { counts.associate { it.first to FocusRequester() } }
        val selectedChip = category?.let { chipFocus[it] } ?: allChip
        val hasChips = counts.size > 1
        if (hasChips) {
            Spacer(Modifier.height(12.dp))
            // moving along the chips only moves focus; the filter changes on OK, and the selected chip keeps its look
            Row(
                Modifier
                    .focusProperties { enter = { selectedChip } }
                    .focusGroup()
                    .horizontalScroll(rememberScrollState())
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TvButton(stringResource(R.string.all_count, items.size), { category = null }, Modifier.focusRequester(allChip), selected = category == null)
                counts.forEach { (c, n) ->
                    TvButton("${stringResource(c.label)} · $n", { category = c }, Modifier.focusRequester(chipFocus.getValue(c)), selected = category == c)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val firstFocus = remember { FocusRequester() }
        LaunchedEffect(s.items) { if (items.isNotEmpty()) runCatching { firstFocus.requestFocus() } }
        if (s.items == null) {
            SearchingPlaceholders()
            return@Column
        }
        LazyColumn(
            Modifier
                .focusProperties { exit = { if (it == FocusDirection.Up && hasChips) selectedChip else FocusRequester.Default } }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
        ) {
            items(shown, key = { "${it.trackerId}:${it.link}" }) { item ->
                ResultCard(item, onClick = { onOpen(item) }, modifier = if (item === shown.first()) Modifier.focusRequester(firstFocus) else Modifier)
            }
        }
    }
}

/**
 * The results screen while trackers are being asked: rows the shape of [ResultCard], so the screen looks like what
 * is coming, and the first one takes focus so the remote has somewhere to be. OK on them does nothing.
 */
@Composable
private fun SearchingPlaceholders() {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val description = stringResource(R.string.searching)
    Column(Modifier.padding(vertical = 8.dp, horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0.72f, 0.56f, 0.64f, 0.48f, 0.6f).forEachIndexed { i, width ->
            TvCard(
                onClick = {},
                modifier = (if (i == 0) Modifier.focusRequester(first) else Modifier)
                    .fillMaxWidth()
                    .semantics { contentDescription = description },
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        PlaceholderBar(Modifier.fillMaxWidth(width).height(16.dp))
                        Spacer(Modifier.height(8.dp))
                        PlaceholderBar(Modifier.width(160.dp).height(12.dp))
                    }
                    Spacer(Modifier.width(20.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        PlaceholderBar(Modifier.width(32.dp).height(20.dp))
                        Spacer(Modifier.height(4.dp))
                        PlaceholderBar(Modifier.width(40.dp).height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderBar(modifier: Modifier) {
    Box(modifier.background(Palette.Glass, Radius.S))
}

@Composable
private fun ResultCard(item: TorrentItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvCard(onClick, modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.title, fontSize = Type.Caption, fontWeight = FontWeight.SemiBold, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    qualityOf(item.title)?.let { (q, c) -> Tag(q, c) }
                    Tag(stringResource(item.category.label))
                    Text("${trackerSize(item.size)} · ${item.tracker}", fontSize = Type.Caption, color = Palette.Muted)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("${item.seeds}", fontSize = Type.Title, fontWeight = FontWeight.ExtraBold, color = seedColor(item.seeds))
                Text(pluralStringResource(R.plurals.seeds_label, item.seeds), fontSize = Type.Caption, color = Palette.Muted)
            }
        }
    }
}

@Composable
private fun LoadingScreen(s: Screen.Loading, onPlayNow: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(shortTitle(s.from.item.title), fontSize = Type.Caption, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Text(s.file.name, fontSize = Type.Display, fontWeight = FontWeight.ExtraBold, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(700.dp))
        Spacer(Modifier.height(28.dp))
        val st = s.stats
        val progress = if (st != null && st.preloadSize > 0) (st.preloaded.toFloat() / st.preloadSize).coerceIn(0f, 1f) else 0f
        Row(Modifier.width(570.dp)) {
            Text(stringResource(R.string.preparing), fontSize = Type.Caption, color = Palette.Text)
            Spacer(Modifier.weight(1f))
            Text(
                if (st != null && st.preloadSize > 0) stringResource(R.string.buffer_of, formatSize(st.preloaded), formatSize(st.preloadSize)) else stringResource(R.string.connecting),
                fontSize = Type.Caption,
                color = Palette.Muted,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.width(570.dp).height(6.dp).background(Palette.Glass, Radius.S)) {
            Box(Modifier.fillMaxWidth(progress).height(6.dp).background(Palette.Accent, Radius.S))
        }
        Spacer(Modifier.height(20.dp))
        st?.let { StatsBlock(it) }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvButton(stringResource(R.string.watch_now), onPlayNow, Modifier.focusRequester(focus), primary = true, large = true, icon = Icon.Play)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.player_auto), fontSize = Type.Caption, color = Palette.Muted)
        }
    }
}

@Composable
private fun StatsBlock(st: TorrentStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(44.dp)) {
        Stat("${st.seeders}", pluralStringResource(R.plurals.seeds_label, st.seeders), seedColor(st.seeders))
        Stat("${st.activePeers} / ${st.totalPeers}", stringResource(R.string.peers_label))
        Stat(formatSpeed(st.speedBytes), stringResource(R.string.speed_label))
    }
    if (st.seeders == 0) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.no_seeds), fontSize = Type.Caption, color = Palette.Accent)
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color = Palette.Text) {
    Column {
        Text(value, fontSize = Type.Headline, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = Type.Caption, color = Palette.Muted)
    }
}

@Composable
private fun ago(ts: Long): String {
    val minutes = ((System.currentTimeMillis() - ts) / 60_000).toInt()
    return when {
        minutes < 1 -> stringResource(R.string.just_now)
        minutes < 60 -> stringResource(R.string.minutes_ago, minutes)
        minutes < 24 * 60 -> stringResource(R.string.hours_ago, minutes / 60)
        minutes < 48 * 60 -> stringResource(R.string.yesterday)
        else -> stringResource(R.string.days_ago, minutes / (24 * 60))
    }
}

@Composable
internal fun formatSpeed(bytesPerSec: Long): String =
    if (bytesPerSec >= 1L shl 20) stringResource(R.string.speed_mb, bytesPerSec / (1L shl 20).toDouble())
    else stringResource(R.string.speed_kb, (bytesPerSec / 1024).toInt())

@Composable
internal fun formatSize(bytes: Long): String =
    if (bytes >= 1L shl 30) stringResource(R.string.size_gb, bytes / (1L shl 30).toDouble())
    else stringResource(R.string.size_mb, (bytes / (1L shl 20)).toInt())

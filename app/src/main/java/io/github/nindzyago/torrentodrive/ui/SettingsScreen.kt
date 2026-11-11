package io.github.nindzyago.torrentodrive.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.nindzyago.torrentodrive.ApkInstaller
import io.github.nindzyago.torrentodrive.R
import io.github.nindzyago.torrentodrive.connection.Connection
import io.github.nindzyago.torrentodrive.connection.ConnectionMethods
import io.github.nindzyago.torrentodrive.lang.AppLanguage
import io.github.nindzyago.torrentodrive.lang.LanguageWords
import io.github.nindzyago.torrentodrive.lang.Languages
import io.github.nindzyago.torrentodrive.trackers.TrackerProvider
import io.github.nindzyago.torrentodrive.trackers.TrackerSwitches
import io.github.nindzyago.torrentodrive.SectionSwitches
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Section { Connection, Trackers, TorrServer, Language, About }

/** Settings: sections on the left (focusing one opens it), the section itself on the right. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SettingsScreen(
    trackers: List<TrackerProvider>,
    switches: TrackerSwitches,
    sectionSwitches: SectionSwitches,
    serverVersion: String?,
    onSignOut: (TrackerProvider) -> Unit,
    update: UpdateState?,
    onUpdate: () -> Unit,
    onCheckUpdates: suspend () -> Boolean,
) {
    val context = LocalContext.current
    var section by remember { mutableStateOf(Section.Connection) }
    val focus = remember { Section.entries.associateWith { FocusRequester() } }
    LaunchedEffect(Unit) { runCatching { focus.getValue(Section.Connection).requestFocus() } }
    val language = remember { AppLanguage.current(context) }

    Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.settings), fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxSize()) {
            // coming back from the right side lands on the open section, not on whichever item is nearest
            Column(
                Modifier.width(210.dp).focusProperties { enter = { focus.getValue(section) } }.focusGroup(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                @Composable
                fun item(s: Section, icon: Icon?, emoji: String?, title: String, dot: Color? = null) =
                    NavItem(icon, emoji, title, section == s, Modifier.focusRequester(focus.getValue(s)), dot) { section = s }
                item(Section.Connection, Icon.Globe, null, stringResource(R.string.settings_connection))
                item(Section.Trackers, Icon.Search, null, stringResource(R.string.trackers))
                item(Section.TorrServer, Icon.Play, null, "TorrServer", if (serverVersion != null) Palette.Good else Palette.Accent)
                item(Section.Language, null, language.flag, stringResource(R.string.settings_language))
                item(Section.About, Icon.Info, null, stringResource(R.string.settings_about))
            }
            Spacer(Modifier.width(36.dp))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (section) {
                    Section.Connection -> ConnectionSection(trackers)
                    Section.Trackers -> {
                        TrackersSection(trackers, switches, onSignOut)
                        ShelfSections(trackers.filter(switches::isOn), sectionSwitches)
                    }
                    Section.TorrServer -> TorrServerSection(serverVersion)
                    Section.Language -> LanguageSection()
                    Section.About -> AboutSection(update, onUpdate, onCheckUpdates)
                }
            }
        }
    }
}

/** A section in the left column: focusing it is enough to open it. */
@Composable
private fun NavItem(
    icon: Icon?,
    emoji: String?,
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    dot: Color? = null,
    onFocus: () -> Unit,
) {
    val shape = RoundedCornerShape(11.dp)
    Surface(
        onClick = onFocus,
        modifier = modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Palette.Glass else Color.Transparent,
            contentColor = if (selected) Palette.Text else Palette.Muted,
            focusedContainerColor = Palette.SurfaceHi,
            focusedContentColor = Palette.Text,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, if (selected) Palette.Line else Color.Transparent), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, Palette.Accent), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                if (icon != null) TvIcon(icon, if (selected) Palette.Text else Palette.Muted, 18.dp)
                else Text(emoji.orEmpty(), fontSize = 15.sp)
            }
            Spacer(Modifier.width(14.dp))
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (selected) Palette.Text else Palette.Muted)
            if (dot != null) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(7.dp).background(dot, CircleShape))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
    if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = Palette.Muted, lineHeight = 19.sp, modifier = Modifier.width(560.dp))
    Spacer(Modifier.height(4.dp))
}

/** A choice among several: a card with a round mark, a title (with an optional badge) and a description. */
@Composable
private fun RadioCard(title: String, description: String?, selected: Boolean, badge: String? = null, onClick: () -> Unit) {
    TvCard(onClick, Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.padding(top = 2.dp).size(18.dp).border(2.dp, if (selected) Palette.Accent else Palette.Muted, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Box(Modifier.size(8.dp).background(Palette.Accent, CircleShape))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
                    if (badge != null) {
                        Spacer(Modifier.width(8.dp))
                        Tag(badge, if (selected) Palette.AccentSoft else Palette.Muted)
                    }
                }
                if (description != null) Text(description, fontSize = 12.sp, color = Palette.Muted, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun ConnectionSection(trackers: List<TrackerProvider>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chosen by Connection.chosen
    val active by Connection.active
    val checking by Connection.checking
    fun choose(id: String) = scope.launch { Connection.choose(context, id, trackers) }

    SectionHeader(stringResource(R.string.connection_title), stringResource(R.string.connection_subtitle))
    RadioCard(
        stringResource(R.string.connection_auto), stringResource(R.string.connection_auto_hint),
        chosen == Connection.AUTO, badge = stringResource(R.string.connection_recommended),
    ) { choose(Connection.AUTO) }
    ConnectionMethods.all.forEach { m ->
        RadioCard(stringResource(m.title), stringResource(m.description), chosen == m.id, m.badge) { choose(m.id) }
    }
    Spacer(Modifier.height(4.dp))
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val caption = when {
                checking -> stringResource(R.string.connection_checking)
                active != null -> stringResource(R.string.connection_check) + " · " +
                    stringResource(R.string.connection_in_use, stringResource(active!!.title))
                else -> stringResource(R.string.connection_check)
            }
            Text(caption.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Palette.Muted, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            TvButton(stringResource(R.string.connection_check_again), { if (!checking) scope.launch { Connection.check(context, trackers) } })
        }
        trackers.forEach { t -> ReachLine(t) }
    }
}

/** One tracker's line from the last connection check: a colored dot, the name, how it opens. */
@Composable
private fun ReachLine(tracker: TrackerProvider) {
    val r = Connection.reach[tracker.id]
    val (color, text) = when {
        r == null -> Palette.Muted to stringResource(R.string.reach_unknown)
        r.method == null -> Palette.Accent to stringResource(R.string.reach_failed)
        else -> Palette.Good to stringResource(R.string.reach_ok, stringResource(r.method.title), r.millis.toInt())
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(tracker.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Palette.Text, modifier = Modifier.width(96.dp))
        Text(text, fontSize = 13.sp, color = Palette.Muted)
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.Glass, RoundedCornerShape(11.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(11.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun TrackersSection(trackers: List<TrackerProvider>, switches: TrackerSwitches, onSignOut: (TrackerProvider) -> Unit) {
    SectionHeader(stringResource(R.string.trackers), stringResource(R.string.trackers_subtitle))
    trackers.forEach { t ->
        val on = switches.isOn(t)
        Row(
            Modifier
                .fillMaxWidth()
                .background(Palette.Surface, RoundedCornerShape(11.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(on) { switches.toggle(t) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (on) Palette.Text else Palette.Muted)
                val login = when {
                    !t.requiresSignIn -> stringResource(R.string.tracker_no_login)
                    t.isSignedIn -> stringResource(R.string.tracker_connected)
                    else -> stringResource(R.string.tracker_not_connected)
                }
                Text(if (on) login else stringResource(R.string.tracker_off), fontSize = 11.sp, color = Palette.Muted)
            }
            if (t.requiresSignIn) {
                if (t.isSignedIn) TvButton(stringResource(R.string.sign_out), { onSignOut(t) })
                else TvButton(stringResource(R.string.sign_in_on_site), t::startSignIn, primary = true)
            }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Palette.Line, RoundedCornerShape(11.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvIcon(Icon.Plus, Palette.Muted, 15.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(stringResource(R.string.add_tracker), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
            Text(stringResource(R.string.add_tracker_hint), fontSize = 11.sp, color = Palette.Muted)
        }
    }
}

/** Which sections of each tracker fill the home screen's New and Popular shelves. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShelfSections(trackers: List<TrackerProvider>, switches: SectionSwitches) {
    val withShelves = trackers.filter { it.sections.isNotEmpty() || it.requiresSignIn }
    if (withShelves.isEmpty()) return
    Spacer(Modifier.height(14.dp))
    SectionHeader(stringResource(R.string.settings_sections))
    withShelves.forEach { t ->
        Text(stringResource(R.string.settings_sections_hint, t.name), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.Muted)
        if (t.sections.isEmpty()) {
            Text(stringResource(R.string.settings_sections_later, t.name), fontSize = 11.sp, color = Palette.Muted)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(2.dp)) {
                t.sections.forEach { s -> TvButton(s.title, { switches.toggle(t, s) }, selected = switches.isOn(t, s)) }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** On/off switch that takes focus like a button. */
@Composable
private fun Switch(on: Boolean, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Palette.SurfaceHi),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Palette.Accent), shape = CircleShape)),
    ) {
        Box(
            Modifier.padding(5.dp).size(38.dp, 22.dp).background(if (on) Palette.Accent else Palette.SurfaceHi, CircleShape).padding(3.dp),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(16.dp).background(Color.White, CircleShape))
        }
    }
}

@Composable
private fun TorrServerSection(version: String?) {
    val context = LocalContext.current
    var launch by remember { mutableStateOf(torrServeLaunch(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            launch = torrServeLaunch(context)
        }
    }
    SectionHeader("TorrServer")
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Surface, RoundedCornerShape(11.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).background(if (version != null) Palette.Good else Palette.Accent, CircleShape))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(if (version != null) R.string.torrserver_running else R.string.server_down),
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text,
            )
            Text(listOfNotNull(version, "127.0.0.1:8090").joinToString(" · "), fontSize = 11.sp, color = Palette.Muted)
        }
        val open = launch
        if (open != null) {
            TvButton(stringResource(R.string.open_torrserve), { context.startActivity(open) }, primary = true)
            Spacer(Modifier.width(8.dp))
        }
        InstallTorrServeButton(reinstall = open != null)
    }
    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.torrserver_help_title).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Palette.Muted, letterSpacing = 1.sp)
    Text(stringResource(R.string.torrserver_help, TORRSERVE_CODE), fontSize = 13.sp, color = Palette.Muted, lineHeight = 21.sp, modifier = Modifier.width(600.dp))
}

/** Downloads the latest TorrServe from GitHub and installs it (over the installed one if [reinstall]). */
@Composable
internal fun InstallTorrServeButton(reinstall: Boolean, primary: Boolean = !reinstall) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val label = when {
        installing -> R.string.torrserve_downloading
        failed -> R.string.torrserve_failed
        reinstall -> R.string.torrserve_reinstall
        else -> R.string.install_torrserve
    }
    TvButton(stringResource(label), {
        if (!installing) scope.launch {
            installing = true
            failed = false
            val apk = ApkInstaller.latestRelease(TORRSERVE_REPO)?.second ?: TORRSERVE_APK
            failed = runCatching { ApkInstaller.install(context, apk) }.isFailure
            installing = false
        }
    }, primary = primary, icon = Icon.Download)
}

@Composable
private fun LanguageSection() {
    val context = LocalContext.current
    val chosen = remember { AppLanguage.chosen(context) }
    val auto = remember { AppLanguage.auto() }
    fun pick(language: LanguageWords?) {
        if (language == chosen) return
        AppLanguage.choose(context, language)
        (context as? Activity)?.let(::restart)
    }
    SectionHeader(stringResource(R.string.settings_language))
    RadioCard("${auto.flag}  " + stringResource(R.string.language_auto, auto.name), null, chosen == null) { pick(null) }
    Languages.all.forEach { l -> RadioCard("${l.flag}  ${l.name}", null, chosen == l) { pick(l) } }
}

/** Starts the app over in the new language, as if just opened: no menus, focus on the voice button. */
private fun restart(activity: Activity) {
    activity.startActivity(
        Intent(activity, activity.javaClass).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
    )
    @Suppress("DEPRECATION")
    activity.overridePendingTransition(0, 0)
}

@Composable
private fun AboutSection(update: UpdateState?, onUpdate: () -> Unit, onCheckUpdates: suspend () -> Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var upToDate by remember { mutableStateOf(false) }
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    SectionHeader("Torrento Drive", stringResource(R.string.about_version, version))
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (update != null) {
            val label = when {
                update.busy -> stringResource(R.string.update_downloading)
                update.failed -> stringResource(R.string.update_failed)
                else -> stringResource(R.string.update_available, update.update.version)
            }
            TvButton(label, onUpdate, primary = true, icon = Icon.Download)
        } else {
            TvButton(stringResource(R.string.about_check_updates), { scope.launch { upToDate = !onCheckUpdates() } })
            if (upToDate) {
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.about_up_to_date), fontSize = 13.sp, color = Palette.Good)
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.about_source, "github.com/nindzyago/torrento-drive"), fontSize = 13.sp, color = Palette.Muted)
    Text(stringResource(R.string.about_license), fontSize = 13.sp, color = Palette.Muted)
}

internal fun torrServeLaunch(context: Context): Intent? = context.packageManager.run {
    getLeanbackLaunchIntentForPackage(TORRSERVE) ?: getLaunchIntentForPackage(TORRSERVE)
}

/** Package of TorrServe, the Android app that runs TorrServer. */
private const val TORRSERVE = "ru.yourok.torrserve"

/** Where TorrServe comes from: the latest release's APK, or this one if GitHub can't be asked. */
private const val TORRSERVE_REPO = "YouROK/TorrServe"
private const val TORRSERVE_APK = "https://github.com/YouROK/TorrServe/releases/download/MatriX.143.Client/TorrServe_MatriX.143.Client.apk"

/** Downloader (AFTVnews) code of [TORRSERVE_APK], for installing it by hand. */
internal const val TORRSERVE_CODE = "7055456"

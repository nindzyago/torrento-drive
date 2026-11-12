package io.github.nindzyago.torrentodrive.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.nindzyago.torrentodrive.R
import io.github.nindzyago.torrentodrive.Shelf
import io.github.nindzyago.torrentodrive.trackers.Browse
import io.github.nindzyago.torrentodrive.trackers.TorrentItem

/** Tabs of the home screen's bottom panel. */
internal enum class PanelTab(val label: Int, val kind: Browse?) {
    Continue(R.string.continue_watching, null),
    New(R.string.shelf_new, Browse.New),
    Popular(R.string.shelf_popular, Browse.Popular),
}

/** Where to put focus when coming back to the home screen from a release opened on a shelf. */
internal data class PanelReturn(val tab: PanelTab, val tracker: String?, val itemKey: String)

internal fun itemKey(item: TorrentItem) = "${item.trackerId}:${item.link}"

/** What the panel shows on the New and Popular tabs. */
internal class ShelfView(
    val shelves: (Browse) -> List<Shelf>,
    val loading: Boolean,
    /** Notes under the tracker filter: sign-in needed, refresh failed. */
    val notes: List<String>,
    val poster: (TorrentItem) -> String?,
    val askPoster: (TorrentItem) -> Unit,
)

/**
 * The bottom of the home screen: collapsed to a row of tabs ("Continue watching · New · Popular");
 * when focus comes to it, the panel slides up from below with the focused tab's content.
 * Continue watching keeps its card row; New and Popular show one shelf per tracker section.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HomePanel(
    shelfView: ShelfView,
    restore: PanelReturn?,
    onOpen: (TorrentItem, PanelReturn) -> Unit,
    continueContent: @Composable (firstCard: FocusRequester) -> Unit,
    continueEmpty: Boolean,
    /** Called when [restore] found nothing to focus (the release left the shelf). */
    onRestoreFailed: () -> Unit,
    /** The panel opened or closed: the home screen makes room for it. */
    onOpenChange: (Boolean) -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    // coming back from a release: keep the panel out until focus lands on that card again
    var restoring by remember { mutableStateOf(restore != null) }
    // the return is used once: later visits to the tab must not pull focus down into its shelves
    var pendingReturn by remember { mutableStateOf(restore) }
    LaunchedEffect(restore) {
        if (restore == null) return@LaunchedEffect
        kotlinx.coroutines.delay(600)
        pendingReturn = null
        if (!open) {
            restoring = false
            onRestoreFailed()
        }
    }
    var tab by remember { mutableStateOf(restore?.tab ?: PanelTab.Continue) }
    val firstContinue = remember { FocusRequester() }
    val firstShelfCard = remember { FocusRequester() }
    val tabFocus = remember { PanelTab.entries.associateWith { FocusRequester() } }
    Column(
        Modifier
            .fillMaxWidth()
            .onFocusChanged {
                if (open != it.hasFocus) onOpenChange(it.hasFocus)
                open = it.hasFocus
                if (it.hasFocus) restoring = false
            }
            // the panel's padding plus the tab's pill sticks out to the left so the first title lines up with the screen edge
            .offset(x = (-24).dp)
            .background(if (open) Palette.Bg.copy(alpha = 0.82f) else Color.Transparent, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelTab.entries.forEach { t ->
                // on the tabs Left/Right only switch tabs (never jump into the cards); Down or OK goes into the tab's content
                val i = t.ordinal
                PanelTabHandle(
                    modifier = Modifier
                        .focusRequester(tabFocus.getValue(t))
                        .focusProperties {
                            left = PanelTab.entries.getOrNull(i - 1)?.let { tabFocus.getValue(it) } ?: FocusRequester.Cancel
                            right = PanelTab.entries.getOrNull(i + 1)?.let { tabFocus.getValue(it) } ?: FocusRequester.Cancel
                        },
                    label = stringResource(t.label),
                    selected = open && tab == t,
                    chevron = if (t == PanelTab.Continue) (if (open) Icon.ChevronDown else Icon.ChevronUp) else null,
                    onFocus = { tab = t },
                    onClick = {
                        runCatching { if (t == PanelTab.Continue) { if (!continueEmpty) firstContinue.requestFocus() } else firstShelfCard.requestFocus() }
                    },
                )
            }
        }
        AnimatedVisibility(
            visible = open || restoring,
            enter = expandVertically(expandFrom = Alignment.Top) + slideInVertically { it } + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + slideOutVertically { it } + fadeOut(),
        ) {
            // Up from any card goes back to the open tab, not to whatever sits above it on the screen
            Column(Modifier.focusProperties { exit = { if (it == FocusDirection.Up) tabFocus.getValue(tab) else FocusRequester.Default } }.focusGroup()) {
                Spacer(Modifier.height(10.dp))
                val kind = tab.kind
                if (kind == null) {
                    if (continueEmpty) {
                        Text(stringResource(R.string.history_empty), fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    } else {
                        continueContent(firstContinue)
                    }
                } else {
                    // each tab keeps its own scroll positions
                    key(kind) { ShelvesTab(kind, shelfView, pendingReturn?.takeIf { it.tab == tab }, firstShelfCard) { item, tracker -> onOpen(item, PanelReturn(tab, tracker, itemKey(item))) } }
                }
            }
        }
    }
}

@Composable
private fun PanelTabHandle(modifier: Modifier, label: String, selected: Boolean, chevron: Icon?, onFocus: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .background(
                when {
                    focused -> Palette.Accent
                    selected -> Palette.Glass
                    else -> Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionTitle(label)
        if (chevron != null) {
            Spacer(Modifier.width(8.dp))
            TvIcon(chevron, Palette.Text, 16.dp)
        }
    }
}

@Composable
private fun ShelvesTab(
    kind: Browse,
    view: ShelfView,
    restore: PanelReturn?,
    firstCard: FocusRequester,
    onOpen: (TorrentItem, String?) -> Unit,
) {
    val all = view.shelves(kind)
    val trackers = all.map { it.tracker.name }.distinct()
    var tracker by remember { mutableStateOf(restore?.tracker?.takeIf { it in trackers }) }
    val shown = if (tracker == null) all else all.filter { it.tracker.name == tracker }

    if (trackers.size > 1) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)) {
            item { TvButton(stringResource(R.string.shelf_all), { tracker = null }, selected = tracker == null) }
            items(trackers, key = { it }) { t -> TvButton(t, { tracker = t }, selected = tracker == t) }
        }
        Spacer(Modifier.height(6.dp))
    }
    view.notes.forEach { Text(it, fontSize = 11.sp, color = Palette.Warn, modifier = Modifier.padding(horizontal = 4.dp)) }
    if (shown.isEmpty()) {
        Text(
            stringResource(if (view.loading) R.string.shelves_loading else R.string.shelves_empty),
            fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
        return
    }

    val restoreShelf = restore?.let { r -> shown.indexOfFirst { s -> s.items.any { itemKey(it) == r.itemKey } } }?.takeIf { it >= 0 }
    val restoreFocus = remember { FocusRequester() }
    val column = rememberLazyListState(initialFirstVisibleItemIndex = restoreShelf ?: 0)
    LaunchedEffect(restore) { if (restore != null && restoreShelf != null) runCatching { restoreFocus.requestFocus() } }
    LazyColumn(
        state = column,
        modifier = Modifier.heightIn(max = 300.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        itemsIndexed(shown, key = { _, s -> "${s.tracker.id}:${s.section.id}" }) { si, shelf ->
            Column {
                Text(
                    "${shelf.tracker.name} · ${shelf.section.title}",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.Muted,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
                val restoreIndex = if (si == restoreShelf) shelf.items.indexOfFirst { itemKey(it) == restore?.itemKey } else -1
                val row = rememberLazyListState(initialFirstVisibleItemIndex = restoreIndex.coerceAtLeast(0))
                LazyRow(state = row, horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)) {
                    itemsIndexed(shelf.items, key = { _, it -> itemKey(it) }) { i, item ->
                        val focus = when {
                            si == restoreShelf && i == restoreIndex -> Modifier.focusRequester(restoreFocus)
                            si == 0 && i == 0 -> Modifier.focusRequester(firstCard)
                            else -> Modifier
                        }
                        ShelfCard(item, view.poster(item), focus, onAsk = { view.askPoster(item) }) { onOpen(item, tracker) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfCard(item: TorrentItem, poster: String?, modifier: Modifier, onAsk: () -> Unit, onClick: () -> Unit) {
    LaunchedEffect(item.link) { onAsk() }
    TvCard(onClick, modifier.width(168.dp)) {
        val title = shortTitle(item.title)
        PosterImage(poster, title, 30.sp, Modifier.fillMaxWidth().height(74.dp))
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                qualityOf(item.title)?.let { (q, c) -> Tag(q, c) }
                Text("${item.seeds}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = seedColor(item.seeds))
                Text("· ${item.size}", fontSize = 10.sp, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

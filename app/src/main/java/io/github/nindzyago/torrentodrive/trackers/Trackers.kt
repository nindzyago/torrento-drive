package io.github.nindzyago.torrentodrive.trackers

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * Every tracker the app searches, in display order.
 *
 * To add a tracker: implement [TrackerProvider] in a new file in this package and add one line here.
 * See CONTRIBUTING.md for a walkthrough and [ExampleProvider] for a template.
 */
object Trackers {
    fun all(rutracker: RutrackerWeb): List<TrackerProvider> = listOf(
        RutrackerProvider(rutracker),
        RutorProvider(),
    )
}

/** Trackers switched off in settings: they don't take part in searches. */
class TrackerSwitches(context: Context) {
    private val prefs = context.getSharedPreferences("trackers", Context.MODE_PRIVATE)
    private val off = mutableStateOf(prefs.getStringSet(KEY, emptySet()).orEmpty())

    fun isOn(tracker: TrackerProvider) = tracker.id !in off.value

    fun toggle(tracker: TrackerProvider) {
        off.value = if (isOn(tracker)) off.value + tracker.id else off.value - tracker.id
        prefs.edit().putStringSet(KEY, off.value).apply()
    }

    private companion object {
        const val KEY = "off"
    }
}

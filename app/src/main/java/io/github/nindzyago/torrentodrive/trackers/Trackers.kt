package io.github.nindzyago.torrentodrive.trackers

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

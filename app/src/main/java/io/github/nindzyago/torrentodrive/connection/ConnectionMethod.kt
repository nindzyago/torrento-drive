package io.github.nindzyago.torrentodrive.connection

import android.content.Context
import androidx.annotation.StringRes
import java.net.InetSocketAddress

/**
 * A way to reach the trackers' websites. Only searching goes this way (tracker pages, .torrent files,
 * posters); video always goes straight through TorrServer.
 *
 * To add a method: implement this interface in a new file in this package and add it to [ConnectionMethods].
 */
interface ConnectionMethod {
    /** Stable id, saved in settings. */
    val id: String

    @get:StringRes val title: Int

    @get:StringRes val description: Int

    /** Small badge next to the title, e.g. the engine and its version; null for none. */
    val badge: String? get() = null

    /**
     * Prepares the route and returns the local SOCKS5 proxy to send tracker traffic through,
     * or null to connect directly. Called again on every check, so it must be cheap when already started.
     */
    suspend fun start(context: Context): InetSocketAddress?
}

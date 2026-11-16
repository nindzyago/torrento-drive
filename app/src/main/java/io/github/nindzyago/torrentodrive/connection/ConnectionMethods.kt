package io.github.nindzyago.torrentodrive.connection

import android.content.Context
import io.github.nindzyago.torrentodrive.R

/** Every connection method, in the order "Automatic" tries them. [Direct] always comes first. */
object ConnectionMethods {
    val all: List<ConnectionMethod> = listOf(
        Direct,
        ByeDpiMethod,
    )

    fun byId(id: String): ConnectionMethod? = all.firstOrNull { it.id == id }
}

/** An ordinary connection, no go-between. */
object Direct : ConnectionMethod {
    override val id = "direct"
    override val title = R.string.connection_direct
    override val description = R.string.connection_direct_hint
    override suspend fun start(context: Context) = null
}

/**
 * ByeDPI: changes how the start of each connection is sent (splits it, reorders the pieces…), so that network
 * filters don't recognize the site while the site itself receives everything as usual.
 * Runs inside the app, without a VPN; see [ByeDpi].
 */
object ByeDpiMethod : ConnectionMethod {
    override val id = "byedpi"
    override val title = R.string.connection_byedpi
    override val description = R.string.connection_byedpi_hint
    override val badge = "byedpi ${ByeDpi.VERSION}"
    override suspend fun start(context: Context) = ByeDpi.start()
}

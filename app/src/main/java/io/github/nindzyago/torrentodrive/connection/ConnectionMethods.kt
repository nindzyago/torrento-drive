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

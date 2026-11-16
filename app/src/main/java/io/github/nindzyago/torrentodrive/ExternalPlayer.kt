package io.github.nindzyago.torrentodrive

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/** Playing a stream in another app: NOVA if it is there, otherwise whatever the system offers. */
internal object ExternalPlayer {
    const val NOVA_PACKAGE = "org.courville.nova"

    /** The intent handed to another player; also what [available] asks the system about. */
    fun intent(url: Uri, title: String): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(url, "video/*")
        .putExtra("title", title)

    /**
     * Whether some other app can play a video stream, so "Other player" leads somewhere rather than to
     * "No app can do this". Android 11+ sees other apps only through the manifest's <queries> (VIEW of http video).
     */
    fun available(context: Context): Boolean {
        val probe = intent(Uri.parse("http://127.0.0.1:8090/stream/probe.mkv"), "")
        return context.packageManager.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
            .any { it.activityInfo?.packageName != context.packageName }
    }
}

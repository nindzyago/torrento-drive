package io.github.nindzyago.torrentodrive

import android.content.Context
import android.content.pm.ApplicationInfo

/** A newer release on GitHub. */
data class Update(val version: String, val apkUrl: String)

/**
 * Checks the project's latest GitHub release and installs its APK over the running app.
 * Release builds only: a debug build is signed with another key and can't be updated this way.
 */
class Updates(private val context: Context) {

    private val enabled = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0

    private val current: String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    /** The latest release if it is newer than this build, else null (also when offline). */
    suspend fun check(): Update? {
        if (!enabled) return null
        val (version, apk) = ApkInstaller.latestRelease(REPO) ?: return null
        return Update(version, apk).takeIf { isNewer(version, current) }
    }

    suspend fun install(update: Update) = ApkInstaller.install(context, update.apkUrl)

    companion object {
        private const val REPO = "nindzyago/torrento-drive"

        /** Compares dotted versions numerically: 0.10.0 > 0.9.1. */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
            val b = current.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
                if (d != 0) return d > 0
            }
            return false
        }
    }
}

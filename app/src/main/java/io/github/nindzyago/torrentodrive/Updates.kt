package io.github.nindzyago.torrentodrive

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

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
    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext null
        runCatching {
            val request = Request.Builder().url(LATEST).header("Accept", "application/vnd.github+json").build()
            val json = Http.client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@runCatching null
                JSONObject(r.body.string())
            }
            val version = json.getString("tag_name").removePrefix("v")
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") }
                ?.getString("browser_download_url") ?: return@runCatching null
            Update(version, apk).takeIf { isNewer(version, current) }
        }.getOrNull()
    }

    /**
     * Downloads the APK into a PackageInstaller session and commits it. Android then asks the
     * user to confirm (and, the first time, to allow installs from this app).
     */
    suspend fun install(update: Update) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            Http.client.newCall(Request.Builder().url(update.apkUrl).build()).execute().use { r ->
                check(r.isSuccessful) { "HTTP ${r.code}" }
                session.openWrite("app.apk", 0, -1).use { out -> r.body.byteStream().copyTo(out) }
            }
            val status = PendingIntent.getBroadcast(
                context, id, Intent(ACTION).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            listenForConfirmation()
            session.commit(status.intentSender)
        }
    }

    /** The installer reports "needs the user's confirmation" with an intent we have to start. */
    private fun listenForConfirmation() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0) != PackageInstaller.STATUS_PENDING_USER_ACTION) return
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
        val filter = IntentFilter(ACTION)
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(receiver, filter)
    }

    companion object {
        private const val LATEST = "https://api.github.com/repos/nindzyago/torrento-drive/releases/latest"
        private const val ACTION = "io.github.nindzyago.torrentodrive.INSTALL_STATUS"

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

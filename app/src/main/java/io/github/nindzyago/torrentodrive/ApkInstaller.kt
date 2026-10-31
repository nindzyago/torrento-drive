package io.github.nindzyago.torrentodrive

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/** Downloads APKs from GitHub releases and installs them: our own updates and TorrServe. */
object ApkInstaller {

    private const val ACTION = "io.github.nindzyago.torrentodrive.INSTALL_STATUS"

    /** Tag (without a leading "v") and APK download URL of a GitHub repository's latest release. */
    suspend fun latestRelease(repo: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("https://api.github.com/repos/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json").build()
            val json = Http.client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@runCatching null
                JSONObject(r.body.string())
            }
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") }
                ?.getString("browser_download_url") ?: return@runCatching null
            json.getString("tag_name").removePrefix("v") to apk
        }.getOrNull()
    }

    /**
     * Downloads the APK into a PackageInstaller session and commits it. Android then asks the
     * user to confirm (and, the first time, to allow installs from this app).
     */
    suspend fun install(context: Context, apkUrl: String) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val installer = app.packageManager.packageInstaller
        val id = installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
        installer.openSession(id).use { session ->
            Http.client.newCall(Request.Builder().url(apkUrl).build()).execute().use { r ->
                check(r.isSuccessful) { "HTTP ${r.code}" }
                session.openWrite("app.apk", 0, -1).use { out -> r.body.byteStream().copyTo(out) }
            }
            val status = PendingIntent.getBroadcast(
                app, id, Intent(ACTION).setPackage(app.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            listenForConfirmation(app)
            session.commit(status.intentSender)
        }
    }

    /** The installer reports "needs the user's confirmation" with an intent we have to start. */
    private fun listenForConfirmation(app: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0) != PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    app.unregisterReceiver(this)
                    return
                }
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
        val filter = IntentFilter(ACTION)
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else app.registerReceiver(receiver, filter)
    }
}

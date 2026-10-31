package io.github.nindzyago.torrentodrive

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import io.github.nindzyago.torrentodrive.lang.AppLanguage
import io.github.nindzyago.torrentodrive.torrserver.TorrentFile
import io.github.nindzyago.torrentodrive.trackers.RutrackerWeb
import io.github.nindzyago.torrentodrive.ui.App
import io.github.nindzyago.torrentodrive.ui.VoiceTuning
import io.github.nindzyago.torrentodrive.ui.WebCursor

class MainActivity : ComponentActivity() {

    /** Query received via intent (`adb shell am start ... --es query "..."`); handy for debugging without voice. */
    private val incomingQuery = mutableStateOf<String?>(null)
    private val openListening = mutableIntStateOf(0)

    private lateinit var rutracker: RutrackerWeb
    private val cursor = WebCursor()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rutracker = RutrackerWeb(this)
        incomingQuery.value = queryFrom(intent)
        applyVoiceDebug(intent)
        // debug builds: --es demo "url1|url2|…" opens these videos as a series in the built-in player
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val demoEpisodes = intent.getStringExtra("demo")?.takeIf { debuggable }?.split('|')?.filter(String::isNotBlank)
        setContent {
            App(
                rutracker = rutracker,
                cursor = cursor,
                openListening = openListening.intValue,
                incomingQuery = incomingQuery.value,
                onIncomingQueryHandled = { incomingQuery.value = null },
                playExternal = ::playExternal,
                demoEpisodes = demoEpisodes,
            )
        }
    }

    /** While a website is on screen (sign-in or Cloudflare check), arrows and OK drive the cursor instead of the UI. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val siteOnScreen = ::rutracker.isInitialized && (rutracker.manualLogin.value || rutracker.challenge.value)
        if (siteOnScreen && cursor.handle(event, rutracker.webView)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        rutracker.webView.destroy()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        queryFrom(intent)?.let { incomingQuery.value = it }
        applyVoiceDebug(intent)
    }

    /** Voice debugging via adb: --ez listen true [--el warmup_ms N --el release_ms N]. */
    private fun applyVoiceDebug(intent: Intent) {
        if (intent.hasExtra("warmup_ms")) VoiceTuning.warmupMs = intent.getLongExtra("warmup_ms", VoiceTuning.warmupMs)
        if (intent.hasExtra("release_ms")) VoiceTuning.releaseMs = intent.getLongExtra("release_ms", VoiceTuning.releaseMs)
        if (intent.getBooleanExtra("listen", false)) openListening.value++
    }

    /** Query from system search (the remote's microphone button) or from adb. SearchManager.QUERY is also "query". */
    private fun queryFrom(intent: Intent): String? {
        val q = intent.getStringExtra(SearchManager.QUERY) ?: intent.getStringExtra(EXTRA_QUERY)
        Log.i("TvTorrent", "intent: action=${intent.action} query=$q")
        return q
    }

    private fun playExternal(url: Uri, file: TorrentFile) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(url, "video/*")
            .putExtra("title", file.name)
        val nova = intent.clone() as Intent
        nova.setPackage(NOVA_PACKAGE)
        try {
            startActivity(nova)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent.createChooser(intent, getString(R.string.open_with)))
        }
    }

    companion object {
        const val EXTRA_QUERY = "query"
        const val NOVA_PACKAGE = "org.courville.nova"

    }
}

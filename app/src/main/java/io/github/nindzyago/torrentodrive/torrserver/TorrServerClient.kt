package io.github.nindzyago.torrentodrive.torrserver

import android.net.Uri
import io.github.nindzyago.torrentodrive.Http
import io.github.nindzyago.torrentodrive.trackers.TorrentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val METADATA_WAIT_S = 180

data class TorrentFile(val index: Int, val path: String, val length: Long) {
    val name: String get() = path.substringAfterLast('/')
    val isVideo: Boolean get() = name.substringAfterLast('.', "").lowercase() in VIDEO_EXT

    companion object {
        private val VIDEO_EXT = setOf("mkv", "mp4", "avi", "m4v", "mov", "ts", "m2ts", "webm", "wmv", "mpg", "mpeg")
    }
}

/** Torrent state in TorrServer: what we show while it opens and buffers. */
data class TorrentStats(
    val status: String,
    val seeders: Int,
    val activePeers: Int,
    val totalPeers: Int,
    val speedBytes: Long,
    val preloaded: Long,
    val preloadSize: Long,
) {
    companion object {
        fun from(json: JSONObject) = TorrentStats(
            status = json.optString("stat_string"),
            seeders = json.optInt("connected_seeders"),
            activePeers = json.optInt("active_peers"),
            totalPeers = json.optInt("total_peers"),
            speedBytes = json.optDouble("download_speed", 0.0).toLong(),
            preloaded = json.optLong("preloaded_bytes"),
            preloadSize = json.optLong("preload_size"),
        )
    }
}

/** Client for the HTTP API of TorrServer (MatriX) running on the TV box itself. */
class TorrServerClient(private val baseUrl: String = "http://127.0.0.1:8090") {

    suspend fun isAlive(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Http.client.newCall(Request.Builder().url("$baseUrl/echo").build()).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /** Adds a torrent to TorrServer and immediately returns its hash; metadata is fetched in the background. */
    suspend fun add(source: TorrentSource, title: String): String = withContext(Dispatchers.IO) {
        val added = when (source) {
            is TorrentSource.Link -> post(JSONObject().put("action", "add").put("link", source.uri).put("title", title).put("save_to_db", false))
            is TorrentSource.File -> upload(source.bytes, title)
        }
        added.getString("hash")
    }

    /** Waits for torrent metadata; [onStats] is called every second to show seeders and speed. */
    suspend fun awaitFiles(hash: String, onStats: (TorrentStats) -> Unit): List<TorrentFile> = withContext(Dispatchers.IO) {
        repeat(METADATA_WAIT_S) {
            val json = post(JSONObject().put("action", "get").put("hash", hash))
            onStats(TorrentStats.from(json))
            val files = files(json)
            if (files.isNotEmpty()) return@withContext files
            delay(1000)
        }
        error("No file list after ${METADATA_WAIT_S / 60} min: the torrent may have no live seeders")
    }

    suspend fun stats(hash: String): TorrentStats = withContext(Dispatchers.IO) {
        TorrentStats.from(post(JSONObject().put("action", "get").put("hash", hash)))
    }

    /** Preloads the start of a file into the TorrServer cache; the request returns once the buffer is full. */
    suspend fun preload(hash: String, file: TorrentFile) = withContext(Dispatchers.IO) {
        val url = stream(hash, file, "preload").toString()
        Http.client.newBuilder().readTimeout(10, TimeUnit.MINUTES).build()
            .newCall(Request.Builder().url(url).build()).execute().use { }
    }

    fun streamUrl(hash: String, file: TorrentFile): Uri = stream(hash, file, "play")

    private fun stream(hash: String, file: TorrentFile, mode: String): Uri =
        Uri.parse("$baseUrl/stream/${Uri.encode(file.name)}").buildUpon()
            .appendQueryParameter("link", hash)
            .appendQueryParameter("index", file.index.toString())
            .appendQueryParameter(mode, "")
            .build()

    private fun files(json: JSONObject): List<TorrentFile> {
        val arr = json.optJSONArray("file_stats") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val f = arr.getJSONObject(i)
            TorrentFile(f.getInt("id"), f.getString("path"), f.optLong("length"))
        }
    }

    private fun upload(bytes: ByteArray, title: String): JSONObject {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "file.torrent", bytes.toRequestBody("application/x-bittorrent".toMediaType()))
            .addFormDataPart("title", title)
            .addFormDataPart("save", "false")
            .build()
        val request = Request.Builder().url("$baseUrl/torrent/upload").post(body).build()
        return Http.client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("TorrServer answered HTTP ${resp.code}")
            JSONObject(resp.body.string())
        }
    }

    private fun post(body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url("$baseUrl/torrents")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return Http.client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("TorrServer answered HTTP ${resp.code}")
            JSONObject(resp.body.string())
        }
    }
}

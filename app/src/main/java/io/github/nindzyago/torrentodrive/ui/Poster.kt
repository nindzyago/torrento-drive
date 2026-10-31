package io.github.nindzyago.torrentodrive.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.tv.material3.Text
import io.github.nindzyago.torrentodrive.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/** Torrent posters: memory (LRU) + disk (cacheDir/posters); images are downscaled to ~480 px, plenty for a TV box. */
object Posters {
    private val memory = LruCache<String, ImageBitmap>(40)

    fun cached(url: String): ImageBitmap? = memory.get(url)

    suspend fun load(context: Context, url: String): ImageBitmap? = memory.get(url) ?: withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "posters").apply { mkdirs() }
            val file = File(dir, sha1(url))
            if (!file.exists()) {
                Http.client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    file.writeBytes(resp.body.bytes())
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MAX_SIDE || bounds.outHeight / (sample * 2) >= MAX_SIDE) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?.asImageBitmap()
                ?.also { memory.put(url, it) }
        }.getOrNull()
    }

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private const val MAX_SIDE = 480
}

/** Torrent poster; until it loads (or if none is found), a colored placeholder with the title's first letter. */
@Composable
fun PosterImage(url: String?, title: String, letterSize: TextUnit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var image by remember(url) { mutableStateOf(url?.let(Posters::cached)) }
    LaunchedEffect(url) { if (url != null && image == null) image = Posters.load(context, url) }
    Box(modifier.background(posterBrush(title))) {
        val img = image
        if (img != null) {
            Image(img, contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                title.take(1).uppercase(),
                fontSize = letterSize,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.18f),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** Placeholder color, distinct per title. */
fun posterBrush(title: String): Brush {
    val hues = listOf(0xFF2B3D5C, 0xFF5C3A2B, 0xFF3B2B5C, 0xFF2B5C4A, 0xFF5C2B45, 0xFF4A5C2B)
    val c = Color(hues[(title.hashCode() and 0x7fffffff) % hues.size])
    return Brush.linearGradient(listOf(c, Color(0xFF0F1219)))
}

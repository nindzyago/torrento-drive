package io.github.nindzyago.torrentodrive.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * Backdrop per mockup A: image on the right at 50% opacity, edges fading into the background (elliptical mask),
 * darkened on the left and bottom for text. [dense] is for list screens: stronger darkening.
 */
/** Fallback for builds without a personal picture: soft out-of-focus city lights on the right. */
@Composable
private fun BoxScope.NightLights() {
    Canvas(Modifier.matchParentSize()) {
        val w = size.width
        val h = size.height
        val lights = listOf(
            Triple(0.72f, 0.48f, Color(0xFFFFAA5A)), Triple(0.79f, 0.45f, Color(0xFFFF7A3C)),
            Triple(0.86f, 0.52f, Color(0xFFFFD296)), Triple(0.66f, 0.40f, Color(0xFFFF5A36)),
            Triple(0.92f, 0.36f, Color(0xFF78AAFF)), Triple(0.60f, 0.58f, Color(0xFFFF9A50)),
        )
        lights.forEachIndexed { i, (x, y, c) ->
            val r = h * (0.05f + 0.025f * (i % 3))
            drawCircle(
                Brush.radialGradient(listOf(c.copy(alpha = 0.35f), Color.Transparent), center = Offset(w * x, h * y), radius = r),
                radius = r,
                center = Offset(w * x, h * y),
            )
        }
        drawRect(
            Brush.radialGradient(
                listOf(Color(0x33FF6A3D), Color.Transparent),
                center = Offset(w * 0.78f, h * 0.8f),
                radius = w * 0.45f,
            )
        )
    }
}

@Composable
fun BoxScope.Backdrop(dense: Boolean = false) {
    Box(Modifier.matchParentSize().background(Palette.Bg))
    // Personal picture: put any image at res/drawable-nodpi/backdrop.png (it is gitignored).
    // Without it the public build shows neutral night lights instead.
    val context = LocalContext.current
    val personal = remember { context.resources.getIdentifier("backdrop", "drawable", context.packageName) }
    if (personal == 0) {
        NightLights()
    } else Image(
        painter = painterResource(personal),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        // like background-position: 45% 20% in the mockup
        alignment = Alignment.Center,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = 30.dp, y = 110.dp)
            .size(620.dp, 320.dp)
            .graphicsLayer { alpha = 0.5f; compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // fade the edges horizontally and vertically; the two masks are multiplied (DstIn)
                drawRect(
                    Brush.horizontalGradient(0f to Color.Transparent, 0.35f to Color.Black, 0.85f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, 0.2f to Color.Black, 0.7f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
                // the picture's top-left corner sits behind the headline and the search field: erase it there (a flattened oval)
                val spot = Offset(size.width * 0.14f, size.height * 0.26f)
                val r = size.width * 0.34f
                scale(scaleX = 1f, scaleY = 0.45f, pivot = spot) {
                    drawCircle(
                        Brush.radialGradient(0f to Color.Black, 0.55f to Color.Black, 1f to Color.Transparent, center = spot, radius = r),
                        radius = r,
                        center = spot,
                        blendMode = BlendMode.DstOut,
                    )
                }
            },
    )
    Box(
        Modifier
            .matchParentSize()
            .background(
                Brush.horizontalGradient(
                    0f to Palette.Bg.copy(alpha = if (dense) 0.95f else 0.55f),
                    0.5f to Palette.Bg.copy(alpha = if (dense) 0.75f else 0f),
                    1f to Palette.Bg.copy(alpha = if (dense) 0.45f else 0f),
                )
            )
    )
    Box(
        Modifier
            .matchParentSize()
            .background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Palette.Bg.copy(alpha = 0.85f)))
    )
}

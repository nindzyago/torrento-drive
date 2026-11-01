package io.github.nindzyago.torrentodrive.ui

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
import io.github.nindzyago.torrentodrive.R

/**
 * Backdrop per mockup A: image on the right at 50% opacity, edges fading into the background (elliptical mask),
 * darkened on the left and bottom for text. [dense] is for list screens: stronger darkening.
 */
@Composable
fun BoxScope.Backdrop(dense: Boolean = false) {
    Box(Modifier.matchParentSize().background(Palette.Bg))
    // Personal picture: put any image at res/drawable-nodpi/backdrop.png (it is gitignored) to use it
    // instead of the default one.
    val context = LocalContext.current
    val picture = remember {
        context.resources.getIdentifier("backdrop", "drawable", context.packageName).takeIf { it != 0 } ?: R.drawable.backdrop_default
    }
    Image(
        painter = painterResource(picture),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        // like background-position: 45% 20% in the mockup
        alignment = Alignment.Center,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = 30.dp, y = 90.dp)
            // wide enough to reach the voice button on the left, where it slowly fades out
            .size(900.dp, 400.dp)
            .graphicsLayer { alpha = 0.5f; compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // fade the edges horizontally and vertically; the two masks are multiplied (DstIn)
                drawRect(
                    Brush.horizontalGradient(0f to Color.Transparent, 0.3f to Color.Black.copy(alpha = 0.35f), 0.62f to Color.Black, 0.82f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, 0.18f to Color.Black, 0.68f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
                // the picture's top-left corner sits behind the headline and the search field: erase it there (a flattened oval)
                val spot = Offset(size.width * 0.2f, size.height * 0.2f)
                val r = size.width * 0.3f
                scale(scaleX = 1f, scaleY = 0.45f, pivot = spot) {
                    drawCircle(
                        Brush.radialGradient(0f to Color.Black, 0.55f to Color.Black, 1f to Color.Transparent, center = spot, radius = r),
                        radius = r,
                        center = spot,
                        blendMode = BlendMode.DstOut,
                    )
                }
                // and fade the bottom-right corner out further
                val corner = Offset(size.width, size.height)
                val cr = size.width * 0.38f
                drawCircle(
                    Brush.radialGradient(0f to Color.Black, 0.25f to Color.Black, 1f to Color.Transparent, center = corner, radius = cr),
                    radius = cr,
                    center = corner,
                    blendMode = BlendMode.DstOut,
                )
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

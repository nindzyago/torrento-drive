package io.github.nindzyago.torrentodrive.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Simple 24×24 stroke icons as in the mockups, drawn by hand without an icon library. */
enum class Icon { Mic, Search, Play, Pause, Prev, Next, Plus, Lock, ChevronUp, ChevronDown, Download }

@Composable
fun TvIcon(icon: Icon, color: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        when (icon) {
            Icon.Mic -> {
                drawRoundRect(color, p(9f, 3f), Size(6f * u, 11f * u), CornerRadius(3f * u), style = stroke)
                arc(color, stroke, u)
                drawLine(color, p(12f, 18f), p(12f, 21f), 2f * u, StrokeCap.Round)
            }
            Icon.Search -> {
                drawCircle(color, 7f * u, p(11f, 11f), style = stroke)
                drawLine(color, p(16f, 16f), p(20f, 20f), 2f * u, StrokeCap.Round)
            }
            Icon.Play -> drawPath(Path().apply { moveTo(7f * u, 4f * u); lineTo(20f * u, 12f * u); lineTo(7f * u, 20f * u); close() }, color)
            Icon.Pause -> {
                drawRoundRect(color, p(6f, 4f), Size(4f * u, 16f * u), CornerRadius(1f * u))
                drawRoundRect(color, p(14f, 4f), Size(4f * u, 16f * u), CornerRadius(1f * u))
            }
            // previous / next episode: a triangle against a bar
            Icon.Prev -> {
                drawPath(Path().apply { moveTo(19f * u, 5f * u); lineTo(8f * u, 12f * u); lineTo(19f * u, 19f * u); close() }, color)
                drawLine(color, p(6f, 5f), p(6f, 19f), 2.5f * u, StrokeCap.Round)
            }
            Icon.Next -> {
                drawPath(Path().apply { moveTo(5f * u, 5f * u); lineTo(16f * u, 12f * u); lineTo(5f * u, 19f * u); close() }, color)
                drawLine(color, p(18f, 5f), p(18f, 19f), 2.5f * u, StrokeCap.Round)
            }
            Icon.Plus -> {
                drawLine(color, p(12f, 5f), p(12f, 19f), 2f * u, StrokeCap.Round)
                drawLine(color, p(5f, 12f), p(19f, 12f), 2f * u, StrokeCap.Round)
            }
            Icon.Lock -> {
                drawRoundRect(color, p(5f, 11f), Size(14f * u, 10f * u), CornerRadius(2f * u), style = stroke)
                drawArc(color, 180f, 180f, false, p(8f, 4f), Size(8f * u, 8f * u), style = stroke)
            }
            Icon.ChevronUp -> {
                drawLine(color, p(6f, 15f), p(12f, 9f), 2f * u, StrokeCap.Round)
                drawLine(color, p(12f, 9f), p(18f, 15f), 2f * u, StrokeCap.Round)
            }
            Icon.ChevronDown -> {
                drawLine(color, p(6f, 9f), p(12f, 15f), 2f * u, StrokeCap.Round)
                drawLine(color, p(12f, 15f), p(18f, 9f), 2f * u, StrokeCap.Round)
            }
            Icon.Download -> {
                drawLine(color, p(12f, 4f), p(12f, 15f), 2f * u, StrokeCap.Round)
                drawLine(color, p(7f, 10f), p(12f, 15f), 2f * u, StrokeCap.Round)
                drawLine(color, p(12f, 15f), p(17f, 10f), 2f * u, StrokeCap.Round)
                drawLine(color, p(5f, 20f), p(19f, 20f), 2f * u, StrokeCap.Round)
            }
        }
    }
}

private fun DrawScope.arc(color: Color, stroke: Stroke, u: Float) {
    // microphone arc: M5 11 a7 7 0 0 0 14 0
    drawArc(color, 0f, 180f, false, Offset(5f * u, 4f * u), Size(14f * u, 14f * u), style = stroke)
}

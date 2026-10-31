package io.github.nindzyago.torrentodrive.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Mouse-style cursor for websites in a WebView: remote arrows move it, OK taps at the cursor position.
 * Tapping a text field opens the box's system keyboard. Works for any website.
 * Coordinates are in window pixels.
 */
class WebCursor {
    val x = mutableFloatStateOf(400f)
    val y = mutableFloatStateOf(400f)

    /** Handles remote buttons while a website is on screen. true means the event was consumed. */
    fun handle(event: KeyEvent, webView: WebView): Boolean {
        val dx: Int
        val dy: Int
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { dx = -1; dy = 0 }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { dx = 1; dy = 0 }
            KeyEvent.KEYCODE_DPAD_UP -> { dx = 0; dy = -1 }
            KeyEvent.KEYCODE_DPAD_DOWN -> { dx = 0; dy = 1 }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) tap(webView)
                return true
            }
            else -> return false
        }
        if (event.action != KeyEvent.ACTION_DOWN) return true
        // holding the button accelerates the cursor
        val step = min(STEP + event.repeatCount * ACCEL, MAX_STEP)
        val loc = IntArray(2).also(webView::getLocationInWindow)
        val left = loc[0].toFloat()
        val top = loc[1].toFloat()
        val right = left + webView.width - 1
        val bottom = top + webView.height - 1
        val nx = x.floatValue + dx * step
        val ny = y.floatValue + dy * step
        // hit the top/bottom edge: scroll the page
        if (ny < top + EDGE && dy < 0) webView.scrollBy(0, -step.toInt())
        if (ny > bottom - EDGE && dy > 0) webView.scrollBy(0, step.toInt())
        x.floatValue = nx.coerceIn(left, right)
        y.floatValue = ny.coerceIn(top + 1, bottom)
        return true
    }

    private fun tap(webView: WebView) {
        val loc = IntArray(2).also(webView::getLocationInWindow)
        val px = x.floatValue - loc[0]
        val py = y.floatValue - loc[1]
        val down = SystemClock.uptimeMillis()
        webView.requestFocus()
        for ((action, time) in listOf(MotionEvent.ACTION_DOWN to down, MotionEvent.ACTION_UP to down + 60)) {
            val e = MotionEvent.obtain(down, time, action, px, py, 0)
            e.source = InputDevice.SOURCE_TOUCHSCREEN
            webView.dispatchTouchEvent(e)
            e.recycle()
        }
    }

    companion object {
        private const val STEP = 18f
        private const val ACCEL = 6f
        private const val MAX_STEP = 70f
        private const val EDGE = 40f
    }
}

/** The cursor itself: an arrow with a white outline, visible on both light and dark sites. */
@Composable
fun CursorPointer(cursor: WebCursor, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .offset { IntOffset(cursor.x.floatValue.roundToInt(), cursor.y.floatValue.roundToInt()) }
            .size(28.dp)
    ) {
        val s = size.width
        val arrow = Path().apply {
            moveTo(0f, 0f)
            lineTo(0f, s)
            lineTo(s * 0.28f, s * 0.72f)
            lineTo(s * 0.5f, s)
            lineTo(s * 0.62f, s * 0.94f)
            lineTo(s * 0.42f, s * 0.66f)
            lineTo(s * 0.75f, s * 0.66f)
            close()
        }
        drawPath(arrow, Color.Black)
        drawPath(arrow, Color.White, style = Stroke(width = s * 0.07f))
        drawCircle(Accent, radius = s * 0.07f, center = Offset.Zero)
    }
}

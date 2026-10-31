package io.github.nindzyago.torrentodrive.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text

/** Palette: dark graphite, a single "nitro" accent, green/yellow only for torrent health. */
internal object Palette {
    val Bg = Color(0xFF0B0C10)
    val Surface = Color(0xE6181A20)
    val SurfaceHi = Color(0xFF20232C)
    val Line = Color(0x24FFFFFF)
    val Glass = Color(0x12FFFFFF)
    val Text = Color(0xFFF2F3F5)
    val Muted = Color(0xFFA3A8B2)
    val Accent = Color(0xFFFF6A3D)
    val AccentSoft = Color(0xFFFF8A66)
    val AccentDim = Color(0x66FF6A3D)
    val Good = Color(0xFF3DDC97)
    val Warn = Color(0xFFFFB547)
}

internal val Accent = Palette.Accent
internal val Muted = Palette.Muted

private val CardShape = RoundedCornerShape(11.dp)

/** Remote-friendly card: when focused, slightly lighter, with an accent border and a slight scale-up. */
@Composable
internal fun TvCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = CardDefaults.shape(CardShape),
        colors = CardDefaults.colors(containerColor = Palette.Surface, focusedContainerColor = Palette.SurfaceHi),
        border = CardDefaults.border(
            border = Border(BorderStroke(0.dp, Color.Transparent), shape = CardShape),
            focusedBorder = Border(BorderStroke(2.dp, Palette.Accent), shape = CardShape),
        ),
        scale = CardDefaults.scale(focusedScale = 1.02f),
        content = content,
    )
}

/** Button: muted at rest, bright accent when focused; [primary] keeps a dim accent at rest so it still stands out. */
@Composable
internal fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    selected: Boolean = false,
    large: Boolean = false,
    icon: Icon? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        contentPadding = if (large) PaddingValues(horizontal = 22.dp, vertical = 12.dp) else PaddingValues(horizontal = 14.dp, vertical = 7.dp),
        colors = ButtonDefaults.colors(
            containerColor = when {
                primary -> Palette.AccentDim
                selected -> Palette.Text
                else -> Palette.Glass
            },
            contentColor = if (selected && !primary) Palette.Bg else Palette.Text,
            focusedContainerColor = Palette.Accent,
            focusedContentColor = Palette.Text,
        ),
        border = ButtonDefaults.border(border = Border(BorderStroke(1.dp, if (primary || selected) Color.Transparent else Palette.Line))),
    ) {
        if (icon != null) {
            TvIcon(icon, Palette.Text, if (large) 17.dp else 14.dp)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(start = if (large) 9.dp else 6.dp))
        }
        Text(text, fontSize = if (large) 15.sp else 12.sp, fontWeight = if (large) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, softWrap = false)
    }
}

/** Small badge: category, quality, tracker. */
@Composable
internal fun Tag(text: String, color: Color = Palette.Muted) {
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** Torrent "health" color based on the seeder count. */
internal fun seedColor(seeds: Int): Color = when {
    seeds >= 5 -> Palette.Good
    seeds >= 1 -> Palette.Warn
    else -> Palette.Accent
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
}

/**
 * Chrome "t" with the TORRENTO DRIVE wordmark, rendered from art/logo-lockup.svg; [height] is the whole lockup.
 * Shifted left so the letter itself (not its speed trails) lines up with the content below.
 */
@Composable
internal fun Logo(height: androidx.compose.ui.unit.Dp = 36.dp) {
    androidx.compose.foundation.Image(
        androidx.compose.ui.res.painterResource(io.github.nindzyago.torrentodrive.R.drawable.logo_lockup),
        contentDescription = androidx.compose.ui.res.stringResource(io.github.nindzyago.torrentodrive.R.string.app_name),
        modifier = Modifier.height(height).offset(x = -height * 0.52f),
    )
}

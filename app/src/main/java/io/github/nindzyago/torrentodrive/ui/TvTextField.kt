package io.github.nindzyago.torrentodrive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

private val OkKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

private val Arrows = mapOf(
    Key.DirectionUp to FocusDirection.Up,
    Key.DirectionDown to FocusDirection.Down,
    Key.DirectionLeft to FocusDirection.Left,
    Key.DirectionRight to FocusDirection.Right,
)

/**
 * Remote-friendly text field. D-pad focus only passes over it like over a button; the on-screen keyboard
 * opens when OK is pressed on it and closes as soon as focus leaves. While the keyboard is open it takes
 * the D-pad itself; an arrow that reaches the field means the keyboard is closed, so it moves focus on.
 * The keyboard's action key ("Search", "Next"…) triggers [onImeAction].
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val outer = remember { FocusRequester() }
    val inner = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    // the text field itself can take focus (and with it the keyboard) only after OK
    var editing by remember { mutableStateOf(false) }
    var okDown by remember { mutableStateOf(false) }
    val action = onImeAction ?: { focusManager.moveFocus(FocusDirection.Down) }

    fun startEditing() {
        editing = true
        runCatching { inner.requestFocus() }
        keyboard?.show()
    }

    fun stopEditing() {
        keyboard?.hide()
        runCatching { outer.requestFocus() }
        editing = false
    }

    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }

    Box(
        modifier
            .focusRequester(outer)
            .onFocusChanged {
                focused = it.hasFocus
                if (!it.hasFocus && editing) {
                    editing = false
                    keyboard?.hide()
                }
            }
            .onPreviewKeyEvent { e ->
                val down = e.type == KeyEventType.KeyDown
                val up = e.type == KeyEventType.KeyUp
                if (!editing) {
                    // OK opens the keyboard; everything else is ordinary D-pad navigation
                    if (e.key !in OkKeys) return@onPreviewKeyEvent false
                    if (down) okDown = true
                    if (up && okDown) {
                        okDown = false
                        startEditing()
                    }
                    return@onPreviewKeyEvent true
                }
                val direction = Arrows[e.key]
                when {
                    direction != null -> {
                        if (down) {
                            stopEditing()
                            focusManager.moveFocus(direction)
                        }
                        true
                    }
                    // the keyboard is already closed (it takes Back itself while open): leave editing, keep the text
                    e.key == Key.Back -> {
                        if (up) stopEditing()
                        true
                    }
                    e.key == Key.DirectionCenter -> {
                        if (up) keyboard?.show()
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .background(Palette.Glass, RoundedCornerShape(50))
            .border(if (focused) 2.dp else 1.dp, if (focused) Accent else Palette.Line, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = Type.Caption),
            cursorBrush = SolidColor(if (editing) Accent else Color.Transparent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
                autoCorrectEnabled = false,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(onAny = { action() }),
            modifier = Modifier
                .focusRequester(inner)
                .focusProperties { canFocus = editing },
            decorationBox = { field ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvIcon(Icon.Search, Muted, 15.dp)
                    Spacer(Modifier.padding(start = 8.dp))
                    Box {
                        if (value.isEmpty()) Text(placeholder, color = Muted, fontSize = Type.Caption)
                        field()
                    }
                }
            },
        )
    }
}

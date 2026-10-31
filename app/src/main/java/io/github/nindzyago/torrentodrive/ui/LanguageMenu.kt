package io.github.nindzyago.torrentodrive.ui

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.nindzyago.torrentodrive.R
import io.github.nindzyago.torrentodrive.lang.AppLanguage
import io.github.nindzyago.torrentodrive.lang.LanguageWords
import io.github.nindzyago.torrentodrive.lang.Languages

/**
 * Flag button that opens the language menu: "Automatic" (the system's language if the app has
 * it, else English) and every language the app has. Picking another one restarts the app.
 */
@Composable
internal fun LanguageButton() {
    val context = LocalContext.current
    val current = remember { AppLanguage.current(context) }
    var open by remember { mutableStateOf(false) }
    TvButton("${current.flag}  ${current.tag.uppercase()}", { open = true })
    if (open) {
        Popup(
            alignment = Alignment.TopEnd,
            offset = IntOffset(0, 120),
            onDismissRequest = { open = false },
            properties = PopupProperties(focusable = true),
        ) {
            LanguageMenu { picked ->
                open = false
                if (picked != AppLanguage.chosen(context)) {
                    AppLanguage.choose(context, picked)
                    (context as? Activity)?.let(::restart)
                }
            }
        }
    }
}

/** Starts the app over in the new language, as if just opened: no menus, focus on the voice button. */
private fun restart(activity: Activity) {
    activity.startActivity(
        Intent(activity, activity.javaClass).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
    )
    @Suppress("DEPRECATION")
    activity.overridePendingTransition(0, 0)
}

@Composable
private fun LanguageMenu(onPick: (LanguageWords?) -> Unit) {
    val context = LocalContext.current
    val chosen = remember { AppLanguage.chosen(context) }
    val auto = remember { AppLanguage.auto() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(
        Modifier
            .padding(end = 48.dp)
            .width(260.dp)
            .background(Palette.Bg.copy(alpha = 0.95f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val autoModifier = Modifier.fillMaxWidth().then(if (chosen == null) Modifier.focusRequester(focus) else Modifier)
        TvButton(stringResource(R.string.language_auto, auto.name), { onPick(null) }, autoModifier, selected = chosen == null)
        Languages.all.forEach { language ->
            val modifier = Modifier.fillMaxWidth().then(if (chosen == language) Modifier.focusRequester(focus) else Modifier)
            TvButton("${language.flag}  ${language.name}", { onPick(language) }, modifier, selected = chosen == language)
        }
    }
}

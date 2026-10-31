package io.github.nindzyago.torrentodrive.ui

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import io.github.nindzyago.torrentodrive.R
import io.github.nindzyago.torrentodrive.SearchQuery
import androidx.compose.ui.res.stringResource
import io.github.nindzyago.torrentodrive.VoiceWarmup
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private const val TAG = "TvTorrent"
/** Warmup settings; changed via debug intent extras (warmup_ms, release_ms) to tune them without rebuilding. */
object VoiceTuning {
    /** How long to wait after opening our recording before starting recognition. */
    var warmupMs = 1000L
    /** How long after starting recognition to release our recording (-1 = hold until the end). */
    var releaseMs = 0L
}
private const val MAX_ATTEMPTS = 2

/** Below this level (recognizer RMS in dB) we assume audio from the remote didn't get through. */
private const val SILENCE_RMS = 2f

private enum class Phase { Warming, Ready, Speaking, Recognizing, Done, Failed }

/** How long the recognized phrase is shown before moving on to search. */
private const val SHOW_RESULT_MS = 1200L

/**
 * Our own listening screen instead of Google's dialog: shows whether audio arrives (level bar) and what was heard,
 * and retries once on an empty result, since on some boxes the first start of the remote's mic gets lost.
 */
@Composable
fun ListeningScreen(warmup: VoiceWarmup, onResult: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val result by rememberUpdatedState(onResult)
    var phase by remember { mutableStateOf(Phase.Warming) }
    var level by remember { mutableFloatStateOf(0f) }
    var maxLevel by remember { mutableFloatStateOf(-10f) }
    var partial by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(1) }
    var session by remember { mutableIntStateOf(0) } // changed to restart listening
    var failure by remember { mutableStateOf<String?>(null) }

    val recognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val scope = rememberCoroutineScope()

    fun retryOrFail(reason: String) {
        warmup.stop()
        Log.i(TAG, "listen: attempt=$attempt failed ($reason), maxRms=$maxLevel")
        if (attempt < MAX_ATTEMPTS) {
            attempt++
            session++
        } else {
            phase = Phase.Failed
            failure = if (maxLevel < SILENCE_RMS) {
                context.getString(R.string.voice_no_sound)
            } else {
                context.getString(R.string.voice_not_understood, reason)
            }
        }
    }

    val listener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { phase = Phase.Ready }
            override fun onBeginningOfSpeech() { phase = Phase.Speaking }
            override fun onRmsChanged(rmsdB: Float) {
                if (rmsdB > maxLevel + 1) Log.i(TAG, "listen: rms=$rmsdB")
                level = rmsdB
                if (rmsdB > maxLevel) maxLevel = rmsdB
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                phase = Phase.Recognizing
                warmup.stop()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }?.let { partial = it }
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                Log.i(TAG, "listen: attempt=$attempt result='$text' maxRms=$maxLevel")
                if (text.isNotBlank()) {
                    warmup.stop()
                    // show the full phrase and the part that goes into the search, and only then search
                    partial = text
                    phase = Phase.Done
                    scope.launch {
                        delay(SHOW_RESULT_MS)
                        result(text)
                    }
                } else {
                    retryOrFail(context.getString(R.string.voice_empty))
                }
            }
            override fun onError(error: Int) = retryOrFail(context.getString(errorText(error), error))
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    DisposableEffect(recognizer) {
        recognizer.setRecognitionListener(listener)
        onDispose {
            recognizer.cancel()
            recognizer.destroy()
            warmup.stop()
        }
    }

    LaunchedEffect(session) {
        phase = Phase.Warming
        level = 0f
        maxLevel = -10f
        partial = ""
        failure = null
        recognizer.cancel()
        warmup.start()
        delay(VoiceTuning.warmupMs) // wait for the remote service to turn on the mic and reroute audio
        Log.i(TAG, "listen: start attempt=$attempt warmupMs=${VoiceTuning.warmupMs} releaseMs=${VoiceTuning.releaseMs} peak=${warmup.lastPeak}")
        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // don't cut off early: people don't start speaking right away and they pause
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        )
        if (VoiceTuning.releaseMs >= 0) {
            delay(VoiceTuning.releaseMs)
            warmup.stop()
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(142.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.matchParentSize().border(2.dp, Palette.Accent.copy(alpha = 0.25f), CircleShape))
            Box(Modifier.size(110.dp).border(2.dp, Palette.Accent.copy(alpha = 0.45f), CircleShape))
            Box(Modifier.size(78.dp).background(Palette.Accent, CircleShape), contentAlignment = Alignment.Center) {
                TvIcon(Icon.Mic, Color.White, 33.dp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            when (phase) {
                Phase.Warming -> stringResource(if (attempt > 1) R.string.listen_retry else R.string.listen_warming)
                Phase.Ready -> stringResource(R.string.listen_ready)
                Phase.Speaking -> stringResource(R.string.listen_speaking)
                Phase.Recognizing -> stringResource(R.string.listen_recognizing)
                Phase.Done -> stringResource(R.string.listen_done)
                Phase.Failed -> stringResource(R.string.listen_failed)
            },
            fontSize = 26.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.height(24.dp))
        // audio level: if the bar doesn't move, audio from the remote isn't getting through
        val fraction = ((level + 2f) / 12f).coerceIn(0f, 1f)
        Box(Modifier.width(600.dp).height(16.dp).background(Color(0xFF2A303C), RoundedCornerShape(8.dp))) {
            Box(
                Modifier
                    .fillMaxWidth(if (phase == Phase.Failed) 0f else fraction)
                    .fillMaxHeight()
                    .background(if (maxLevel >= SILENCE_RMS) Accent else Muted, RoundedCornerShape(8.dp))
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(partial.ifEmpty { " " }, fontSize = 22.sp, color = Color.White)
        if (phase == Phase.Done) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.listen_searching, SearchQuery.parse(partial).label), fontSize = 14.sp, color = Palette.AccentSoft)
        }
        failure?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, fontSize = 20.sp, color = Accent)
            Spacer(Modifier.height(24.dp))
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            Button(onClick = { attempt = 1; session++ }, modifier = Modifier.focusRequester(focus)) { Text(stringResource(R.string.try_again)) }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            if (attempt > 1 && phase != Phase.Failed) stringResource(R.string.attempt_of, attempt, MAX_ATTEMPTS) else stringResource(R.string.key_back),
            fontSize = 14.sp,
            color = Muted,
        )
    }
}

@androidx.annotation.StringRes
private fun errorText(code: Int): Int = when (code) {
    SpeechRecognizer.ERROR_NO_MATCH -> R.string.err_no_match
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.err_timeout
    SpeechRecognizer.ERROR_AUDIO -> R.string.err_audio
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> R.string.err_network
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> R.string.err_busy
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.err_permission
    else -> R.string.err_other
}

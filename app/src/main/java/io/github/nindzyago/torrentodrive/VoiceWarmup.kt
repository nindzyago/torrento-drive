package io.github.nindzyago.torrentodrive

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlin.concurrent.thread

/**
 * Workaround for a race on boxes with a voice remote (ATV Remote): the system remote service turns on the
 * remote's microphone and reroutes audio only ~200 ms after someone opens a recording. Google's recognizer
 * manages to attach to the built-in microphone in that window, and on some boxes its driver is broken,
 * so it hears silence. So we open a recording ourselves first, wait for the remote to switch on,
 * and only then start recognition; the warmup is held until recognition finishes.
 */
class VoiceWarmup {
    @Volatile private var record: AudioRecord? = null

    /** Latest amplitude peak of our recording (0..32767); shows whether audio from the remote gets through. */
    @Volatile var lastPeak: Int = -1
        private set

    @SuppressLint("MissingPermission") // permission is requested before the call
    fun start() {
        if (record != null) return
        val rate = 16_000
        val size = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size * 2)
        }.getOrElse {
            Log.w(TAG, "warmup: AudioRecord failed", it)
            return
        }
        if (runCatching { r.startRecording() }.isFailure) {
            Log.w(TAG, "warmup: startRecording failed (no microphone permission?)")
            r.release()
            return
        }
        record = r
        Log.i(TAG, "warmup: recording state=${r.recordingState}")
        // the recording must be read, otherwise some systems treat it as inactive
        thread(name = "voice-warmup", isDaemon = true) {
            val buf = ShortArray(size)
            var peak = 0
            var samples = 0
            while (record === r) {
                val n = r.read(buf, 0, buf.size)
                if (n < 0) break
                for (i in 0 until n) peak = maxOf(peak, kotlin.math.abs(buf[i].toInt()))
                samples += n
                // log the peak every half second: a live remote mic has noise even in silence, a dead one is flat zero
                if (samples >= rate / 2) {
                    Log.i(TAG, "warmup: peak=$peak")
                    lastPeak = peak
                    peak = 0
                    samples = 0
                }
            }
        }
    }

    fun stop() {
        val r = record ?: return
        record = null
        runCatching { r.stop() }
        r.release()
        Log.i(TAG, "warmup: stopped")
    }

    private companion object {
        const val TAG = "TvTorrent"
    }
}

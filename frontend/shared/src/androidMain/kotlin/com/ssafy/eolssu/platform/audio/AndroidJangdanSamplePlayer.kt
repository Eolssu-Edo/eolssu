package com.ssafy.eolssu.platform.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import com.ssafy.eolssu.core.audio.JangdanClip
import com.ssafy.eolssu.core.audio.JangdanSamplePlayer
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Plays cached samples at their original pitch on the UI dispatcher. */
internal class AndroidJangdanSamplePlayer(context: Context, private val cache: JangdanSampleCache) : JangdanSamplePlayer {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private var pool: SoundPool? = null
    private var adjustedClip: JangdanClip? = null
    private val samples = mutableMapOf<JangdanClip, Int>()
    private val loaded = mutableMapOf<Int, CompletableDeferred<Boolean>>()
    private val streams = ArrayDeque<Int>()
    private var focus: AudioFocusRequest? = null
    private var ownsFocus = false
    private var registered = false
    override val available = true
    override var interrupted = false
        private set
    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        if (ownsFocus && change != AudioManager.AUDIOFOCUS_GAIN) { interrupted = true; silence() }
    }
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) { interrupted = true; silence() }
        }
    }

    override suspend fun prepare(clips: Set<JangdanClip>): Boolean {
        val current = pool ?: SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attributes).build().also {
            pool = it
            it.setOnLoadCompleteListener { owner, id, status ->
                if (owner === pool) loaded[id]?.complete(status == 0)
            }
        }
        val adjusted = clips.filter { it.targetFrames != null }
        if (adjusted.size > 1) return false
        val next = adjusted.firstOrNull()
        adjustedClip?.takeIf { it != next }?.let { old ->
            samples.remove(old)?.let { id ->
                current.unload(id)
                loaded.remove(id)
            }
        }
        adjustedClip = next
        for (clip in clips) {
            var temporary: File? = null
            try {
                val id = samples[clip] ?: run {
                    val cached = cache.fileFor(clip.sample)
                    val file = clip.targetFrames?.let { frames ->
                        withContext(Dispatchers.IO) {
                            val audio = stretchJangdanRoll(cached, frames)
                            File.createTempFile("jangdan-roll-", ".wav", context.cacheDir).also {
                                temporary = it
                                it.writeBytes(audio)
                            }
                        }
                    } ?: cached.file
                    if (current !== pool) return false
                    val result = current.load(file.absolutePath, 1)
                    if (result == 0) return false
                    samples[clip] = result
                    result
                }
                val ready = loaded.getOrPut(id) { CompletableDeferred() }
                if (withTimeoutOrNull(10_000) { ready.await() } != true) return false
            } finally {
                temporary?.delete()
            }
        }
        return true
    }

    override fun begin(accompanyingModel: Boolean): Boolean {
        interrupted = false
        // The model player already owns focus. Requesting it again would interrupt that same media.
        if (!accompanyingModel) {
            val granted = if (Build.VERSION.SDK_INT >= 26) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes).setOnAudioFocusChangeListener(listener).build()
                focus = request
                manager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                manager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
            ownsFocus = granted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (!ownsFocus) return false
        }
        if (!registered) {
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), Context.RECEIVER_NOT_EXPORTED)
            else context.registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
            registered = true
        }
        return true
    }

    override fun play(clip: JangdanClip, gain: Float): Boolean {
        if (interrupted) return false
        val volume = gain.coerceIn(0f, 1f) * 0.65f
        val current = pool ?: return false
        val id = samples[clip] ?: return false
        val stream = current.play(id, volume, volume, 1, 0, 1f)
        if (stream == 0) return false
        streams.addLast(stream)
        // Finished stream IDs are safe to stop; bound bookkeeping for arbitrarily long practice.
        if (streams.size > 32) current.stop(streams.removeFirst())
        return true
    }

    override fun silence() {
        streams.forEach { pool?.stop(it) }
        streams.clear()
    }

    override fun stop() {
        silence()
        if (registered) { context.unregisterReceiver(noisy); registered = false }
        if (ownsFocus) {
            if (Build.VERSION.SDK_INT >= 26) focus?.let(manager::abandonAudioFocusRequest)
            else { @Suppress("DEPRECATION") manager.abandonAudioFocus(listener) }
        }
        ownsFocus = false
        focus = null
    }

    override fun release() {
        stop()
        pool?.release()
        pool = null
        loaded.values.forEach { it.complete(false) }
        loaded.clear()
        samples.clear()
        adjustedClip = null
    }
}

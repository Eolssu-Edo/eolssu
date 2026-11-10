package com.ssafy.eolssu.platform.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import android.util.Log
import com.ssafy.eolssu.core.audio.JANGDAN_SAMPLE_RATE
import com.ssafy.eolssu.core.audio.JangdanClip
import com.ssafy.eolssu.core.audio.JangdanSample
import com.ssafy.eolssu.core.audio.JangdanSamplePlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One continuous PCM timeline, shared with the recorder's acoustic echo reference. */
internal class AndroidJangdanSamplePlayer(context: Context, private val cache: JangdanSampleCache) : JangdanSamplePlayer {
    private sealed interface Command {
        data class Play(val clip: JangdanClip, val pcm: ShortArray, val gain: Float) : Command
        data object Silence : Command
    }

    private val context = context.applicationContext
    private val manager = this.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val lock = Any()
    private val preparing = Mutex()
    private val originals = mutableMapOf<JangdanSample, ShortArray>()
    private val samples = mutableMapOf<JangdanClip, ShortArray>()
    private val commands = ArrayDeque<Command>()
    private val events = ArrayDeque<RenderedJangdanEvent>()
    private var adjustedClip: JangdanClip? = null
    private var track: AudioTrack? = null
    private var worker: Thread? = null
    private var running = false
    private var begun = false
    private var released = false
    private var epoch = 0L
    private var frameZeroNanos: Long? = null
    private var clockObservedNanos = 0L
    private var deviceId: Int? = null
    private var deviceType: Int? = null
    private var underruns = 0
    private var clipped = false
    private var focus: AudioFocusRequest? = null
    private var ownsFocus = false
    private var registered = false
    override val available = true
    @Volatile override var interrupted = false
        private set
    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        synchronized(lock) {
            if (ownsFocus && change != AudioManager.AUDIOFOCUS_GAIN) {
                interrupted = true
                silence()
            }
        }
    }
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                interrupted = true
                silence()
            }
        }
    }

    override suspend fun prepare(clips: Set<JangdanClip>): Boolean = preparing.withLock {
        val adjusted = clips.filter { it.targetFrames != null }
        if (adjusted.size > 1 || adjusted.any { it.targetFrames !in 1..JANGDAN_SAMPLE_RATE * 4 }) return@withLock false
        if (synchronized(lock) { released }) return@withLock false
        try {
            for (clip in clips) {
                if (synchronized(lock) { samples.containsKey(clip) }) continue
                val original = synchronized(lock) { originals[clip.sample] } ?: withContext(Dispatchers.IO) {
                    decodeJangdanPcm(cache.fileFor(clip.sample))
                }
                val pcm = clip.targetFrames?.let { frames ->
                    withContext(Dispatchers.Default) { stretchJangdanRoll(original, frames) }
                } ?: original
                synchronized(lock) {
                    if (released) return@withLock false
                    originals[clip.sample] = original
                    samples[clip] = pcm
                }
            }
            synchronized(lock) {
                val next = adjusted.firstOrNull()
                adjustedClip?.takeIf { it != next }?.let(samples::remove)
                adjustedClip = next
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w("JangdanPcm", "Unable to prepare PCM samples", failure)
            false
        }
    }

    /** Returns a copy: neither the recorder nor a new prepare can modify a playing voice. */
    fun pcmFor(clip: JangdanClip): ShortArray? = synchronized(lock) { samples[clip]?.copyOf() }

    fun outputSnapshot(): JangdanOutputSnapshot = synchronized(lock) {
        val currentClock = frameZeroNanos.takeIf { System.nanoTime() - clockObservedNanos <= 500_000_000L }
        JangdanOutputSnapshot(currentClock, epoch, manager.getStreamVolume(AudioManager.STREAM_MUSIC),
            deviceId, deviceType, underruns, clipped, events.toList())
    }

    override fun begin(accompanyingModel: Boolean): Boolean = synchronized(lock) {
        if (released) return false
        interrupted = false
        // The model player already owns focus. Requesting it again would interrupt that same media.
        if (!accompanyingModel && !ownsFocus) {
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
        try {
            if (!registered) {
                if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), Context.RECEIVER_NOT_EXPORTED)
                else context.registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
                registered = true
            }
            if (!running) startTrack()
            begun = true
            true
        } catch (failure: Exception) {
            Log.w("JangdanPcm", "Unable to begin PCM playback", failure)
            interrupted = true
            stop()
            false
        }
    }

    override fun play(clip: JangdanClip, gain: Float): Boolean = synchronized(lock) {
        if (!begun || !running || interrupted || !gain.isFinite()) return false
        val pcm = samples[clip] ?: return false
        // Bound requests even if an unhealthy device stops draining the output worker.
        if (commands.size >= 64) return false
        commands.addLast(Command.Play(clip, pcm, gain.coerceIn(0f, 1f) * 0.65f))
        true
    }

    override fun silence() = synchronized(lock) {
        commands.clear()
        if (running) commands.addLast(Command.Silence)
    }

    override fun stop() = synchronized(lock) {
        begun = false
        silence()
        if (registered) { context.unregisterReceiver(noisy); registered = false }
        if (ownsFocus) {
            if (Build.VERSION.SDK_INT >= 26) focus?.let(manager::abandonAudioFocusRequest)
            else { @Suppress("DEPRECATION") manager.abandonAudioFocus(listener) }
        }
        ownsFocus = false
        focus = null
        // Keep writing silence: calibration and the following take must share this hardware clock.
    }

    override fun release() {
        val closing = synchronized(lock) {
            if (released) return
            stop()
            released = true
            running = false
            commands.clear()
            samples.clear()
            originals.clear()
            frameZeroNanos = null
            worker to track
        }
        // A paused track releases a blocking write. Only the worker releases the AudioTrack itself.
        runCatching { closing.second?.pause() }
        closing.first?.join(500)
    }

    /** Caller holds lock; a failed construction never leaves an initialized native track behind. */
    private fun startTrack() {
        val minimum = AudioTrack.getMinBufferSize(JANGDAN_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0)
        val builder = AudioTrack.Builder().setAudioAttributes(attributes)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(JANGDAN_SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(minimum, BLOCK_FRAMES * 4))
        if (Build.VERSION.SDK_INT >= 26) builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        val output = builder.build()
        try {
            check(output.state == AudioTrack.STATE_INITIALIZED)
            output.play()
        } catch (failure: Exception) {
            output.release()
            throw failure
        }
        epoch++
        frameZeroNanos = null
        deviceId = null
        deviceType = null
        underruns = 0
        clipped = false
        events.clear()
        track = output
        running = true
        worker = Thread({ pump(output) }, "jangdan-pcm").also { it.start() }
    }

    private fun pump(output: AudioTrack) {
        val mixer = JangdanPcmMixer()
        val timestamp = AudioTimestamp()
        val clock = JangdanOutputClock()
        var nextClockPollNanos = 0L
        var writtenFrames = 0L
        var lastVoiceFrame = 0L
        var block: JangdanPcmBlock? = null
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            while (true) {
                var flush = false
                synchronized(lock) {
                    if (!running || track !== output) return
                    while (commands.isNotEmpty()) {
                        when (val command = commands.removeFirst()) {
                            is Command.Play -> if (mixer.play(command.clip, command.pcm, command.gain)) epoch++
                            Command.Silence -> {
                                val head = output.playbackHeadPosition.toLong() and 0xffffffffL
                                flush = flush || mixer.active || (block?.let { it.written < it.voiceFrames } == true) || lastVoiceFrame > head
                                mixer.clear()
                                block = null
                            }
                        }
                    }
                }
                if (flush) {
                    output.pause()
                    output.flush()
                    writtenFrames = 0
                    lastVoiceFrame = 0
                    clock.reset()
                    nextClockPollNanos = 0
                    synchronized(lock) {
                        epoch++
                        frameZeroNanos = null
                        events.clear()
                        clipped = false
                    }
                    output.play()
                }
                val next = block ?: mixer.render(BLOCK_FRAMES, writtenFrames).also { block = it }
                val offset = next.written
                val count = output.write(next.pcm, offset, next.remaining, AudioTrack.WRITE_BLOCKING)
                if (count <= 0) {
                    if (synchronized(lock) { !running }) return
                    error("Jangdan PCM write failed: $count")
                }
                val started = next.accept(count)
                val voiced = minOf(count, (next.voiceFrames - offset).coerceAtLeast(0))
                if (voiced > 0) lastVoiceFrame = writtenFrames + voiced
                writtenFrames += count
                val route = output.routedDevice
                synchronized(lock) {
                    if (next.clipped && !clipped) epoch++
                    clipped = next.clipped
                    if (deviceId != null && (deviceId != route?.id || deviceType != route?.type)) {
                        epoch++
                        clock.reset()
                        nextClockPollNanos = 0
                    }
                    deviceId = route?.id
                    deviceType = route?.type
                    underruns = output.underrunCount
                    val now = System.nanoTime()
                    if (now >= nextClockPollNanos) {
                        frameZeroNanos = if (output.getTimestamp(timestamp)) {
                            clock.observe(timestamp.framePosition, timestamp.nanoTime, writtenFrames, now)
                        } else {
                            clock.reset()
                            null
                        }
                        clockObservedNanos = now
                        nextClockPollNanos = now + 100_000_000L
                    }
                    events.addAll(started)
                    while (events.isNotEmpty() && (events.first().frame < writtenFrames - EVENT_HISTORY_FRAMES || events.size > 4096)) events.removeFirst()
                }
                if (next.remaining == 0) block = null
            }
        } catch (failure: Exception) {
            Log.w("JangdanPcm", "PCM playback stopped", failure)
            synchronized(lock) { if (!released) interrupted = true }
        } finally {
            runCatching { output.pause() }
            runCatching { output.flush() }
            output.release()
            synchronized(lock) {
                if (track === output) {
                    track = null
                    worker = null
                    running = false
                    begun = false
                    frameZeroNanos = null
                    commands.clear()
                    epoch++
                }
            }
        }
    }

    private companion object {
        const val BLOCK_FRAMES = 441
        const val EVENT_HISTORY_FRAMES = JANGDAN_SAMPLE_RATE * 120L
    }
}

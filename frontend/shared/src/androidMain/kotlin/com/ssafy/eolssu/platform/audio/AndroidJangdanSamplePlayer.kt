package com.ssafy.eolssu.platform.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.ssafy.eolssu.core.audio.JANGDAN_SAMPLE_RATE
import com.ssafy.eolssu.core.audio.JangdanClip
import com.ssafy.eolssu.core.audio.JangdanSamplePlayer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.locks.LockSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToLong

/** One continuous PCM output avoids the per-attack start latency of separate SoundPool streams. */
internal class AndroidJangdanSamplePlayer(context: Context, private val cache: JangdanSampleCache) : JangdanSamplePlayer {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val outputRate = manager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
        ?.takeIf { it in 8_000..192_000 } ?: JANGDAN_SAMPLE_RATE
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val samples = mutableMapOf<JangdanClip, ShortArray>()
    private val queue = JangdanPcmQueue()
    private var track: AudioTrack? = null
    private var focus: AudioFocusRequest? = null
    private var ownsFocus = false
    private var registered = false
    private var acceptingHits = false
    @Volatile override var failed = false
        private set
    private val prepareMutex = Mutex()
    private var epoch = 0
    private var outputGeneration = 0
    private var originNs: Long? = null
    private var timingDispatcher: ExecutorCoroutineDispatcher? = null
    private var outputExecutor: ExecutorService? = null
    override val timingContext: CoroutineContext
        @Synchronized get() = timingDispatcher ?: Executors.newSingleThreadExecutor(audioThread("eolssu-jangdan"))
            .asCoroutineDispatcher().also { timingDispatcher = it }
    override val available = true
    @Volatile override var interrupted = false
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
        return prepareMutex.withLock { prepareSamples(clips) }
    }

    private suspend fun prepareSamples(clips: Set<JangdanClip>): Boolean {
        val request = synchronized(this) { epoch }
        for (clip in clips) {
            if (synchronized(this) { clip in samples }) continue
            val pcm = withContext(Dispatchers.IO) {
                val cached = cache.fileFor(clip.sample)
                val data = clip.targetFrames?.let { stretchJangdanRoll(cached, it).copyOfRange(44, 44 + it * 2) }
                    ?: decodeJangdanPcm(cached)
                jangdanOutputPcm(data, outputRate)
            }
            synchronized(this) {
                if (request != epoch) return false
                samples[clip] = pcm
            }
        }
        val needsTrack = synchronized(this) {
            samples.keys.removeAll { it.targetFrames != null && it !in clips }
            track == null
        }
        if (needsTrack) {
            withContext(Dispatchers.IO) {
                val created = createTrack()
                synchronized(this@AndroidJangdanSamplePlayer) {
                    if (request != epoch || track != null) created.release() else track = created
                }
            }
        }
        return synchronized(this) { request == epoch && track != null }
    }

    private fun createTrack(): AudioTrack {
        val minimum = AudioTrack.getMinBufferSize(outputRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0)
        val builder = AudioTrack.Builder().setAudioAttributes(attributes)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(outputRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(minimum).setTransferMode(AudioTrack.MODE_STREAM)
        if (Build.VERSION.SDK_INT >= 26) builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        return builder.build().also {
            if (it.state != AudioTrack.STATE_INITIALIZED) { it.release(); error("Jangdan output unavailable") }
            // Keep the native buffer: two hardware bursts can starve with Java scheduling or playback capture.
            if (Build.VERSION.SDK_INT >= 31) it.setStartThresholdInFrames(BLOCK_FRAMES)
        }
    }

    @Synchronized override fun begin(accompanyingModel: Boolean): Boolean {
        if (track == null) return false
        interrupted = false
        failed = false
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
        acceptingHits = true
        return true
    }

    @Synchronized override fun play(clip: JangdanClip, gain: Float): Boolean {
        if (interrupted || failed || !acceptingHits) return false
        val current = track ?: return false
        val pcm = samples[clip] ?: return false
        val now = System.nanoTime()
        val first = originNs == null
        val origin = originNs ?: now.also { originNs = it }
        val frame = ((now - origin) * (outputRate / 1_000_000_000.0)).roundToLong() + current.bufferSizeInFrames + BLOCK_FRAMES
        queue.add(pcm, gain.coerceIn(0f, 1f) * 0.65f, frame)
        if (first) {
            val token = ++outputGeneration
            val executor = outputExecutor ?: Executors.newSingleThreadExecutor(audioThread("eolssu-jangdan-pcm"))
                .also { outputExecutor = it }
            executor.execute { writePcm(current, token) }
        }
        return true
    }

    private fun writePcm(current: AudioTrack, token: Int) {
        var block = ShortArray(0)
        var offset = 0
        var started = false
        var underruns = 0
        while (true) {
            val active = synchronized(this) {
                if (token != outputGeneration || !acceptingHits || interrupted || current !== track) return@synchronized false
                val latestUnderruns = current.underrunCount
                if (!started) underruns = latestUnderruns
                else if (latestUnderruns > underruns) {
                    val elapsedFrames = ((System.nanoTime() - checkNotNull(originNs)) * (outputRate / 1_000_000_000.0)).roundToLong()
                    if (elapsedFrames > queue.nextFrame) {
                        // A starved native track must not shift every later attack or replay a missed onset.
                        queue.discardBefore(elapsedFrames)
                        block = ShortArray(0)
                        offset = 0
                    }
                    underruns = latestUnderruns
                }
                if (offset == block.size) { block = queue.read(BLOCK_FRAMES); offset = 0 }
                val written = try {
                    current.write(block, offset, block.size - offset, AudioTrack.WRITE_NON_BLOCKING)
                } catch (_: IllegalStateException) { AudioTrack.ERROR_INVALID_OPERATION }
                if (written < 0) {
                    failed = true
                    silence()
                    return@synchronized false
                }
                offset += written
                if (!started && offset == block.size) {
                    current.play()
                    started = true
                }
                true
            }
            if (!active) return
            LockSupport.parkNanos(1_000_000)
        }
    }

    @Synchronized override fun silence() {
        ++outputGeneration
        originNs = null
        queue.clear()
        track?.let { it.pause(); it.flush() }
    }

    @Synchronized override fun stop() {
        acceptingHits = false
        silence()
        if (registered) { context.unregisterReceiver(noisy); registered = false }
        if (ownsFocus) {
            if (Build.VERSION.SDK_INT >= 26) focus?.let(manager::abandonAudioFocusRequest)
            else { @Suppress("DEPRECATION") manager.abandonAudioFocus(listener) }
        }
        ownsFocus = false
        focus = null
    }

    @Synchronized override fun release() {
        stop()
        ++epoch
        track?.release()
        track = null
        samples.clear()
        timingDispatcher?.close()
        timingDispatcher = null
        outputExecutor?.shutdownNow()
        outputExecutor = null
    }

    private fun audioThread(name: String) = ThreadFactory { action ->
        Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            action.run()
        }, name)
    }
}

private const val BLOCK_FRAMES = 128

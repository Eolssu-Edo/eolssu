package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.JANGDAN_SAMPLE_RATE
import com.ssafy.eolssu.core.audio.JangdanClip
import com.ssafy.eolssu.core.audio.JangdanSampleFile
import com.ssafy.eolssu.core.audio.JangdanSampleFiles
import com.ssafy.eolssu.core.audio.JangdanSamplePlayer
import kotlin.math.roundToInt
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioEngineConfigurationChangeNotification
import platform.AVFAudio.AVAudioEngineManualRenderingModeOffline
import platform.AVFAudio.AVAudioEngineManualRenderingStatusSuccess
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioPlayerNodeBufferInterrupts
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioSessionRouteChangeReasonKey
import platform.AVFAudio.AVAudioSessionRouteChangeReasonOldDeviceUnavailable
import platform.AVFAudio.AVAudioTime
import platform.AVFAudio.AVAudioUnitTimePitch
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.QuartzCore.CACurrentMediaTime
import platform.darwin.NSObjectProtocol

/**
 * Plays cached jangdan samples at their original pitch, overlapping hits on a small pool of player nodes.
 * Calls come from the UI (main) thread like [IosAudioPlayer]; only decoding and the roll time-stretch run on [io].
 * Interruptions, unplugged headphones and engine configuration changes silence playback as interrupted.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosJangdanSamplePlayer(
    private val files: JangdanSampleFiles,
    private val io: CoroutineDispatcher,
) : JangdanSamplePlayer {
    private val format = checkNotNull(AVAudioFormat(standardFormatWithSampleRate = JANGDAN_SAMPLE_RATE.toDouble(), channels = 1u))
    private var engine: AVAudioEngine? = null
    private var nodes: List<AVAudioPlayerNode> = emptyList()
    private var next = 0
    private val buffers = mutableMapOf<JangdanClip, AVAudioPCMBuffer>()
    private var adjustedClip: JangdanClip? = null
    private var ownsSession = false
    private val notifications = NSNotificationCenter.defaultCenter
    private val observers = mutableListOf<NSObjectProtocol>()
    override val available = true
    override var interrupted = false
        private set

    override suspend fun prepare(clips: Set<JangdanClip>): Boolean {
        val adjusted = clips.filter { it.targetFrames != null }
        if (adjusted.size > 1) return false
        val nextAdjusted = adjusted.firstOrNull()
        adjustedClip?.takeIf { it != nextAdjusted }?.let(buffers::remove)
        adjustedClip = nextAdjusted
        for (clip in clips) {
            if (clip in buffers) continue
            val source = files.fileFor(clip.sample)
            val buffer = withContext(io) {
                val decoded = decode(source)
                clip.targetFrames?.let { decoded?.let { pcm -> stretchJangdanRoll(pcm, it) } } ?: decoded
            } ?: return false
            buffers[clip] = buffer
        }
        return true
    }

    override fun begin(accompanyingModel: Boolean): Boolean {
        interrupted = false
        // The model player already owns the playback session; starting it again would not help and stopping it would cut that media.
        if (!accompanyingModel && !ownsSession) {
            ownsSession = IosAudioSession.startPlayback()
            if (!ownsSession) return false
        }
        if (observers.isEmpty()) observe()
        val current = engine ?: createEngine().also { engine = it }
        if (!current.running) {
            current.prepare()
            if (!current.startAndReturnError(null)) return false
        }
        return true
    }

    override fun play(clip: JangdanClip, gain: Float, delayMs: Double): Boolean {
        if (interrupted) return false
        val current = engine?.takeIf { it.running } ?: return false
        val buffer = buffers[clip] ?: return false
        if (nodes.isEmpty()) return false
        val node = nodes[next]
        next = (next + 1) % nodes.size
        node.volume = gain.coerceIn(0f, 1f) * 0.65f
        // The node's timeline must run before a host-time buffer is converted to its sample time.
        if (!node.playing) node.play()
        // Every hit gets the same lead past the render cycle, so the first hit and scheduled hits share one latency.
        // Host time is the audio clock (mach_absolute_time), so polling jitter never reaches the attack.
        val at = AVAudioTime(hostTime = AVAudioTime.hostTimeForSeconds(
            CACurrentMediaTime() + delayMs.coerceAtLeast(0.0) / 1000.0 + scheduleLeadSeconds()))
        // Each node holds one hit; reusing it cuts that node's oldest hit, like the Android stream bound.
        node.scheduleBuffer(buffer, atTime = at, options = AVAudioPlayerNodeBufferInterrupts, completionHandler = null)
        return current.running
    }

    /** Two I/O buffers: later than the next render cycle even when the main thread wakes late. */
    private fun scheduleLeadSeconds(): Double =
        (AVAudioSession.sharedInstance().IOBufferDuration * 2).coerceIn(MIN_LEAD_SECONDS, MAX_LEAD_SECONDS)

    override fun silence() {
        nodes.forEach { it.stop() }
    }

    override fun stop() {
        silence()
        engine?.stop()
        observers.forEach(notifications::removeObserver)
        observers.clear()
        if (ownsSession) IosAudioSession.stopPlayback()
        ownsSession = false
    }

    override fun release() {
        stop()
        engine?.let { current -> nodes.forEach(current::detachNode) }
        engine = null
        nodes = emptyList()
        buffers.clear()
        adjustedClip = null
    }

    private fun createEngine(): AVAudioEngine = AVAudioEngine().also { current ->
        nodes = List(NODE_COUNT) { AVAudioPlayerNode() }
        next = 0
        nodes.forEach { node ->
            current.attachNode(node)
            current.connect(node, to = current.mainMixerNode, format = format)
        }
    }

    private fun observe() {
        observers += notifications.addObserverForName(AVAudioSessionInterruptionNotification, `object` = null,
            queue = NSOperationQueue.mainQueue) { note ->
            if (note?.number(AVAudioSessionInterruptionTypeKey) == AVAudioSessionInterruptionTypeBegan) interrupt()
        }
        observers += notifications.addObserverForName(AVAudioSessionRouteChangeNotification, `object` = null,
            queue = NSOperationQueue.mainQueue) { note ->
            if (note?.number(AVAudioSessionRouteChangeReasonKey) == AVAudioSessionRouteChangeReasonOldDeviceUnavailable) interrupt()
        }
        // The engine stops itself when the output route's format changes; treat it like an interruption.
        observers += notifications.addObserverForName(AVAudioEngineConfigurationChangeNotification, `object` = null,
            queue = NSOperationQueue.mainQueue) { note -> if (note?.`object` === engine) interrupt() }
    }

    private fun interrupt() {
        interrupted = true
        silence()
    }

    private fun NSNotification.number(key: Any?): ULong? = (userInfo?.get(key) as? NSNumber)?.unsignedLongValue

    /** Decodes the AAC with the container's priming removed, keeping at most the manifest's frame count. */
    private fun decode(source: JangdanSampleFile): AVAudioPCMBuffer? {
        val url = NSURL.fileURLWithPath(source.path.toString())
        val file = AVAudioFile(forReading = url, commonFormat = AVAudioPCMFormatFloat32, interleaved = false, error = null)
        val fileFormat = file.processingFormat
        if (fileFormat.sampleRate != JANGDAN_SAMPLE_RATE.toDouble() || fileFormat.channelCount != 1u) return null
        val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = source.frames.toUInt()) ?: return null
        if (!file.readIntoBuffer(buffer, frameCount = source.frames.toUInt(), error = null)) return null
        return buffer.takeIf { it.frameLength > 0u }
    }

    private companion object {
        const val NODE_COUNT = 8
        const val MIN_LEAD_SECONDS = 0.01
        const val MAX_LEAD_SECONDS = 0.05
    }
}

private const val RENDER_CHUNK = 4096u

/**
 * Changes a mono roll's length to [targetFrames] without changing its pitch (offline `AVAudioUnitTimePitch`),
 * trimming or zero-padding the tail so the roll fills exactly the score's span, like the Android Sonic stretch.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun stretchJangdanRoll(source: AVAudioPCMBuffer, targetFrames: Int): AVAudioPCMBuffer? {
    val format = source.format
    val sourceFrames = source.frameLength.toInt()
    if (targetFrames <= 0 || sourceFrames <= 0 || format.channelCount != 1u) return null
    val output = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = targetFrames.toUInt()) ?: return null
    val renderer = AVAudioEngine()
    val player = AVAudioPlayerNode()
    val pitch = AVAudioUnitTimePitch().apply { rate = (sourceFrames.toFloat() / targetFrames).coerceIn(1f / 32, 32f) }
    renderer.attachNode(player)
    renderer.attachNode(pitch)
    renderer.connect(player, to = pitch, format = format)
    renderer.connect(pitch, to = renderer.mainMixerNode, format = format)
    if (!renderer.enableManualRenderingMode(AVAudioEngineManualRenderingModeOffline, format, RENDER_CHUNK, null)) return null
    if (!renderer.startAndReturnError(null)) return null
    try {
        player.scheduleBuffer(source, completionHandler = null)
        player.play()
        val chunk = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = RENDER_CHUNK) ?: return null
        val out = checkNotNull(output.floatChannelData)[0]!!
        // The time-pitch unit delays its output; drop that lead-in so hits stay on the beat.
        var skip = (pitch.latency * format.sampleRate).roundToInt()
        var written = 0
        var rendered = 0
        val limit = targetFrames + skip + format.sampleRate.toInt()
        while (written < targetFrames && rendered < limit) {
            val status = renderer.renderOffline(RENDER_CHUNK, toBuffer = chunk, error = null)
            if (status != AVAudioEngineManualRenderingStatusSuccess) return null
            val count = chunk.frameLength.toInt()
            if (count == 0) return null
            rendered += count
            val samples = checkNotNull(chunk.floatChannelData)[0]!!
            var index = minOf(skip, count)
            skip -= index
            while (index < count && written < targetFrames) out[written++] = samples[index++]
        }
        while (written < targetFrames) out[written++] = 0f
        output.frameLength = targetFrames.toUInt()
        return output
    } finally {
        player.stop()
        renderer.stop()
    }
}

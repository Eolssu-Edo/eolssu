package com.ssafy.eolssu.platform.audio

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import android.media.AudioDeviceInfo
import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.CaptureBoundary
import com.ssafy.eolssu.core.audio.PitchConfirmation
import com.ssafy.eolssu.core.audio.PitchReading
import com.ssafy.eolssu.core.audio.RecordedAudio
import com.ssafy.eolssu.core.audio.YinPitchEstimator
import com.ssafy.eolssu.core.audio.JangdanSamplePlayer
import com.ssafy.eolssu.core.audio.JangdanClip
import com.ssafy.eolssu.core.audio.RecordingEchoStatus
import com.ssafy.eolssu.core.audio.EchoCalibrationProgress
import com.ssafy.eolssu.core.audio.EchoCalibrationStrike
import com.ssafy.eolssu.core.audio.EchoQuietWindow
import com.ssafy.eolssu.core.audio.EchoPlaybackEvent
import com.ssafy.eolssu.core.audio.EchoCalibrationResult
import com.ssafy.eolssu.core.audio.RecordingEchoCalibrator
import com.ssafy.eolssu.core.audio.RecordingEchoFilter
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 마이크 입력 하나를 m4a 저장과 별도 작업의 음높이 분석에 전달한다. */
class AndroidAudioRecorder(context: Context) : AudioRecorder {
    private val debugBuild = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val directory = File(context.applicationContext.cacheDir, "recordings")
    private val readings = MutableSharedFlow<PitchReading>(extraBufferCapacity = 16)
    private val echoState = MutableStateFlow(RecordingEchoStatus.Off)
    private val ids = AtomicLong()
    private val stateLock = Any()
    @Volatile private var session: Session? = null
    private var completed: File? = null

    override val supportsEchoCalibration = true
    override val echoStatus = echoState.asStateFlow()

    override suspend fun prepareEcho(
        player: JangdanSamplePlayer,
        clips: Set<JangdanClip>,
        progress: (EchoCalibrationProgress) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val output = player as? AndroidJangdanSamplePlayer ?: return@withContext false
        if (clips.isEmpty()) return@withContext false
        var preparing: Session? = null
        var accepted = false
        echoState.value = RecordingEchoStatus.Measuring
        try {
            check(output.prepare(clips))
            val lengths = clips.associateWith { checkNotNull(output.pcmFor(it)).size }
            // Bound preparatory mic storage. A very large/unsupported plan can still record raw.
            check(lengths.values.sumOf { it.toLong() + RATE * 3L / 4 } * 4 < RATE * 55L)
            val next = synchronized(stateLock) {
                val previous = session
                previous?.cancelled = true
                Session(ids.incrementAndGet(), previous, warming = true).also { session = it }
            }
            preparing = next
            next.worker.start()
            next.await(next.ready)
            output.retainCalibrationClock(true)
            check(output.begin(accompanyingModel = false))
            val quiet = mutableListOf<EchoQuietWindow>()
            val events = mutableListOf<RenderedJangdanEvent>()
            progress(EchoCalibrationProgress(0, clips.size * 4))
            delay(1_000)
            val baseline = output.outputSnapshot()
            check(baseline.frameZeroNanos != null && baseline.volume > 0 &&
                baseline.deviceType == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER && !baseline.clipped)
            val input = next.probe.snapshot(includeSamples = false)
            check(input.zeroNanos != null && input.deviceType == AudioDeviceInfo.TYPE_BUILTIN_MIC)
            quiet += EchoQuietWindow(RATE / 10L, input.frames)
            var lastFrame = baseline.events.lastOrNull()?.frame ?: -1L
            repeat(4) { round ->
                for (clip in clips.sortedBy { it.sample.ordinal }) {
                    currentCoroutineContext().ensureActive()
                    check(!next.cancelled && !next.failed && !output.interrupted)
                    check(output.play(clip, 1f))
                    val eventDeadline = System.nanoTime() + 1_000_000_000L
                    var event: RenderedJangdanEvent? = null
                    while (event == null && System.nanoTime() < eventDeadline) {
                        delay(10)
                        event = output.outputSnapshot().events.firstOrNull { it.frame > lastFrame && it.clip == clip }
                    }
                    val hit = checkNotNull(event)
                    lastFrame = hit.frame
                    events += hit
                    val out = output.outputSnapshot()
                    val microphone = next.probe.snapshot(includeSamples = false)
                    check(sameOutput(baseline, out) && microphone.deviceId == input.deviceId)
                    val start = ((checkNotNull(out.frameZeroNanos) - checkNotNull(microphone.zeroNanos)) * RATE / 1e9).roundToLong() + hit.frame
                    val quietStart = start + lengths.getValue(clip) + RATE / 4
                    // Slightly different quiet gaps avoid always sampling the same unrelated tonal phase.
                    val quietEnd = quietStart + RATE * (350 + round * 17) / 1000
                    val deadline = System.nanoTime() + (lengths.getValue(clip) * 1_000_000_000L / RATE) + 2_000_000_000L
                    while (next.probe.frames < quietEnd && System.nanoTime() < deadline) {
                        delay(20)
                        check(!next.cancelled && !next.failed && !output.interrupted)
                        check(sameOutput(baseline, output.outputSnapshot()))
                    }
                    check(next.probe.frames >= quietEnd)
                    quiet += EchoQuietWindow(quietStart, quietEnd)
                    progress(EchoCalibrationProgress(events.size, clips.size * 4))
                }
            }
            val captured = next.probe.snapshot(includeSamples = true)
            val rendered = output.outputSnapshot()
            check(sameOutput(baseline, rendered) && captured.deviceId == input.deviceId)
            val shift = ((checkNotNull(rendered.frameZeroNanos) - checkNotNull(captured.zeroNanos)) * RATE / 1e9).roundToLong()
            val strikes = events.map { EchoCalibrationStrike(it.clip, it.frame + shift, it.gain) }
            when (val result = RecordingEchoCalibrator.calibrate(captured.samples, strikes, lengths, quiet, RATE)) {
                is EchoCalibrationResult.Accepted -> {
                    currentCoroutineContext().ensureActive()
                    check(session === next && !next.cancelled)
                    next.echo = PreparedEcho(output, baseline, captured.deviceId, RecordingEchoFilter(result.model))
                    next.probe.clearSamples()
                    echoState.value = RecordingEchoStatus.Ready
                    if (debugBuild) Log.d("EolssuEcho", "calibration=accepted clips=${clips.size} frames=${captured.frames} similarity=${result.model.minimumSimilarity}")
                    accepted = true
                }
                is EchoCalibrationResult.Rejected -> if (session === next && !next.cancelled) {
                    echoState.value = result.reason
                    if (debugBuild) Log.d("EolssuEcho", "calibration=rejected reason=${result.reason}")
                }
            }
        } catch (cancelled: CancellationException) {
            if (session === preparing) echoState.value = RecordingEchoStatus.Off
            throw cancelled
        } catch (failure: Exception) {
            if (preparing == null || session === preparing) echoState.value = RecordingEchoStatus.Unavailable
            if (debugBuild) Log.d("EolssuEcho", "calibration=unavailable", failure)
        } finally {
            if (!accepted) output.retainCalibrationClock(false)
            output.stop()
            if (!accepted) preparing?.cancelled = true
        }
        accepted
    }

    override fun discardEchoCalibration() {
        session?.let { it.clearEcho(); if (it.warming) it.cancelled = true }
        echoState.value = RecordingEchoStatus.Off
    }

    override val pitchReadings = readings.asSharedFlow()
    override val captureBoundary: CaptureBoundary?
        get() = session?.let { it.boundary.copy(observedNanos = System.nanoTime(), analysisAvailable = it.analysisAvailable) }
    override val measuresLevel = true
    override val interrupted: Boolean get() = session?.failed == true

    override fun start() {
        session?.takeIf { it.warming && !it.cancelled && it.echo != null }?.let {
            it.command(Action.BeginRecording)
            return
        }
        echoState.value = RecordingEchoStatus.Off
        val next = synchronized(stateLock) {
            val previous = session
            previous?.cancelled = true
            Session(ids.incrementAndGet(), previous).also { session = it }
        }
        next.worker.start()
        next.await(next.ready)
    }

    override fun pause() { session?.command(Action.Pause) }
    override fun resume() { session?.command(Action.Resume) }
    override fun stop(): RecordedAudio? {
        val current = session ?: return null
        return runCatching {
            current.command(Action.Stop)
            current.await(current.result)?.also { completed = current.file }
        }.getOrNull().also {
            current.clearEcho()
            if (session === current) echoState.value = RecordingEchoStatus.Off
        }
    }
    override fun cancel() {
        synchronized(stateLock) { session?.let { it.cancelled = true; it.clearEcho() } }
        echoState.value = RecordingEchoStatus.Off
    }
    override fun level(): Float = session?.level ?: 0f

    private enum class Action { BeginRecording, Pause, Resume, Stop }
    private data class Command(val action: Action, val result: CompletableFuture<Unit>)
    private data class Window(val samples: FloatArray, val reading: PitchReading)

    private inner class Session(val id: Long, private val previous: Session?, @Volatile var warming: Boolean = false) {
        val file = File(directory, "practice-$id-${System.currentTimeMillis()}.m4a")
        val ready = CompletableFuture<Unit>()
        val result = CompletableFuture<RecordedAudio?>()
        val done = CountDownLatch(1)
        @Volatile var cancelled = false
        @Volatile var failed = false
        @Volatile var echo: PreparedEcho? = null
        val probe = EchoCaptureProbe()
        @Volatile var level = 0f
        @Volatile var boundary = CaptureBoundary(id, 0, 0, RATE, System.nanoTime(), false)
        private val commands = ArrayBlockingQueue<Command>(4)
        private val windows = ArrayBlockingQueue<Window>(4)
        @Volatile private var analyzing = true
        @Volatile var analysisAvailable = true
            private set
        private var sequence = 0L
        private var segment = 0L
        private var fileSamples = 0L
        private var segmentStart = 0L
        private var segmentNanos = 0L
        private var physicalFrames = 0L
        private var segmentPhysicalStart = 0L
        private var physicalZeroNanos = 0L
        private var lastTimestampNanos = 0L
        private var correctedBlocks = 0L
        private var examinedBlocks = 0L
        private var timestampValid = false
        private var fill = 0
        private val rolling = FloatArray(WINDOW + HOP)
        val worker = Thread({ capture() }, "practice-recording-$id")
        private val analysis = Thread({ analyze() }, "practice-pitch-$id")

        fun <T> await(future: CompletableFuture<T>): T = try {
            future.get(4, TimeUnit.SECONDS)
        } catch (e: Exception) { cancelled = true; throw e }

        fun command(action: Action) {
            check(!cancelled && !result.isDone) { "Recording is closed" }
            val response = CompletableFuture<Unit>()
            check(commands.offer(Command(action, response)))
            await(response)
        }

        private fun newSegment(input: AudioRecord) {
            if (!warming) segment++
            segmentStart = fileSamples
            fill = 0
            timestampValid = false
            physicalFrames = 0L
            segmentPhysicalStart = 0L
            lastTimestampNanos = 0L
            segmentNanos = System.nanoTime()
            boundary = CaptureBoundary(id, segment, segmentStart, RATE, segmentNanos, false)
            windows.clear()
            input.startRecording()
            check(input.recordingState == AudioRecord.RECORDSTATE_RECORDING)
        }

        private fun capture() {
            var input: AudioRecord? = null
            var writer: AndroidAacWriter? = null
            var keep = false
            try {
                check(previous == null || previous.done.await(3, TimeUnit.SECONDS)) { "Previous microphone still active" }
                check(!cancelled)
                check(directory.isDirectory || directory.mkdirs())
                completed?.delete()
                completed = null
                val minimum = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(minimum > 0)
                input = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(maxOf(minimum, HOP * 8)).build()
                check(input.state == AudioRecord.STATE_INITIALIZED && input.sampleRate == RATE)
                if (!warming) writer = AndroidAacWriter(file, RATE)
                check(!cancelled)
                newSegment(input)
                if (!warming) analysis.start()
                ready.complete(Unit)
                var paused = false
                var stopping = false
                val pcm = ShortArray(HOP)
                val timestamp = AudioTimestamp()
                while (!cancelled && !stopping) {
                    val command = if (paused) commands.poll(20, TimeUnit.MILLISECONDS) else commands.poll()
                    if (command != null) {
                        try {
                            when (command.action) {
                                Action.BeginRecording -> {
                                    check(warming && !paused)
                                    writer = AndroidAacWriter(file, RATE)
                                    warming = false
                                    segment++
                                    segmentStart = fileSamples
                                    segmentPhysicalStart = physicalFrames
                                    segmentNanos = physicalZeroNanos + physicalFrames * 1_000_000_000L / RATE
                                    boundary = CaptureBoundary(id, segment, segmentStart, RATE, segmentNanos, timestampValid)
                                    echoState.value = if (echo != null) RecordingEchoStatus.Active else RecordingEchoStatus.Off
                                    analysis.start()
                                }
                                Action.Pause -> if (!paused) {
                                    invalidateEcho()
                                    input.stop()
                                    paused = true
                                    fill = 0
                                    windows.clear()
                                    level = 0f
                                    boundary = CaptureBoundary(id, segment, fileSamples, RATE,
                                        segmentNanos + (fileSamples - segmentStart) * 1_000_000_000 / RATE, timestampValid)
                                }
                                Action.Resume -> if (paused) { newSegment(input); paused = false }
                                Action.Stop -> stopping = true
                            }
                            command.result.complete(Unit)
                        } catch (e: Exception) { command.result.completeExceptionally(e); throw e }
                        continue
                    }
                    if (paused) continue
                    val count = input.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                    check(count > 0) { "Microphone read failed: $count" }
                    if (cancelled) break
                    if (input.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
                        if (captureTimestampAligned(timestamp.framePosition, physicalFrames + count, input.bufferSizeInFrames)) {
                            physicalZeroNanos = timestamp.nanoTime - timestamp.framePosition * 1_000_000_000 / RATE
                            lastTimestampNanos = System.nanoTime()
                            segmentNanos = physicalZeroNanos + segmentPhysicalStart * 1_000_000_000L / RATE
                            timestampValid = true
                            boundary = CaptureBoundary(id, segment, segmentStart, RATE, segmentNanos, true)
                        } else if (timestampValid) {
                            // 녹음은 계속하고, 위치가 다시 맞을 때까지 실시간 음높이의 시각 기준으로 쓰지 않는다.
                            timestampValid = false
                            boundary = CaptureBoundary(id, segment, segmentStart, RATE, segmentNanos, false)
                        }
                    }
                    if (warming) {
                        probe.append(pcm, count, timestampValid.takeIf { it }?.let { physicalZeroNanos }, input.routedDevice,
                            retain = echo == null)
                        physicalFrames += count
                        continue
                    }
                    processEcho(pcm, count, input)
                    checkNotNull(writer).write(pcm, count)
                    physicalFrames += count
                    var peak = 0
                    repeat(count) { i ->
                        peak = maxOf(peak, abs(pcm[i].toInt()))
                        rolling[fill + i] = pcm[i] / 32768f
                    }
                    level = if (peak == 0) 0f else ((20 * log10(peak / 32768.0) + 50) / 50).toFloat().coerceIn(0f, 1f)
                    fileSamples += count
                    fill += count
                    while (fill >= WINDOW) {
                        val end = fileSamples - (fill - WINDOW)
                        val reading = PitchReading(id, segment, sequence++, end - WINDOW, end, RATE, null,
                            segmentNanos + (end - segmentStart) * 1_000_000_000 / RATE,
                            analysisAvailable = timestampValid)
                        if (analyzing && !windows.offer(Window(rolling.copyOfRange(0, WINDOW), reading))) {
                            windows.clear()
                            windows.offer(Window(FloatArray(0), reading.copy(analysisAvailable = false)))
                        }
                        rolling.copyInto(rolling, 0, HOP, fill)
                        fill -= HOP
                    }
                }
                if (!cancelled) {
                    if (!paused) input.stop()
                    writer?.finish()
                    keep = !cancelled && writer != null
                }
            } catch (e: Exception) {
                failed = !cancelled
                ready.completeExceptionally(e)
            } finally {
                analyzing = false
                windows.clear()
                runCatching { input?.stop() }
                runCatching { input?.release() }
                writer?.close()
                probe.clearSamples()
                if (debugBuild && writer != null) Log.d("EolssuEcho", "recording=$id applied=$correctedBlocks examined=$examinedBlocks frames=$fileSamples state=${echoState.value}")
                if (failed) invalidateEcho() else clearEcho()
                level = 0f
                if (!keep || cancelled) file.delete()
                while (true) {
                    val pending = commands.poll() ?: break
                    pending.result.completeExceptionally(IllegalStateException("Recording closed"))
                }
                ready.completeExceptionally(IllegalStateException("Recording cancelled"))
                result.complete(if (keep && !cancelled && file.length() > 0) RecordedAudio(file.absolutePath, file.length()) else null)
                done.countDown()
            }
        }

        private fun invalidateEcho() {
            if (echo != null) {
                clearEcho()
                if (session === this) echoState.value = RecordingEchoStatus.Changed
            }
        }

        fun clearEcho() {
            val previous = echo
            echo = null
            previous?.player?.retainCalibrationClock(false)
        }

        private fun processEcho(pcm: ShortArray, count: Int, input: AudioRecord) {
            val correction = echo ?: return
            val output = correction.player.outputSnapshot()
            if (!timestampValid || System.nanoTime() - lastTimestampNanos > 250_000_000L ||
                input.routedDevice?.id != correction.inputDeviceId || correction.player.interrupted ||
                !sameOutput(correction.output, output) || output.frameZeroNanos == null) {
                invalidateEcho()
                return
            }
            val shift = ((output.frameZeroNanos - physicalZeroNanos) * RATE / 1e9).roundToLong()
            val events = output.events.map { EchoPlaybackEvent(it.clip, it.frame + shift, it.gain) }
            examinedBlocks++
            if (correction.filter.process(pcm, count, physicalFrames, events)) correctedBlocks++
        }

        private fun analyze() {
            var gap = false
            val confirmation = PitchConfirmation()
            try {
                while (analyzing && !cancelled) {
                    val window = windows.poll(50, TimeUnit.MILLISECONDS) ?: continue
                    val estimate = if (window.reading.analysisAvailable) YinPitchEstimator.estimate(window.samples, RATE) else null
                    val value = confirmation.apply(window.reading.copy(frequencyHz = estimate?.frequencyHz,
                        rawFrequencyHz = estimate?.frequencyHz,
                        normalizedDifference = estimate?.normalizedDifference,
                        analysisAvailable = window.reading.analysisAvailable && !gap))
                    gap = !readings.tryEmit(value)
                    if (debugBuild && Log.isLoggable("EolssuPitch", Log.DEBUG)) {
                        Log.d("EolssuPitch", "recording=${value.recordingId} segment=${value.segmentId} " +
                            "sequence=${value.sequence} start=${value.startSample} end=${value.endSample} rate=${value.sampleRate} " +
                            "raw=${value.rawFrequencyHz} confirmed=${value.frequencyHz} available=${value.analysisAvailable} " +
                            "difference=${value.normalizedDifference} captured=${value.capturedEndNanos} " +
                            "observed=${System.nanoTime()} emitted=${!gap}")
                    }
                }
            } catch (_: Exception) {
                analyzing = false
                analysisAvailable = false
                readings.tryEmit(PitchReading(id, segment, sequence, fileSamples, fileSamples, RATE, null,
                    System.nanoTime(), analysisAvailable = false))
            }
        }
    }

    private companion object {
        const val RATE = 44_100
        const val WINDOW = 2048
        const val HOP = 882
    }
}

private data class PreparedEcho(
    val player: AndroidJangdanSamplePlayer,
    val output: JangdanOutputSnapshot,
    val inputDeviceId: Int?,
    val filter: RecordingEchoFilter,
)

private fun sameOutput(before: JangdanOutputSnapshot, now: JangdanOutputSnapshot): Boolean =
    before.epoch == now.epoch && before.volume == now.volume && before.deviceId == now.deviceId &&
        now.deviceType == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER && now.underruns == before.underruns && !now.clipped

private data class EchoCaptureSnapshot(
    val samples: FloatArray, val frames: Long, val zeroNanos: Long?, val deviceId: Int?, val deviceType: Int?,
)

private class EchoCaptureProbe {
    private val chunks = mutableListOf<FloatArray>()
    @Volatile var frames = 0L
        private set
    private var zeroNanos: Long? = null
    private var deviceId: Int? = null
    private var deviceType: Int? = null

    @Synchronized fun append(pcm: ShortArray, count: Int, zero: Long?, device: AudioDeviceInfo?, retain: Boolean) {
        check(frames + count < 70L * 44_100 || !retain) { "Echo calibration too long" }
        if (retain) chunks += FloatArray(count) { pcm[it] / 32768f }
        frames += count
        zeroNanos = zero
        deviceId = device?.id
        deviceType = device?.type
    }

    @Synchronized fun snapshot(includeSamples: Boolean): EchoCaptureSnapshot {
        val samples = if (includeSamples) FloatArray(chunks.sumOf { it.size }).also { output ->
            var offset = 0
            for (chunk in chunks) { chunk.copyInto(output, offset); offset += chunk.size }
        } else FloatArray(0)
        return EchoCaptureSnapshot(samples, frames, zeroNanos, deviceId, deviceType)
    }

    @Synchronized fun clearSamples() { chunks.clear() }
}

/**
 * 입력 장치가 잡은 위치([framePosition])가 앱이 읽은 양([readFrames])과 클라이언트 버퍼 안에서 맞아 시각 기준으로 쓸 수 있는지.
 * 녹음을 막 시작하면 장치가 먼저 잡아 둔 프레임이 버퍼보다 많을 수 있다(API 37 에뮬레이터 약 0.33초).
 * 이 프레임도 이어서 모두 읽히므로 맞지 않는다고 녹음을 끊지 않는다.
 */
internal fun captureTimestampAligned(framePosition: Long, readFrames: Long, bufferFrames: Int): Boolean =
    framePosition <= readFrames + bufferFrames

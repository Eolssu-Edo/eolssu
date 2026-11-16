package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.diagnostics.*
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import com.ssafy.eolssu.core.diagnostics.PerfPhase
import com.ssafy.eolssu.core.diagnostics.PerfTarget
import com.ssafy.eolssu.core.diagnostics.PerformanceFlow
import com.ssafy.eolssu.core.diagnostics.PerformanceLog
import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.CaptureBoundary
import com.ssafy.eolssu.core.audio.PitchConfirmation
import com.ssafy.eolssu.core.audio.PitchGate
import com.ssafy.eolssu.core.audio.PitchReading
import com.ssafy.eolssu.core.audio.RecordedAudio
import com.ssafy.eolssu.core.audio.YinPitchEstimator
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.log10
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 마이크 입력 하나를 m4a 저장과 별도 작업의 음높이 분석에 전달한다. */
class AndroidAudioRecorder(context: Context) : AudioRecorder {
    private val debugBuild = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val directory = File(context.applicationContext.cacheDir, "recordings")
    private val qaInputAllowed = debugBuild && context.packageName == "com.ssafy.eolssu.qa"
    private val qaInputFile = File(context.applicationContext.filesDir, "qa-live-input.pcm")
    private val readings = MutableSharedFlow<PitchReading>(extraBufferCapacity = 16)
    private val ids = AtomicLong()
    private val stateLock = Any()
    @Volatile private var session: Session? = null
    private var completed: File? = null

    override val pitchReadings = readings.asSharedFlow()
    override val diagnostics: Boolean get() = debugBuild
    @Volatile override var pitchGate: PitchGate = PitchGate.Default
    override val captureBoundary: CaptureBoundary?
        get() = session?.let { it.boundary.copy(observedNanos = System.nanoTime(), analysisAvailable = it.analysisAvailable) }
    override val measuresLevel = true
    override val interrupted: Boolean get() = session?.failed == true

    override fun start() {
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
        }.getOrNull()
    }
    override fun cancel() { synchronized(stateLock) { session?.cancelled = true } }
    override fun level(): Float = session?.level ?: 0f

    private enum class Action { Pause, Resume, Stop }
    private data class Command(val action: Action, val result: CompletableFuture<Unit>)
    private data class Window(val samples: FloatArray, val reading: PitchReading)

    private inner class Session(val id: Long, private val previous: Session?) {
        private val qaInteraction = UserActionLog.capture()
        private fun qa(outcome: QaOutcome) = UserActionLog.event(QaKind.RECORDING_STATE, QaTarget.RECORDING, outcome, qaInteraction)
        val file = File(directory, "practice-$id-${System.currentTimeMillis()}.m4a")
        val ready = CompletableFuture<Unit>()
        val result = CompletableFuture<RecordedAudio?>()
        val done = CountDownLatch(1)
        @Volatile var cancelled = false
        @Volatile var failed = false
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
        private var timestampValid = false
        private var fixedInput: QaPcmInput? = null
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

        private fun newSegment(input: AudioRecord?) {
            segment++
            segmentStart = fileSamples
            fill = 0
            timestampValid = fixedInput != null
            segmentNanos = fixedInput?.startSegment() ?: System.nanoTime()
            boundary = CaptureBoundary(id, segment, segmentStart, RATE, segmentNanos, timestampValid)
            windows.clear()
            if (input != null) {
                input.startRecording()
                check(input.recordingState == AudioRecord.RECORDSTATE_RECORDING)
            }
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
                // Check per recording, because the QA operator may stage input after opening the screen.
                fixedInput = if (qaInputAllowed && PerformanceLog.liveRequestsEnabled) QaPcmInput.open(qaInputFile, RATE) else null
                if (fixedInput == null) {
                    val minimum = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    check(minimum > 0)
                    input = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                        .setBufferSizeInBytes(maxOf(minimum, HOP * 8)).build()
                    check(input.state == AudioRecord.STATE_INITIALIZED && input.sampleRate == RATE)
                } else {
                    PerformanceVisit(PerformanceFlow(), PerfTarget.PRACTICE).milestone(PerfPhase.SYNTHETIC_INPUT)
                }
                writer = AndroidAacWriter(file, RATE)
                check(!cancelled)
                newSegment(input)
                analysis.start()
                qa(QaOutcome.STARTED)
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
                                Action.Pause -> if (!paused) {
                                    input?.stop()
                                    paused = true
                                    qa(QaOutcome.PAUSED)
                                    fill = 0
                                    windows.clear()
                                    level = 0f
                                    boundary = CaptureBoundary(id, segment, fileSamples, RATE,
                                        segmentNanos + (fileSamples - segmentStart) * 1_000_000_000 / RATE, timestampValid)
                                }
                                Action.Resume -> if (paused) { newSegment(input); paused = false; qa(QaOutcome.STARTED) }
                                Action.Stop -> stopping = true
                            }
                            command.result.complete(Unit)
                        } catch (e: Exception) { command.result.completeExceptionally(e); throw e }
                        continue
                    }
                    if (paused) continue
                    val count = fixedInput?.read(pcm)
                        ?: checkNotNull(input).read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                    check(count > 0) { "Microphone read failed: $count" }
                    if (cancelled) break
                    if (input != null && input.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
                        if (captureTimestampAligned(timestamp.framePosition, fileSamples - segmentStart + count, input.bufferSizeInFrames)) {
                            segmentNanos = timestamp.nanoTime - timestamp.framePosition * 1_000_000_000 / RATE
                            timestampValid = true
                            boundary = CaptureBoundary(id, segment, segmentStart, RATE, segmentNanos, true)
                        } else if (timestampValid) {
                            // 녹음은 계속하고, 위치가 다시 맞을 때까지 실시간 음높이의 시각 기준으로 쓰지 않는다.
                            timestampValid = false
                            boundary = CaptureBoundary(id, segment, segmentStart, RATE, segmentNanos, false)
                        }
                    }
                    writer.write(pcm, count)
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
                    if (!paused) input?.stop()
                    writer.finish()
                    keep = !cancelled
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
                level = 0f
                if (!keep || cancelled) file.delete()
                while (true) {
                    val pending = commands.poll() ?: break
                    pending.result.completeExceptionally(IllegalStateException("Recording closed"))
                }
                ready.completeExceptionally(IllegalStateException("Recording cancelled"))
                val recorded = if (keep && !cancelled && file.length() > 0) RecordedAudio(file.absolutePath, file.length()) else null
                qa(if (cancelled) QaOutcome.CANCELLED else if (recorded != null) QaOutcome.COMPLETED else QaOutcome.FAILED)
                result.complete(recorded)
                done.countDown()
            }
        }

        private fun analyze() {
            var gap = false
            val confirmation = PitchConfirmation()
            try {
                while (analyzing && !cancelled) {
                    val window = windows.poll(50, TimeUnit.MILLISECONDS) ?: continue
                    val estimate = if (window.reading.analysisAvailable) YinPitchEstimator.estimate(window.samples, RATE, pitchGate) else null
                    val value = confirmation.apply(window.reading.copy(frequencyHz = estimate?.frequencyHz,
                        rawFrequencyHz = estimate?.frequencyHz,
                        normalizedDifference = estimate?.normalizedDifference,
                        analysisAvailable = window.reading.analysisAvailable && !gap,
                        inputDbfs = if (window.reading.analysisAvailable) YinPitchEstimator.rmsDbfs(window.samples) else null))
                    gap = !readings.tryEmit(value)
                    if (debugBuild && Log.isLoggable("EolssuPitch", Log.DEBUG)) {
                        Log.d("EolssuPitch", "recording=${value.recordingId} segment=${value.segmentId} " +
                            "sequence=${value.sequence} start=${value.startSample} end=${value.endSample} rate=${value.sampleRate} " +
                            "raw=${value.rawFrequencyHz} confirmed=${value.frequencyHz} available=${value.analysisAvailable} " +
                            "difference=${value.normalizedDifference} dbfs=${value.inputDbfs} captured=${value.capturedEndNanos} " +
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

/**
 * 입력 장치가 잡은 위치([framePosition])가 앱이 읽은 양([readFrames])과 클라이언트 버퍼 안에서 맞아 시각 기준으로 쓸 수 있는지.
 * 녹음을 막 시작하면 장치가 먼저 잡아 둔 프레임이 버퍼보다 많을 수 있다(API 37 에뮬레이터 약 0.33초).
 * 이 프레임도 이어서 모두 읽히므로 맞지 않는다고 녹음을 끊지 않는다.
 */
internal fun captureTimestampAligned(framePosition: Long, readFrames: Long, bufferFrames: Int): Boolean =
    framePosition <= readFrames + bufferFrames

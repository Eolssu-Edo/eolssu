package com.ssafy.eolssu.platform.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.CaptureBoundary
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
    private val directory = File(context.applicationContext.cacheDir, "recordings")
    private val readings = MutableSharedFlow<PitchReading>(extraBufferCapacity = 16)
    private val ids = AtomicLong()
    private val stateLock = Any()
    @Volatile private var session: Session? = null
    private var completed: File? = null

    override val pitchReadings = readings.asSharedFlow()
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
            segment++
            segmentStart = fileSamples
            fill = 0
            timestampValid = false
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
                writer = AndroidAacWriter(file, RATE)
                check(!cancelled)
                newSegment(input)
                analysis.start()
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
                        check(timestamp.framePosition <= fileSamples - segmentStart + count + input.bufferSizeInFrames) {
                            "Microphone input overrun"
                        }
                        segmentNanos = timestamp.nanoTime - timestamp.framePosition * 1_000_000_000 / RATE
                        timestampValid = true
                        boundary = CaptureBoundary(id, segment, segmentStart, RATE, segmentNanos, true)
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
                    if (!paused) input.stop()
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
                result.complete(if (keep && !cancelled && file.length() > 0) RecordedAudio(file.absolutePath, file.length()) else null)
                done.countDown()
            }
        }

        private fun analyze() {
            var gap = false
            try {
                while (analyzing && !cancelled) {
                    val window = windows.poll(50, TimeUnit.MILLISECONDS) ?: continue
                    val estimate = if (window.reading.analysisAvailable) YinPitchEstimator.estimate(window.samples, RATE) else null
                    val value = window.reading.copy(frequencyHz = estimate?.frequencyHz,
                        normalizedDifference = estimate?.normalizedDifference,
                        analysisAvailable = window.reading.analysisAvailable && !gap)
                    gap = !readings.tryEmit(value)
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

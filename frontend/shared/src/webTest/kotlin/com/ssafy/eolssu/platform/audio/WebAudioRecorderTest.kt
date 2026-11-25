package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.PitchReading
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/** 브라우저 마이크 대신 [FakeMic]으로 입력 묶음을 넣어 웹 녹음기의 파일·관측·자원 정리를 검사한다(S15P31E102-1262). */
class WebAudioRecorderTest {
    private class FakeMic(override val sampleRate: Int, val onChunk: (FloatArray, Long) -> Unit, val onStopped: () -> Unit) : WebMicInput {
        override var live = true
        var connected = false
        var closed = false
        override fun connect(on: Boolean) { connected = on }
        override fun close() { closed = true; live = false }
    }

    private var now = 0L
    private var failure: Exception? = null
    private val mics = mutableListOf<FakeMic>()
    private val store = WebRecordingStore()
    private val recorder = WebAudioRecorder(store, object : WebMicOpener {
        override suspend fun open(onChunk: (FloatArray, Long) -> Unit, onStopped: () -> Unit): WebMicInput {
            failure?.let { throw it }
            return FakeMic(RATE, onChunk, onStopped).also { mics += it }
        }
    }) { now }

    private val mic get() = mics.last()

    /** [seconds]초의 [hz] 사인파를 1024샘플 묶음으로 넣는다. 묶음 끝 시각은 입력 시계로 흐르고 처리는 바로 한다. */
    private fun play(seconds: Double, hz: Double = 440.0) {
        val total = (RATE * seconds).toInt()
        var sent = 0
        while (sent < total) {
            val count = minOf(CHUNK, total - sent)
            val chunk = FloatArray(count) { (0.4 * sin(2 * PI * hz * (played + it) / RATE)).toFloat() }
            played += count
            sent += count
            now = played * 1_000_000_000L / RATE
            if (mic.connected && !mic.closed) mic.onChunk(chunk, now)
        }
    }

    private var played = 0L

    @Test
    fun startNeedsAPreparedMicrophone() {
        assertFailsWith<IllegalStateException> { recorder.start() }
    }

    @Test
    fun stopReturnsA16kHzWavKeptInMemoryAndClosesTheMicrophone() = runTest {
        recorder.prepare()
        recorder.start()
        assertTrue(mic.connected)
        play(1.0)

        val audio = assertNotNull(recorder.stop())

        assertTrue(audio.path.endsWith(".wav"))
        val wav = assertNotNull(store[audio.path])
        assertEquals(wav.size.toLong(), audio.bytes)
        assertEquals(16_000, int32(wav, 24))
        assertTrue(abs((wav.size - 44) / 2 - 16_000) <= 1, "samples ${(wav.size - 44) / 2}")
        assertTrue(mic.closed)
    }

    @Test
    fun readingsUseInputSamplePositionsAndCaptureTimes() = runTest {
        val readings = mutableListOf<PitchReading>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { recorder.pitchReadings.collect { readings += it } }
        recorder.prepare()
        recorder.start()
        play(0.5)

        val first = readings.first()
        assertEquals(RATE, first.sampleRate)
        assertEquals(2229L, first.endSample - first.startSample) // 44.1kHz의 2048샘플과 같은 길이
        assertTrue(abs(first.endSample * 1_000_000_000L / RATE - first.capturedEndNanos) <= 1, "captured ${first.capturedEndNanos}")
        assertEquals(960L, readings[1].endSample - first.endSample) // 20ms 간격
        val heard = readings.mapNotNull { it.frequencyHz }
        assertTrue(heard.isNotEmpty())
        assertTrue(heard.all { abs(it - 440.0) < 5 }, "heard $heard")
        assertTrue(recorder.level() > 0.5f)
        assertEquals(true, recorder.captureBoundary?.timestampValid)
        recorder.cancel()
    }

    @Test
    fun lateChunksAreReportedAsUnavailableInsteadOfAnalyzed() = runTest {
        val readings = mutableListOf<PitchReading>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { recorder.pitchReadings.collect { readings += it } }
        recorder.prepare()
        recorder.start()
        mic.onChunk(FloatArray(RATE / 2) { (0.4 * sin(2 * PI * 440.0 * it / RATE)).toFloat() }, 0L)
        now = 1_000_000_000L // 묶음이 1초 늦게 왔다.
        mic.onChunk(FloatArray(RATE / 2) { (0.4 * sin(2 * PI * 440.0 * it / RATE)).toFloat() }, 500_000_000L)

        assertTrue(readings.size > 30)
        assertTrue(readings.take(5).any { it.analysisAvailable })
        assertTrue(readings.takeLast(10).all { !it.analysisAvailable && it.frequencyHz == null })
        recorder.cancel()
    }

    @Test
    fun pauseStopsInputAndResumeStartsANewSegment() = runTest {
        val readings = mutableListOf<PitchReading>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { recorder.pitchReadings.collect { readings += it } }
        recorder.prepare()
        recorder.start()
        play(0.2)
        recorder.pause()
        assertFalse(mic.connected)
        assertEquals(0f, recorder.level())
        recorder.resume()
        assertTrue(mic.connected)
        play(0.2)

        assertEquals(setOf(1L, 2L), readings.map { it.segmentId }.toSet())
        assertNotNull(recorder.stop())
    }

    @Test
    fun deviceThatStopsMarksTheTakeInterruptedAndDropsIt() = runTest {
        recorder.prepare()
        recorder.start()
        play(0.2)
        mic.onStopped()

        assertTrue(recorder.interrupted)
        assertTrue(mic.closed)
        assertFailsWith<IllegalStateException> { recorder.resume() }
        assertNull(recorder.stop())
        assertTrue(store.keys().isEmpty())
    }

    @Test
    fun cancelClosesAPreparedButUnstartedMicrophone() = runTest {
        recorder.prepare()
        recorder.cancel()

        assertTrue(mic.closed)
        assertFailsWith<IllegalStateException> { recorder.start() }
    }

    @Test
    fun microphoneFailureIsThrownFromPrepare() = runTest {
        failure = WebMicException("NotAllowedError")

        assertEquals("NotAllowedError", assertFailsWith<WebMicException> { recorder.prepare() }.name)
        assertFailsWith<IllegalStateException> { recorder.start() }
    }

    @Test
    fun newRecordingDropsTheLastUnkeptTakeButNotAKeptOne() = runTest {
        val files = WebRecordingFiles(store)
        suspend fun record(): String {
            recorder.prepare()
            recorder.start()
            play(0.1)
            return assertNotNull(recorder.stop()).path
        }
        val kept = assertNotNull(files.keep(record()))
        val unkept = record()
        recorder.prepare()
        recorder.start()

        assertTrue(files.exists(kept))
        assertFalse(files.exists(unkept))
        recorder.cancel()
    }

    private fun int32(bytes: ByteArray, offset: Int) = (0..3).sumOf { (bytes[offset + it].toInt() and 0xFF) shl (8 * it) }

    private companion object {
        const val RATE = 48_000
        const val CHUNK = 1024
    }
}

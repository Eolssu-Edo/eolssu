package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.CaptureBoundary
import com.ssafy.eolssu.core.audio.PcmWavWriter
import com.ssafy.eolssu.core.audio.PitchConfirmation
import com.ssafy.eolssu.core.audio.PitchGate
import com.ssafy.eolssu.core.audio.PitchReading
import com.ssafy.eolssu.core.audio.PitchWindows
import com.ssafy.eolssu.core.audio.RecordedAudio
import com.ssafy.eolssu.core.audio.YinPitchEstimator
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 열린 마이크 입력 하나. 브라우저 구현은 [BrowserMicOpener], 테스트는 대역을 넣는다. */
internal interface WebMicInput {
    val sampleRate: Int

    /** 장치가 아직 소리를 보내는지(트랙이 살아 있고 오디오 처리가 돌고 있음). */
    val live: Boolean

    /** 입력을 녹음 처리에 잇거나 끊는다. 끊긴 동안은 입력 묶음이 오지 않는다. */
    fun connect(on: Boolean)

    /** 트랙과 오디오 처리를 닫는다. 여러 번 불러도 된다. */
    fun close()
}

/** 마이크를 연다. 열지 못하면(권한 거부·장치 없음·오디오 처리 시작 실패) 예외를 던진다. */
internal interface WebMicOpener {
    /**
     * [onChunk]는 입력 묶음(샘플, 마지막 샘플이 들어온 시각 ns, [browserNanos]와 같은 시계)을, [onStopped]는 장치가 끊긴 것
     * (트랙 종료·권한 회수·오디오 처리 중단)을 알린다. 둘 다 [WebMicInput.close] 뒤에는 오지 않는다.
     */
    suspend fun open(onChunk: (FloatArray, Long) -> Unit, onStopped: () -> Unit): WebMicInput
}

/**
 * 브라우저 녹음(S15P31E102-1262). getUserMedia·AudioWorklet로 받은 입력을 16kHz 모노 WAV로 메모리에 쌓고,
 * 입력 샘플레이트(보통 48kHz) 그대로 Android·iOS와 같은 길이(약 46ms)·간격(20ms)의 창으로 음높이를 잰다.
 * 장치는 [prepare]가 열고 [stop]·[cancel]이 닫는다. 분석은 입력 묶음을 받는 메인 스레드에서 하며, 처리가 밀리면 창을 분석 불가로 보낸다.
 * 업로드할 WAV는 [WebRecordingStore]에 두고 [RecordedAudio.path]는 그 키다. 서버가 WAV를 AAC m4a로 바꿔 저장한다(S15P31E102-1272).
 */
class WebAudioRecorder internal constructor(
    private val store: WebRecordingStore,
    private val opener: WebMicOpener,
    private val nanos: () -> Long,
) : AudioRecorder {
    constructor(store: WebRecordingStore) : this(store, BrowserMicOpener, ::browserNanos)

    private val readings = MutableSharedFlow<PitchReading>(extraBufferCapacity = 16)
    private var mic: WebMicInput? = null
    private var micGeneration = 0
    private var take: Take? = null
    private var completedKey: String? = null
    private var ids = 0L

    override val pitchReadings: Flow<PitchReading> = readings.asSharedFlow()
    override var pitchGate: PitchGate = PitchGate.Default
    override val captureBoundary: CaptureBoundary? get() = take?.boundary?.copy(observedNanos = nanos())
    override val measuresLevel = true
    override val interrupted: Boolean get() = take?.interrupted == true

    override suspend fun prepare() {
        if (mic?.live == true) return
        closeMic()
        val generation = micGeneration
        mic = opener.open(
            onChunk = { samples, endNanos -> if (generation == micGeneration) take?.accept(samples, endNanos) },
            onStopped = { if (generation == micGeneration) onStopped() },
        )
    }

    override fun start() {
        val input = checkNotNull(mic?.takeIf { it.live }) { "Microphone is not ready" }
        take = null
        completedKey?.let(store::remove)
        completedKey = null
        take = Take(++ids, input.sampleRate).also { it.beginSegment() }
        input.connect(true)
    }

    override fun pause() {
        val current = take ?: return
        if (current.paused) return
        mic?.connect(false)
        current.pause()
    }

    override fun resume() {
        val current = checkNotNull(take) { "Recording is closed" }
        val input = mic
        check(!current.interrupted && input != null && input.live) { "Microphone is gone" }
        if (!current.paused) return
        current.beginSegment()
        input.connect(true)
    }

    override fun stop(): RecordedAudio? {
        val current = take ?: return null
        take = null
        closeMic()
        if (current.interrupted || current.inputSamples == 0L) return null
        val wav = current.finish()
        val key = "web-recording-${current.id}-${Clock.System.now().toEpochMilliseconds()}.wav"
        store.put(key, wav)
        completedKey = key
        return RecordedAudio(key, wav.size.toLong())
    }

    /** 진행 중인 녹음을 버리고, [prepare]만 하고 시작하지 않은 장치도 닫는다. */
    override fun cancel() {
        take = null
        closeMic()
    }

    override fun level(): Float = take?.level ?: 0f

    private fun onStopped() {
        take?.interrupted = true
        closeMic()
    }

    private fun closeMic() {
        micGeneration++
        val current = mic ?: return
        mic = null
        runCatching { current.close() }
    }

    private inner class Take(val id: Long, private val rate: Int) {
        private val windowSize = (WINDOW_44K * rate / 44_100L).toInt()
        private val writer = PcmWavWriter(rate)
        private val windows = PitchWindows(windowSize, maxOf(1, rate / HOPS_PER_SECOND))
        private val confirmation = PitchConfirmation()
        private var segment = 0L
        private var segmentStart = 0L
        private var sequence = 0L
        private var anchored = false
        private var gap = false
        var paused = true
            private set
        var interrupted = false
        var level = 0f
            private set
        var boundary = CaptureBoundary(id, 0, 0, rate, nanos(), false)
            private set
        val inputSamples: Long get() = windows.fileSamples

        fun beginSegment() {
            segment++
            segmentStart = windows.fileSamples
            windows.reset()
            anchored = false
            paused = false
            boundary = CaptureBoundary(id, segment, segmentStart, rate, nanos(), false)
        }

        fun pause() {
            paused = true
            level = 0f
            windows.reset()
            boundary = boundary.copy(samplePosition = windows.fileSamples)
        }

        fun finish(): ByteArray = writer.finish()

        /** 입력 묶음 하나. [endNanos]는 마지막 샘플의 입력 시각이고, 묶음 안의 위치는 샘플레이트로 거꾸로 센다. */
        fun accept(samples: FloatArray, endNanos: Long) {
            if (paused || samples.isEmpty()) return
            val chunkEnd = windows.fileSamples + samples.size
            if (!anchored) {
                anchored = true
                val segmentNanos = endNanos - (chunkEnd - segmentStart) * NANOS_PER_SECOND / rate
                boundary = CaptureBoundary(id, segment, segmentStart, rate, segmentNanos, true)
            }
            writer.push(samples)
            // 메인 스레드가 밀려 묶음이 늦게 오면 실시간 표시에 쓸 수 없으므로 높이를 구하지 않고 분석 불가로 보낸다.
            val analyze = nanos() - endNanos <= MAX_ANALYSIS_LAG_NANOS
            var peak = 0f
            windows.push(samples.size, { i -> samples[i].also { peak = max(peak, abs(it)) } }) { end, window ->
                emit(end, window, endNanos - (chunkEnd - end) * NANOS_PER_SECOND / rate, analyze)
            }
            level = if (peak <= 0f) 0f else ((20 * log10(peak.toDouble()) + 50) / 50).toFloat().coerceIn(0f, 1f)
        }

        private fun emit(end: Long, window: FloatArray, capturedNanos: Long, analyze: Boolean) {
            val estimate = if (analyze) YinPitchEstimator.estimate(window, rate, pitchGate) else null
            val reading = confirmation.apply(
                PitchReading(id, segment, sequence++, end - windowSize, end, rate, estimate?.frequencyHz, capturedNanos,
                    normalizedDifference = estimate?.normalizedDifference,
                    analysisAvailable = analyze && !gap,
                    rawFrequencyHz = estimate?.frequencyHz,
                    inputDbfs = if (analyze) YinPitchEstimator.rmsDbfs(window) else null),
            )
            gap = !readings.tryEmit(reading)
        }
    }

    private companion object {
        /** Android 녹음기의 창(44.1kHz에서 2048샘플, 약 46ms). 입력 샘플레이트에 비례해 늘린다. */
        const val WINDOW_44K = 2048L

        /** 창 간격 20ms(Android 44.1kHz의 882샘플). */
        const val HOPS_PER_SECOND = 50
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val MAX_ANALYSIS_LAG_NANOS = 250_000_000L
    }
}

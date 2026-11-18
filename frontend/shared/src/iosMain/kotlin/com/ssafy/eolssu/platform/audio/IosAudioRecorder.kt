package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.CaptureBoundary
import com.ssafy.eolssu.core.audio.PitchConfirmation
import com.ssafy.eolssu.core.audio.PitchGate
import com.ssafy.eolssu.core.audio.PitchReading
import com.ssafy.eolssu.core.audio.RecordedAudio
import com.ssafy.eolssu.core.audio.YinPitchEstimator
import kotlin.concurrent.Volatile
import kotlin.experimental.ExperimentalNativeApi
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.pointed
import kotlinx.cinterop.value
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioConverter
import platform.AVFAudio.AVAudioConverterInputStatus_HaveData
import platform.AVFAudio.AVAudioConverterInputStatus_NoDataNow
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.AVFAudio.AVAudioTime
import platform.AVFAudio.AVEncoderBitRateKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSLock
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970
import platform.QuartzCore.CACurrentMediaTime

/**
 * `AVAudioEngine`의 마이크 입력 하나를 모노·AAC 128kbps·44.1kHz m4a 저장(PRA-02)과 실시간 음높이 분석에 함께 쓴다.
 * 입력은 기기 형식(보통 48kHz)에서 44.1kHz 모노로 바꾼 뒤 파일에 쓰고, Android 녹음기와 같은 창·간격으로 [YinPitchEstimator]에 넘긴다.
 * 파일은 캐시의 `recordings/`에 두고 마지막으로 마친 녹음 하나만 남긴다(업로드할 녹음은 [IosRecordingFiles]가 옮겨 둔다).
 * start·pause·resume·stop·cancel은 메인 스레드에서 부르고, 입력 처리는 엔진의 tap 스레드, 분석은 별도 작업에서 한다.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
class IosAudioRecorder : AudioRecorder {
    private val files = NSFileManager.defaultManager
    private val directory = (NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String) +
        "/" + DIRECTORY
    private val readings = MutableSharedFlow<PitchReading>(extraBufferCapacity = 16)
    private var nextId = 0L
    @Volatile private var session: Session? = null

    override val pitchReadings: Flow<PitchReading> = readings.asSharedFlow()
    override val diagnostics: Boolean = Platform.isDebugBinary
    @Volatile override var pitchGate: PitchGate = PitchGate.Default
    override val captureBoundary: CaptureBoundary? get() = session?.boundary()
    override val measuresLevel: Boolean = true

    /** 일시정지하지 않았는데 엔진이 멈췄으면 시스템(전화·Siri·다른 앱의 녹음·입력 장치 변경)이 끊은 것이다. iOS는 끊긴 녹음을 다시 잇지 않는다. */
    override val interrupted: Boolean get() = session?.interrupted == true

    override fun start() {
        cancel()
        files.removeItemAtPath(directory, error = null)
        check(files.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)) {
            "녹음 폴더를 만들지 못했어요"
        }
        val path = "$directory/practice-${(NSDate().timeIntervalSince1970 * 1000).toLong()}.m4a"
        val next = Session(++nextId, path)
        // 세션을 녹음용으로 바꾼 뒤 어느 단계에서 실패해도 세션을 되돌려야 재생기가 다시 세션을 쓸 수 있다.
        try {
            next.open()
        } catch (e: Throwable) {
            next.close(keep = false)
            throw e
        }
        session = next
    }

    override fun pause() {
        session?.pause()
    }

    override fun resume() {
        session?.resume()
    }

    override fun stop(): RecordedAudio? {
        val current = session ?: return null
        session = null
        current.close(keep = true)
        val bytes = fileSize(current.path)
        if (bytes <= 0L) {
            files.removeItemAtPath(current.path, error = null)
            return null
        }
        return RecordedAudio(current.path, bytes)
    }

    override fun cancel() {
        val current = session ?: return
        session = null
        current.close(keep = false)
    }

    override fun level(): Float = session?.level ?: 0f

    private fun fileSize(path: String): Long =
        (files.attributesOfItemAtPath(path, error = null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L

    private class Window(val samples: FloatArray, val reading: PitchReading)

    private inner class Session(private val id: Long, val path: String) {
        private val engine = AVAudioEngine()
        private val lock = NSLock()
        private val windows = PitchWindows(WINDOW, HOP)
        private val queue = Channel<Window>(capacity = 4)
        private val analysis = CoroutineScope(Dispatchers.Default).launch { analyze() }
        private var converter: AVAudioConverter? = null
        private var file: AVAudioFile? = null
        private var ownsAudioSession = false
        private var tapInstalled = false

        // 아래 값은 [lock] 안에서만 바꾼다.
        private var open = false
        private var paused = false
        private var segment = 0L
        private var segmentStart = 0L
        private var segmentNanos = 0L
        private var anchored = false
        private var timestampValid = false
        private var sequence = 0L
        private var dropped = false

        @Volatile private var bound = CaptureBoundary(id, 0, 0, RATE, nowNanos(), false)
        @Volatile private var failed = false
        @Volatile private var analysisAvailable = true
        @Volatile var level = 0f
            private set

        val interrupted: Boolean get() = failed || (open && !paused && !engine.running)

        fun boundary(): CaptureBoundary = bound.copy(observedNanos = nowNanos(), analysisAvailable = analysisAvailable)

        fun open() {
            ownsAudioSession = true
            check(IosAudioSession.startRecording()) { "녹음 세션을 켜지 못했어요" }
            val input = engine.inputNode
            val inputFormat = input.outputFormatForBus(0u)
            check(inputFormat.sampleRate > 0.0 && inputFormat.channelCount > 0u) { "마이크 입력을 찾지 못했어요" }
            val target = checkNotNull(
                AVAudioFormat(commonFormat = AVAudioPCMFormatFloat32, sampleRate = RATE.toDouble(), channels = 1u, interleaved = false),
            )
            converter = checkNotNull(AVAudioConverter(fromFormat = inputFormat, toFormat = target)) { "녹음 형식을 바꾸지 못했어요" }
            val settings = mapOf<Any?, Any?>(
                AVFormatIDKey to NSNumber(unsignedInt = kAudioFormatMPEG4AAC),
                AVSampleRateKey to RATE.toDouble(),
                AVNumberOfChannelsKey to 1,
                AVEncoderBitRateKey to BIT_RATE,
            )
            val output = AVAudioFile(forWriting = NSURL.fileURLWithPath(path), settings = settings,
                commonFormat = AVAudioPCMFormatFloat32, interleaved = false, error = null)
            check(output.processingFormat.sampleRate == RATE.toDouble() && output.processingFormat.channelCount == 1u) {
                "녹음 파일을 만들지 못했어요"
            }
            file = output
            input.installTapOnBus(0u, bufferSize = TAP_FRAMES, format = inputFormat) { buffer, time ->
                if (buffer != null) process(buffer, time)
            }
            tapInstalled = true
            locked {
                open = true
                newSegment()
            }
            engine.prepare()
            check(engine.startAndReturnError(null)) { "녹음을 시작하지 못했어요" }
        }

        fun pause() {
            locked {
                if (!open || paused) return
                paused = true
                windows.reset()
                level = 0f
                bound = CaptureBoundary(id, segment, windows.fileSamples, RATE, nanosAt(windows.fileSamples), timestampValid)
            }
            engine.pause()
        }

        fun resume() {
            locked {
                if (!open || !paused) return
                newSegment()
                paused = false
            }
            check(engine.startAndReturnError(null)) { "녹음을 이어 가지 못했어요" }
        }

        fun close(keep: Boolean) {
            locked { open = false }
            if (tapInstalled) engine.inputNode.removeTapOnBus(0u)
            tapInstalled = false
            engine.stop()
            queue.close()
            analysis.cancel()
            // tap이 쓰는 중일 수 있어 잠근 뒤 파일을 닫는다. 닫아야 AAC 끝부분과 길이 정보가 기록된다.
            locked {
                file?.close()
                file = null
                converter = null
            }
            if (ownsAudioSession) IosAudioSession.stopRecording()
            ownsAudioSession = false
            level = 0f
            if (!keep) files.removeItemAtPath(path, error = null)
        }

        /** 입력이 다시 시작되는 구간. 첫 입력 묶음의 시각으로 기준을 잡을 때까지 시각 기준은 무효다. */
        private fun newSegment() {
            segment++
            segmentStart = windows.fileSamples
            windows.reset()
            converter?.reset()
            anchored = false
            timestampValid = false
            segmentNanos = nowNanos()
            bound = CaptureBoundary(id, segment, segmentStart, RATE, segmentNanos, false)
        }

        private fun process(buffer: AVAudioPCMBuffer, time: AVAudioTime?) {
            locked {
                if (!open || paused || failed) return
                val convert = converter ?: return
                val output = file ?: return
                val capacity = ceil(buffer.frameLength.toDouble() * RATE / buffer.format.sampleRate).toInt() + CONVERTER_SLACK
                val converted = AVAudioPCMBuffer(pCMFormat = convert.outputFormat, frameCapacity = capacity.toUInt())
                var supplied = false
                convert.convertToBuffer(converted, error = null) { _, status ->
                    if (supplied) {
                        status?.pointed?.value = AVAudioConverterInputStatus_NoDataNow
                        null
                    } else {
                        supplied = true
                        status?.pointed?.value = AVAudioConverterInputStatus_HaveData
                        buffer
                    }
                }
                val count = converted.frameLength.toInt()
                if (count == 0) return
                if (!anchored) {
                    anchored = true
                    if (time != null && time.hostTimeValid) {
                        segmentNanos = hostNanos(time.hostTime) - (windows.fileSamples - segmentStart) * NANOS_PER_SECOND / RATE
                        timestampValid = true
                    }
                    bound = CaptureBoundary(id, segment, segmentStart, RATE, segmentNanos, timestampValid)
                }
                if (!output.writeFromBuffer(converted, error = null)) {
                    failed = true
                    return
                }
                val samples = checkNotNull(converted.floatChannelData)[0]!!
                var peak = 0f
                windows.push(count, { i -> samples[i].also { peak = max(peak, abs(it)) } }) { end, window -> offer(end, window) }
                level = if (peak <= 0f) 0f else ((20 * log10(peak.toDouble()) - SILENCE_DBFS) / -SILENCE_DBFS).toFloat().coerceIn(0f, 1f)
            }
        }

        private fun offer(end: Long, samples: FloatArray) {
            val reading = PitchReading(id, segment, sequence++, end - WINDOW, end, RATE, null, nanosAt(end),
                analysisAvailable = timestampValid)
            // 분석이 밀려 창을 버리면 다음 창을 분석 불가로 보내 화면이 빈 구간을 알게 한다(Android와 같은 처리).
            val window = if (dropped) Window(FloatArray(0), reading.copy(analysisAvailable = false)) else Window(samples, reading)
            dropped = queue.trySend(window).isFailure
        }

        private suspend fun analyze() {
            var gap = false
            val confirmation = PitchConfirmation()
            val probe = if (diagnostics) IosVolumeProbe() else null
            try {
                for (window in queue) {
                    val reading = window.reading
                    val usable = reading.analysisAvailable && window.samples.isNotEmpty()
                    val estimate = if (usable) YinPitchEstimator.estimate(window.samples, RATE, pitchGate) else null
                    val dbfs = if (usable) YinPitchEstimator.rmsDbfs(window.samples) else null
                    dbfs?.let { probe?.add(it) }
                    val value = confirmation.apply(reading.copy(frequencyHz = estimate?.frequencyHz,
                        rawFrequencyHz = estimate?.frequencyHz,
                        normalizedDifference = estimate?.normalizedDifference,
                        analysisAvailable = reading.analysisAvailable && !gap,
                        inputDbfs = dbfs))
                    gap = !readings.tryEmit(value)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                analysisAvailable = false
                val last = bound
                readings.tryEmit(PitchReading(id, last.segmentId, Long.MAX_VALUE, last.samplePosition, last.samplePosition, RATE,
                    null, nowNanos(), analysisAvailable = false))
            }
        }

        private fun nanosAt(samplePosition: Long): Long = segmentNanos + (samplePosition - segmentStart) * NANOS_PER_SECOND / RATE

        private inline fun <T> locked(block: () -> T): T {
            lock.lock()
            try {
                return block()
            } finally {
                lock.unlock()
            }
        }
    }

    private companion object {
        const val DIRECTORY = "recordings"
        const val RATE = 44_100
        const val BIT_RATE = 128_000
        const val WINDOW = 2048
        const val HOP = 882
        const val TAP_FRAMES = 4096u
        const val CONVERTER_SLACK = 64
        const val SILENCE_DBFS = -50.0
        const val NANOS_PER_SECOND = 1_000_000_000L

        fun hostNanos(hostTime: ULong): Long = (AVAudioTime.secondsForHostTime(hostTime) * NANOS_PER_SECOND).toLong()

        /** 엔진 입력 시각(host time)과 같은 시계(`mach_absolute_time`)의 현재 시각. */
        fun nowNanos(): Long = (CACurrentMediaTime() * NANOS_PER_SECOND).toLong()
    }
}

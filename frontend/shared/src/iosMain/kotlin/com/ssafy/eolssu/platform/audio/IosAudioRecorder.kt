package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.RecordedAudio
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVEncoderBitRateKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970

/**
 * `AVAudioRecorder`로 모노·AAC 128kbps·44.1kHz m4a를 캐시의 `recordings/`에 녹음한다.
 * 마지막으로 마친 녹음 하나만 남기고, 새 녹음을 시작할 때 지운다(업로드할 녹음은 [IosRecordingFiles]가 옮겨 둔다).
 */
@OptIn(ExperimentalForeignApi::class)
class IosAudioRecorder : AudioRecorder {
    private val files = NSFileManager.defaultManager
    private val directory = (NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String) +
        "/" + DIRECTORY
    private var recorder: AVAudioRecorder? = null
    private var output: String? = null

    override val measuresLevel: Boolean = true

    override fun start() {
        cancel()
        files.removeItemAtPath(directory, error = null)
        check(files.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)) {
            "녹음 폴더를 만들지 못했어요"
        }
        val path = "$directory/practice-${(NSDate().timeIntervalSince1970 * 1000).toLong()}.m4a"
        val settings = mapOf<Any?, Any?>(
            AVFormatIDKey to NSNumber(unsignedInt = kAudioFormatMPEG4AAC),
            AVSampleRateKey to SAMPLE_RATE,
            AVNumberOfChannelsKey to 1,
            AVEncoderBitRateKey to BIT_RATE,
        )
        val sessionStarted = IosAudioSession.startRecording()
        val audioRecorder = AVAudioRecorder(uRL = NSURL.fileURLWithPath(path), settings = settings, error = null)
        audioRecorder.meteringEnabled = true
        if (!sessionStarted || !audioRecorder.prepareToRecord() || !audioRecorder.record()) {
            audioRecorder.stop()
            files.removeItemAtPath(path, error = null)
            IosAudioSession.stopRecording()
            throw IllegalStateException("녹음을 시작하지 못했어요")
        }
        recorder = audioRecorder
        output = path
    }

    override fun pause() {
        recorder?.pause()
    }

    override fun resume() {
        val audioRecorder = recorder ?: return
        check(audioRecorder.record()) { "녹음을 이어 가지 못했어요" }
    }

    override fun stop(): RecordedAudio? {
        val audioRecorder = recorder ?: return null
        val path = output
        recorder = null
        output = null
        audioRecorder.stop()
        IosAudioSession.stopRecording()
        val bytes = path?.let(::fileSize) ?: 0L
        if (path == null || bytes <= 0L) {
            path?.let { files.removeItemAtPath(it, error = null) }
            return null
        }
        return RecordedAudio(path, bytes)
    }

    override fun cancel() {
        val audioRecorder = recorder ?: return
        recorder = null
        audioRecorder.stop()
        audioRecorder.deleteRecording()
        IosAudioSession.stopRecording()
        output?.let { files.removeItemAtPath(it, error = null) }
        output = null
    }

    /** 마지막 측정 뒤의 최고 크기(dBFS)를 -50dB → 0, 0dB → 1로 옮긴다. Android의 `maxAmplitude`와 같은 기준이다. */
    override fun level(): Float {
        val audioRecorder = recorder ?: return 0f
        if (!audioRecorder.recording) return 0f
        audioRecorder.updateMeters()
        val dbfs = audioRecorder.peakPowerForChannel(0uL).toDouble()
        return ((dbfs - SILENCE_DBFS) / -SILENCE_DBFS).toFloat().coerceIn(0f, 1f)
    }

    private fun fileSize(path: String): Long =
        (files.attributesOfItemAtPath(path, error = null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L

    private companion object {
        const val DIRECTORY = "recordings"
        const val SAMPLE_RATE = 44_100.0
        const val BIT_RATE = 128_000
        const val SILENCE_DBFS = -50.0
    }
}

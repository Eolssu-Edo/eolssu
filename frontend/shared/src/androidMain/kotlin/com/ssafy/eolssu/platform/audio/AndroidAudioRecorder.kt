package com.ssafy.eolssu.platform.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.RecordedAudio
import java.io.File
import kotlin.math.log10

/**
 * `MediaRecorder`로 모노·AAC 128kbps·44.1kHz m4a를 앱 캐시의 `recordings/`에 녹음한다.
 * 업로드가 붙기 전이라 마지막으로 마친 녹음 하나만 남기고, 새 녹음을 시작할 때 지운다.
 */
class AndroidAudioRecorder(context: Context) : AudioRecorder {
    private val context = context.applicationContext
    private val directory = File(this.context.cacheDir, DIRECTORY)
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    override fun start() {
        cancel()
        directory.mkdirs()
        directory.listFiles()?.forEach { it.delete() }
        val file = File(directory, "practice-${System.currentTimeMillis()}.m4a")
        val mediaRecorder = newMediaRecorder()
        try {
            mediaRecorder.apply {
                // MIC는 기기에 따라 잡음 제거가 켜져 길게 부는 소리를 줄일 수 있다.
                // VOICE_RECOGNITION은 Android 호환성 정의상 잡음 제거·자동 음량 조절을 끄도록 되어 있다.
                setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(SAMPLE_RATE)
                setAudioEncodingBitRate(BIT_RATE)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            mediaRecorder.release()
            file.delete()
            throw e
        }
        recorder = mediaRecorder
        output = file
    }

    override fun pause() {
        recorder?.pause()
    }

    override fun resume() {
        recorder?.resume()
    }

    override fun stop(): RecordedAudio? {
        val mediaRecorder = recorder ?: return null
        val file = output
        recorder = null
        output = null
        // 소리 데이터가 하나도 없으면 stop()이 RuntimeException을 던진다.
        val stopped = runCatching { mediaRecorder.stop() }.isSuccess
        mediaRecorder.release()
        if (!stopped || file == null || !file.exists()) {
            file?.delete()
            return null
        }
        return RecordedAudio(file.absolutePath, file.length())
    }

    override fun cancel() {
        val mediaRecorder = recorder ?: return
        recorder = null
        runCatching { mediaRecorder.stop() }
        mediaRecorder.release()
        output?.delete()
        output = null
    }

    /** `maxAmplitude`(0~32767)를 dBFS로 바꿔 -50dB → 0, 0dB → 1로 옮긴다. */
    override fun level(): Float {
        val amplitude = recorder?.let { runCatching { it.maxAmplitude }.getOrNull() } ?: return 0f
        if (amplitude <= 0) return 0f
        val dbfs = 20 * log10(amplitude / MAX_AMPLITUDE)
        return ((dbfs - SILENCE_DBFS) / -SILENCE_DBFS).toFloat().coerceIn(0f, 1f)
    }

    private fun newMediaRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()

    private companion object {
        const val DIRECTORY = "recordings"
        const val SAMPLE_RATE = 44_100
        const val BIT_RATE = 128_000
        const val MAX_AMPLITUDE = 32767.0
        const val SILENCE_DBFS = -50.0
    }
}

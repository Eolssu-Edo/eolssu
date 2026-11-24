package com.ssafy.eolssu.backend.feature.practice.domain

import java.nio.file.Path
import kotlin.math.roundToLong

data class RecordingMetadata(
    val bytes: Long,
    val durationMs: Long,
    val sampleRate: Int,
    val channels: Int,
)

/** 업로드한 파일의 형식. 웹 녹음의 WAV는 저장 전에 서버가 AAC m4a로 바꾼다(S15P31E102-1272). */
enum class RecordingFormat { AAC_MP4, PCM_WAV }

sealed interface RecordingCheck {
    data class Accepted(val metadata: RecordingMetadata, val format: RecordingFormat) : RecordingCheck
    data object TooLarge : RecordingCheck
    data object UnsupportedFormat : RecordingCheck
    data object InvalidDuration : RecordingCheck
}

/**
 * 녹음 파일 규격(PRA-02)을 판정한다. 크기 → 형식 → 길이 순서이며 크기는 파일을 열기 전에 본다.
 * 형식은 MP4 계열 컨테이너의 AAC(앱 녹음) 또는 WAV 컨테이너의 PCM16(`pcm_s16le`, 웹 녹음)이다.
 * WAV도 업로드한 파일 그대로 크기·길이를 판정한다. 변환은 [com.ssafy.eolssu.backend.feature.practice.application.UploadRecording]이 한다.
 * 샘플레이트·채널 수를 양수로 읽지 못한 오디오는 형식 위반이다. 메타데이터 4칸은 전부 양수여야 저장된다(V3 CHECK).
 * 길이 상한은 600초이고 AAC 앞뒤 패딩으로 1초 미만 길어지는 것은 허용한다.
 * 하한은 1ms다. 그보다 짧으면 저장할 길이가 0ms로 반올림돼 V3의 메타데이터 CHECK(전부 양수)에 걸린다.
 */
class RecordingFileValidator(
    private val probe: AudioProbe,
    private val maxBytes: Long = MAX_BYTES,
    private val maxDurationSecondsExclusive: Double = MAX_DURATION_SECONDS_EXCLUSIVE,
) {
    fun validate(file: Path, sizeBytes: Long): RecordingCheck {
        if (sizeBytes > maxBytes) return RecordingCheck.TooLarge

        val result = probe.probe(file) ?: return RecordingCheck.UnsupportedFormat
        val format = formatOf(result) ?: return RecordingCheck.UnsupportedFormat
        val sampleRate = result.sampleRate?.takeIf { it > 0 } ?: return RecordingCheck.UnsupportedFormat
        val channels = result.channels?.takeIf { it > 0 } ?: return RecordingCheck.UnsupportedFormat

        val seconds = result.durationSeconds
        if (seconds == null || seconds < MIN_DURATION_SECONDS || seconds >= maxDurationSecondsExclusive) {
            return RecordingCheck.InvalidDuration
        }

        return RecordingCheck.Accepted(
            RecordingMetadata(
                bytes = sizeBytes,
                durationMs = (seconds * 1_000).roundToLong(),
                sampleRate = sampleRate,
                channels = channels,
            ),
            format,
        )
    }

    private fun formatOf(result: AudioProbeResult): RecordingFormat? = when {
        result.formatNames.any { it in MP4_FORMAT_NAMES } && result.audioCodec == AAC -> RecordingFormat.AAC_MP4
        WAV_FORMAT_NAME in result.formatNames && result.audioCodec == PCM_S16LE -> RecordingFormat.PCM_WAV
        else -> null
    }

    companion object {
        const val MAX_BYTES: Long = 20L * 1024 * 1024
        const val MIN_DURATION_SECONDS: Double = 0.001
        const val MAX_DURATION_SECONDS_EXCLUSIVE: Double = 601.0
        private const val AAC = "aac"
        private val MP4_FORMAT_NAMES = setOf("mp4", "m4a")
        private const val PCM_S16LE = "pcm_s16le"
        private const val WAV_FORMAT_NAME = "wav"
    }
}

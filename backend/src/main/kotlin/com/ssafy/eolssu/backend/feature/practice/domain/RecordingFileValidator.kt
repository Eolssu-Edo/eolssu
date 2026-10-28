package com.ssafy.eolssu.backend.feature.practice.domain

import java.nio.file.Path
import kotlin.math.roundToLong

data class RecordingMetadata(
    val bytes: Long,
    val durationMs: Long,
    val sampleRate: Int?,
    val channels: Int?,
)

sealed interface RecordingCheck {
    data class Accepted(val metadata: RecordingMetadata) : RecordingCheck
    data object TooLarge : RecordingCheck
    data object UnsupportedFormat : RecordingCheck
    data object InvalidDuration : RecordingCheck
}

/**
 * 녹음 파일 규격(PRA-02)을 판정한다. 크기 → 형식 → 길이 순서이며 크기는 파일을 열기 전에 본다.
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
        if (result.formatNames.none { it in MP4_FORMAT_NAMES } || result.audioCodec != AAC) {
            return RecordingCheck.UnsupportedFormat
        }

        val seconds = result.durationSeconds
        if (seconds == null || seconds < MIN_DURATION_SECONDS || seconds >= maxDurationSecondsExclusive) {
            return RecordingCheck.InvalidDuration
        }

        return RecordingCheck.Accepted(
            RecordingMetadata(
                bytes = sizeBytes,
                durationMs = (seconds * 1_000).roundToLong(),
                sampleRate = result.sampleRate,
                channels = result.channels,
            ),
        )
    }

    companion object {
        const val MAX_BYTES: Long = 20L * 1024 * 1024
        const val MIN_DURATION_SECONDS: Double = 0.001
        const val MAX_DURATION_SECONDS_EXCLUSIVE: Double = 601.0
        private const val AAC = "aac"
        private val MP4_FORMAT_NAMES = setOf("mp4", "m4a")
    }
}

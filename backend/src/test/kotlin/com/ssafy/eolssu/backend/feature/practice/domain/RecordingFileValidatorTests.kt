package com.ssafy.eolssu.backend.feature.practice.domain

import java.nio.file.Path
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class RecordingFileValidatorTests {
    private val file = Path.of("recording.m4a")
    private val aac = AudioProbeResult(
        formatNames = setOf("mov", "mp4", "m4a", "3gp", "3g2", "mj2"),
        audioCodec = "aac",
        sampleRate = 44_100,
        channels = 2,
        durationSeconds = 12.3456,
    )

    private class FakeProbe(private val result: AudioProbeResult?) : AudioProbe {
        var calls = 0
        override fun probe(file: Path): AudioProbeResult? {
            calls++
            return result
        }
    }

    @Test
    fun `accepts AAC in MP4 and returns metadata with rounded milliseconds`() {
        val result = RecordingFileValidator(FakeProbe(aac)).validate(file, sizeBytes = 1_000)

        assertEquals(
            RecordingCheck.Accepted(
                RecordingMetadata(bytes = 1_000, durationMs = 12_346, sampleRate = 44_100, channels = 2),
            ),
            result,
        )
    }

    @Test
    fun `size limit is 20 MiB inclusive and is checked before probing`() {
        val probe = FakeProbe(aac)
        val validator = RecordingFileValidator(probe)

        assertEquals(RecordingCheck.TooLarge, validator.validate(file, sizeBytes = 20_971_521))
        assertEquals(0, probe.calls)
        assertEquals(RecordingCheck.Accepted::class, validator.validate(file, sizeBytes = 20_971_520)::class)
    }

    @Test
    fun `unreadable file, non MP4 container and non AAC audio are unsupported`() {
        fun check(result: AudioProbeResult?) = RecordingFileValidator(FakeProbe(result)).validate(file, sizeBytes = 1_000)

        assertEquals(RecordingCheck.UnsupportedFormat, check(null))
        assertEquals(RecordingCheck.UnsupportedFormat, check(aac.copy(formatNames = setOf("mp3"))))
        assertEquals(RecordingCheck.UnsupportedFormat, check(aac.copy(audioCodec = "mp3")))
        assertEquals(RecordingCheck.UnsupportedFormat, check(aac.copy(audioCodec = null)))
    }

    @Test
    fun `AAC without a readable positive sample rate and channel count is unsupported`() {
        fun check(result: AudioProbeResult) = RecordingFileValidator(FakeProbe(result)).validate(file, sizeBytes = 1_000)

        // 메타데이터 4칸은 전부 양수여야 저장된다(V3 CHECK). 못 읽은 값은 저장 단계의 500이 아니라 415로 거부한다.
        for (broken in listOf(aac.copy(sampleRate = null), aac.copy(sampleRate = 0), aac.copy(channels = null), aac.copy(channels = 0))) {
            assertEquals(RecordingCheck.UnsupportedFormat, check(broken), broken.toString())
        }
        // 형식 판정이 길이 판정보다 먼저다.
        assertEquals(RecordingCheck.UnsupportedFormat, check(aac.copy(channels = null, durationSeconds = null)))
    }

    @Test
    fun `duration must be at least 1 ms and below 601 seconds`() {
        fun check(seconds: Double?) =
            RecordingFileValidator(FakeProbe(aac.copy(durationSeconds = seconds))).validate(file, sizeBytes = 1_000)

        assertEquals(RecordingCheck.InvalidDuration, check(null))
        assertEquals(RecordingCheck.InvalidDuration, check(0.0))
        // 1ms 미만은 저장할 길이가 0ms로 반올림될 수 있어 V3 CHECK(전부 양수) 전에 거부한다.
        assertEquals(RecordingCheck.InvalidDuration, check(0.0004))
        assertEquals(RecordingCheck.InvalidDuration, check(0.000999))
        assertEquals(1, (check(0.001) as RecordingCheck.Accepted).metadata.durationMs)
        assertEquals(RecordingCheck.InvalidDuration, check(601.0))
        assertEquals(RecordingCheck.Accepted::class, check(600.999)::class)
    }
}

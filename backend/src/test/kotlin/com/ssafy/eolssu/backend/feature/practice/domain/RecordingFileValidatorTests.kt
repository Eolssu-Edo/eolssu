package com.ssafy.eolssu.backend.feature.practice.domain

import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `duration must be above zero and below 601 seconds`() {
        fun check(seconds: Double?) =
            RecordingFileValidator(FakeProbe(aac.copy(durationSeconds = seconds))).validate(file, sizeBytes = 1_000)

        assertEquals(RecordingCheck.InvalidDuration, check(null))
        assertEquals(RecordingCheck.InvalidDuration, check(0.0))
        assertEquals(RecordingCheck.InvalidDuration, check(601.0))
        assertEquals(RecordingCheck.Accepted::class, check(600.999)::class)
        assertFalse(check(0.001) is RecordingCheck.InvalidDuration)
    }
}

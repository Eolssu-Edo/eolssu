package com.ssafy.eolssu.backend.feature.practice.infrastructure.media

import com.ssafy.eolssu.backend.feature.practice.domain.AudioProbeException
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingCheck
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingFileValidator
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingMetadata
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.fileSize
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** 실제 ffmpeg로 만든 파일을 ffprobe로 검사한다. ffmpeg·ffprobe가 없으면 실패한다(건너뛰지 않음). */
@Tag("media")
class FfprobeAudioProbeMediaTests {
    @TempDir
    private lateinit var dir: Path

    private val validator = RecordingFileValidator(FfprobeAudioProbe())

    private fun ffmpeg(name: String, vararg args: String): Path {
        val out = dir.resolve(name)
        val process = ProcessBuilder(listOf("ffmpeg", "-hide_banner", "-loglevel", "error", "-y", *args, out.toString()))
            .redirectErrorStream(true)
            .start()
        val log = process.inputStream.bufferedReader().readText()
        check(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) { "ffmpeg failed for $name: $log" }
        return out
    }

    private fun sine(name: String, sampleRate: Int, channels: Int, codec: String = "aac") = ffmpeg(
        name, "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=$sampleRate:duration=2",
        "-ac", "$channels", "-c:a", codec,
    )

    private fun silence(name: String, seconds: String) = ffmpeg(
        name, "-f", "lavfi", "-i", "anullsrc=r=8000:cl=mono", "-t", seconds, "-c:a", "aac", "-b:a", "8k",
    )

    private fun check(file: Path) = validator.validate(file, file.fileSize())

    @Test
    fun `accepts 44_1kHz stereo and 48kHz mono AAC m4a with metadata`() {
        val stereo = sine("stereo.m4a", 44_100, 2)
        val mono = sine("mono.m4a", 48_000, 1)

        assertEquals(RecordingCheck.Accepted(RecordingMetadata(stereo.fileSize(), 2_000, 44_100, 2)), check(stereo))
        assertEquals(RecordingCheck.Accepted(RecordingMetadata(mono.fileSize(), 2_000, 48_000, 1)), check(mono))
    }

    @Test
    fun `rejects files that are not AAC in MP4 by content, not extension`() {
        val mp3 = sine("source.mp3", 44_100, 1, codec = "libmp3lame")
        val renamedMp3 = Files.copy(mp3, dir.resolve("renamed.m4a"))
        val alac = sine("alac.m4a", 44_100, 1, codec = "alac")
        val noAudio = silence("no-audio.m4a", "0")
        val truncated = Files.write(dir.resolve("truncated.m4a"), Files.readAllBytes(sine("full.m4a", 44_100, 1)).copyOf(3_000))
        val empty = Files.createFile(dir.resolve("empty.m4a"))

        for (file in listOf(renamedMp3, alac, noAudio, truncated, empty)) {
            assertEquals(RecordingCheck.UnsupportedFormat, check(file), file.fileName.toString())
        }
    }

    @Test
    fun `duration boundary passes 600_5 seconds and rejects 601 seconds`() {
        assertEquals(600_500, (check(silence("600_5.m4a", "600.5")) as RecordingCheck.Accepted).metadata.durationMs)
        assertEquals(RecordingCheck.InvalidDuration, check(silence("601.m4a", "601")))
    }

    @Test
    fun `size over 20 MiB is rejected before reading content`() {
        val oversized = Files.write(dir.resolve("big.m4a"), ByteArray(20_971_521))
        val limit = Files.write(dir.resolve("limit.m4a"), ByteArray(20_971_520))

        assertEquals(RecordingCheck.TooLarge, check(oversized))
        assertEquals(RecordingCheck.UnsupportedFormat, check(limit))
    }

    @Test
    fun `missing ffprobe binary is a tool failure, not a format rejection`() {
        val file = sine("ok.m4a", 44_100, 1)
        val broken = RecordingFileValidator(FfprobeAudioProbe(command = listOf(dir.resolve("no-ffprobe").toString())))

        assertFailsWith<AudioProbeException> { broken.validate(file, file.fileSize()) }
    }
}

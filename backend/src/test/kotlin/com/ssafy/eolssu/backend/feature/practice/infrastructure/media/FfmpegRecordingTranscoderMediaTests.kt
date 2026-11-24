package com.ssafy.eolssu.backend.feature.practice.infrastructure.media

import com.ssafy.eolssu.backend.feature.practice.domain.RecordingCheck
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingFileValidator
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingFormat
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingMetadata
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.fileSize
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** 웹 녹음 WAV를 실제 ffmpeg로 바꾸고 변환본을 ffprobe로 검사한다. ffmpeg·ffprobe가 없으면 실패한다(건너뛰지 않음). */
@Tag("media")
class FfmpegRecordingTranscoderMediaTests {
    @TempDir
    private lateinit var dir: Path

    private val validator = RecordingFileValidator(FfprobeAudioProbe())
    private val transcoder = FfmpegRecordingTranscoder()

    private fun ffmpeg(name: String, vararg args: String): Path {
        val out = dir.resolve(name)
        val process = ProcessBuilder(listOf("ffmpeg", "-hide_banner", "-loglevel", "error", "-y", *args, out.toString()))
            .redirectErrorStream(true)
            .start()
        val log = process.inputStream.bufferedReader().readText()
        check(process.waitFor(120, TimeUnit.SECONDS) && process.exitValue() == 0) { "ffmpeg failed for $name: $log" }
        return out
    }

    private fun wav(name: String, seconds: String, sampleRate: Int = 16_000, channels: Int = 1) = ffmpeg(
        name, "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=$sampleRate", "-t", seconds,
        "-ac", "$channels", "-c:a", "pcm_s16le",
    )

    private fun check(file: Path) = validator.validate(file, file.fileSize())

    private fun convert(source: Path): Path = dir.resolve("${source.fileName}.m4a").also {
        assertTrue(transcoder.toAac(source, it), "conversion failed for ${source.fileName}")
    }

    @Test
    fun `16kHz mono PCM16 WAV becomes AAC m4a that passes the recording check with the same length`() {
        val converted = convert(wav("web.wav", "2"))

        assertEquals(RecordingCheck.Accepted(RecordingMetadata(converted.fileSize(), 2_000, 16_000, 1), RecordingFormat.AAC_MP4), check(converted))
    }

    @Test
    fun `a 10 minute web recording fits in 20 MiB as WAV and stays within the duration limit after conversion`() {
        val source = wav("ten-minutes.wav", "600")
        val uploaded = check(source)
        val converted = check(convert(source))

        assertEquals(RecordingFormat.PCM_WAV, (uploaded as RecordingCheck.Accepted).format)
        assertEquals(600_000, uploaded.metadata.durationMs)
        assertEquals(RecordingFormat.AAC_MP4, (converted as RecordingCheck.Accepted).format)
        assertEquals(600_000, converted.metadata.durationMs)
        // 64kbps AAC라 WAV(약 19.2MB)보다 훨씬 작다.
        assertTrue(converted.metadata.bytes < 5_000_000, converted.metadata.bytes.toString())
    }

    @Test
    fun `stereo WAV is mixed down to mono and keeps its sample rate`() {
        val converted = convert(wav("stereo.wav", "2", sampleRate = 48_000, channels = 2))

        assertEquals(RecordingMetadata(converted.fileSize(), 2_000, 48_000, 1), (check(converted) as RecordingCheck.Accepted).metadata)
    }

    @Test
    fun `files that ffmpeg cannot read as a whole WAV are rejected, not converted`() {
        val m4a = ffmpeg("real.m4a", "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=16000:duration=2", "-c:a", "aac")
        val renamedM4a = Files.copy(m4a, dir.resolve("renamed.wav"))
        val junk = Files.write(dir.resolve("junk.wav"), ByteArray(5_000) { (it * 31).toByte() })
        val empty = Files.createFile(dir.resolve("empty.wav"))
        // 헤더의 데이터 길이보다 짧게 잘린 WAV는 -xerror로 일부만 바꾸지 않고 실패한다.
        val truncated = Files.write(dir.resolve("truncated.wav"), Files.readAllBytes(wav("full.wav", "2")).copyOf(30_000))

        for (source in listOf(renamedM4a, junk, empty, truncated)) {
            assertFalse(transcoder.toAac(source, dir.resolve("${source.fileName}.m4a")), source.fileName.toString())
        }
    }
}

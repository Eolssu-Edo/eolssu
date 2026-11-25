package com.ssafy.eolssu.backend.feature.practice.infrastructure.media

import com.ssafy.eolssu.backend.feature.practice.domain.RecordingTranscodeException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** ffmpeg 대신 [FakeFfprobe]로 종료 코드·지연을 흉내 낸다. 실제 변환 검사는 FfmpegRecordingTranscoderMediaTests. */
class FfmpegRecordingTranscoderTests {
    @TempDir
    private lateinit var dir: Path

    private val source get() = Files.createTempFile(dir, "rec", ".wav")
    private val target get() = dir.resolve("out.m4a")

    @Test
    fun `zero exit means converted and non zero exit means the WAV cannot be converted`() {
        val ok = FfmpegRecordingTranscoder(command = FakeFfprobe.command("sleep", "0", dir.resolve("ok.pid").toString()))
        val broken = FfmpegRecordingTranscoder(command = FakeFfprobe.command("fail"))

        assertTrue(ok.toAac(source, target))
        assertFalse(broken.toAac(source, target))
    }

    @Test
    fun `missing ffmpeg binary is a tool failure, not a format rejection`() {
        val missing = FfmpegRecordingTranscoder(command = listOf(dir.resolve("no-ffmpeg").toString()))

        assertIs<RecordingTranscodeException>(runCatching { missing.toAac(source, target) }.exceptionOrNull())
    }

    @Test
    fun `slow ffmpeg is stopped and reported as tool failure`() {
        val pidFile = dir.resolve("slow.pid")
        val transcoder = FfmpegRecordingTranscoder(
            command = FakeFfprobe.command("sleep", "30000", pidFile.toString()),
            timeout = Duration.ofSeconds(2),
        )

        assertIs<RecordingTranscodeException>(runCatching { transcoder.toAac(source, target) }.exceptionOrNull())
        assertProcessGone(pidFile)
    }

    @Test
    fun `interrupt while waiting kills ffmpeg and keeps the interrupt flag`() {
        val pidFile = dir.resolve("interrupted.pid")
        val transcoder = FfmpegRecordingTranscoder(
            command = FakeFfprobe.command("sleep", "30000", pidFile.toString()),
            timeout = Duration.ofSeconds(60),
        )
        val input = source
        var error: Throwable? = null
        var interrupted = false
        val worker = Thread.ofPlatform().start {
            error = runCatching { transcoder.toAac(input, target) }.exceptionOrNull()
            interrupted = Thread.currentThread().isInterrupted
        }

        waitUntil { pidFile.exists() && pidFile.readText().isNotBlank() }
        worker.interrupt()
        worker.join(10_000)

        assertIs<RecordingTranscodeException>(error)
        assertTrue(interrupted)
        assertProcessGone(pidFile)
    }

    private fun assertProcessGone(pidFile: Path) {
        val pid = pidFile.readText().trim().toLong()
        waitUntil { ProcessHandle.of(pid).map { !it.isAlive }.orElse(true) }
        assertFalse(ProcessHandle.of(pid).map { it.isAlive }.orElse(false), "fake ffmpeg $pid is still running")
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(20)
    }
}

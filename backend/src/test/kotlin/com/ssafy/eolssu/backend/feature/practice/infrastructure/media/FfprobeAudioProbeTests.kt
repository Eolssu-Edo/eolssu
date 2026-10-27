package com.ssafy.eolssu.backend.feature.practice.infrastructure.media

import com.ssafy.eolssu.backend.feature.practice.domain.AudioProbeException
import com.ssafy.eolssu.backend.feature.practice.domain.AudioProbeResult
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** ffprobe 대신 [FakeFfprobe]로 출력·종료 코드·지연을 흉내 낸다. 실제 ffprobe 검사는 FfprobeAudioProbeMediaTests. */
class FfprobeAudioProbeTests {
    @TempDir
    private lateinit var dir: Path

    private val file get() = Files.createTempFile(dir, "rec", ".m4a")

    @Test
    fun `parses container names and first audio stream from ffprobe json`() {
        val json = dir.resolve("out.json")
        json.writeText(
            """
            {"streams":[{"codec_type":"video","codec_name":"h264"},
                        {"codec_type":"audio","codec_name":"aac","sample_rate":"48000","channels":1,"duration":"3.2"}],
             "format":{"format_name":"mov,mp4,m4a,3gp,3g2,mj2","duration":"3.250000"}}
            """.trimIndent(),
        )
        val probe = FfprobeAudioProbe(command = FakeFfprobe.command("print", json.toString()))

        assertEquals(
            AudioProbeResult(setOf("mov", "mp4", "m4a", "3gp", "3g2", "mj2"), "aac", 48_000, 1, 3.25),
            probe.probe(file),
        )
    }

    @Test
    fun `non zero exit means unreadable file`() {
        assertNull(FfprobeAudioProbe(command = FakeFfprobe.command("fail")).probe(file))
    }

    @Test
    fun `slow ffprobe is stopped and reported as tool failure`() {
        val pidFile = dir.resolve("slow.pid")
        val probe = FfprobeAudioProbe(
            command = FakeFfprobe.command("sleep", "30000", pidFile.toString()),
            timeout = Duration.ofSeconds(2),
        )

        assertIs<AudioProbeException>(runCatching { probe.probe(file) }.exceptionOrNull())
        assertProcessGone(pidFile)
    }

    @Test
    fun `interrupt while waiting kills ffprobe and keeps the interrupt flag`() {
        val pidFile = dir.resolve("interrupted.pid")
        val probe = FfprobeAudioProbe(
            command = FakeFfprobe.command("sleep", "30000", pidFile.toString()),
            timeout = Duration.ofSeconds(60),
        )
        var error: Throwable? = null
        var interrupted = false
        val worker = Thread.ofPlatform().start {
            error = runCatching { probe.probe(file) }.exceptionOrNull()
            interrupted = Thread.currentThread().isInterrupted
        }

        waitUntil { pidFile.exists() && pidFile.readText().isNotBlank() }
        worker.interrupt()
        worker.join(10_000)

        assertIs<AudioProbeException>(error)
        assertTrue(interrupted)
        assertProcessGone(pidFile)
    }

    @Test
    fun `default timeout lets a normal one second run finish`() {
        val probe = FfprobeAudioProbe(command = FakeFfprobe.command("sleep", "1000", dir.resolve("ok.pid").toString()))

        assertEquals(AudioProbeResult(emptySet(), null, null, null, null), probe.probe(file))
    }

    private fun assertProcessGone(pidFile: Path) {
        val pid = pidFile.readText().trim().toLong()
        waitUntil { ProcessHandle.of(pid).map { !it.isAlive }.orElse(true) }
        assertFalse(ProcessHandle.of(pid).map { it.isAlive }.orElse(false), "fake ffprobe $pid is still running")
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(20)
    }
}

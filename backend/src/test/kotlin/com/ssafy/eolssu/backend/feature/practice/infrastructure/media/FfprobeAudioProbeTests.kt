package com.ssafy.eolssu.backend.feature.practice.infrastructure.media

import com.ssafy.eolssu.backend.feature.practice.domain.AudioProbeException
import com.ssafy.eolssu.backend.feature.practice.domain.AudioProbeResult
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** ffprobe 대신 셸 스크립트로 출력·종료 코드·지연을 흉내 낸다. 실제 ffprobe 검사는 FfprobeAudioProbeMediaTests. */
class FfprobeAudioProbeTests {
    @TempDir
    private lateinit var dir: Path

    private fun fakeFfprobe(body: String): String {
        val script = dir.resolve("fake-ffprobe")
        script.writeText("#!/bin/sh\n$body\n")
        script.toFile().setExecutable(true)
        return script.toString()
    }

    private val file get() = Files.createTempFile(dir, "rec", ".m4a")

    @Test
    fun `parses container names and first audio stream from ffprobe json`() {
        val json = """
            {"streams":[{"codec_type":"video","codec_name":"h264"},
                        {"codec_type":"audio","codec_name":"aac","sample_rate":"48000","channels":1,"duration":"3.2"}],
             "format":{"format_name":"mov,mp4,m4a,3gp,3g2,mj2","duration":"3.250000"}}
        """.trimIndent()
        val probe = FfprobeAudioProbe(command = fakeFfprobe("cat <<'EOF'\n$json\nEOF"))

        assertEquals(
            AudioProbeResult(setOf("mov", "mp4", "m4a", "3gp", "3g2", "mj2"), "aac", 48_000, 1, 3.25),
            probe.probe(file),
        )
    }

    @Test
    fun `non zero exit means unreadable file`() {
        assertNull(FfprobeAudioProbe(command = fakeFfprobe("echo broken >&2; exit 1")).probe(file))
    }

    @Test
    fun `slow ffprobe is stopped and reported as tool failure`() {
        val probe = FfprobeAudioProbe(command = fakeFfprobe("sleep 5"), timeout = Duration.ofMillis(200))

        assertFailsWith<AudioProbeException> { probe.probe(file) }
    }

    @Test
    fun `default timeout lets a normal one second run finish`() {
        val probe = FfprobeAudioProbe(command = fakeFfprobe("sleep 1; echo '{}'"))

        assertEquals(AudioProbeResult(emptySet(), null, null, null, null), probe.probe(file))
    }
}

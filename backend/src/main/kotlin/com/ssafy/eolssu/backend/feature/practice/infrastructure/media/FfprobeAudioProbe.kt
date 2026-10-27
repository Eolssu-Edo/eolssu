package com.ssafy.eolssu.backend.feature.practice.infrastructure.media

import com.ssafy.eolssu.backend.feature.practice.domain.AudioProbe
import com.ssafy.eolssu.backend.feature.practice.domain.AudioProbeException
import com.ssafy.eolssu.backend.feature.practice.domain.AudioProbeResult
import java.io.IOException
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * ffprobe의 JSON 출력으로 컨테이너·첫 오디오 스트림을 읽는다.
 * ffprobe가 읽지 못한 파일은 null, ffprobe 실행 실패·시간 초과는 [AudioProbeException]이다.
 */
class FfprobeAudioProbe(
    /** 실행 파일과 앞 인자. 테스트는 OS에 관계없이 도는 가짜 ffprobe를 넣는다. */
    private val command: List<String> = listOf("ffprobe"),
    private val timeout: Duration = Duration.ofSeconds(10),
) : AudioProbe {
    private val mapper = JsonMapper.builder().build()

    override fun probe(file: Path): AudioProbeResult? {
        val process = try {
            ProcessBuilder(
                command + listOf(
                    "-v", "error", "-print_format", "json", "-show_format", "-show_streams",
                    // file: 접두어로 경로가 URL·다른 프로토콜로 해석되지 않게 한다.
                    "file:" + file.toAbsolutePath(),
                ),
            ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        } catch (e: IOException) {
            throw AudioProbeException("ffprobe를 실행할 수 없습니다: $command", e)
        }

        val output = readWithTimeout(process)
        if (process.exitValue() != 0) return null
        return parse(output)
    }

    // 출력은 별도 스레드에서 읽어, 출력이 많아도 막히지 않고 시간 초과를 지킨다.
    // 시간 초과·중단·읽기 실패는 모두 도구 실패다. 빈·잘린 출력을 형식 위반(415)으로 오판하지 않는다.
    private fun readWithTimeout(process: Process): String {
        var text: String? = null
        var readError: IOException? = null
        val reader = Thread.ofVirtual().start {
            try {
                text = process.inputStream.bufferedReader().use { it.readText() }
            } catch (e: IOException) {
                readError = e
            }
        }
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw AudioProbeException("ffprobe가 ${timeout.toSeconds()}초 안에 끝나지 않았습니다")
            }
            if (!reader.join(timeout)) throw AudioProbeException("ffprobe 출력을 다 읽지 못했습니다")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw AudioProbeException("ffprobe를 기다리는 중 중단됐습니다", e)
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
        readError?.let { throw AudioProbeException("ffprobe 출력을 읽지 못했습니다", it) }
        return text ?: throw AudioProbeException("ffprobe 출력을 읽지 못했습니다")
    }

    private fun parse(json: String): AudioProbeResult? {
        val root = try {
            mapper.readTree(json)
        } catch (e: JacksonException) {
            return null
        }
        val format = root.path("format")
        val audio = root.path("streams").firstOrNull { it.path("codec_type").stringOrNull() == "audio" }
        return AudioProbeResult(
            formatNames = format.path("format_name").stringOrNull()?.split(',')?.toSet().orEmpty(),
            audioCodec = audio?.path("codec_name")?.stringOrNull(),
            sampleRate = audio?.path("sample_rate")?.stringOrNull()?.toIntOrNull(),
            channels = audio?.path("channels")?.takeIf { it.isNumber }?.asInt(),
            durationSeconds = (format.path("duration").stringOrNull() ?: audio?.path("duration")?.stringOrNull())
                ?.toDoubleOrNull(),
        )
    }

    private fun JsonNode.stringOrNull(): String? = if (isString) asString() else null
}

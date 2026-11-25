package com.ssafy.eolssu.backend.feature.practice.infrastructure.media

import com.ssafy.eolssu.backend.feature.practice.domain.RecordingTranscodeException
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingTranscoder
import java.io.IOException
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * ffmpeg로 WAV를 모노 AAC(64kbps) m4a로 바꾼다. 샘플레이트는 AAC가 지원하면 그대로 둔다.
 * ffmpeg가 0이 아닌 코드로 끝나면(읽을 수 없거나 잘린 WAV 등) false, 실행 실패·시간 초과는 [RecordingTranscodeException]이다.
 * 10분 WAV(16kHz 모노)는 Apple M4에서 약 4.6초 걸린다. 상한 30초는 느린 서버 CPU와 부하를 감안한 여유다.
 */
class FfmpegRecordingTranscoder(
    /** 실행 파일과 앞 인자. 테스트는 OS에 관계없이 도는 가짜 프로세스를 넣는다. */
    private val command: List<String> = listOf("ffmpeg"),
    private val timeout: Duration = Duration.ofSeconds(30),
) : RecordingTranscoder {
    override fun toAac(source: Path, target: Path): Boolean {
        val process = try {
            ProcessBuilder(
                command + listOf(
                    "-nostdin", "-hide_banner", "-v", "error", "-xerror", "-y",
                    // 입력은 WAV로만 읽고 file: 외 프로토콜을 막는다. -xerror는 손상된 패킷을 건너뛰지 않고 실패로 끝낸다.
                    "-protocol_whitelist", "file", "-f", "wav", "-i", "file:" + source.toAbsolutePath(),
                    "-map", "0:a:0", "-ac", "1", "-c:a", "aac", "-b:a", "64k",
                    "-movflags", "+faststart", "-f", "mp4", "file:" + target.toAbsolutePath(),
                ),
            ).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        } catch (e: IOException) {
            throw RecordingTranscodeException("ffmpeg를 실행할 수 없습니다: $command", e)
        }

        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw RecordingTranscodeException("ffmpeg가 ${timeout.toSeconds()}초 안에 끝나지 않았습니다")
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RecordingTranscodeException("ffmpeg를 기다리는 중 중단됐습니다", e)
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
        return process.exitValue() == 0
    }
}

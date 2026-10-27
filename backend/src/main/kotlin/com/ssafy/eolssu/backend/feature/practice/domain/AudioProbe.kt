package com.ssafy.eolssu.backend.feature.practice.domain

import java.nio.file.Path

/** 녹음 파일의 컨테이너·오디오 정보를 읽는다. 오디오로 읽을 수 없는 파일이면 null이다. */
fun interface AudioProbe {
    fun probe(file: Path): AudioProbeResult?
}

data class AudioProbeResult(
    val formatNames: Set<String>,
    val audioCodec: String?,
    val sampleRate: Int?,
    val channels: Int?,
    val durationSeconds: Double?,
)

/** 검사 도구를 실행할 수 없거나 시간 안에 끝나지 않은 경우. 파일 규격 위반과 구분한다. */
class AudioProbeException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

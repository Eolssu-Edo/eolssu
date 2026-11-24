package com.ssafy.eolssu.backend.feature.practice.domain

import java.nio.file.Path

/** 업로드한 WAV(PCM16, 웹 녹음)를 저장용 AAC m4a로 바꾼다. 저장·재생·AI 입력을 m4a 하나로 유지한다. */
fun interface RecordingTranscoder {
    /** [source]를 AAC m4a로 바꿔 [target]에 쓴다. 바꿀 수 없는 내용이면 false다. */
    fun toAac(source: Path, target: Path): Boolean
}

/** 변환 도구를 실행할 수 없거나 시간 안에 끝나지 않은 경우. 파일 규격 위반과 구분한다. */
class RecordingTranscodeException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

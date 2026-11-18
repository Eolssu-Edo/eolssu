package com.ssafy.eolssu.backend.feature.practice.domain

import com.ssafy.eolssu.backend.common.EvaluationTimingId
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime

/** 업로드 판정(PRA-02 1~3번)에 쓰는 세션 상태. 시각은 UTC다. */
data class RecordingUploadTarget(
    val status: String,
    val failReason: String?,
    val startedAt: LocalDateTime,
) {
    /** 만료: `RECORDING`이고 `started_at` + 40분이 지났거나, 배치가 이미 `UPLOAD_TIMEOUT`으로 정리했다. 정확히 40분은 아직 만료가 아니다. */
    fun isExpired(now: LocalDateTime) =
        (status == RECORDING && startedAt.plus(RECORDING_UPLOAD_WINDOW) < now) || isTimedOut()

    fun isTimedOut() = status == FAILED && failReason == UPLOAD_TIMEOUT

    companion object {
        const val RECORDING = "RECORDING"
        const val FAILED = "FAILED"
    }
}

/** 저장에 성공한 원본. `uploadedAt`은 `ended_at`과 분석 시도 시작 시각으로 함께 쓴다(서버 UTC). */
data class UploadedRecording(
    val key: String,
    val metadata: RecordingMetadata,
    val uploadedAt: LocalDateTime,
    val timingId: EvaluationTimingId? = null,
)

interface RecordingUploadRepository {
    /** 회원 본인의 세션. 없거나 다른 회원의 세션이면 null. */
    fun findOwned(sessionId: Long, userId: Long): RecordingUploadTarget?

    /**
     * 세션이 아직 `RECORDING`이면 원본 키·메타데이터·`ended_at`을 기록하고 `PENDING`으로 바꾸며 분석 시도를 시작한다
     * (`analysis_attempt` + 1, `analysis_started_at`). 한 번의 조건부 갱신이다. 바꿨으면 true.
     * 바꿨으면 같은 트랜잭션 안에서 [RecordingUploaded]를 발행한다. 분석(PRA-03)은 커밋 뒤에만 시작한다.
     */
    fun markUploaded(sessionId: Long, userId: Long, recording: UploadedRecording): Boolean
}

/**
 * 녹음 원본 저장소(S3 호환). 키는 `recordings/{sessionId}/{UUID}.m4a`이고 버킷은 공개하지 않는다.
 * 서명 URL은 녹음 접근 토큰이라 로그·예외 메시지에 남기지 않는다.
 */
interface RecordingStorage {
    /** 실패하면 [RecordingStorageException]. */
    fun put(key: String, file: Path)

    /** 없는 키는 성공으로 본다. */
    fun delete(key: String)

    /** 자격 증명 없이 `ttl` 동안 읽을 수 있는 GET 서명 URL. */
    fun readUrl(key: String, ttl: Duration): String
}

/** 저장소에 연결할 수 없거나 쓰기가 거부됐다. 업로드는 503으로 답하고 같은 세션으로 다시 올릴 수 있다. */
class RecordingStorageException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** AI가 원본을 읽는 서명 URL의 유효 시간(PRA-02 원본 저장소). */
val RECORDING_READ_URL_TTL: Duration = Duration.ofMinutes(15)

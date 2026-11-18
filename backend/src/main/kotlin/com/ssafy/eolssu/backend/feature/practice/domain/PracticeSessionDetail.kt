package com.ssafy.eolssu.backend.feature.practice.domain

import com.ssafy.eolssu.backend.common.EvaluationTimingId
import java.time.LocalDateTime

/** 같은 녹음으로 분석을 다시 시도할 수 있는 실패 코드(PRA-03). 나머지는 다시 연주한다. */
private val RETRYABLE_FAIL_REASONS = setOf("TIMEOUT", "SERVER_ERROR")

/** 연습 세션·분석 상태 조회(PRA-03, API 명세 `GET /practice-sessions/{sessionId}`)에 쓰는 세션. 시각은 UTC. */
data class PracticeSessionDetail(
    val sessionId: Long,
    val userId: Long,
    val songId: Long,
    val bpm: Int,
    val bpmUnit: String,
    val startNoteIdx: Int,
    val endNoteIdx: Int,
    val status: String,
    val evaluationId: Long?,
    val failReason: String?,
    /** 녹음 원본이 있고 기록을 지우지 않았다. */
    val recordingAvailable: Boolean,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime?,
    val analysisStartedAt: LocalDateTime?,
    val savedAt: LocalDateTime?,
    val memo: String?,
    /** 저장한 기록의 상세를 처음 연 시각(REC-02 `isNew`). 응답에는 넣지 않는다. */
    val viewedAt: LocalDateTime? = null,
    val scoreVersion: Int = 1,
    val songTitle: String = "",
    val analysisTimingId: EvaluationTimingId? = null,
) {
    val retryable: Boolean
        get() = status == "FAILED" && failReason in RETRYABLE_FAIL_REASONS
}

interface PracticeSessionDetails {
    /** 회원 본인의 세션만 돌려준다. 없거나 다른 회원의 세션, 기록을 지운 세션(REC-04)이면 null. */
    fun findOwned(sessionId: Long, userId: Long): PracticeSessionDetail?

    /** 저장했고 지우지 않은 기록에 `viewed_at`이 비어 있을 때만 채운다. 채웠으면 true. */
    fun markViewed(sessionId: Long, userId: Long, viewedAt: LocalDateTime): Boolean
}

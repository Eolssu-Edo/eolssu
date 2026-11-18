package com.ssafy.eolssu.backend.feature.practice.application

import com.ssafy.eolssu.backend.feature.analysis.domain.DEFAULT_ANALYSIS_TIME_LIMIT
import com.ssafy.eolssu.backend.feature.practice.domain.PracticeSessionDetail
import com.ssafy.eolssu.backend.feature.practice.domain.PracticeSessionDetails
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.slf4j.LoggerFactory

/**
 * 연습 세션·분석 상태 조회(PRA-03). 앱이 분석 대기 중 2초마다 부른다.
 * 제한 시간을 넘긴 `PENDING`은 응답 전에 `TIMEOUT`으로 바꿔, 서버가 재시작돼도 모든 시도가 끝나게 한다.
 * 다른 회원의 세션은 상태를 바꾸지 않도록 소유를 먼저 확인한다. 기록을 지운 세션은 없는 세션과 같다.
 * 저장한 기록은 상세가 이 조회로 열리므로, 처음 조회한 시각을 한 번 남긴다(REC-02 `isNew`). 응답은 바꾸지 않는다.
 */
class GetPracticeSession(
    private val details: PracticeSessionDetails,
    /** 분석 저장소의 `expireOverdue`. 시도가 `startedAfter`보다 먼저 시작한 `PENDING`을 `TIMEOUT`으로 바꿨으면 true. */
    private val expireOverdue: (sessionId: Long, startedAfter: LocalDateTime) -> Boolean,
    private val clock: Clock,
    private val timeLimit: Duration = DEFAULT_ANALYSIS_TIME_LIMIT,
) {
    fun execute(userId: Long, sessionId: Long): PracticeSessionDetail? {
        val detail = details.findOwned(sessionId, userId) ?: return null
        // 저장은 DONE에서만 되므로 분석 대기 중 2초마다 오는 조회는 여기서 쓰지 않는다.
        if (detail.savedAt != null && detail.viewedAt == null) {
            details.markViewed(sessionId, userId, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
        }
        if (detail.status != "PENDING") return detail
        val startedAfter = LocalDateTime.ofInstant(clock.instant().minus(timeLimit), ZoneOffset.UTC)
        // 2초마다 오는 조회마다 쓰기를 보내지 않도록, 읽은 시작 시각이 마감 전이거나 시작 전이면 갱신을 건너뛴다.
        // 마감을 넘겼을 때의 최종 판정은 DB의 조건부 갱신이 한다.
        val analysisStartedAt = detail.analysisStartedAt ?: return detail
        if (!analysisStartedAt.isBefore(startedAfter)) return detail
        if (!expireOverdue(sessionId, startedAfter)) return detail
        // 분석 흐름의 시간 초과 기록과 같은 문구로 남겨 시간 초과 횟수를 함께 센다.
        log.info("Analysis timed out session={}", sessionId)
        return details.findOwned(sessionId, userId)
    }

    private companion object {
        val log = LoggerFactory.getLogger(GetPracticeSession::class.java)
    }
}

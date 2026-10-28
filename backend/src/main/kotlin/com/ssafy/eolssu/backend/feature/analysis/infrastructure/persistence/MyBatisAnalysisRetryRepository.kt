package com.ssafy.eolssu.backend.feature.analysis.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisRetryRepository
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisState
import com.ssafy.eolssu.backend.feature.analysis.domain.SERVER_ERROR
import java.time.LocalDateTime

/** 다시 시도는 조건부 갱신 한 문장이라 트랜잭션을 따로 두지 않는다. 행 잠금 덕분에 동시 요청 중 하나만 바뀐다. */
class MyBatisAnalysisRetryRepository(private val mapper: PracticeAnalysisMapper) : AnalysisRetryRepository {
    override fun findState(sessionId: Long, userId: Long) = mapper.findState(sessionId, userId)?.run { AnalysisState(status, failReason, attempt) }

    override fun startRetry(sessionId: Long, userId: Long, attempt: Int, startedAt: LocalDateTime) =
        mapper.startRetry(sessionId, userId, attempt, startedAt) == 1

    // 시작 시각을 마감 기준으로 넘겨 바로 그 시도가 PENDING일 때만 바꾼다(분석 결과 저장과 같은 조건).
    override fun failStart(sessionId: Long, attempt: Int, startedAt: LocalDateTime) =
        mapper.markFailed(sessionId, attempt, SERVER_ERROR, startedAt) == 1
}

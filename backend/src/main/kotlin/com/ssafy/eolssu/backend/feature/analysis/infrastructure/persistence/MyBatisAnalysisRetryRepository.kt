package com.ssafy.eolssu.backend.feature.analysis.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisRetryRepository
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisState
import java.time.LocalDateTime

/** 다시 시도는 조건부 갱신 한 문장이라 트랜잭션을 따로 두지 않는다. 행 잠금 덕분에 동시 요청 중 하나만 바뀐다. */
class MyBatisAnalysisRetryRepository(private val mapper: PracticeAnalysisMapper) : AnalysisRetryRepository {
    override fun findState(sessionId: Long, userId: Long) = mapper.findState(sessionId, userId)?.run { AnalysisState(status, failReason, attempt) }

    override fun startRetry(sessionId: Long, userId: Long, startedAt: LocalDateTime) = mapper.startRetry(sessionId, userId, startedAt) == 1
}
